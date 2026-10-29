'use server';

import { revalidatePath } from 'next/cache';
import { redirect } from 'next/navigation';
import { headers } from 'next/headers';
import { q, q1, tx } from '@/lib/db';
import { hashPassword, verifyPassword, md5 } from '@/lib/auth';
import {
  createSessionToken,
  setSessionCookie,
  currentUserOrNull,
} from '@/lib/session';
import { DURATIONS, KEY_BATCH_SIZES, normalizeDevices, priceFor, randomKey, genReferralCode, devicesOf } from '@/lib/domain';
import { describeError, str, num, bool, parseUtcInput, type FormState } from '@/lib/result';
import { checkLoginThrottle, recordLoginAttempt } from '@/lib/ratelimit';
import { bumpCurrentVersion, deleteRelease, isVersion, publishRelease, setCurrentRelease, MAX_UPLOAD_BYTES } from '@/lib/release';

/* ------------------------------------------------------------------ utils */

const fail = (message: string, field?: string, code = 'VALIDATION'): FormState => ({ ok: false, message, field, code });
const okay = (message: string): FormState => ({ ok: true, message });

function flash(path: string, message: string, type: 'success' | 'danger' | 'warning' = 'success'): never {
  // some destinations already carry a query (the freshly generated keys do)
  const separator = path.includes('?') ? '&' : '?';
  redirect(`${path}${separator}m=${encodeURIComponent(message)}&t=${type}`);
}

async function clientIp(): Promise<string> {
  const h = await headers();
  return h.get('x-forwarded-for')?.split(',')[0]?.trim() || h.get('x-real-ip') || '0.0.0.0';
}

/** Runs a mutation, converting exceptions into a friendly message. */
async function guard(fn: () => Promise<FormState>): Promise<FormState> {
  try {
    return await fn();
  } catch (e) {
    // redirect()/notFound() throw control-flow errors — let those through
    if (isRedirectError(e)) throw e;
    console.error('[action]', e);
    const d = describeError(e);
    return { ok: false, message: d.message, code: d.code };
  }
}

function isRedirectError(e: unknown): boolean {
  return typeof e === 'object' && e !== null && 'digest' in e && String((e as { digest?: string }).digest).startsWith('NEXT_REDIRECT');
}

/* ============================== AUTH ==================================== */

export async function loginAction(_prev: FormState, formData: FormData): Promise<FormState> {
  return guard(async () => {
    const username = str(formData, 'username');
    const password = String(formData.get('password') ?? '');
    const remember = bool(formData, 'stay_log');

    if (!username) return fail('Please enter your username.', 'username');
    if (!/^[A-Za-z0-9]{4,25}$/.test(username)) return fail('Username must be 4-25 letters or numbers.', 'username');
    if (password.length < 6) return fail('Password must be at least 6 characters.', 'password');

    const ip = await clientIp();
    const throttle = await checkLoginThrottle(username, ip);
    if (throttle.locked) {
      const mins = Math.ceil(throttle.retryInSeconds / 60);
      return fail(`Too many failed attempts. Try again in about ${mins} minute(s).`, 'password', 'THROTTLED');
    }

    const user = await q1<{
      id_users: number;
      username: string;
      level: number;
      status: number;
      password: string;
      expiration_date: Date | null;
    }>(`SELECT id_users, username, level, status, password, expiration_date FROM users WHERE username = $1`, [username]);

    if (!user || !(await verifyPassword(password, user.password))) {
      await recordLoginAttempt(username, ip, false);
      return fail('Invalid username or password.', 'password', 'BADCREDS');
    }

    await recordLoginAttempt(username, ip, true);

    if (!user.status) return fail('This account has been disabled. Contact the owner.', 'username', 'DISABLED');
    if (user.expiration_date && new Date(user.expiration_date) < new Date()) {
      return fail('Your panel access has expired. Contact the owner to renew.', 'username', 'EXPIRED');
    }

    await q(`UPDATE users SET user_ip = $1, updated_at = now() WHERE id_users = $2`, [ip, user.id_users]);

    const token = await createSessionToken(
      { id: user.id_users, username: user.username, level: Number(user.level) },
      remember
    );
    await setSessionCookie(token, remember);
    flash('/dashboard', `Welcome back, ${user.username}.`, 'success');
  });
}

export async function registerAction(_prev: FormState, formData: FormData): Promise<FormState> {
  return guard(async () => {
    const username = str(formData, 'username');
    const fullname = str(formData, 'fullname');
    const email = str(formData, 'email');
    const password = String(formData.get('password') ?? '');
    const password2 = String(formData.get('password2') ?? '');
    const referral = str(formData, 'referral');

    if (!referral) return fail('A referral code is required to register.', 'referral');
    if (!/^[A-Za-z0-9]{4,25}$/.test(username)) return fail('Username must be 4-25 letters or numbers.', 'username');
    if (!/^[A-Za-z0-9 ]{4,25}$/.test(fullname)) return fail('Full name must be 4-25 letters or numbers.', 'fullname');
    if (email && !/^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(email)) return fail('That email address looks invalid.', 'email');
    if (password.length < 6) return fail('Password must be at least 6 characters.', 'password');
    if (password !== password2) return fail('The two passwords do not match.', 'password2');

    const code = await q1<{
      id_reff: number;
      used_by: string;
      set_saldo: number;
      level: number;
      created_by: string;
      acc_expiration: Date | null;
    }>(
      `SELECT id_reff, used_by, set_saldo, level, created_by, acc_expiration
         FROM referral_code WHERE code = $1`,
      [md5(referral)]
    );

    if (!code) return fail('That referral code is not valid.', 'referral');
    if (code.used_by) return fail(`That referral code was already used by ${code.used_by}.`, 'referral');
    if (code.acc_expiration && new Date(code.acc_expiration) < new Date()) {
      return fail('That referral code has expired.', 'referral');
    }

    const taken = await q1<{ username: string }>(
      `SELECT username FROM users WHERE lower(username) = lower($1) OR ($2 <> '' AND lower(email) = lower($2)) LIMIT 1`,
      [username, email]
    );
    if (taken) return fail('That username or email is already registered.', taken.username.toLowerCase() === username.toLowerCase() ? 'username' : 'email');

    const ip = await clientIp();
    await tx(async (c) => {
      await c.query(
        `INSERT INTO users (username, fullname, email, level, saldo, uplink, password, user_ip, expiration_date, created_at, updated_at)
         VALUES ($1,$2,$3,$4,$5,$6,$7,$8,COALESCE($9, now() + interval '7 days'), now(), now())`,
        [username, fullname, email, code.level, code.set_saldo || 0, code.created_by, await hashPassword(password), ip, code.acc_expiration]
      );
      await c.query(`UPDATE referral_code SET used_by = $1, updated_at = now() WHERE id_reff = $2`, [username, code.id_reff]);
    });

    flash('/login', 'Account created. You can sign in now.', 'success');
  });
}

/* ================================ KEYS ================================== */

export async function generateKeysAction(_prev: FormState, formData: FormData): Promise<FormState> {
  return guard(async () => {
    const me = await currentUserOrNull();
    if (!me) return fail('Your session expired. Please sign in again.', undefined, 'NOAUTH');

    const game = str(formData, 'game') || 'PUBG';
    const duration = num(formData, 'duration');
    const maxDevices = num(formData, 'max_devices');
    const batch = str(formData, 'loopcount') || '5';
    const mode = str(formData, 'custominput') || 'auto';
    const custom = str(formData, 'cuslicense');

    if (!DURATIONS[duration]) return fail('Choose a valid duration.', 'duration');
    if (!Number.isInteger(maxDevices) || maxDevices < 1 || maxDevices > 50) {
      return fail('Max devices must be between 1 and 50.', 'max_devices');
    }
    if (!(batch in KEY_BATCH_SIZES)) return fail('Choose how many keys to generate.', 'loopcount');
    if (mode === 'custom' && (custom.length < 4 || custom.length > 20)) {
      return fail('Custom key must be 4-20 characters.', 'cuslicense');
    }
    if (mode === 'custom' && !/^[A-Za-z0-9_-]+$/.test(custom)) {
      return fail('Custom key can only use letters, numbers, - and _.', 'cuslicense');
    }

    const count = KEY_BATCH_SIZES[batch];
    const total = priceFor(duration, maxDevices) * (count - 1);

    if (Number(me.saldo) - total < 0) {
      return fail(
        `Not enough balance: this costs ${total} but you have ${Number(me.saldo)}. Ask your admin to top up.`,
        undefined,
        'NOFUNDS'
      );
    }

    const created: string[] = [];
    for (let i = 1; i < count; i++) {
      let key = mode === 'custom' ? custom : `${me.username}-${duration}-${randomKey()}`;
      const exists = await q1(`SELECT 1 FROM keys_code WHERE user_key = $1`, [key]);
      if (exists) {
        if (mode === 'custom') return fail(`The key "${custom}" already exists.`, 'cuslicense', 'DUPLICATE');
        key = `${me.username}-${duration}-${randomKey()}`;
      }
      await q(
        `INSERT INTO keys_code (game, user_key, duration, max_devices, registrator, created_at, updated_at)
         VALUES ($1,$2,$3,$4,$5, now(), now())`,
        [game, key, duration, maxDevices, me.username]
      );
      created.push(key);
    }

    await tx(async (c) => {
      await c.query(`UPDATE users SET saldo = saldo - $1, updated_at = now() WHERE id_users = $2`, [total, me.id_users]);
      await c.query(`INSERT INTO history (keys_id, user_do, info) VALUES ($1,$2,$3)`, [
        String(created[0] ?? ''),
        me.username,
        `${game}|${(created[0] ?? '').slice(0, 5)}|${duration}h|${maxDevices} dev|${created.length} keys`,
      ]);
    });

    revalidatePath('/keys');
    revalidatePath('/dashboard');

    // Land on the key list so the result is impossible to miss (the toast alone
    // used to appear at the top of a long form, off-screen on a phone). The
    // list marks the new rows and offers a copy button for each of them.
    const preview =
      created.length > 2
        ? `${created[0]}, ${created[1]} +${created.length - 2} more`
        : created.join(', ');
    flash('/keys', `${created.length} key(s) generated · ${total} credits — ${preview}`, 'success');
  });
}

export async function updateKeyAction(_prev: FormState, formData: FormData): Promise<FormState> {
  return guard(async () => {
    const me = await currentUserOrNull();
    if (!me) return fail('Your session expired. Please sign in again.', undefined, 'NOAUTH');

    const id = num(formData, 'id_keys');
    const key = await q1<{ id_keys: number; registrator: string | null }>(
      `SELECT id_keys, registrator FROM keys_code WHERE id_keys = $1`,
      [id]
    );
    if (!key) return fail('That key no longer exists.', undefined, 'NOTFOUND');
    if (me.level !== 1 && key.registrator !== me.username) {
      return fail('That key belongs to another reseller.', undefined, 'FORBIDDEN');
    }

    const status = num(formData, 'status') ? 1 : 0;

    if (me.level !== 1) {
      await q(`UPDATE keys_code SET status = $1, updated_at = now() WHERE id_keys = $2`, [status, id]);
    } else {
      const userKey = str(formData, 'user_key');
      const maxDevices = Math.max(1, num(formData, 'max_devices', 1));
      const duration = Math.max(1, num(formData, 'duration', 2));
      const expired = parseUtcInput(str(formData, 'expired_date'));

      if (!/^[A-Za-z0-9_-]{1,40}$/.test(userKey)) {
        return fail('Key may only contain letters, numbers, - and _ (max 40).', 'user_key');
      }
      if (expired === 'invalid') return fail('Expiry must look like 2026-12-31 23:59:59 (UTC).', 'expired_date');

      const dup = await q1(`SELECT 1 FROM keys_code WHERE user_key = $1 AND id_keys <> $2`, [userKey, id]);
      if (dup) return fail('Another key already uses that name.', 'user_key', 'DUPLICATE');

      await q(
        `UPDATE keys_code SET game=$1, user_key=$2, duration=$3, max_devices=$4, status=$5,
                registrator=$6, expired_date=$7, devices=$8, updated_at=now()
          WHERE id_keys=$9`,
        [
          str(formData, 'game') || 'PUBG',
          userKey,
          duration,
          maxDevices,
          status,
          str(formData, 'registrator') || null,
          expired,
          normalizeDevices(str(formData, 'devices'), maxDevices),
          id,
        ]
      );
    }

    revalidatePath('/keys');
    revalidatePath(`/keys/${id}`);
    return okay('Key saved.');
  });
}

export async function resetDevicesAction(_prev: FormState, formData: FormData): Promise<FormState> {
  return guard(async () => {
    const me = await currentUserOrNull();
    if (!me) return fail('Your session expired. Please sign in again.', undefined, 'NOAUTH');

    const userKey = str(formData, 'user_key');
    const key = await q1<{ id_keys: number; registrator: string | null }>(
      `SELECT id_keys, registrator FROM keys_code WHERE user_key = $1`,
      [userKey]
    );
    if (!key) return fail('That key no longer exists.', undefined, 'NOTFOUND');
    if (me.level !== 1 && key.registrator !== me.username) return fail('That key belongs to another reseller.', undefined, 'FORBIDDEN');

    await q(`UPDATE keys_code SET devices = NULL, updated_at = now() WHERE id_keys = $1`, [key.id_keys]);
    await q(`INSERT INTO history (keys_id, user_do, info) VALUES ($1,$2,$3)`, [userKey, me.username, 'HWID reset']);
    revalidatePath('/keys');
    return okay(`All devices unbound from ${userKey}.`);
  });
}

export async function revokeDeviceAction(_prev: FormState, formData: FormData): Promise<FormState> {
  return guard(async () => {
    const me = await currentUserOrNull();
    if (!me) return fail('Your session expired. Please sign in again.', undefined, 'NOAUTH');

    const userKey = str(formData, 'user_key');
    const device = str(formData, 'device');
    const key = await q1<{ id_keys: number; registrator: string | null; devices: string | null }>(
      `SELECT id_keys, registrator, devices FROM keys_code WHERE user_key = $1`,
      [userKey]
    );
    if (!key) return fail('That key no longer exists.', undefined, 'NOTFOUND');
    if (me.level !== 1 && key.registrator !== me.username) return fail('That key belongs to another reseller.', undefined, 'FORBIDDEN');

    const next = devicesOf(key.devices).filter((d) => d !== device);
    await q(`UPDATE keys_code SET devices = $1, updated_at = now() WHERE id_keys = $2`, [
      next.length ? next.join(',') : null,
      key.id_keys,
    ]);
    revalidatePath(`/keys/${key.id_keys}`);
    revalidatePath('/keys');
    return okay('Device unbound.');
  });
}

export async function deleteKeysAction(_prev: FormState, formData: FormData): Promise<FormState> {
  return guard(async () => {
    const me = await currentUserOrNull();
    if (!me) return fail('Your session expired. Please sign in again.', undefined, 'NOAUTH');

    const scope = str(formData, 'scope');
    const userKey = str(formData, 'user_key');
    const staff = me.level === 1;
    const cond = staff ? '' : ' AND registrator = $1';
    const args = staff ? [] : [me.username];

    if (scope === 'expired') {
      const res = await q<{ id_keys: number }>(
        `DELETE FROM keys_code WHERE expired_date IS NOT NULL AND expired_date < now()${cond} RETURNING id_keys`,
        args
      );
      revalidatePath('/keys');
      return okay(res.length ? `${res.length} expired key(s) deleted.` : 'No expired keys to delete.');
    }

    if (scope === 'unused') {
      const res = await q<{ id_keys: number }>(
        `DELETE FROM keys_code WHERE expired_date IS NULL${cond} RETURNING id_keys`,
        args
      );
      revalidatePath('/keys');
      return okay(res.length ? `${res.length} unused key(s) deleted.` : 'No unused keys to delete.');
    }

    if (scope === 'one') {
      if (!userKey) return fail('No key selected.', undefined, 'VALIDATION');
      const key = await q1<{ registrator: string | null }>(`SELECT registrator FROM keys_code WHERE user_key = $1`, [userKey]);
      if (!key) return fail('That key no longer exists.', undefined, 'NOTFOUND');
      if (me.level !== 1 && key.registrator !== me.username) return fail('That key belongs to another reseller.', undefined, 'FORBIDDEN');
      await q(`DELETE FROM keys_code WHERE user_key = $1`, [userKey]);
      revalidatePath('/keys');
      return okay(`Key ${userKey} deleted.`);
    }

    return fail('Unknown delete action.', undefined, 'VALIDATION');
  });
}

export async function toggleKeyStatusAction(_prev: FormState, formData: FormData): Promise<FormState> {
  return guard(async () => {
    const me = await currentUserOrNull();
    if (!me) return fail('Your session expired. Please sign in again.', undefined, 'NOAUTH');
    const userKey = str(formData, 'user_key');
    const key = await q1<{ id_keys: number; registrator: string | null; status: number }>(
      `SELECT id_keys, registrator, status FROM keys_code WHERE user_key = $1`,
      [userKey]
    );
    if (!key) return fail('That key no longer exists.', undefined, 'NOTFOUND');
    if (me.level !== 1 && key.registrator !== me.username) return fail('That key belongs to another reseller.', undefined, 'FORBIDDEN');
    const next = key.status ? 0 : 1;
    await q(`UPDATE keys_code SET status = $1, updated_at = now() WHERE id_keys = $2`, [next, key.id_keys]);
    revalidatePath('/keys');
    return okay(next ? 'Key unblocked.' : 'Key blocked.');
  });
}

/* ============================ SERVER SETTINGS ============================ */

export async function saveServerAction(_prev: FormState, formData: FormData): Promise<FormState> {
  return guard(async () => {
    const me = await currentUserOrNull();
    if (!me) return fail('Your session expired. Please sign in again.', undefined, 'NOAUTH');
    if (me.level > 2) return fail('Only admins and owners can change server settings.', undefined, 'FORBIDDEN');

    const section = str(formData, 'section');

    if (section === 'modname') {
      const modname = str(formData, 'modname').slice(0, 100);
      if (!modname) return fail('Mod name cannot be empty.', 'modname');
      await q(`UPDATE modname SET modname = $1 WHERE id = 1`, [modname]);
    } else if (section === 'maintenance') {
      const on = str(formData, 'status') === 'on';
      const msg = str(formData, 'myinput').slice(0, 500);
      if (on && !msg) return fail('Write the message users should see while maintenance is on.', 'myinput');
      await q(`UPDATE onoff SET status = $1, myinput = $2 WHERE id = 1`, [on ? 'on' : 'off', msg]);
    } else if (section === 'credit') {
      const credit = str(formData, 'credit').slice(0, 100);
      const statusText = str(formData, 'mod_status').slice(0, 100);
      if (!credit) return fail('Credit text cannot be empty.', 'credit');
      await q(`UPDATE _ftext SET _ftext = $1, _status = $2 WHERE id = 1`, [credit, statusText || 'Safe']);
    } else {
      return fail('Unknown section.', undefined, 'VALIDATION');
    }

    revalidatePath('/settings/server');
    return okay('Saved. Clients pick this up on their next check.');
  });
}

/* =========================== LIB UPDATE CHANNEL ========================== */

export async function uploadLibAction(prev: FormState, formData: FormData): Promise<FormState> {
  return guard(async () => {
    const me = await currentUserOrNull();
    if (!me) return fail('Your session expired. Please sign in again.', undefined, 'NOAUTH');
    if (me.level !== 1) return fail('Only the owner can publish lib updates.', undefined, 'FORBIDDEN');

    const version = str(formData, 'version');
    const notes = str(formData, 'notes');
    const externalUrl = str(formData, 'external_url');
    const file = formData.get('libfile');

    if (!isVersion(version)) {
      return fail('Version must be 1-32 chars: letters, numbers, dot, dash or underscore (e.g. 1.4).', 'version');
    }

    let content: Buffer | undefined;
    let fileName = 'lib.zip';

    if (file instanceof File && file.size > 0) {
      if (file.size > MAX_UPLOAD_BYTES) {
        return fail(
          `That file is ${(file.size / 1024 / 1024).toFixed(1)} MB. The serverless limit is ${(MAX_UPLOAD_BYTES / 1024 / 1024).toFixed(0)} MB — ` +
            'host bigger zips on GitHub Releases and paste the URL below instead.',
          'libfile',
          'TOOBIG'
        );
      }
      const buf = Buffer.from(await file.arrayBuffer());
      const isZip = buf[0] === 0x50 && buf[1] === 0x4b;
      if (!isZip) return fail('That file is not a zip (PK header missing).', 'libfile', 'BADFILE');
      content = buf;
      fileName = file.name || `lib-${version}.zip`;
    } else if (!externalUrl) {
      return fail('Attach a zip file or paste an external download URL.', 'libfile', 'NOFILE');
    }

    if (externalUrl && !/^https?:\/\/\S+$/i.test(externalUrl)) {
      return fail('The external URL must start with http:// or https://', 'external_url');
    }

    await publishRelease({ version, fileName, content, externalUrl: externalUrl || undefined, notes, uploadedBy: me.username });

    revalidatePath('/settings/server');
    revalidatePath('/settings/server/releases');
    return okay(`Published v${version}. Every loader will now offer the update.`);
  });
}

export async function bumpVersionAction(_prev: FormState, formData: FormData): Promise<FormState> {
  return guard(async () => {
    const me = await currentUserOrNull();
    if (!me) return fail('Your session expired. Please sign in again.', undefined, 'NOAUTH');
    if (me.level !== 1) return fail('Only the owner can push updates.', undefined, 'FORBIDDEN');

    const version = str(formData, 'version');
    if (!isVersion(version)) return fail('Invalid version string.', 'version');
    await bumpCurrentVersion(version, me.username);
    revalidatePath('/settings/server');
    revalidatePath('/settings/server/releases');
    return okay(`Version set to ${version}. Devices with an older version will download the lib once, then stop.`);
  });
}

export async function setCurrentReleaseAction(_prev: FormState, formData: FormData): Promise<FormState> {
  return guard(async () => {
    const me = await currentUserOrNull();
    if (!me) return fail('Your session expired. Please sign in again.', undefined, 'NOAUTH');
    if (me.level !== 1) return fail('Only the owner can roll back releases.', undefined, 'FORBIDDEN');
    await setCurrentRelease(num(formData, 'id'));
    revalidatePath('/settings/server');
    revalidatePath('/settings/server/releases');
    return okay('Release rolled back to the selected build.');
  });
}

export async function deleteReleaseAction(_prev: FormState, formData: FormData): Promise<FormState> {
  return guard(async () => {
    const me = await currentUserOrNull();
    if (!me) return fail('Your session expired. Please sign in again.', undefined, 'NOAUTH');
    if (me.level !== 1) return fail('Only the owner can delete releases.', undefined, 'FORBIDDEN');
    await deleteRelease(num(formData, 'id'));
    revalidatePath('/settings/server');
    revalidatePath('/settings/server/releases');
    return okay('Release removed from history.');
  });
}

/* ================================ USERS ================================= */

export async function updateUserAction(_prev: FormState, formData: FormData): Promise<FormState> {
  return guard(async () => {
    const me = await currentUserOrNull();
    if (!me) return fail('Your session expired. Please sign in again.', undefined, 'NOAUTH');
    if (me.level > 2) return fail('Access denied.', undefined, 'FORBIDDEN');

    const id = num(formData, 'id_users');
    if (!id) return fail('Invalid user.', undefined, 'VALIDATION');

    const target = await q1<{ id_users: number; uplink: string | null; level: number }>(
      `SELECT id_users, uplink, level FROM users WHERE id_users = $1`,
      [id]
    );
    if (!target) return fail('That user no longer exists.', undefined, 'NOTFOUND');

    const saldo = num(formData, 'saldo', 0);
    if (!Number.isFinite(saldo) || saldo < 0) return fail('Balance cannot be negative.', 'saldo');
    const status = num(formData, 'status', 1) ? 1 : 0;

    if (me.level === 1) {
      const level = Math.min(3, Math.max(1, num(formData, 'level', 3)));
      const expired = parseUtcInput(str(formData, 'expiration_date'));
      if (expired === 'invalid') return fail('Access-until must look like 2030-01-01 00:00:00.', 'expiration_date');
      if (id === me.id_users && level !== 1) return fail('You cannot demote your own owner account.', 'level');
      if (id === me.id_users && !status) return fail('You cannot disable your own account.', 'status');

      await q(
        `UPDATE users SET fullname=$1, email=$2, level=$3, saldo=$4, status=$5, expiration_date=$6, updated_at=now()
          WHERE id_users=$7`,
        [str(formData, 'fullname'), str(formData, 'email'), level, saldo, status, expired, id]
      );

      const newPassword = String(formData.get('password') ?? '');
      if (newPassword) {
        if (newPassword.length < 6) return fail('New password must be at least 6 characters.', 'password');
        await q(`UPDATE users SET password=$1, updated_at=now() WHERE id_users=$2`, [await hashPassword(newPassword), id]);
      }
    } else {
      if (target.uplink !== me.username) return fail('That user is not in your downline.', undefined, 'FORBIDDEN');
      if (Number(target.level) <= 2) return fail('You cannot modify an admin or owner.', undefined, 'FORBIDDEN');
      await q(`UPDATE users SET saldo=$1, status=$2, updated_at=now() WHERE id_users=$3`, [saldo, status, id]);
      const newPassword = String(formData.get('password') ?? '');
      if (newPassword) {
        if (newPassword.length < 6) return fail('New password must be at least 6 characters.', 'password');
        await q(`UPDATE users SET password=$1, updated_at=now() WHERE id_users=$2`, [await hashPassword(newPassword), id]);
      }
    }

    revalidatePath('/admin/manage-users');
    revalidatePath(`/admin/user/${id}`);
    return okay('User saved.');
  });
}

export async function createReferralAction(_prev: FormState, formData: FormData): Promise<FormState> {
  return guard(async () => {
    const me = await currentUserOrNull();
    if (!me) return fail('Your session expired. Please sign in again.', undefined, 'NOAUTH');
    if (me.level > 2) return fail('Access denied.', undefined, 'FORBIDDEN');

    const level = Math.min(3, Math.max(1, num(formData, 'level', 3)));
    const saldo = Math.max(0, num(formData, 'set_saldo', 0));
    const days = num(formData, 'accExpire', 30);
    if (!Number.isFinite(days) || days < 1 || days > 3650) return fail('Access days must be between 1 and 3650.', 'accExpire');
    if (level === 1 && me.level !== 1) return fail('Only the owner can mint owner codes.', 'level', 'FORBIDDEN');

    const plain = genReferralCode();
    await q(
      `INSERT INTO referral_code (code, "Referral", level, set_saldo, created_by, acc_expiration, created_at, updated_at)
       VALUES ($1,$2,$3,$4,$5, now() + ($6 || ' days')::interval, now(), now())`,
      [md5(plain), plain, level, saldo, me.username, String(Math.round(days))]
    );

    revalidatePath('/admin/create-referral');
    return okay(`Code created: ${plain} — share it with your reseller.`);
  });
}

/* ============================== SETTINGS ================================ */

export async function updateProfileAction(_prev: FormState, formData: FormData): Promise<FormState> {
  return guard(async () => {
    const me = await currentUserOrNull();
    if (!me) return fail('Your session expired. Please sign in again.', undefined, 'NOAUTH');

    const fullname = str(formData, 'fullname');
    const email = str(formData, 'email');
    const current = String(formData.get('current_password') ?? '');
    const next = String(formData.get('new_password') ?? '');
    const next2 = String(formData.get('new_password2') ?? '');

    if (email && !/^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(email)) return fail('That email address looks invalid.', 'email');

    if (next || current || next2) {
      const row = await q1<{ password: string }>(`SELECT password FROM users WHERE id_users=$1`, [me.id_users]);
      if (!current) return fail('Enter your current password to change it.', 'current_password');
      if (!row || !(await verifyPassword(current, row.password))) {
        return fail('Your current password is incorrect.', 'current_password');
      }
      if (next.length < 6) return fail('The new password must be at least 6 characters.', 'new_password');
      if (next !== next2) return fail('The new passwords do not match.', 'new_password2');
      if (next === current) return fail('The new password must be different from the old one.', 'new_password');
      await q(`UPDATE users SET password=$1, updated_at=now() WHERE id_users=$2`, [await hashPassword(next), me.id_users]);
    }

    await q(`UPDATE users SET fullname=$1, email=$2, updated_at=now() WHERE id_users=$3`, [fullname, email, me.id_users]);
    revalidatePath('/settings');
    return okay(next ? 'Profile and password updated.' : 'Profile updated.');
  });
}
