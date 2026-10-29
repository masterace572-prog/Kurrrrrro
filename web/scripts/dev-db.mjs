#!/usr/bin/env node
/**
 * Zero-setup local Postgres for development.
 *
 *   npm run db:dev            # starts a real Postgres 18 on 127.0.0.1:55432
 *   npm run db:push           # in another terminal: schema + seed
 *   npm run dev               # start the panel
 *
 * Data lives in web/.pgdata (git-ignored). Production uses Supabase/Neon —
 * see docs/DEPLOY.md. Pass --reset to wipe the cluster first.
 */
import EmbeddedPostgres from 'embedded-postgres';
import { existsSync, rmSync } from 'node:fs';
import path from 'node:path';

const dataDir = path.join(process.cwd(), '.pgdata');
const PORT = Number(process.env.DEV_DB_PORT || 55432);

if (process.argv.includes('--reset') && existsSync(dataDir)) {
  rmSync(dataDir, { recursive: true, force: true });
  console.log('[dev-db] wiped', dataDir);
}

const pg = new EmbeddedPostgres({
  databaseDir: dataDir,
  user: 'postgres',
  password: 'postgres',
  port: PORT,
  persistent: true,
});

if (!existsSync(path.join(dataDir, 'PG_VERSION'))) {
  console.log('[dev-db] initialising cluster…');
  await pg.initialise();
}

await pg.start();
console.log(`[dev-db] listening on postgresql://postgres:postgres@127.0.0.1:${PORT}/postgres`);
console.log('[dev-db] next: npm run db:push');

const stop = async () => {
  try {
    await pg.stop();
  } finally {
    process.exit(0);
  }
};
process.on('SIGINT', stop);
process.on('SIGTERM', stop);
