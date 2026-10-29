#!/usr/bin/env node
/** Applies db/schema.sql then db/seed.sql to DATABASE_URL. */
import { readFileSync } from 'node:fs';
import path from 'node:path';
import pg from 'pg';

const root = path.resolve(process.cwd(), '..');
const url = process.env.DATABASE_URL;
if (!url) {
  console.error('DATABASE_URL is not set. Example:\n  DATABASE_URL="postgresql://..." npm run db:push');
  process.exit(1);
}

const ssl = /localhost|127\.0\.0\.1/.test(url) ? false : { rejectUnauthorized: false };
const client = new pg.Client({ connectionString: url, ssl });

const files = ['db/schema.sql', 'db/seed.sql'];

try {
  await client.connect();
  for (const rel of files) {
    const sql = readFileSync(path.join(root, rel), 'utf8');
    process.stdout.write(`→ ${rel} … `);
    await client.query(sql);
    console.log('ok');
  }
  const { rows } = await client.query('select count(*)::int as n from users');
  console.log(`\nDone. users table has ${rows[0].n} row(s).`);
  console.log('Login with admin / admin123 (change it immediately).');
} catch (e) {
  console.error('\nFailed:', e.message);
  process.exitCode = 1;
} finally {
  await client.end().catch(() => {});
}
