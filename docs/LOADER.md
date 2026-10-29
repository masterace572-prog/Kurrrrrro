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

Two ways the game data gets into the sandbox, in this order:

1. **Automatic copy** — if the phone lets the app read
   `/storage/emulated/0/Android/obb/com.pubg.imobile`, the loader copies every
   `.obb` it finds there and skips the rest of this section. That works on
   Android 10 and below, and on some OEM builds.
2. **Hand-over through the picker** — everything from Android 11 up. Google
   closed the folder to third-party apps: `/Android/obb` and `/Android/data`
   belong to the app whose name is on them, and **All files access
   (`MANAGE_EXTERNAL_STORAGE`) does not open them** — it grants shared storage
   *except* those two directories. Android 13 also closed the Storage Access
   Framework loophole that file managers used to reach them. So no permission
   can make the loader read that folder on a current phone; the file has to be
   given to it. The card says so, and "Choose the .obb file" opens the system
   picker. The read grant travels with the selected file, and the sandbox
   folder is writable, so nothing blocks that route.

   The file must keep its name: `main.<version>.com.pubg.imobile.obb` (or
   `patch.<version>…`). Anything else is rejected with a message saying what to
   rename it to. Keeping the file in `Download` is the easiest place for the
   picker to find it.

If copying fails, the card's **Show details** opens a report you can copy and
send on: Android version, the grant state, every folder that was probed with
what the filesystem answered (`exists`, `readable`, `.obb` count), the sandbox
folder, free space and the last error.

The two file decisions are plain Java (`ObbSelection`, `ObbAccess`) with JVM
tests that the build workflow runs before compiling the APK:

```bash
javac -d /tmp/obb loader/app/src/main/java/com/xlox/utils/ObbSelection.java \
      loader/app/src/main/java/com/xlox/utils/ObbAccess.java \
      loader/app/src/test/*.java
java -cp /tmp/obb ObbSelectionTest
java -cp /tmp/obb ObbAccessTest
```

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
