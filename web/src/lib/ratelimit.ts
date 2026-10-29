import { q, q1 } from './db';

/**
 * Login throttling backed by Postgres — works across serverless instances
 * without a Redis/KV add-on.
 *
 * Policy: 8 failed attempts per username (or per IP) in 15 minutes → locked.
 */
const WINDOW = "15 minutes";
const MAX_FAILS = 8;

export type ThrottleState = { locked: boolean; retryInSeconds: number; failures: number };

export async function checkLoginThrottle(username: string, ip: string): Promise<ThrottleState> {
  try {
    const row = await q1<{ failures: number; last: Date | null }>(
      `SELECT count(*)::int AS failures, max(created_at) AS last
         FROM auth_attempt
        WHERE ok = false
          AND created_at > now() - interval '${WINDOW}'
          AND (lower(username) = lower($1) OR ($2 <> '' AND ip = $2))`,
      [username, ip]
    );
    const failures = row?.failures ?? 0;
    if (failures < MAX_FAILS) return { locked: false, retryInSeconds: 0, failures };

    const last = row?.last ? new Date(row.last).getTime() : Date.now();
    const unlockAt = last + 15 * 60 * 1000;
    return {
      locked: true,
      retryInSeconds: Math.max(1, Math.ceil((unlockAt - Date.now()) / 1000)),
      failures,
    };
  } catch (e) {
    // Never block logins because the limiter itself failed.
    console.error('[throttle] check failed', e);
    return { locked: false, retryInSeconds: 0, failures: 0 };
  }
}

export async function recordLoginAttempt(username: string, ip: string, ok: boolean): Promise<void> {
  try {
    await q(`INSERT INTO auth_attempt (username, ip, ok) VALUES ($1,$2,$3)`, [username.slice(0, 66), ip, ok]);
    if (ok) {
      // clear the slate for this identity
      await q(`DELETE FROM auth_attempt WHERE lower(username) = lower($1) AND ok = false`, [username]);
    }
    // opportunistic housekeeping
    await q(`DELETE FROM auth_attempt WHERE created_at < now() - interval '2 days'`);
  } catch (e) {
    console.error('[throttle] record failed', e);
  }
}
