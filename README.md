# KURO PANEL — PHP → serverless rewrite

The supplied `Free Source .zip` was a CodeIgniter 4 / PHP + MySQL key panel.
Since there's no hosting, it has been **fully ported to Next.js + Postgres**, so it
runs for free on **Vercel + Supabase** — the two you already have connected.

```
panel (browser)  ──►  Vercel · Next.js  ──►  Supabase Postgres
XLOX loader (APK) ──►  POST /server      ──►  same database
```

---

## ⚠️ Read this first

`legacy-php/public/assets/css/load.php` in the original zip is a **remote shell
backdoor** — `?code=<payload>` executes arbitrary PHP on the host. It has been
deleted here. Details, plus the SQL injections and token weaknesses, are in
[`docs/SECURITY-FINDINGS.md`](docs/SECURITY-FINDINGS.md).

---

## What's in the repo

| Path | Contents |
|---|---|
| `web/` | **The new panel.** Next.js 15 App Router, TypeScript, Tailwind, `pg` |
| `db/schema.sql`, `db/seed.sql` | Postgres schema + seed, ported from `main.sql` |
| `legacy-php/` | The original PHP source, extracted and browsable (backdoor removed, `vendor/` omitted) |
| `loader/` | XLOX loader source, extracted; endpoint switched to your own panel |
| `docs/DEPLOY.md` | Step-by-step free deployment (Supabase + Vercel) and alternatives |
| `docs/LOADER.md` | The `/server` contract, how to change the URL, how to build the APK |
| `docs/SECURITY-FINDINGS.md` | Full audit of what came in the zip |
| `.github/workflows/build-loader.yml` | Builds the APK on GitHub Actions |

## Quick start

```bash
cd web
npm install
npm run db:dev     # terminal 1 — real embedded Postgres on :55432
npm run db:push    # terminal 2 — schema + seed
npm run dev        # http://localhost:3000  → admin / admin123
npm run smoke      # terminal 3 — 17 checks against /server
```

Deploy for free in ~10 minutes: **[docs/DEPLOY.md](docs/DEPLOY.md)**.

## Loading the loader

One line in `loader/app/src/main/jni/main.cpp`:

```cpp
#define PANEL_BASE_URL "https://<your-app>.vercel.app"
```

The old hard-coded `https://vipercheat.authapi.xyz/server` is gone. Rebuild the
APK (Android Studio or the included GitHub Actions workflow).

## Route map (old → new)

| PHP (CodeIgniter) | New |
|---|---|
| `GET /login`, `POST /login` | `/login` |
| `/register` | `/register` (referral code required) |
| `/dashboard` → `User::index` | `/dashboard` |
| `/keys`, `/keys/generate` | `/keys`, `/keys/generate` |
| `GET /keys/(:num)` (edit) | `/keys/[id]` |
| `/keys/reset?userkey=` | *Reset HWID* button on `/keys` |
| `/keys/deleteExp`, `/keys/deleteUnused` | *Delete expired / unused* buttons |
| `/settings` | `/settings` |
| `/Server` | `/settings/server` (panel UI) |
| `POST /connect` | **`POST /server`** (loader endpoint — same payload) |
| `/admin/manage-users`, `/admin/user/(:num)` | `/admin/manage-users`, `/admin/user/[id]` |
| `/admin/create-referral` | `/admin/create-referral` |
| `/dbg` (prints session dump) | removed |

## Compatibility

* **Passwords carry over.** The old panel stored `bcrypt(md5("XquxmymXDtWRA66D" + password))`.
  The rewrite reproduces that pre-hash, so every account from your MySQL dump logs
  in unchanged — verified against the `admin` hash in the original `main.sql`.
* **The loader keeps working.** The response shape, token formula, `rng` window,
  device binding and failure reasons are identical. Nothing needs rebuilding to
  test — only the URL needs changing when you build the final APK.
* **Data import.** `db/schema.sql` + `db/seed.sql` recreate the original rows
  (owner account, `VIP MOD`, feature flags, maintenance switch). To bring real
  keys across from the old MySQL dump, run `legacy-php/main.sql` through a
  MySQL→Postgres converter, or insert `keys_code` rows directly — the columns
  are the same.

## Roles

| Level | Role | Can do |
|---|---|---|
| 1 | Owner | everything: users, features, keys, server settings |
| 2 | Admin | keys of its own downline, server text/maintenance, manage its resellers |
| 3 | Reseller | generate/spend keys under its own name, reset own HWIDs |
