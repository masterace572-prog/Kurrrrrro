'use server';

import { redirect } from 'next/navigation';
import { headers } from 'next/headers';
import { revalidatePath } from 'next/cache';
import { q, q1, tx } from '@/lib/db';
import { hashPassword, verifyPassword, md5 } from '@/lib/auth';
import { createSessionToken, setSessionCookie, clearSessionCookie, readSession, currentUserOrNull } from '@/lib/session';
import { DURATIONS, KEY_BATCH_SIZES, normalizeDevices, priceFor, randomKey, genReferralCode } from '@/lib/domain';

const str = (fd: FormData, k: string) => String(fd.get(k) ?? '').trim();
const num = (fd: FormData, k: string) => Number(fd.get(k) ?? 0);

function flash(path: string, message: string, type: 'success' | 'danger' | 'warning' = 'success'): never {
  redirect(`${path}?m=${encodeURIComponent(message)}&t=${type}`);
}

async function clientIp(): Promise<string> {
  const h = await headers();
  return (
    h.get('x-forwarded-for')?.split(',')[0]?.trim() ||
    h.get('x-real-ip') ||
    '0.0.0.0'
  );
}

/* ============================== AUTH ==================================== */

export async function loginAction(formData: FormData) {
  const username = str(formData, 'username');
  const password = String(formData.get('password') ?? '');
  const remember = str(formData, 'stay_log') === '1';

  if (!/^[A-Za-z0-9]{4,25}$/.test(username)) flash('/login', 'Invalid username or password.', 'danger');
  if (password.length < 6 || password.length > 45) flash('/login', 'Invalid username or password.', 'danger');

  const user = await q1<{ id_users: number; username: string; level: number; status: number; password: string; expiration_date: Date | null }>(
    `SELECT id_users, username, level, status, password, expiration_date FROM users WHERE username = $1`,
    [username]
  );

  if (!user || !(await verifyPassword(password, user.password))) {
    flash('/login', 'Invalid username or password.', 'danger');
  }
  if (!user.status) flash('/login', 'Your account is disabled.', 'danger');
  if (user.expiration_date && new Date(user.expiration_date) < new Date()) {
    flash('/login', 'Your panel access has expired. Contact the owner.', 'warning');
  }

  await q(`UPDATE users SET user_ip = $1, updated_at = now() WHERE id_users = $2`, [await clientIp(), user.id_users]);

  const token = await createSessionToken(
    { id: user.id_users, username: user.username, level: Number(user.level) },
    remember
  );
  await setSessionCookie(token, remember);
  flash('/dashboard', 'Login successful.', 'success');
}

export async function registerAction(formData: FormData) {
  const username = str(formData, 'username');
  const fullname = str(formData, 'fullname');
  const email = str(formData, 'email');
  const password = String(formData.get('password') ?? '');
  const password2 = String(formData.get('password2') ?? '');
  const referral = str(formData, 'referral');

  if (!/^[A-Za-z0-9]{4,25}$/.test(username)) flash('/register', 'Username must be 4-25 letters/numbers.', 'danger');
  if (!/^[A-Za-z0-9]{4,25}$/.test(fullname)) flash('/register', 'Full name must be 4-25 letters/numbers.', 'danger');
  if (password.length < 6) flash('/register', 'Password too short.', 'danger');
  if (password !== password2) flash('/register', 'Passwords do not match.', 'danger');
  if (!referral) flash('/register', 'A referral code is required.', 'danger');

  const code = await q1<{ id_reff: number; used_by: string; set_saldo: number; level: number; created_by: string; acc_expiration: Date | null }>(
    `SELECT id_reff, used_by, set_saldo, level, created_by, acc_expiration FROM referral_code WHERE code = $1`,
    [md5(referral)]
  );
  if (!code) flash('/register', 'Wrong referral code.', 'danger');
  if (code.used_by) flash('/register', 'This referral code has already been used.', 'danger');

  const taken = await q1(`SELECT 1 FROM users WHERE username = $1 OR (email <> '' AND email = $2)`, [username, email]);
  if (taken) flash('/register', 'Username or email already registered.', 'danger');

  await tx(async (c) => {
    await c.query(
      `INSERT INTO users (username, fullname, email, level, saldo, uplink, password, user_ip, expiration_date, created_at, updated_at)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8,COALESCE($9, now() + interval '7 days'), now(), now())`,
      [username, fullname, email, code.level, code.set_saldo || 0, code.created_by, await hashPassword(password), await clientIp(), code.acc_expiration]
    );
    await c.query(`UPDATE referral_code SET used_by = $1, updated_at = now() WHERE id_reff = $2`, [username, code.id_reff]);
  });

  flash('/login', 'Registration successful. You can sign in now.', 'success');
}

export async function logoutAction() {
  await clearSessionCookie();
  redirect('/login?m=Signed+out.&t=success');
}

/* ============================== KEYS ==================================== */

export async function generateKeysAction(formData: FormData) {
  const me = await currentUserOrNull();
  if (!me) redirect('/login');

  const game = str(formData, 'game') || 'PUBG';
  const duration = num(formData, 'duration');
  const maxDevices = num(formData, 'max_devices');
  const batch = str(formData, 'loopcount') || '1';
  const mode = str(formData, 'custominput') || 'auto';
  const custom = str(formData, 'cuslicense');

  if (!DURATIONS[duration]) flash('/keys/generate', 'Invalid duration.', 'danger');
  if (maxDevices < 1) flash('/keys/generate', 'Invalid device count.', 'danger');

  const count = KEY_BATCH_SIZES[batch] ?? 6;
  const total = priceFor(duration, maxDevices) * (count - 1);
  if (Number(me.saldo) - total < 0) {
    flash('/keys/generate', 'Insufficient balance. Please top up.', 'warning');
  }

  const created: string[] = [];
  for (let i = 1; i < count; i++) {
    let key = `${me.username}-${duration}-${randomKey()}`;
    if (mode === 'custom') {
      if (custom.length < 4 || custom.length > 20) {
        flash('/keys/generate', 'Custom key must be 4-20 characters.', 'danger');
      }
      const exists = await q1(`SELECT 1 FROM keys_code WHERE user_key = $1`, [custom]);
      if (exists) flash('/keys/generate', `Key "${custom}" already exists.`, 'danger');
      key = custom;
    }
    await q(
      `INSERT INTO keys_code (game, user_key, duration, max_devices, registrator, created_at, updated_at)
       VALUES ($1,$2,$3,$4,$5, now(), now()) ON CONFLICT (user_key) DO NOTHING`,
      [game, key, duration, maxDevices, me.username]
    );
    created.push(key);
    if (mode === 'custom') break;
  }

  await tx(async (c) => {
    await c.query(`UPDATE users SET saldo = saldo - $1, updated_at = now() WHERE id_users = $2`, [total, me.id_users]);
    await c.query(`INSERT INTO history (keys_id, user_do, info) VALUES ($1,$2,$3)`, [
      String(created[0] ?? ''),
      me.username,
      `${game}|${(created[0] ?? '').slice(0, 5)}|${duration}|${maxDevices}|${created.length} keys`,
    ]);
  });

  revalidatePath('/keys');
  flash('/keys', `${created.length} key(s) generated. ${total} credits used.`, 'success');
}

export async function updateKeyAction(formData: FormData) {
  const me = await currentUserOrNull();
  if (!me) redirect('/login');

  const id = num(formData, 'id_keys');
  const key = await q1<{ id_keys: number; registrator: string | null }>(
    `SELECT id_keys, registrator FROM keys_code WHERE id_keys = $1`,
    [id]
  );
  if (!key) flash('/keys', 'Key not found.', 'danger');
  if (me.level !== 1 && key.registrator !== me.username) flash('/keys', 'Not your key.', 'danger');

  const status = num(formData, 'status') ? 1 : 0;

  if (me.level === 1) {
    const userKey = str(formData, 'user_key');
    const maxDevices = Math.max(1, num(formData, 'max_devices'));
    const expiredRaw = str(formData, 'expired_date');
    const expired = expiredRaw ? new Date(expiredRaw.replace(' ', 'T') + 'Z') : null;

    if (!/^[A-Za-z0-9]{1,40}$/.test(userKey)) flash(`/keys/${id}`, 'Invalid key format.', 'danger');

    await q(
      `UPDATE keys_code SET game=$1, user_key=$2, duration=$3, max_devices=$4, status=$5,
              registrator=$6, expired_date=$7, devices=$8, updated_at=now()
        WHERE id_keys=$9`,
      [
        str(formData, 'game') || 'PUBG',
        userKey,
        Math.max(1, num(formData, 'duration')),
        maxDevices,
        status,
        str(formData, 'registrator') || null,
        expired && !Number.isNaN(expired.getTime()) ? expired : null,
        normalizeDevices(str(formData, 'devices'), maxDevices),
        id,
      ]
    );
  } else {
    await q(`UPDATE keys_code SET status=$1, updated_at=now() WHERE id_keys=$2`, [status, id]);
  }

  revalidatePath('/keys');
  flash('/keys', 'Key updated.', 'success');
}

export async function resetDevicesAction(formData: FormData) {
  const me = await currentUserOrNull();
  if (!me) redirect('/login');
  const userKey = str(formData, 'user_key');
  const key = await q1<{ id_keys: number; registrator: string | null }>(
    `SELECT id_keys, registrator FROM keys_code WHERE user_key = $1`,
    [userKey]
  );
  if (!key) flash('/keys', 'Key not found.', 'danger');
  if (me.level !== 1 && key.registrator !== me.username) flash('/keys', 'Not your key.', 'danger');
  await q(`UPDATE keys_code SET devices = NULL, updated_at = now() WHERE id_keys = $1`, [key.id_keys]);
  revalidatePath('/keys');
  flash('/keys', `Devices reset for ${userKey}.`, 'success');
}

export async function deleteKeysAction(formData: FormData) {
  const me = await currentUserOrNull();
  if (!me) redirect('/login');
  const scope = str(formData, 'scope');
  const userKey = str(formData, 'user_key');

  if (scope === 'expired') {
    const cond = me.level === 1 ? '' : ` AND registrator = $1`;
    const res = await q(
      `DELETE FROM keys_code WHERE expired_date IS NOT NULL AND expired_date < now()${cond} RETURNING id_keys`,
      me.level === 1 ? [] : [me.username]
    );
    revalidatePath('/keys');
    flash('/keys', `Deleted ${res.length} expired key(s).`, 'success');
  }

  if (scope === 'unused') {
    const cond = me.level === 1 ? '' : ` AND registrator = $1`;
    const res = await q(
      `DELETE FROM keys_code WHERE expired_date IS NULL${cond} RETURNING id_keys`,
      me.level === 1 ? [] : [me.username]
    );
    revalidatePath('/keys');
    flash('/keys', `Deleted ${res.length} unused key(s).`, 'success');
  }

  if (scope === 'one' && userKey) {
    const key = await q1<{ registrator: string | null }>(`SELECT registrator FROM keys_code WHERE user_key = $1`, [userKey]);
    if (!key) flash('/keys', 'Key not found.', 'danger');
    if (me.level !== 1 && key.registrator !== me.username) flash('/keys', 'Not your key.', 'danger');
    await q(`DELETE FROM keys_code WHERE user_key = $1`, [userKey]);
    revalidatePath('/keys');
    flash('/keys', 'Key deleted.', 'success');
  }

  flash('/keys', 'Nothing to do.', 'warning');
}

/* ============================ SERVER SETTINGS ============================ */

export async function saveServerAction(formData: FormData) {
  const me = await currentUserOrNull();
  if (!me || me.level > 2) redirect('/dashboard');

  const section = str(formData, 'section');

  if (section === 'modname') {
    await q(`UPDATE modname SET modname = $1 WHERE id = 1`, [str(formData, 'modname').slice(0, 100)]);
  } else if (section === 'maintenance') {
    const on = str(formData, 'status') === 'on';
    await q(`UPDATE onoff SET status = $1, myinput = $2 WHERE id = 1`, [
      on ? 'on' : 'off',
      str(formData, 'myinput').slice(0, 500),
    ]);
  } else if (section === 'credit') {
    await q(`UPDATE _ftext SET _ftext = $1, _status = $2 WHERE id = 1`, [
      str(formData, 'credit').slice(0, 100),
      str(formData, 'mod_status').slice(0, 100),
    ]);
  } else if (section === 'feature') {
    if (me.level !== 1) flash('/settings/server', 'Owner only.', 'danger');
    const cols = ['esp', 'item', 'silentaim', 'aim', 'bullettrack', 'memory', 'floating', 'setting'];
    const values = cols.map((c) => (str(formData, c) === 'on' ? 'on' : 'off'));
    await q(
      `UPDATE feature SET esp=$1, item=$2, silentaim=$3, aim=$4, bullettrack=$5, memory=$6, floating=$7, setting=$8 WHERE id=1`,
      values
    );
  }

  revalidatePath('/settings/server');
  flash('/settings/server', 'Settings saved. Live clients pick this up on their next check.', 'success');
}

/* ================================ USERS ================================= */

export async function updateUserAction(formData: FormData) {
  const me = await currentUserOrNull();
  if (!me || me.level > 2) redirect('/dashboard');
  const id = num(formData, 'id_users');

  if (me.level === 1) {
    const level = Math.min(3, Math.max(1, num(formData, 'level'))) || 3;
    const expRaw = str(formData, 'expiration_date');
    const exp = expRaw ? new Date(expRaw.replace(' ', 'T') + 'Z') : null;
    const newPassword = String(formData.get('password') ?? '');
    await q(
      `UPDATE users SET fullname=$1, email=$2, level=$3, saldo=$4, status=$5, expiration_date=$6, updated_at=now()
        WHERE id_users=$7`,
      [str(formData, 'fullname'), str(formData, 'email'), level, num(formData, 'saldo'), num(formData, 'status') ? 1 : 0, exp, id]
    );
    if (newPassword.length >= 6) {
      await q(`UPDATE users SET password=$1 WHERE id_users=$2`, [await hashPassword(newPassword), id]);
    }
  } else {
    // admins may only flip status / balance of their own downline
    const target = await q1<{ uplink: string | null }>(`SELECT uplink FROM users WHERE id_users=$1`, [id]);
    if (!target || target.uplink !== me.username) flash('/admin/manage-users', 'Not your downline.', 'danger');
    await q(`UPDATE users SET saldo=$1, status=$2, updated_at=now() WHERE id_users=$3`, [
      num(formData, 'saldo'),
      num(formData, 'status') ? 1 : 0,
      id,
    ]);
  }

  revalidatePath('/admin/manage-users');
  flash('/admin/manage-users', 'User updated.', 'success');
}

export async function createReferralAction(formData: FormData) {
  const me = await currentUserOrNull();
  if (!me || me.level > 2) redirect('/dashboard');

  const level = Math.min(3, Math.max(1, num(formData, 'level'))) || 3;
  const saldo = Math.max(0, num(formData, 'set_saldo'));
  const days = Math.max(1, num(formData, 'accExpire'));
  const plain = genReferralCode();

  await q(
    `INSERT INTO referral_code (code, "Referral", level, set_saldo, created_by, acc_expiration, created_at, updated_at)
     VALUES ($1,$2,$3,$4,$5, now() + ($6 || ' days')::interval, now(), now())`,
    [md5(plain), plain, level, saldo, me.username, String(days)]
  );

  revalidatePath('/admin/create-referral');
  flash('/admin/create-referral', `Referral code created: ${plain}`, 'success');
}

/* ============================== SETTINGS ================================ */

export async function updateProfileAction(formData: FormData) {
  const me = await currentUserOrNull();
  if (!me) redirect('/login');

  const fullname = str(formData, 'fullname');
  const email = str(formData, 'email');
  const current = String(formData.get('current_password') ?? '');
  const next = String(formData.get('new_password') ?? '');
  const next2 = String(formData.get('new_password2') ?? '');

  await q(`UPDATE users SET fullname=$1, email=$2, updated_at=now() WHERE id_users=$3`, [fullname, email, me.id_users]);

  if (next || current) {
    const row = await q1<{ password: string }>(`SELECT password FROM users WHERE id_users=$1`, [me.id_users]);
    if (!row || !(await verifyPassword(current, row.password))) {
      flash('/settings', 'Profile saved, but the current password was wrong.', 'warning');
    }
    if (next.length < 6) flash('/settings', 'New password must be at least 6 characters.', 'danger');
    if (next !== next2) flash('/settings', 'New passwords do not match.', 'danger');
    await q(`UPDATE users SET password=$1, updated_at=now() WHERE id_users=$2`, [await hashPassword(next), me.id_users]);
    flash('/settings', 'Profile and password updated.', 'success');
  }

  flash('/settings', 'Profile updated.', 'success');
}
