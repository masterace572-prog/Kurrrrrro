import { Pool, type QueryResultRow } from 'pg';

/**
 * Single shared Postgres pool.
 *
 * In serverless (Vercel) every warm lambda reuses this module-level pool.
 * Point DATABASE_URL at the Supabase *pooler* (port 6543, transaction mode)
 * so connections are multiplexed and you never exhaust the free-tier limit.
 */
declare global {
  // eslint-disable-next-line no-var
  var __kuroPool: Pool | undefined;
}

function needsSsl(url: string) {
  if (process.env.PGSSL === 'disable') return false;
  if (process.env.PGSSL === 'require') return true;
  if (/localhost|127\.0\.0\.1|::1/.test(url)) return false;
  return true;
}

export function getPool(): Pool {
  if (global.__kuroPool) return global.__kuroPool;

  const url = process.env.DATABASE_URL;
  if (!url) {
    throw new Error(
      'DATABASE_URL is not set. Copy web/.env.example to web/.env.local and paste your Supabase connection string.'
    );
  }

  const pool = new Pool({
    connectionString: url,
    ssl: needsSsl(url) ? { rejectUnauthorized: false } : undefined,
    max: Number(process.env.PGPOOL_MAX ?? 3),
    idleTimeoutMillis: 10_000,
    connectionTimeoutMillis: 10_000,
  });

  pool.on('error', (err) => {
    console.error('[db] idle client error', err.message);
  });

  global.__kuroPool = pool;
  return pool;
}

export async function q<T extends QueryResultRow = QueryResultRow>(
  text: string,
  params: unknown[] = []
): Promise<T[]> {
  const res = await getPool().query<T>(text, params as never[]);
  return res.rows;
}

export async function q1<T extends QueryResultRow = QueryResultRow>(
  text: string,
  params: unknown[] = []
): Promise<T | null> {
  const rows = await q<T>(text, params);
  return rows[0] ?? null;
}

export async function tx<T>(fn: (client: import('pg').PoolClient) => Promise<T>): Promise<T> {
  const client = await getPool().connect();
  try {
    await client.query('BEGIN');
    const out = await fn(client);
    await client.query('COMMIT');
    return out;
  } catch (e) {
    await client.query('ROLLBACK');
    throw e;
  } finally {
    client.release();
  }
}
