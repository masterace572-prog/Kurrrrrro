# Live deployment

Everything below is already running. This file records **what was created**,
**where it lives**, and **what only you can do**.

```
        ┌──────────────────────────────────────────────────────────┐
        │  Panel      https://kuro-panel-psi.vercel.app            │
        │  Loader     https://kuro-panel-psi.vercel.app/server     │
        │  Lib        https://kuro-panel-psi.vercel.app/api/version│
        │             https://kuro-panel-psi.vercel.app/api/download│
        │  Health     https://kuro-panel-psi.vercel.app/api/health  │
        └──────────────────────────────────────────────────────────┘
                     │                                │
             Vercel Hobby                     Supabase (ap-south-1)
       project  kuro-panel                     project  kuro-panel
       dir      web/                           ref      ufiylsnyhreyxgeprvcj
```

## 1. Panel

| | |
|---|---|
| URL | **https://kuro-panel-psi.vercel.app** |
| Login | username `admin` · password `admin123` ← **change it** |
| Vercel project | `kuro-panel` (team *masterace572-prog's projects*) |
| Root directory | `web` |
| Framework | Next.js (auto) |
| Node | 24.x |
| Health check | https://kuro-panel-psi.vercel.app/api/health |

`kuro-panel.vercel.app` (without `-psi`) belongs to somebody else, so Vercel
assigned `kuro-panel-psi.vercel.app`. Every place that needs the URL is already
pointing at it: `PUBLIC_BASE_URL` in Vercel, `PANEL_BASE_URL` in the loader.

**Environment variables set in Vercel**

| Key | Purpose |
|---|---|
| `DATABASE_URL` | Supabase pooler, port 6543 |
| `AUTH_SECRET` | 64-hex cookie signing key |
| `PUBLIC_BASE_URL` | `https://kuro-panel-psi.vercel.app` |
| `LOADER_LICENSE_SALT` | must equal the constant inside the APK |
| `FALLBACK_LIB_VERSION` | `1.0` until you publish a lib |
| `MAX_LIB_UPLOAD_MB` | `4` (Vercel body limit) |
| `PGPOOL_MAX` | `3` |

## 2. Database

| | |
|---|---|
| Project | `kuro-panel` (free plan, region `ap-south-1` / Mumbai) |
| Ref | `ufiylsnyhreyxgeprvcj` |
| Tables | `users`, `keys_code`, `referral_code`, `history`, `modname`, `onoff`, `_ftext`, `feature`, `credit`, `lib`, `client_release`, `auth_attempt` |
| Seeded row | `admin` / `admin123` (the original hash from `main.sql`) |
| Password | stored in Vercel → `DATABASE_URL`; rotate it in Supabase → Settings → Database if you ever paste it somewhere unsafe |

The migration history contains `kuro_core_tables`, `kuro_release_and_throttle`
and `kuro_seed`, so you can see exactly what was applied.

The direct host (`db.<ref>.supabase.co`) is **IPv6-only** on new projects, which
is why the app uses the pooler. `web/src/lib/db.ts` also retries the sibling
`aws-0` ↔ `aws-1` shard automatically if Supabase ever moves the project.

## 3. APK

| | |
|---|---|
| Release | **https://github.com/masterace572-prog/Kurrrrrro/releases/latest** |
| Direct download | https://github.com/masterace572-prog/Kurrrrrro/releases/download/loader-5/xlox-loader.apk |
| Size | 9.6 MB, arm64-v8a, minSdk 21 |
| Compiled endpoint | `https://kuro-panel-psi.vercel.app` |

Rebuild any time: push a change under `loader/`, or run the **Build loader APK**
workflow by hand. Each build publishes a new release, so the "latest" link always
points at the newest APK.

## 4. Verification that was actually run

| Check | Result |
|---|---|
| `npm run build` (Next.js 15.5.28) | clean |
| `npm run smoke` (local, embedded Postgres) | 28/28 |
| GitHub Action **Verify deployment** against the live URL | **23/23** |
| ↳ health: env + database + schema | ok |
| ↳ `POST /server` with an unknown key | `USER OR GAME NOT REGISTERED` |
| ↳ `POST /server` with a real key | `status:true`, token = `md5("PUBG-key-serial-salt")`, fresh `rng`, `EXP`, feature flags, device slot consumed |
| ↳ `/api/version`, `/api/download` | 200 / correct content type |
| ↳ `/login`, `/dashboard` redirect | 200 / 307 |
| APK build | success, release `loader-5` |

`web/scripts/verify-live.mjs` is the internet-side checker; the workflow runs it
weekly as a keep-alive so the free Supabase project is not paused for inactivity.

## 5. Things only you can do

1. **Change the admin password** — Panel → Account → Change password
   (`admin123` is public knowledge; it is in the original MySQL dump).
2. **Lock down signups** — referral codes are single-use and admin-only, so
   nobody can register without one. Create them under **Referrals**.
3. **Re-sign the APK before selling it.** The release is debug-signed and the
   keystore in `loader/app/xlox.jks` uses password `11111111` — both are
   public knowledge now. Generate a new keystore, put it in `app/`, and update
   `signingConfigs` + the GitHub secrets.
4. **Rotate the Supabase database password** if you shared `DATABASE_URL`
   anywhere. Vercel keeps the value; changing it means updating that env var and
   redeploying.
5. **Add a custom domain** (optional) — Vercel → project → Settings → Domains.
   If you do, also update `PUBLIC_BASE_URL` and `PANEL_BASE_URL` (rebuild the APK).

### Commercial use
Vercel **Hobby** is for non-commercial projects. Selling keys counts as
commercial; then either Vercel Pro ($20/mo) or move the app to Cloudflare
Workers + Neon (see `DEPLOY.md`). Nothing in the code needs changing — it only
needs Node and a Postgres URL.

### Free-tier behaviour to expect
* Supabase pauses a project after ~7 days with no queries — the weekly Action
  keeps it awake; visiting the panel also wakes it.
* The first request after a long idle can take ~1 s while the pooler and the
  function warm up.
* 4 MB is the maximum lib zip you can upload through the panel; larger ones go
  through an external URL (GitHub Releases) — see `LIB-UPDATES.md`.
