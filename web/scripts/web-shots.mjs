#!/usr/bin/env node
/**
 * Photographs the panel with a real browser, so a restyle can be reviewed
 * instead of guessed at.
 *
 *   BASE_URL=http://127.0.0.1:3000 \
 *   PANEL_USER=admin PANEL_PASS=admin123 \
 *   node web/scripts/web-shots.mjs
 *
 * Writes docs/web-shots/*.png (or SHOT_DIR) and prints one line per screen.
 */
import { chromium } from 'playwright';
import { mkdirSync, writeFileSync } from 'node:fs';
import path from 'node:path';

const BASE = (process.env.BASE_URL || 'http://127.0.0.1:3000').replace(/\/+$/, '');
const USER = process.env.PANEL_USER || 'admin';
const PASS = process.env.PANEL_PASS || 'admin123';
const OUT = process.env.SHOT_DIR || 'docs/web-shots';

mkdirSync(OUT, { recursive: true });

const desktop = { width: 1440, height: 900 };
const mobile = { width: 390, height: 844 };

const browser = await chromium.launch();
const ctx = await browser.newContext({ viewport: desktop, deviceScaleFactor: 2 });
const page = await ctx.newPage();

async function shot(name) {
  await page.waitForLoadState('networkidle').catch(() => {});
  await page.waitForTimeout(400);
  const file = path.join(OUT, `${name}.png`);
  await page.screenshot({ path: file, fullPage: false });
  console.log('shot', file);
  await audit(name);
}

/**
 * A phone screenshot that is quietly cut off on the right looks fine until
 * somebody reads it. This lists every element that sticks out past the viewport
 * so the culprit is named instead of guessed at.
 */
const overflow = [];
async function audit(name) {
  const report = await page.evaluate(() => {
    const vw = document.documentElement.clientWidth;
    const bad = [];
    for (const el of document.querySelectorAll('body *')) {
      const r = el.getBoundingClientRect();
      if (r.width < 2 || r.height < 2) continue;
      if (r.right <= vw + 1) continue;
      const cls = typeof el.className === 'string' ? el.className.trim().split(/\s+/).slice(0, 5).join('.') : '';
      bad.push({
        sel: `${el.tagName.toLowerCase()}${el.id ? `#${el.id}` : ''}${cls ? `.${cls}` : ''}`,
        right: Math.round(r.right),
        width: Math.round(r.width),
        text: (el.textContent || '').replace(/\s+/g, ' ').trim().slice(0, 40),
      });
    }
    return { vw, scrollWidth: document.documentElement.scrollWidth, bad: bad.slice(0, 10) };
  });
  if (!report.bad.length && report.scrollWidth <= report.vw + 1) return;
  const line =
    `${name}: viewport=${report.vw} scrollWidth=${report.scrollWidth} ` +
    report.bad.map((b) => `${b.sel} (right=${b.right}, w=${b.width}, "${b.text}")`).join(' | ');
  overflow.push(line);
  console.log(`::warning title=overflow ${name}::${line}`);
}

async function login() {
  await page.goto(`${BASE}/login`, { waitUntil: 'domcontentloaded' });
  await shot('01-login');

  const user = page.locator('input[name="username"], input#username, input[type="text"]').first();
  const pass = page.locator('input[type="password"]').first();
  await user.fill(USER);
  await pass.fill(PASS);
  await Promise.all([
    page.waitForURL((u) => !u.pathname.startsWith('/login'), { timeout: 20000 }).catch(() => {}),
    page.locator('button[type="submit"]').first().click(),
  ]);
  await page.waitForTimeout(800);
  console.log('after login ->', page.url());
}

try {
  await login();

  // An empty panel photographs empty. Generate one key first so the lists,
  // the detail page and the dashboard table show their real states.
  await page.goto(`${BASE}/keys/generate`, { waitUntil: 'domcontentloaded' });
  await shot('04-generate');
  const gen = page.locator('button[type="submit"]').last();
  if (await gen.count()) {
    await Promise.all([
      page.waitForURL((u) => u.pathname.startsWith('/keys'), { timeout: 20000 }).catch(() => {}),
      gen.click().catch(() => {}),
    ]);
    await page.waitForTimeout(800);
  }

  await page.goto(`${BASE}/dashboard`, { waitUntil: 'domcontentloaded' });
  await shot('02-dashboard');

  await page.goto(`${BASE}/keys`, { waitUntil: 'domcontentloaded' });
  await shot('03-keys');

  await page.goto(`${BASE}/keys/1`, { waitUntil: 'domcontentloaded' }).catch(() => {});
  await shot('05-key-detail');

  await page.goto(`${BASE}/settings/server`, { waitUntil: 'domcontentloaded' });
  await shot('06-server');

  await page.goto(`${BASE}/admin/manage-users`, { waitUntil: 'domcontentloaded' });
  await shot('07-users');

  // public: the client API reference
  await page.goto(`${BASE}/docs/api`, { waitUntil: 'domcontentloaded' });
  await shot('07b-api-docs');

  // phone width, where the sidebar becomes a drawer
  await page.setViewportSize(mobile);
  await page.goto(`${BASE}/dashboard`, { waitUntil: 'domcontentloaded' });
  await shot('08-mobile-dashboard');

  const burger = page.locator('header button').first();
  if (await burger.count()) {
    await burger.click().catch(() => {});
    await page.waitForTimeout(500);
    await shot('09-mobile-nav');
  }

  // the docs page as a phone shows it — it has to be readable without panning
  await page.goto(`${BASE}/docs/api`, { waitUntil: 'domcontentloaded' });
  await shot('10-mobile-api-docs');

  await page.goto(`${BASE}/settings/server`, { waitUntil: 'domcontentloaded' });
  await shot('11-mobile-server');
} finally {
  if (overflow.length) {
    writeFileSync(path.join(OUT, 'overflow.txt'), overflow.join('\n') + '\n');
    console.log(`${overflow.length} screen(s) overflow horizontally — see overflow.txt`);
  }
  await ctx.close();
  await browser.close();
}
