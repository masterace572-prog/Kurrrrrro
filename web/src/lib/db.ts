import { Pool, type QueryResultRow } from 'pg';

/**
 * Single shared Postgres pool.
 *
 * In serverless (Vercel) every warm lambda reuses this module-level pool.
 * Point DATABASE_URL at the Supabase *pooler* (port 6543, transaction mode)
 * so connections are multiplexed and you never exhaust the free-tier limit.
 *
 * Supabase moves projects between pooler shards (`aws-0-…` ↔ `aws-1-…`) and
 * the shard in the published connection string occasionally lags behind. If the
 * first connection fails at the network level we retry once against the sibling
 * shard and remember whichever one answered.
 */
declare global {
  // eslint-disable-next-line no-var
  var __kuroPool: Pool | undefined;
}

const SHARDED =
  /^(postgres(?:ql)?:\/\/)([^@/]+)@(aws-(\d+))-(.+?)(:\d+)?(\/.*)?$/i;

/** DATABASE_URL first, then the same URL pointed at the other aws-N shard. */
export function candidateUrls(): string[] {
  const url = process.env.DATABASE_URL;
  if (!url) {
    throw new Error(
      'DATABASE_URL is not set. In Vercel: Settings → Environment Variables. Locally: web/.env.local (see .env.example).'
    );
  }

  const m = url.match(SHARDED);
  if (!m) return [url];

  const [, scheme, creds, shard, shardNo, rest, port = '', tail = ''] = m;
  const other = String(1 - Number(shardNo));
  const alt = `${scheme}${creds}@aws-${other}-${rest}${port}${tail}`;
  return [url, alt];
}

function needsSsl(url: string) {
  if (process.env.PGSSL === 'disable') return false;
  if (process.env.PGSSL === 'require') return true;
  if (/localhost|127\.0\.0\.1|::1/.test(url)) return false;
  return true;
}

const pools = new Map<string, Pool>();
let preferred: string | null = null;

function poolFor(url: string): Pool {
  const existing = pools.get(url);
  if (existing) return existing;

  const pool = new Pool({
    connectionString: url,
    ssl: needsSsl(url) ? { rejectUnauthorized: false } : undefined,
    max: Number(process.env.PGPOOL_MAX ?? 3),
    idleTimeoutMillis: 10_000,
    connectionTimeoutMillis: 10_000,
    // Supavisor transaction mode cannot keep prepared statements alive
    statement_timeout: 20_000,
    query_timeout: 25_000,
  });

  pool.on('error', (err) => {
    console.error('[db] idle client error:', err.message);
  });

  pools.set(url, pool);
  return pool;
}

export function getPool(): Pool {
  if (global.__kuroPool) return global.__kuroPool;
  const url = preferred ?? candidateUrls()[0];
  const pool = poolFor(url);
  global.__kuroPool = pool;
  return pool;
}

/** True for errors that mean "this host is not reachable", not "your SQL is wrong". */
function isConnectError(e: unknown): boolean {
  const err = e as { code?: string; message?: string };
  const code = err?.code ?? '';
  if (
    ['ECONNREFUSED', 'ENOTFOUND', 'ETIMEDOUT', 'ECONNRESET', 'EHOSTUNREACH', 'ENETUNREACH', 'EAI_AGAIN'].includes(code)
  ) {
    return true;
  }
  const msg = (err?.message ?? '').toLowerCase();
  return (
    msg.includes('connection terminated') ||
    msg.includes('timeout expired') ||
    msg.includes('terminating connection') ||
    msg.includes('connection closed') ||
    msg.includes('server closed the connection')
  );
}

async function run<R extends QueryResultRow>(text: string, params: unknown[]): Promise<R[]> {
  const urls = candidateUrls();
  const order = preferred ? [preferred, ...urls.filter((u) => u !== preferred)] : urls;

  let lastError: unknown;
  for (const url of order) {
    try {
      const res = await poolFor(url).query<R>(text, params as never[]);
      if (preferred !== url) preferred = url;
      return res.rows;
    } catch (e) {
      lastError = e;
      if (!isConnectError(e) || url === order[order.length - 1]) break;
      console.warn('[db] connection failed, trying the other pooler shard:', (e as Error).message);
    }
  }
  throw lastError;
}

export async function q<T extends QueryResultRow = QueryResultRow>(
  text: string,
  params: unknown[] = []
): Promise<T[]> {
  return run<T>(text, params);
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

/** Used by /api/health to show which shard answered. */
export function activeHost(): string {
  try {
    const url = preferred ?? candidateUrls()[0];
    return url.replace(/\/\/[^@]+@/, '//');
  } catch {
    return 'DATABASE_URL missing';
  }
}
