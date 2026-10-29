import { createHash } from 'node:crypto';
import bcrypt from 'bcryptjs';

/**
 * The legacy PHP panel never hashed the raw password. It did:
 *
 *   $hash = md5("XquxmymXDtWRA66D" . $password);          // nata_helper.php
 *   password_hash($hash, PASSWORD_DEFAULT, ['cost' => 8]); // -> $2y$08$...
 *
 * To keep every account from the old MySQL dump usable without a password
 * reset, we reproduce that exact pre-hash and then let bcrypt do its job.
 */
export const PHP_SALT_PATTERN = 'XquxmymXDtWRA66D';

export function md5(input: string): string {
  return createHash('md5').update(input, 'utf8').digest('hex');
}

/** md5(salt + password) — the value the old panel fed into bcrypt. */
export function legacyPrehash(password: string): string {
  return md5(PHP_SALT_PATTERN + password);
}

export async function hashPassword(password: string): Promise<string> {
  return bcrypt.hash(legacyPrehash(password), 8);
}

export async function verifyPassword(password: string, stored: string): Promise<boolean> {
  if (!stored) return false;
  // PHP's PASSWORD_DEFAULT emits $2y$; bcryptjs understands it.
  try {
    return await bcrypt.compare(legacyPrehash(password), stored);
  } catch {
    return false;
  }
}

/**
 * Salt baked into the loader (main.cpp):
 *   auth = md5(`${game}-${user_key}-${UUID}-Vm8Lk7Uj2JmsjCPVPVjrLa7zgfx3uz9E`)
 *
 * The APK compares our `token` against the MD5 it computes locally, so this
 * value MUST stay in sync with the loader build.
 */
export function licenseSalt(): string {
  return process.env.LOADER_LICENSE_SALT || 'Vm8Lk7Uj2JmsjCPVPVjrLa7zgfx3uz9E';
}

export function buildToken(game: string, userKey: string, serial: string): { real: string; token: string } {
  const real = `${game}-${userKey}-${serial}-${licenseSalt()}`;
  return { real, token: md5(real) };
}
