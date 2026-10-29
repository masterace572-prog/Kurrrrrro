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
import { mkdirSync } from 'node:fs';
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
  await shot('02-dashboard');

  await page.goto(`${BASE}/keys`, { waitUntil: 'domcontentloaded' });
  await shot('03-keys');

  await page.goto(`${BASE}/keys/generate`, { waitUntil: 'domcontentloaded' });
  await shot('04-generate');

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
  await ctx.close();
  await browser.close();
}
