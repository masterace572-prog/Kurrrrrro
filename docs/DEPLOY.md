# Deploying the panel for ₹0

> **Already done for this repo** — the live deployment is at
> `https://kuro-panel-psi.vercel.app`. See [`LIVE.md`](LIVE.md) for the record.
> This file is the reproducible procedure, e.g. for a second instance.

The old panel was PHP + MySQL, which needs a traditional host (cPanel / VPS).
The rewrite in `web/` is a Next.js app + Postgres, so it runs on the free tiers
you already have connected: **Vercel** (app) + **Supabase** (database).

```
Browser  ──────────────►  Vercel (Next.js)  ──────────►  Supabase Postgres
Android loader  ───────►  https://<app>.vercel.app/server
```

---

## 1. Create the database (Supabase)

1. Open <https://supabase.com/dashboard> → **New project**.
   - Name: `kuro-panel`
   - Region: pick the one closest to your players.
   - Save the database password somewhere safe.
2. Go to **Project → Settings → Database → Connection string → URI**.
   Take the **Connection pooling** URI (port `6543`, mode *Transaction*):

   ```
   postgresql://postgres.abcdefghijkl:YOUR-PASSWORD@aws-0-ap-south-1.pooler.supabase.com:6543/postgres
   ```

   > The direct connection (port `5432`) also works but only allows a handful of
   > connections — Vercel's serverless functions will exhaust it. Use the pooler.

3. Load the schema. Two ways:

   **a) From your machine**
   ```bash
   cd web
   DATABASE_URL="postgresql://…6543/postgres" npm run db:push
   ```

   **b) From the Supabase dashboard**
   SQL Editor → paste `db/schema.sql` → Run, then paste `db/seed.sql` → Run.

4. You now have an account: **`admin` / `admin123`** (the original hash from the
   MySQL dump — it still works). **Change it right after your first login.**

## 2. Deploy the app (Vercel)

### Option A — GitHub (recommended)

1. Push this repo to GitHub (already done). The panel lives in `web/`, so the
   branch Vercel deploys **must contain that directory** — see the note below.
2. On <https://vercel.com/new> → **Import** the repository.
3. Set **Root Directory** to `web`.
4. Framework preset: **Next.js** (auto-detected). Build command and output stay default.
5. Add environment variables:

   | Key | Value |
   |-----|-------|
   | `DATABASE_URL` | the Supabase pooler URI from step 1 |
   | `AUTH_SECRET` | `node -e "console.log(require('crypto').randomBytes(32).toString('hex'))"` |
   | `PUBLIC_BASE_URL` | `https://kuro-panel-psi.vercel.app` |
   | `LOADER_LICENSE_SALT` | leave default (see `docs/LOADER.md`) |
| `FALLBACK_LIB_VERSION` | `1.0` (optional — only used before you publish a lib) |
| `MAX_LIB_UPLOAD_MB` | `4` (optional; Vercel caps bodies at 4.5 MB) |

6. **Deploy**. Your panel is live at `https://<your-app>.vercel.app` and the
   loader endpoint is `https://<your-app>.vercel.app/server`.

### If production builds fail with `Root Directory "web" does not exist`

That error (`NOW_SANDBOX_WORKER_ROOTDIR_NOT_EXIST`) means the deployed branch has
no `web/` folder — Vercel looks for it inside the commit it just cloned. It
happened while `main` held only `Version.txt` and the sources, so every
production build failed while the previews of the feature branch built fine.

The fix is to keep `web/` on the production branch. `main` now carries the whole
project (this was merged in), so:

* editing `Version.txt` on `main` deploys as usual;
* a branch that only has the loader and a `Version.txt` cannot be the production
  branch.

### Option B — from the CLI

```bash
cd web
npm i -g vercel
vercel                 # links the project
vercel env add DATABASE_URL
vercel env add AUTH_SECRET
vercel --prod
```

### Custom domain
Vercel → Project → Settings → Domains → add `panel.yourdomain.com` and point
the DNS record they show you. Free with any domain you own.

---

## 3. Point the loader at it

Edit one line in `loader/app/src/main/jni/main.cpp`:

```cpp
#define PANEL_BASE_URL "https://<your-app>.vercel.app"
```

Then rebuild the APK — see `docs/LOADER.md`.

After that first build you never need to rebuild for a **lib** change: publish
the new zip under **Server control → Lib updates** and bump the version. Every
device downloads it once and caches it — see `docs/LIB-UPDATES.md`.

---

## 4. Free tier limits (what actually matters)

| Service | Free allowance | Practical impact |
|---|---|---|
| Vercel Hobby | 100 GB bandwidth, 100k function invocations/mo | Thousands of key checks per day. Fine. |
| Supabase Free | 500 MB DB, 5 GB egress, pauses after 7 days idle | Keys are tiny; this holds hundreds of thousands. The project pauses if unused for a week — one page load wakes it. |
| Neon Free | 0.5 GB, auto-suspend after 5 min idle | Cold start ~0.5 s on the first request after idle. |

> ⚠️ **Vercel Hobby is licensed for non-commercial use.** A paid panel that
> resells keys counts as commercial. If you start charging, either move to
> Vercel Pro ($20/mo) or use one of the alternatives below.

## 5. Other free options (equally supported)

The app only needs *Node + one Postgres URL*, so it is portable.

| Host | Free? | Notes |
|---|---|---|
| **Vercel** | yes (hobby) | Best DX, zero config. Non-commercial only. |
| **Cloudflare Workers** | yes, generous | Use `@opennextjs/cloudflare`. Pair with **Neon** (Workers can't open raw TCP to Supabase without Hyperdrive). |
| **Netlify** | yes | Next.js runtime supported; set root dir to `web`. |
| **Render** | free web service | Sleeps after 15 min idle (~50 s cold start). Good as a backup. |
| **Railway / Fly.io / Koyeb** | trial/small free | Fine if you want a long-running container. |
| **Supabase** | yes | Postgres + storage. **Cannot run PHP**, so it is not a host for the old panel. |
| **Neon / Aiven / Tembo** | yes | Drop-in Postgres alternatives to Supabase. |

### What about keeping the PHP version?
If you ever *must* run the original PHP code, you need PHP 8.1+ and MySQL/MariaDB
on the same host. Free options exist (InfinityFree, 000webhost, FreeHostia) but
they inject ads, throttle requests and usually block the outbound
`file_get_contents`/cURL calls a panel needs — and **none of them can host the
`/server` endpoint reliably enough for a paid product**. Vercel + Supabase is the
better path, which is what `web/` gives you.

---

## 6. Local development

```bash
cd web
cp .env.example .env.local     # already filled in for the embedded DB
npm install
npm run db:dev                 # terminal 1: real Postgres 18 on :55432
npm run db:push                # terminal 2: schema + seed
npm run dev                    # http://localhost:3000
```

`npm run db:dev -- --reset` wipes the local cluster.
`npm run smoke` (with the app running) exercises the whole loader contract plus
the lib update channel — **28 checks**.
