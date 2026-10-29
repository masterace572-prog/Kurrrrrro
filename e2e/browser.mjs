/**
 * Real-browser test of the deployed panel: opens Chromium, logs in, clicks
 * "Generate keys" like a user does, and reports every console/page error.
 *
 * This is what distinguishes "the server rejects the action" from "React never
 * hydrated, so the click does nothing".
 *
 *   node e2e/browser.mjs
 */
import { chromium } from 'playwright';

const BASE = (process.env.BASE_URL || 'https://kuro-panel-psi.vercel.app').replace(/\/+$/, '');
const USER = process.env.PANEL_USER || 'admin';
const PASS = process.env.PANEL_PASS || 'admin123';

const consoleErrors = [];
const pageErrors = [];
const failedRequests = [];
const notes = [];

function say(m) {
  notes.push(m);
  console.log(m);
}

const browser = await chromium.launch();
const context = await browser.newContext({ viewport: { width: 1280, height: 900 } });
const page = await context.newPage();

page.on('console', (msg) => {
  if (msg.type() === 'error') consoleErrors.push(msg.text().slice(0, 300));
});
page.on('pageerror', (err) => pageErrors.push(String(err).slice(0, 300)));
page.on('requestfailed', (req) => failedRequests.push(`${req.method()} ${req.url().slice(0, 120)} — ${req.failure()?.errorText}`));

let exitCode = 1;
try {
  say(`target: ${BASE}`);

  /* ---- login ---- */
  await page.goto(`${BASE}/login`, { waitUntil: 'networkidle', timeout: 45000 });
  await page.fill('input[name="username"]', USER);
  await page.fill('input[name="password"]', PASS);
  await Promise.all([
    page.waitForURL('**/dashboard**', { timeout: 45000 }).catch(() => {}),
    page.click('button[type="submit"]'),
  ]);
  say(`after login: ${new URL(page.url()).pathname}`);

  /* ---- generate ---- */
  await page.goto(`${BASE}/keys/generate`, { waitUntil: 'networkidle', timeout: 45000 });

  // what the form actually looks like in the browser
  const select = page.locator('select[name="loopcount"]');
  say(`loopcount select present: ${await select.count()}`);
  await select.selectOption('5');
  await page.fill('input[name="max_devices"]', '1');
  await page.selectOption('select[name="duration"]', '24');

  const btn = page.locator('form button[type="submit"]').last();
  say(`generate button disabled: ${await btn.isDisabled()}`);
  say(`generate button text: ${(await btn.innerText()).trim()}`);

  await btn.click();

  // success now redirects to /keys, where the toast and the NEW badge live
  const redirected = await page
    .waitForURL('**/keys**', { timeout: 30000 })
    .then(() => true)
    .catch(() => false);
  say(`redirected to /keys after generate: ${redirected}`);
  await page.waitForTimeout(1800);
  const toast = await page.locator('text=/key\(s\) generated/i').first().innerText().catch(() => '');
  say(`toast: ${toast || '(none)'}`);
  const badge = await page.locator('span:has-text("new")').count();
  say(`NEW badges visible: ${badge}`);
  const body = await page.evaluate(() => document.body.innerText);
  const msg = body.match(/(\d+ key\(s\) generated[^\n]*|[A-Z][^\n]{5,80}(?:error|failed|invalid|Not enough|expired)[^\n]*)/i);
  say(`page message: ${msg ? msg[0].trim() : '(none)'}`);
  say(`button after click: ${(await btn.innerText()).trim()} (disabled: ${await btn.isDisabled()})`);

  await page.screenshot({ path: 'after-generate.png', fullPage: true }).catch(() => {});

  /* ---- did it land? ---- */
  await page.goto(`${BASE}/keys`, { waitUntil: 'networkidle', timeout: 45000 });
  const rows = await page.locator('table tbody tr').count();
  const keyText = await page.locator('table tbody').innerText().catch(() => '');
  say(`/keys rows: ${rows}`);
  say(`newest key visible: ${(keyText.match(/admin-\d+-[A-Za-z0-9]+/g) || ['none'])[0]}`);

  exitCode = redirected && /key\(s\) generated/i.test(toast) && rows > 0 ? 0 : 1;
} catch (e) {
  say(`crash: ${e.message}`);
  await page.screenshot({ path: 'crash.png', fullPage: true }).catch(() => {});
} finally {
  say(`console errors: ${consoleErrors.length ? consoleErrors.join(' | ') : 'none'}`);
  say(`page errors: ${pageErrors.length ? pageErrors.join(' | ') : 'none'}`);
  say(`failed requests: ${failedRequests.length ? failedRequests.join(' | ') : 'none'}`);
  await browser.close();
}

if (process.env.GITHUB_ACTIONS === 'true') {
  for (const n of notes) console.log(`::notice title=browser::${n.replace(/[\r\n]+/g, ' ')}`);
  if (exitCode !== 0) {
    console.log(`::error title=browser e2e failed::${pageErrors[0] || consoleErrors[0] || 'no key created'}`);
  }
}
process.exit(exitCode);
