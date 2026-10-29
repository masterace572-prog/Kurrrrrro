#!/usr/bin/env node
/**
 * Drives the *deployed* panel like a browser would, and prints exactly what the
 * server action answered. Used to debug "the button does nothing" in production,
 * where we cannot open devtools.
 *
 *   BASE_URL=https://kuro-panel-psi.vercel.app \
 *   PANEL_USER=admin PANEL_PASS=admin123 \
 *   node scripts/e2e-panel.mjs
 *
 * Exit code 0 = a key was created.
 */
const BASE = (process.env.BASE_URL || 'http://127.0.0.1:3000').replace(/\/+$/, '');
const USER = process.env.PANEL_USER || 'admin';
const PASS = process.env.PANEL_PASS || 'admin123';
const GH = process.env.GITHUB_ACTIONS === 'true';

let cookie = '';
const notes = [];

function say(msg) {
  notes.push(msg);
  console.log(msg);
}

function absorb(res) {
  const raw = res.headers.getSetCookie?.() ?? (res.headers.get('set-cookie') ? [res.headers.get('set-cookie')] : []);
  for (const c of raw) {
    const [pair] = c.split(';');
    if (pair.startsWith('kuro_session=')) cookie = pair;
  }
}

/** Hidden $ACTION_* inputs of the first <form> containing `marker`. */
function actionFields(html, marker) {
  const forms = html.match(/<form[\s\S]*?<\/form>/g) || [];
  const form = forms.find((f) => f.includes(marker));
  if (!form) throw new Error(`no form containing ${marker} (found ${forms.length} forms)`);

  const fields = [];
  const hidden = form.match(/<input[^>]*type="hidden"[^>]*>/g) || [];
  for (const tag of hidden) {
    const name = tag.match(/name="([^"]*)"/);
    const value = tag.match(/value="([^"]*)"/);
    if (name) {
      fields.push([unescapeHtml(name[1]), value ? unescapeHtml(value[1]) : '']);
    }
  }
  return fields;
}

function unescapeHtml(s) {
  return s
    .replace(/&quot;/g, '"')
    .replace(/&#x27;/g, "'")
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&amp;/g, '&');
}

async function get(path) {
  const res = await fetch(`${BASE}${path}`, { headers: cookie ? { cookie } : {}, redirect: 'manual' });
  absorb(res);
  return { status: res.status, text: await res.text(), headers: res.headers };
}

async function submit(path, fields) {
  const body = new FormData();
  for (const [k, v] of fields) body.append(k, v);
  const res = await fetch(`${BASE}${path}`, {
    method: 'POST',
    headers: { ...(cookie ? { cookie } : {}), origin: BASE },
    body,
    redirect: 'manual',
  });
  absorb(res);
  const text = await res.text();
  return { status: res.status, location: res.headers.get('location') || '', text };
}

/** The flight payload carries {ok, message, field, code} — pull the message out. */
function messageFrom(text) {
  const m = text.match(/\{"ok":(true|false),"message":"((?:[^"\\]|\\.)*)"/);
  if (m) return { ok: m[1] === 'true', message: JSON.parse(`"${m[2]}"`) };
  const gen = text.match(/(\d+ key\(s\) generated[^"<\\]*)/);
  if (gen) return { ok: true, message: gen[1] };
  const err = text.match(/"(?:message|reason)":"((?:[^"\\]|\\.)*)"/);
  if (err) return { ok: false, message: JSON.parse(`"${err[1]}"`) };
  return { ok: false, message: '' };
}

async function main() {
  say(`target: ${BASE}`);

  /* ---- 1. can we even load the login page + its JS? ---- */
  const login = await get('/login');
  say(`GET /login -> ${login.status}`);
  const chunk = login.text.match(/\/_next\/static\/chunks\/[^"']+\.js/);
  if (chunk) {
    const js = await fetch(`${BASE}${chunk[0]}`);
    say(`client JS chunk -> ${js.status} (${js.headers.get('content-type')})`);
  }

  /* ---- 2. log in ---- */
  const loginFields = actionFields(login.text, 'name="password"');
  const loginRes = await submit('/login', [
    ...loginFields,
    ['username', USER],
    ['password', PASS],
    ['stay_log', '1'],
  ]);
  const loggedIn = loginRes.status === 303 && loginRes.location.includes('/dashboard');
  say(`POST /login -> ${loginRes.status} ${loginRes.location || ''}`);
  if (!loggedIn) {
    say(`LOGIN FAILED: ${messageFrom(loginRes.text).message || 'no message'}`);
    return 2;
  }

  /* ---- 3. load the generate page ---- */
  const gen = await get('/keys/generate');
  say(`GET /keys/generate -> ${gen.status}`);
  if (gen.status !== 200) return 3;

  /* ---- 4. submit the generate form ---- */
  const genFields = actionFields(gen.text, 'name="loopcount"');
  const started = Date.now();
  const res = await submit('/keys/generate', [
    ...genFields,
    ['game', 'PUBG'],
    ['duration', '24'],
    ['max_devices', '1'],
    ['loopcount', '5'], // 1 key
    ['custominput', 'auto'],
  ]);
  let { ok, message } = messageFrom(res.text);
  if (res.status === 303 && res.location) {
    // success path: the action redirects to /keys?m=<message>
    const q = new URL(res.location, BASE).searchParams;
    message = q.get('m') || message;
    ok = q.get('t') === 'success' || ok;
    say(`POST /keys/generate -> 303 ${res.location}`);
  } else {
    say(`POST /keys/generate -> ${res.status} in ${Date.now() - started}ms`);
  }
  say(`answer: ${message || '(no message in payload)'}`);

  /* ---- 5. did the key actually land? ---- */
  const keys = await get('/keys');
  const count = new Set(keys.text.match(/href="\/keys\/\d+"/g) || []).size;
  say(`GET /keys -> ${keys.status}, ${count} key row(s) visible on the page`);

  const success = ok && count > 0;
  say(success ? 'RESULT: key created' : 'RESULT: no key created');

  if (GH) {
    for (const n of notes) console.log(`::notice title=e2e::${n.replace(/[\r\n]+/g, ' ')}`);
    if (!success) console.log(`::error title=e2e failed::${(message || 'no message').replace(/[\r\n]+/g, ' ')}`);
  }
  return success ? 0 : 1;
}

main()
  .then((code) => process.exit(code))
  .catch((e) => {
    console.error('crashed:', e.message);
    if (GH) console.log(`::error title=e2e crashed::${e.message}`);
    process.exit(4);
  });
