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

const GH = process.env.GITHUB_ACTIONS === 'true';
const failures = [];

function check(label, ok, extra = '') {
  if (!ok && GH) failures.push(`${label}${extra ? ` — ${extra}` : ''}`);
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

/* 3b. real license end-to-end (optional) ------------------------------------ */
const TEST_KEY = process.env.TEST_KEY || '';
if (TEST_KEY) {
  console.log('\nLicense end-to-end');
  const SALT = process.env.LOADER_LICENSE_SALT || 'Vm8Lk7Uj2JmsjCPVPVjrLa7zgfx3uz9E';
  const serial = process.env.TEST_SERIAL || 'E2E-DEVICE-1';
  try {
    const { createHash } = await import('node:crypto');
    const res = await get('/server', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: `game=PUBG&user_key=${encodeURIComponent(TEST_KEY)}&serial=${encodeURIComponent(serial)}`,
    });
    const body = await res.json().catch(() => ({}));
    const data = body?.data ?? {};
    check('valid key accepted', body?.status === true, JSON.stringify(body).slice(0, 200));
    check('token matches md5(PUBG-key-serial-salt)',
      data.token === createHash('md5').update(`PUBG-${TEST_KEY}-${serial}-${SALT}`).digest('hex'),
      `got "${data.token}"`);
    check('rng is fresh (within 60s)', Math.abs(Number(data.rng) - Math.floor(Date.now() / 1000)) < 60, `rng=${data.rng}`);
    check('EXP date returned', typeof data.EXP === 'string' && data.EXP.length >= 10, `EXP=${data.EXP}`);
    check('feature flags present', ['ESP', 'Item', 'AIM', 'SilentAim'].every((k) => typeof data[k] === 'string'));
    check('device slot consumed', Number(data.device) >= 1, `device=${data.device}`);
  } catch (e) {
    check('license end-to-end', false, e.message);
  }
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

// Annotations survive in the Checks API, which is handy when the raw job log
// is not reachable.
if (GH) {
  for (const f of failures) {
    console.log(`::error title=Verify failed::${f.replace(/[\r\n]+/g, ' ')}`);
  }
  console.log(
    `::notice title=Live check::${BASE} — ${passed} passed, ${failed} failed`
  );
}

process.exit(failed === 0 ? 0 : 1);

// checked again after the toast/UX deploy: 2026-10-29T11:23:48Z
