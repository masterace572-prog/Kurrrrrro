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

## Where the lib comes from

There are two channels, tried in this order:

1. **The panel** — `GET /api/version` (and its `?format=json` form) plus
   `GET /api/download`. This is the one to use once a release is published:
   it is the only place the version and the bytes are guaranteed to agree.
2. **The GitHub mirror** — used only when the panel reports *no published
   release* (`file_name` is null in `/api/version?format=json`), or when the
   panel's download fails:

   | what | URL |
   |---|---|
   | version | `https://raw.githubusercontent.com/masterace572-prog/Kurrrrrro/main/Version.txt` |
   | zip | `https://github.com/masterace572-prog/Kurrrrrro/releases/download/Lib/libbbb.zip` |

   Both URLs live next to `PANEL_BASE_URL` in
   `loader/app/src/main/jni/main.cpp` (`MIRROR_VERSION_URL`,
   `MIRROR_PACKAGE_URL`), so they change in one place — and changing them
   needs a new APK, unlike the panel channel.

   **Bumping the mirror is two edits:** put the new version on the first line
   of `Version.txt` in the `main` branch, and replace the asset on the `Lib`
   release (the file may be called anything; the URL above must keep working,
   so keep the asset name `libbbb.zip` or update `MIRROR_PACKAGE_URL`).
   The loader compares that line with the version it installed and downloads
   only when it differs — so *bump `Version.txt` first*, then re-upload.

A version string identifies the bytes, not the channel: if the panel and the
mirror both say `1.1.0`, that is the same file. Changing **either** side is
enough — the first source (panel, then mirror) whose version differs from what
is installed is the one that gets downloaded, and the same version is never
downloaded twice. That is why switching channels cannot start a download loop,
and why an older panel release can never hide a newer mirror version.

## What the zip must contain

The library is loaded from exactly one path:

    /data/data/com.xlox/files/loader/libbgmi.so

So the zip has to contain `libbgmi.so` — at the root or inside a folder, both
work now (the loader moves a nested one to the root after extracting, together
with any `.so` sitting next to it, since those are usually its dependencies).

A zip built by Android tooling carries one copy per ABI
(`lib/arm64-v8a/libbgmi.so`, `lib/armeabi-v7a/libbgmi.so`, …). The loader keeps
the **arm64-v8a** one, deletes the rest, and refuses the download with
`The zip's libbgmi.so is armeabi-v7a, the game is arm64-v8a` if that is all the
archive has — which is better than discovering it at launch.

Two more things have to be true or the load fails even though the file is there:

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
* `MAct` runs a **check-only** pass on every start (`Downtwo.MODE_CHECK`) and
  whenever `CHECK` in the LIBRARY card is tapped. If an update exists, the
  launch button becomes `INSTALL UPDATE 1.2`, the card shows
  `v1.1.0 · arm64-v8a, 2.9 MB  →  v1.2`, and tapping the button downloads.
  Before this check existed the app only looked for the file, never for a newer
  one — so the only way to get an update was to clear the app data.
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
  process. `Elf` reads the header before loading, and the version line under
  LAUNCH shows what is on disk: `Version 3 · arm64-v8a, 812 KB`. A wrong
  architecture reads `armeabi-v7a, 23 KB — cannot load into arm64-v8a`, and a
  file that is not a library at all reads `not an ELF file (…)`.
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
| `LIB IS UP TO DATE` (after CHECK) | The version on the panel/mirror equals the installed one. Re-uploading the same version changes nothing: bump the version. |
| `LIB LOAD FAILED · … not arm64` | The file is not a 64-bit ARM library (or is not an ELF at all). |
| `LIB LOAD FAILED · System.load(…): dlopen failed …` | The platform refused it; the message says why (missing dependency, wrong arch, empty file). |
