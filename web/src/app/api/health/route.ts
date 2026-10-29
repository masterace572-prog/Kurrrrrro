import { NextResponse } from 'next/server';
import { q1, activeHost } from '@/lib/db';
import { getCurrentRelease } from '@/lib/release';

export const dynamic = 'force-dynamic';
export const runtime = 'nodejs';

/** GET /api/health — one glance at whether the deployment is wired up correctly. */
export async function GET() {
  const started = Date.now();
  const checks: Record<string, { ok: boolean; detail?: string }> = {};

  checks.env = {
    ok: Boolean(process.env.DATABASE_URL && process.env.AUTH_SECRET),
    detail: !process.env.DATABASE_URL
      ? 'DATABASE_URL missing'
      : !process.env.AUTH_SECRET
        ? 'AUTH_SECRET missing'
        : `ok · ${activeHost()}`,
  };

  try {
    const row = await q1<{ now: Date }>('SELECT now() AS now');
    checks.database = {
      ok: Boolean(row),
      detail: row ? `${activeHost()} · ${row.now.toISOString()}` : 'no rows',
    };
  } catch (e) {
    checks.database = { ok: false, detail: (e as Error).message };
  }

  try {
    const rel = await getCurrentRelease();
    checks.lib_channel = {
      ok: true,
      detail: rel
        ? `version ${rel.version}${rel.external_url ? ' (external)' : rel.has_content ? ' (stored zip)' : ' (no file!)'}`
        : 'no release published yet',
    };
  } catch (e) {
    checks.lib_channel = { ok: false, detail: (e as Error).message };
  }

  try {
    const tables = await q1<{ n: number }>(
      `SELECT count(*)::int AS n FROM information_schema.tables
        WHERE table_schema = 'public'
          AND table_name IN ('users','keys_code','referral_code','onoff','feature','client_release')`
    );
    checks.schema = { ok: tables?.n === 6, detail: `${tables?.n ?? 0}/6 expected tables present` };
  } catch (e) {
    checks.schema = { ok: false, detail: (e as Error).message };
  }

  const ok = Object.values(checks).every((c) => c.ok);
  return NextResponse.json(
    { ok, ms: Date.now() - started, checks },
    { status: ok ? 200 : 503, headers: { 'Cache-Control': 'no-store' } }
  );
}
