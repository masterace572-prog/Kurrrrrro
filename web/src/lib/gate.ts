import { q, q1, tx } from './db';
import { buildToken } from './auth';
import { devicesOf, fmtUtc } from './domain';

/**
 * Port of App/Controllers/Connect.php — the endpoint the XLOX loader talks to.
 *
 * Request (application/x-www-form-urlencoded OR json):
 *   game=PUBG&user_key=xxx&serial=device_uid
 *
 * Success:
 *   {"status":true,"data":{...}}
 * Failure:
 *   {"status":false,"reason":"..."}
 */

export type GateOk = { status: true; data: Record<string, unknown> };
export type GateErr = { status: false; reason: string };
export type GateResult = GateOk | GateErr;

type KeyRow = {
  id_keys: number;
  game: string;
  user_key: string;
  duration: number | null;
  expired_date: Date | null;
  max_devices: number | null;
  devices: string | null;
  status: number;
  registrator: string | null;
};

const NAME_RE = /^[A-Za-z0-9_-]+$/;

export async function checkLicense(input: {
  game?: string | null;
  user_key?: string | null;
  serial?: string | null;
}): Promise<GateResult> {
  const game = (input.game ?? '').trim();
  const userKey = (input.user_key ?? '').trim();
  const serial = (input.serial ?? '').trim();

  if (!game || !NAME_RE.test(game)) return { status: false, reason: 'Bad Parameter' };
  if (!userKey || userKey.length > 64) return { status: false, reason: 'Bad Parameter' };
  if (!serial || !NAME_RE.test(serial) || serial.length > 128)
    return { status: false, reason: 'Bad Parameter' };

  // ---- maintenance switch (onoff table) -----------------------------------
  const onoff = await q1<{ status: string; myinput: string }>(
    `SELECT status, myinput FROM onoff WHERE id = 1`
  );
  if (onoff?.status === 'on') {
    return { status: false, reason: onoff.myinput?.trim() || 'SERVER UNDER MAINTENANCE' };
  }

  const key = await q1<KeyRow>(
    `SELECT id_keys, game, user_key, duration, expired_date, max_devices, devices, status, registrator
       FROM keys_code WHERE user_key = $1 AND game = $2 LIMIT 1`,
    [userKey, game]
  );

  if (!key) return { status: false, reason: 'USER OR GAME NOT REGISTERED' };
  if (Number(key.status) !== 1) return { status: false, reason: 'USER BLOCKED' };

  const now = new Date();
  const hours = Number(key.duration ?? 0);

  // ---- expiry ------------------------------------------------------------
  let expiry = key.expired_date;
  if (!expiry) {
    expiry = new Date(now.getTime() + Math.max(hours, 1) * 3600_000);
    await q(`UPDATE keys_code SET expired_date = $1, updated_at = now() WHERE id_keys = $2`, [
      expiry,
      key.id_keys,
    ]);
  } else if (now.getTime() >= new Date(expiry).getTime()) {
    return { status: false, reason: 'EXPIRED KEY' };
  }

  // ---- device binding ----------------------------------------------------
  const maxDevices = Number(key.max_devices ?? 1);
  const list = devicesOf(key.devices);
  if (!list.includes(serial)) {
    if (list.length >= maxDevices) return { status: false, reason: 'MAX DEVICE REACHED' };
    list.push(serial);
    await q(`UPDATE keys_code SET devices = $1, updated_at = now() WHERE id_keys = $2`, [
      list.join(','),
      key.id_keys,
    ]);
  }

  // ---- feature / branding payload ---------------------------------------
  const [modname, ftext, feature] = await Promise.all([
    q1<{ modname: string }>(`SELECT modname FROM modname WHERE id = 1`),
    q1<{ _status: string; _ftext: string }>(`SELECT _status, _ftext FROM _ftext WHERE id = 1`),
    q1<Record<string, string>>(`SELECT * FROM feature WHERE id = 1`),
  ]);

  const { real, token } = buildToken(game, userKey, serial);
  const exp = fmtUtc(expiry);

  return {
    status: true,
    data: {
      real,
      token,
      modname: modname?.modname ?? 'VIP MOD',
      mod_status: ftext?._status ?? 'Safe',
      credit: ftext?._ftext ?? '',
      ESP: feature?.esp ?? 'on',
      Item: feature?.item ?? 'on',
      AIM: feature?.aim ?? 'on',
      SilentAim: feature?.silentaim ?? 'on',
      BulletTrack: feature?.bullettrack ?? 'on',
      Floating: feature?.floating ?? 'on',
      Memory: feature?.memory ?? 'on',
      Setting: feature?.setting ?? 'on',
      expired_date: exp,
      EXP: exp,
      exdate: exp,
      device: maxDevices,
      duration: hours,
      rng: Math.floor(now.getTime() / 1000),
    },
  };
}

/** Public "is the panel alive" payload (Connect::index in the old panel). */
export async function panelInfo() {
  const [onoff, modname] = await Promise.all([
    q1<{ status: string; myinput: string }>(`SELECT status, myinput FROM onoff WHERE id = 1`),
    q1<{ modname: string }>(`SELECT modname FROM modname WHERE id = 1`),
  ]);
  return {
    web_info: {
      _client: 'KURO PANEL',
      version: '1.0.0',
      status: onoff?.status === 'on' ? 'maintenance' : 'online',
    },
    mod: { modname: modname?.modname ?? 'VIP MOD' },
    endpoint: { auth: '/server', method: 'POST', fields: ['game', 'user_key', 'serial'] },
    server_time: Math.floor(Date.now() / 1000),
  };
}

/** Device reset used by the keys page (`keys/reset` in the old panel). */
export async function resetDevices(userKey: string, actor: { username: string; level: number }) {
  const key = await q1<KeyRow & { registrator: string | null }>(
    `SELECT * FROM keys_code WHERE user_key = $1 LIMIT 1`,
    [userKey]
  );
  if (!key) return { ok: false as const, reason: 'not_registered' };
  if (actor.level !== 1 && key.registrator !== actor.username) {
    return { ok: false as const, reason: 'forbidden' };
  }
  await tx(async (c) => {
    await c.query(`UPDATE keys_code SET devices = NULL, updated_at = now() WHERE id_keys = $1`, [
      key.id_keys,
    ]);
  });
  return { ok: true as const, devices_max: Number(key.max_devices ?? 1) };
}
