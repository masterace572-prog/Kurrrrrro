#!/usr/bin/env node
/**
 * Verifies a *deployed* panel from the public internet.
 *
 *   node scripts/verify-live.mjs https://your-app.vercel.app
 *
 * Used by .github/workflows/verify-deploy.yml, because the build only proves the
 * code compiles — this proves the deployment can actually reach Postgres and
 * answer the loader.
 */
const BASE = (process.argv[2] || process.env.BASE_URL || '').replace(/\/+$/, '');

if (!BASE) {
  console.error('usage: node scripts/verify-live.mjs https://your-app.vercel.app');
  process.exit(2);
}

let passed = 0;
let failed = 0;

function check(label, ok, extra = '') {
  if (ok) {
    passed++;
    console.log(`  \x1b[32m✓\x1b[0m ${label}`);
  } else {
    failed++;
    console.log(`  \x1b[31m✗\x1b[0m ${label}${extra ? ` — ${extra}` : ''}`);
  }
  return ok;
}

async function get(path, init) {
  const res = await fetch(`${BASE}${path}`, { redirect: 'manual', ...init });
  return res;
}

console.log(`\nVerifying ${BASE}\n`);

/* 1. health ---------------------------------------------------------------- */
console.log('Health');
try {
  const res = await get('/api/health');
  const body = await res.json().catch(() => ({}));
  check('GET /api/health responds', res.status < 500, `HTTP ${res.status}`);
  check('database reachable', body?.checks?.database?.ok === true, body?.checks?.database?.detail);
  check('env vars present', body?.checks?.env?.ok === true, body?.checks?.env?.detail);
  check('schema applied', body?.checks?.schema?.ok === true, body?.checks?.schema?.detail);
  check('overall ok', body?.ok === true, JSON.stringify(body?.checks ?? {}));
} catch (e) {
  check('GET /api/health responds', false, e.message);
}

/* 2. loader endpoint ------------------------------------------------------- */
console.log('\nLoader endpoint');
try {
  const res = await get('/server', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: 'game=PUBG&user_key=verify-live-no-such-key&serial=VERIFYDEVICE',
  });
  const body = await res.json().catch(() => ({}));
  check('POST /server returns JSON', res.headers.get('content-type')?.includes('json') ?? false);
  check('unknown key rejected', body?.status === false, JSON.stringify(body).slice(0, 160));
  check(
    'correct error string',
    body?.reason === 'USER OR GAME NOT REGISTERED',
    `got "${body?.reason}"`
  );
  check('CORS open for the APK', res.headers.get('access-control-allow-origin') === '*');
} catch (e) {
  check('POST /server responds', false, e.message);
}

/* 3. lib channel ----------------------------------------------------------- */
console.log('\nLib update channel');
try {
  const res = await get('/api/version');
  const text = (await res.text()).trim();
  check('GET /api/version responds', res.status === 200, `HTTP ${res.status}`);
  check('version is plain text', /^[A-Za-z0-9._-]{1,32}$/.test(text), `got "${text}"`);
  check('version is not cached', /no-store/.test(res.headers.get('cache-control') || ''));

  const dl = await get('/api/download');
  check(
    'GET /api/download answers (zip or "no release yet")',
    dl.status === 200 || dl.status === 404,
    `HTTP ${dl.status}`
  );
} catch (e) {
  check('lib channel', false, e.message);
}

/* 4. panel pages ----------------------------------------------------------- */
console.log('\nPanel');
try {
  const login = await get('/login');
  const html = await login.text();
  check('GET /login responds', login.status === 200, `HTTP ${login.status}`);
  check('login page renders the form', /name="password"/.test(html));
  check('not behind an SSO wall', !/vercel\.com\/sso|Authentication Required/i.test(html));

  const dash = await get('/dashboard');
  check(
    'unauthenticated /dashboard redirects to /login',
    dash.status === 307 || dash.status === 302,
    `HTTP ${dash.status}`
  );
} catch (e) {
  check('panel pages', false, e.message);
}

console.log(`\n${failed === 0 ? '\x1b[32m' : '\x1b[31m'}${passed} passed, ${failed} failed\x1b[0m\n`);
process.exit(failed === 0 ? 0 : 1);
