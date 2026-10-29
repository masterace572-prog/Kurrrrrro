export type FormState = {
  ok: boolean;
  message: string;
  field?: string;
  code?: string;
} | null;

/** Turns any thrown value into something safe to show a user. */
export function describeError(e: unknown): { message: string; code: string } {
  const err = e as { code?: string; message?: string; constraint?: string };
  const code = err?.code ?? '';

  // Postgres error codes (see https://www.postgresql.org/docs/current/errcodes-appendix.html)
  const pg: Record<string, string> = {
    '23505': 'That value already exists (duplicate).',
    '23503': 'A related record is missing.',
    '23502': 'A required field was empty.',
    '22P02': 'Invalid input format.',
    '22003': 'Number out of range.',
    '42P01': 'Database is not initialised — run `npm run db:push`.',
    '42703': 'Database schema is out of date — run `npm run db:push`.',
    '53300': 'Database is busy (too many connections). Try again in a moment.',
    ECONNREFUSED: 'Cannot reach the database. Is DATABASE_URL correct?',
    ENOTFOUND: 'Cannot reach the database host. Check DATABASE_URL.',
    ETIMEDOUT: 'Database connection timed out.',
    ECONNRESET: 'Database connection was dropped. Try again.',
  };

  if (code && pg[code]) return { message: pg[code], code };
  if (err?.message?.includes('DATABASE_URL')) {
    return { message: 'Database is not configured (DATABASE_URL missing).', code: 'NODB' };
  }
  if (err?.message?.includes('AUTH_SECRET')) {
    return { message: 'Server misconfigured: AUTH_SECRET is missing.', code: 'NOSECRET' };
  }
  return { message: 'Something went wrong. Please try again.', code: code || 'UNKNOWN' };
}

/** Safely read a FormData field as a trimmed string. */
export function str(fd: FormData, key: string, fallback = ''): string {
  const v = fd.get(key);
  return typeof v === 'string' ? v.trim() : fallback;
}

export function num(fd: FormData, key: string, fallback = NaN): number {
  const v = str(fd, key);
  if (v === '') return fallback;
  const n = Number(v);
  return Number.isFinite(n) ? n : fallback;
}

export function bool(fd: FormData, key: string): boolean {
  const v = str(fd, key);
  return v === 'on' || v === '1' || v === 'true' || v === 'yes';
}

/** Validates a "YYYY-MM-DD HH:mm:ss" (UTC) input, returns null for empty. */
export function parseUtcInput(value: string): Date | null | 'invalid' {
  const v = value.trim();
  if (!v) return null;
  const normalised = v.includes('T') ? v : v.replace(' ', 'T');
  const d = new Date(/[zZ]|[+-]\d\d:?\d\d$/.test(normalised) ? normalised : `${normalised}Z`);
  return Number.isNaN(d.getTime()) ? 'invalid' : d;
}
