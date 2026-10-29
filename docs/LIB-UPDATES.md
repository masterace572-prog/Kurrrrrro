# Pushing a lib update (no APK rebuild)

The loader downloads an optional payload — the **lib** — into its private
storage on first launch. After that it only re-downloads when the **version
string** changes. That means you can ship a new lib to every user *without*
rebuilding the APK, and without anybody re-downloading when nothing changed.

```
                 ┌──────────────────────────────┐
  launch ───────►│ GET  /api/version            │  "1.4"
                 │ GET  /api/download   (only   │  lib.zip
                 │      if 1.4 ≠ installed)     │
                 └──────────────────────────────┘
```

## Panel UI

**Server control → Lib updates** (`/settings/server/releases`)

| Action | What happens on devices |
|---|---|
| **Publish update** (upload zip + version) | Every device whose stored version differs downloads the zip once, then remembers the new version. |
| **Bump version** (no upload) | Same zip, new number → every device downloads it again. Use this when you replaced the *file* behind an external URL. |
| **Use** (on an older row) | Roll back: the version of that row becomes live, so devices that had a newer one download it again. |
| **Delete** | Removes a row from history (the live one can't be deleted). |

Uploaded zips are stored in Postgres (`client_release.content`) so there is no
extra storage service to configure. **Limit: 4 MB** by default, because Vercel
caps request bodies at 4.5 MB. Larger libs are published as a URL instead:

1. Create a **GitHub Release** on a public repo and attach `lib.zip`.
2. Copy the asset URL, e.g.
   `https://github.com/you/unlock/releases/download/v1.4/lib.zip`
3. In the panel choose **External URL**, paste it, set the version, publish.
4. `/api/download` answers with a `302` to that URL; `HttpURLConnection` follows
   redirects, so the loader needs no changes.

## What the zip must contain

The library is loaded from exactly one path:

    /data/data/com.xlox/files/loader/libbgmi.so

So the zip has to contain `libbgmi.so` — at the root or inside a folder, both
work now (the loader moves a nested one to the root after extracting). Two more
things have to be true or the load fails even though the file is there:

* **arm64-v8a.** The APK ships `arm64-v8a` only, so a 32-bit lib cannot be
  loaded into the process (`is 32-bit instead of 64-bit` in logcat).
* **A real shared object.** `file libbgmi.so` should say "ELF 64-bit LSB shared
  object, ARM aarch64".

The loader now says what went wrong instead of failing quietly: on a failed
update the main screen shows `LIB FAILED: …` with the HTTP status, "not a zip"
or "no libbgmi.so in it", and logcat (`adb logcat -s Downtwo Load`) names the
URL, the size and the extracted path.

## Endpoints

| Endpoint | Response |
|---|---|
| `GET /api/version` | `text/plain`, first line = version. No caching. |
| `GET /api/version?format=json` | Details for debugging (size, sha256, published at). |
| `GET /api/download` | The zip (`application/zip`), or `302` to the external URL. |
| `GET /api/health` | `{ ok, checks }` — env vars, DB reachable, schema present, lib channel. |

Nothing here is authenticated: the loader has no credentials, exactly like the
original panel. The version string is public information anyway.

## Version rules

* 1–32 characters: `A-Z a-z 0-9 . _ -` — e.g. `1.4`, `1.5`, `2026.10.29`.
* Must be **unique** in the history (so rollbacks stay unambiguous).
* Anything the loader has stored that differs → one download → cached.
* Semver is not required; the loader only does an equality check.

## What the client does (already patched in `loader/`)

* `main.cpp` → `Downtwo.Version()` returns `<PANEL_BASE_URL>/api/version` and
  `Downtwo.Link()` returns `<PANEL_BASE_URL>/api/download`, both derived from the
  single `PANEL_BASE_URL` define.
* `MAct.startWorkflow()` runs a **check-only** pass at launch
  (`Downtwo.MODE_CHECK`). If an update exists the main button becomes
  `UPDATE AVAILABLE` (one toast per version); tapping it downloads.
* `Downtwo` downloads to `Xlox.zip.part`, checks the HTTP status, the byte count
  and the `PK` header, then extracts into `files/loader/`. A zip that packs the
  library inside a folder is hoisted, so `files/loader/libbgmi.so` always ends up
  where the loader looks for it. Up to 3 attempts, then it reports the reason.
* Never blocks the loader: an unreachable update server just means "keep using
  the lib you already have".

## How the library gets loaded (and why it may not)

The library is not loaded by the loader app: it is loaded **inside the game's
own process**, which is the only place a mod library can run.

1. `BoxApplication.attachBaseContext` registers
   `MetaCore.setProcessLifecycleCallback(this)` with the sandbox SDK.
2. When the sandbox starts the guest process for `com.pubg.imobile` (including
   sub-processes such as `com.pubg.imobile:xxx`), it calls
   `BoxApplication.onCreate(packageName, processName)` in that process.
3. `LibLoader.load()` finds the file, loads any sibling `.so` it `DT_NEEDED`s,
   and calls `System.load(...)` on it.

Three things used to make that silently do nothing:

* **The path.** Inside a guest process the sandbox rewrites `getFilesDir()` to
  the guest's virtual storage, so `new File(getFilesDir(), "loader/libbgmi.so")`
  pointed at an empty directory. `LibLoader` now tries, in order: the absolute
  path the download recorded (`com.xlox.download → lib_path`), the literal
  `/data/user/0/<pkg>/files/loader/libbgmi.so` and its `/data/data/` twin, then
  the context-relative locations (`files`, `noBackupFiles`, `codeCache`).
* **The ABI.** A 32-bit or non-ELF file can never load into an arm64-v8a
  process. `Elf` reads the header before loading, and the panel shows
  `Lib · arm64-v8a, 812 KB` after an update. Wrong architecture reads
  `armeabi-v7a, … — cannot load into arm64-v8a`.
* **Dependencies.** A zip that ships extra `.so` files gets them preloaded from
  the same directory; otherwise the load fails with `library "libX.so" not
  found`. Newer Android also refuses to `dlopen` a world-writable file, so a
  failure mentioning `writable` retries after `chmod 0444` on the library and
  its siblings (the downloader deletes the old copy before updating, so that
  read-only flag never blocks the next update).

Every attempt appends one line to `files/loader/load.log` — `ok: /data/.../libbgmi.so`
or `fail: … <the platform message>`. After the game has been launched once, the
loader shows a red `LIB LOAD FAILED · …` banner with that line. On a development
device the same information is in logcat:

```
adb logcat -s Load LibLoader Downtwo
```

## Failure modes you may see

| Message | Cause |
|---|---|
| `Update server returned HTTP 404` | `/api/version` missing — wrong `PANEL_BASE_URL`, or the app isn't deployed. |
| `Unexpected version format: …` | The endpoint returned HTML (usually a login page or a hosting error page). |
| `The server did not return a zip file` | `/api/download` returned JSON/HTML — no release published, or the external URL is a landing page instead of the asset. |
| `Download incomplete (a of b)` | Connection dropped; it retries twice automatically. |
| `Not enough free storage …` | Device is full (needs size + 12 MB). |
| `The zip has no libbgmi.so in it` | The archive contains something else entirely — check what was uploaded. |
| `LIB LOAD FAILED · … not arm64` | The file is not a 64-bit ARM library (or is not an ELF at all). |
| `LIB LOAD FAILED · System.load(…): dlopen failed …` | The platform refused it; the message says why (missing dependency, wrong arch, empty file). |
