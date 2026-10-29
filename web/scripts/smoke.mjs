#!/usr/bin/env node
/**
 * End-to-end test of the loader contract against a running panel.
 *
 *   npm run dev            # terminal 1
 *   npm run smoke          # terminal 2
 *
 * Env: BASE_URL (default http://127.0.0.1:3000), DATABASE_URL (to create a
 * throwaway key), LOADER_LICENSE_SALT.
 */
import pg from 'pg';
import { createHash } from 'node:crypto';

const BASE = process.env.BASE_URL || 'http://127.0.0.1:3000';
const SALT = process.env.LOADER_LICENSE_SALT || 'Vm8Lk7Uj2JmsjCPVPVjrLa7zgfx3uz9E';
const md5 = (s) => createHash('md5').update(s).digest('hex');

let pass = 0;
let fail = 0;

function check(name, cond, extra = '') {
  if (cond) {
    pass++;
    console.log(`  ✓ ${name}`);
  } else {
    fail++;
    console.log(`  ✗ ${name} ${extra}`);
  }
}

async function license(game, key, serial) {
  const body = new URLSearchParams({ game, user_key: key, serial });
  const res = await fetch(`${BASE}/server`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body,
  });
  return { status: res.status, json: await res.json().catch(() => ({})) };
}

const KEY_GOOD = `smoke-24-${Math.random().toString(36).slice(2, 7)}`;
const KEY_EXPIRED = `smoke-exp-${Math.random().toString(36).slice(2, 7)}`;
const KEY_BLOCKED = `smoke-blk-${Math.random().toString(36).slice(2, 7)}`;

const db = process.env.DATABASE_URL
  ? new pg.Client({
      connectionString: process.env.DATABASE_URL,
      ssl: /localhost|127\.0\.0\.1/.test(process.env.DATABASE_URL) ? false : { rejectUnauthorized: false },
    })
  : null;

try {
  if (db) {
    await db.connect();
    await db.query(`DELETE FROM keys_code WHERE user_key LIKE 'smoke-%'`);
    await db.query(
      `INSERT INTO keys_code (game, user_key, duration, max_devices, status) VALUES
         ('PUBG',$1,24,2,1), ('PUBG',$2,24,1,1), ('PUBG',$3,24,1,0)`,
      [KEY_GOOD, KEY_EXPIRED, KEY_BLOCKED]
    );
    await db.query(`UPDATE keys_code SET expired_date = now() - interval '1 day' WHERE user_key = $1`, [KEY_EXPIRED]);
  }

  console.log(`\nTesting ${BASE}\n`);

  console.log('GET /server');
  const info = await fetch(`${BASE}/server`);
  const infoJson = await info.json();
  check('returns 200', info.status === 200, `got ${info.status}`);
  check('has web_info', Boolean(infoJson.web_info));

  console.log('\nPOST /server (valid key)');
  const ok = await license('PUBG', KEY_GOOD, 'deviceAAA111');
  check('status true', ok.json.status === true, JSON.stringify(ok.json));
  check('token matches loader formula', ok.json?.data?.token === md5(`PUBG-${KEY_GOOD}-deviceAAA111-${SALT}`));
  check('rng is fresh', Math.abs(ok.json?.data?.rng - Math.floor(Date.now() / 1000)) < 60);
  check('EXP present', typeof ok.json?.data?.EXP === 'string');
  check('modname present', Boolean(ok.json?.data?.modname));

  console.log('\nPOST /server (same device again = idempotent)');
  const again = await license('PUBG', KEY_GOOD, 'deviceAAA111');
  check('status true', again.json.status === true);

  console.log('\nPOST /server (device limit)');
  const dev2 = await license('PUBG', KEY_GOOD, 'deviceBBB222');
  check('second device allowed (max 2)', dev2.json.status === true);
  const dev3 = await license('PUBG', KEY_GOOD, 'deviceCCC333');
  check('third device rejected', dev3.json.status === false && dev3.json.reason === 'MAX DEVICE REACHED', JSON.stringify(dev3.json));

  console.log('\nPOST /server (expired key)');
  const exp = await license('PUBG', KEY_EXPIRED, 'deviceAAA111');
  check('rejected as expired', exp.json.status === false && exp.json.reason === 'EXPIRED KEY', JSON.stringify(exp.json));

  console.log('\nPOST /server (blocked key)');
  const blk = await license('PUBG', KEY_BLOCKED, 'deviceAAA111');
  check('rejected as blocked', blk.json.status === false && blk.json.reason === 'USER BLOCKED', JSON.stringify(blk.json));

  console.log('\nPOST /server (unknown key)');
  const nope = await license('PUBG', 'does-not-exist', 'deviceAAA111');
  check('rejected as unknown', nope.json.status === false && nope.json.reason === 'USER OR GAME NOT REGISTERED');

  console.log('\nPOST /server (bad params)');
  const bad = await license('PUBG', '', '');
  check('rejected as bad parameter', bad.json.status === false && bad.json.reason === 'Bad Parameter');

  console.log('\nPOST /server (json body also accepted)');
  const jsonRes = await fetch(`${BASE}/server`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ game: 'PUBG', user_key: KEY_GOOD, serial: 'deviceAAA111' }),
  }).then((r) => r.json());
  check('json body works', jsonRes.status === true);

  console.log('\nMaintenance mode');
  if (db) {
    await db.query(`UPDATE onoff SET status='on', myinput='Back in 10 minutes' WHERE id=1`);
    const mt = await license('PUBG', KEY_GOOD, 'deviceAAA111');
    check('rejects all logins', mt.json.status === false);
    check('returns custom message', mt.json.reason === 'Back in 10 minutes', JSON.stringify(mt.json));
    await db.query(`UPDATE onoff SET status='off', myinput='' WHERE id=1`);
  }
} catch (e) {
  fail++;
  console.error('\nSmoke test crashed:', e.message);
} finally {
  if (db) {
    await db.query(`DELETE FROM keys_code WHERE user_key LIKE 'smoke-%'`).catch(() => {});
    await db.end().catch(() => {});
  }
}

console.log(`\n${pass} passed, ${fail} failed\n`);
process.exit(fail ? 1 : 0);
