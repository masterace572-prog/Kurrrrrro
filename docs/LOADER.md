# The XLOX loader ↔ panel contract

The loader is an Android app whose native library (`app/src/main/jni/main.cpp`)
does the license check in C++ with libcurl.

## 1. What the loader sends

```
POST https://<your-panel>/server
Content-Type: application/x-www-form-urlencoded
Accept: application/json

game=PUBG&user_key=<key>&serial=<device-uuid>
```

`serial` is a UUID derived from `user_key + android_id + Build.MODEL + Build.BRAND`
computed in `GetDeviceUniqueIdentifier()`.

## 2. What it expects back

```json
{
  "status": true,
  "data": {
    "real":  "PUBG-<key>-<serial>-<SALT>",
    "token": "<md5(real)>",
    "rng":   1793269147,
    "EXP":   "2026-10-30 10:19:07",
    "modname":    "VIP MOD",
    "mod_status": "Safe",
    "credit":     "MOD STATUS :- 100% SAFE",
    "ESP": "on", "Item": "on", "AIM": "on", "SilentAim": "on",
    "BulletTrack": "on", "Floating": "on", "Memory": "on", "Setting": "on",
    "expired_date": "2026-10-30 10:19:07",
    "device": 2
  }
}
```

Failure:

```json
{ "status": false, "reason": "EXPIRED KEY" }
```

Reasons the loader can receive: `Bad Parameter`, `USER OR GAME NOT REGISTERED`,
`USER BLOCKED`, `EXPIRED KEY`, `MAX DEVICE REACHED`, `SERVER ERROR`, or your
custom maintenance message.

### What the C++ side actually checks

```cpp
exdate = result["data"]["EXP"];                              // shown in the UI
token  = result["data"]["token"];
rng    = result["data"]["rng"];
if (rng + 30 > time(0)) {                                    // replay window
    auth = "PUBG-" + user_key + "-" + UUID + "-" + LICENSE;  // LICENSE is compiled in
    g_Auth = md5(auth);
    xConnected = (g_Token == g_Auth);
}
```

So **two things must match or the login silently fails**:

1. `rng` must be the current Unix timestamp (the panel returns `now`), and
2. `token` must be `md5("PUBG-" + key + "-" + serial + "-" + SALT)` where `SALT`
   is the constant in `main.cpp`:

```cpp
std::string license = oxorany("Vm8Lk7Uj2JmsjCPVPVjrLa7zgfx3uz9E");
```

That string is mirrored on the server as `LOADER_LICENSE_SALT`
(`web/.env.local` / Vercel env). **If you change one side you must change the
other and rebuild the APK.**

> Note: `game` is hard-coded to `PUBG` inside the APK, so the panel must store
> keys with `game = 'PUBG'` (the panel already does).

## 3. Change the endpoint

`loader/app/src/main/jni/main.cpp`, line ~33:

```cpp
#define PANEL_BASE_URL "https://kuro-panel-psi.vercel.app"
```

Set it to your deployment (no trailing slash). It is used as
`snprintf(lol, sizeof lol, "%s/server", PANEL_BASE_URL);` — the old hard-coded
`https://vipercheat.authapi.xyz/server` is gone.

The two other URLs (`Downtwo.Version()` / `Downtwo.Link()`) are also derived from
`PANEL_BASE_URL` now — they return `<PANEL_BASE_URL>/api/version` and
`<PANEL_BASE_URL>/api/download`. Nothing else has to be edited to push lib
updates; see **[LIB-UPDATES.md](LIB-UPDATES.md)**.

## 4. Build the APK

### Android Studio
1. Open the `loader/` folder as a project.
2. Let Gradle sync (it downloads NDK 24.0.8215888 and the obfuscator plugins).
3. Build → Build Bundle(s)/APK(s) → Build APK(s).

`app/build.gradle` builds **arm64-v8a only**, `minSdk 23`, `targetSdk 34`
(`-PxloxTargetSdk=28` builds a legacy behaviour-level APK instead).
The `app/libs/*.aar` (mundo, recommendpermission) and `classes.jar` are already
in the zip.

### GitHub Actions (no local Android SDK needed)
`.github/workflows/build-loader.yml` in this repo builds a debug APK on every
push that touches `loader/` and uploads it as a downloadable artifact.
You still need a signing keystore for a release build (`app/xlox.jks` expects
password `11111111` — **replace it**, the whole internet knows that one now).

## 4b. The game data (OBB) copy

### Both storage permissions are required

| permission | kind | what it unlocks |
|---|---|---|
| **Files** — `READ_EXTERNAL_STORAGE` + `WRITE_EXTERNAL_STORAGE` | runtime dialog | in legacy mode (this app targets Android 9 with `requestLegacyExternalStorage`), the ability to open `/sdcard/Android/obb/<game>` at all |
| **All files access** — `MANAGE_EXTERNAL_STORAGE` | Settings toggle | shared storage: `Download`, the storage root, `Android/media` |

Asking for only one of them is what made the copy report *"no game data"* on a
phone that had it: `Download` and the storage root were readable (all files
access), while `Android/obb/com.pubg.imobile` answered `null` from
`listFiles()` because the runtime grant was missing. A folder that cannot be
listed looks exactly like a folder that does not exist, which is why the message
named the wrong folders.

`StorageAccess` now asks for them one system screen at a time — the runtime
dialog first, then the app's own **All files access** toggle
(`ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION` with the package, not the
generic list) — from the login screen and from the main screen. While either is
missing the main button reads **Allow file access**, the GAME DATA card reads
**Needs file access**, and granting both immediately continues into the copy.

### Where it looks


`AppManager` looks for `main.<version>.com.pubg.imobile.obb` in every place it
can legitimately be, in this order:

| where | why |
|---|---|
| the sandbox folder for the game | the copy the loader writes |
| `/storage/emulated/0/Android/obb/com.pubg.imobile/` | the normal install location, and the one a user pastes into by hand |
| `/storage/emulated/0/Android/media/com.pubg.imobile/` | where some installs end up |
| `Download`, `Downloads`, the storage root | a file still sitting where it was downloaded |

For each folder it first probes the exact name the game will look for
(`main.<versionCode>.com.pubg.imobile.obb`, version code read from
`PackageManager`) — a known path can be opened even when the folder itself
cannot be listed, which is how the original loader's own check worked — and only
then lists the folder for anything else.

The first file found wins and its location is shown on the GAME DATA card
(`Ready · Android/obb`). If it is not in the sandbox folder, the loader copies it
there; if that copy cannot be written but the file is in the phone's own
`Android/obb`, the copy is treated as done — the sandbox serves that exact path,
which is why a hand-pasted obb starts the game.

When nothing is found, the failure dialog names every folder with its verdict
(`Android/obb — not readable (needs Files access)`, `Download — no .obb`), so a
permission problem can never again be reported as a missing file. It also names
the write path that failed, or the exception the sandbox threw. It also offers **Launch anyway** —
if the data is in place and only the copy is broken, the game still opens.

### What the copy needs to exist first

The copy writes into the game's **sandbox** data folder
(`MetaStorageManager.obtainAppExternalStorageDir(<game>)/Android/obb/<game>`).
The sandbox only maps that folder once it holds the game — so:

1. start the game once (the container is created, the folder appears),
2. then copy, and the file lands where the game looks for it.

Before that, the folder does not exist and a copy has nowhere to go. The main
screen says so instead of half-copying: with the game installed on the phone but
not in the sandbox, the hint under the button reads *Launch the game once — the
sandbox creates its data folder then*, and **Diagnostics** shows
`In sandbox: no`.

### Version match, the reason a "successful" copy still fails

BGMI loads exactly `main.<versionCode>.com.pubg.imobile.obb`, where the version
code is the installed build's. An obb from any other build — an older XAPK, or
one downloaded for a different update — copies fine, then the game closes on
launch or asks to download its data again. The loader now:

* builds the expected name from `PackageManager` and shows it in **Diagnostics**,
* reads the version out of a picked file's name and, when it differs, says
  *Different game version* — this file is for `main.<other>.…`, the game is
  `main.<yours>.…` — before copying anything, with **Copy anyway** / **Cancel**,
* reports the bytes that actually landed (`Copied 1.24 GB to the game's data
  folder`) instead of assuming the write succeeded.

### When the folder cannot be read at all

On Android 11 and newer a third-party app may be refused `/Android/obb` of
another app **no matter which permission it holds** — the runtime grant, All
files access, and the legacy flag together do not always get past it on recent
releases. Detection cannot be fixed by asking harder, so the screen offers the
two roads that remain:

| road | what it does |
|---|---|
| **Choose an .obb file** | the system picker (`ACTION_OPEN_DOCUMENT`) hands back a readable stream for the file the user picks; the loader copies that stream into the game's obb folder with a percentage. A real copy, no storage grant involved. |
| **Launch game anyway** | always on screen once the game is installed and the data is not in place, and the failure dialog leads with it. If BGMI already reads its own data, the game simply opens. |

**Diagnostics** (a quiet link under the button) prints every fact the app can
observe — game version code, expected file name, whether the sandbox holds the
game, the copy target and whether it is writable, each folder's verdict, the
library version, the device and the time — with a **Copy** button, so a report
can be pasted rather than retyped.

So a copy that cannot see the phone's folder is no longer a dead end. The
failure dialog says *Game data not found* and offers **Launch game**,
**Choose .obb**, **Close** — never a bare *Copy failed*.

Two older traps are still worth knowing:

* **The folder has to exist.** A freshly installed BGMI that has never been
  opened has no OBB folder at all — the game downloads its data on first run.
  The loader then says no data was found, which means "there is nothing there
  to copy", not "permission denied".
* **The sandbox folder appears after the game is installed into the sandbox.**
  Install first, then copy.

On Android 11 and newer the shared `/Android/obb` folder of another app is
restricted by the platform. This build targets Android 9 (`targetSdk 28`, the
upstream value) and sets `requestLegacyExternalStorage="true"`, which is the
combination that still reads it; a build with `-PxloxTargetSdk=34` gives that
up.

### Launching

`MAct.launchGame()` tries, in order:

1. `MetaActivityManager.obtainSplashLaunchIntent(pkg, ctx)` and `startActivity`
2. `MetaActivityManager.launchAppWithSplash(ctx, pkg)` — for builds where the
   intent comes back null
3. `getLaunchIntentForPackage(pkg)` — a plain intent

After 3.5 s it asks `MetaActivityManager.isAppRunning(pkg)`. If the game is
up, the strip says *Game started*; if a launch was handed over and nothing has
come up yet, it says nothing rather than reporting a failure. It only prints
*Launch failed* when every path threw. The old build reported
"Launch failed: game not available" while the game was opening, because it
trusted a null return value instead of the game actually starting.

## 5. Local testing without an APK

```bash
cd web
npm run dev
# in another terminal
curl -X POST http://localhost:3000/server \
  -d "game=PUBG&user_key=<a key from the panel>&serial=TESTDEVICE"
```

`npm run smoke` does this automatically, including the device-limit, expiry,
maintenance and token checks.
