#!/usr/bin/env node
/** Re-applies only db/seed.sql (idempotent). */
import { readFileSync } from 'node:fs';
import path from 'node:path';
import pg from 'pg';

const root = path.resolve(process.cwd(), '..');
const url = process.env.DATABASE_URL;
if (!url) {
  console.error('DATABASE_URL is not set.');
  process.exit(1);
}

const ssl = /localhost|127\.0\.0\.1/.test(url) ? false : { rejectUnauthorized: false };
const client = new pg.Client({ connectionString: url, ssl });

try {
  await client.connect();
  await client.query(readFileSync(path.join(root, 'db/seed.sql'), 'utf8'));
  console.log('Seeded.');
} catch (e) {
  console.error('Failed:', e.message);
  process.exitCode = 1;
} finally {
  await client.end().catch(() => {});
}
