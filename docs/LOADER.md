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

`loader/` is the original source again, so the copy behaves as it always did:
`AppManager.copyObbFolder()` reads

    /storage/emulated/0/Android/obb/com.pubg.imobile/

and copies `main.<version>.com.pubg.imobile.obb` (plus `patch.*`) into the
sandbox folder the SDK hands out:

    …/Android/obb/com.pubg.imobile/Android/obb/com.pubg.imobile/

Two things are worth knowing when it does not copy:

* **The folder has to exist.** A freshly installed BGMI that has never been
  opened has no OBB folder at all — the game downloads its data on first run.
  The loader reports `OBB folder not found!`, which means "there is nothing
  there to copy", not "permission denied".
* **The sandbox folder appears after the game is installed into the sandbox.**
  That is the LAUNCH button's install step. Install first, then copy.

On Android 11 and newer the shared `/Android/obb` folder of another app is
restricted by the platform. This build targets Android 9 (`targetSdk 28`, the
upstream value) and sets `requestLegacyExternalStorage="true"`, which is the
combination that still reads it; a build with `-PxloxTargetSdk=34` gives that
up.

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
