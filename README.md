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
| `docs/LIB-UPDATES.md` | Pushing a new lib to every device by changing only the version |
| `docs/SECURITY-FINDINGS.md` | Full audit of what came in the zip |
| `.github/workflows/build-loader.yml` | Builds the APK on GitHub Actions |

## Live right now

| | |
|---|---|
| **Panel** | **https://kuro-panel-psi.vercel.app** — login `admin` / `admin123` (change it) |
| **APK** | https://github.com/masterace572-prog/Kurrrrrro/releases/latest |
| Loader endpoint | `https://kuro-panel-psi.vercel.app/server` |
| Health | `https://kuro-panel-psi.vercel.app/api/health` |

Deployment records, verification results and the manual steps left for you:
**[`docs/LIVE.md`](docs/LIVE.md)**.

## Quick start

```bash
cd web
npm install
npm run db:dev     # terminal 1 — real embedded Postgres on :55432
npm run db:push    # terminal 2 — schema + seed
npm run dev        # http://localhost:3000  → admin / admin123
npm run smoke      # terminal 3 — 28 checks against /server + lib channel
```

Deploy for free in ~10 minutes: **[docs/DEPLOY.md](docs/DEPLOY.md)**.

## Loading the loader

One line in `loader/app/src/main/jni/main.cpp`:

```cpp
#define PANEL_BASE_URL "https://kuro-panel-psi.vercel.app"
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
| `download.php?t=ver15` (version text) | `GET /api/version` |
| `download.php?t=zip13` (lib zip) | `GET /api/download` |
| — | `/api/health` (deployment self-check) |
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

## Pushing a lib update

Open **Server control → Lib updates**, upload the zip (or paste a GitHub Release
URL), give it a version like `1.4`, publish. Every device whose stored version
differs downloads it once; devices already on `1.4` skip the download entirely.
To force a re-download without re-uploading, use **Bump version**.
Details: [`docs/LIB-UPDATES.md`](docs/LIB-UPDATES.md).

## Roles

| Level | Role | Can do |
|---|---|---|
| 1 | Owner | everything: users, features, keys, server settings |
| 2 | Admin | keys of its own downline, server text/maintenance, manage its resellers |
| 3 | Reseller | generate/spend keys under its own name, reset own HWIDs |
