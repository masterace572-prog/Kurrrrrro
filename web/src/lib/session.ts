import { cookies } from 'next/headers';
import { SignJWT, jwtVerify } from 'jose';
import { redirect } from 'next/navigation';
import { q1 } from './db';

export const COOKIE_NAME = 'kuro_session';

export type SessionUser = {
  id: number;
  username: string;
  level: number;
};

function secret(): Uint8Array {
  const s = process.env.AUTH_SECRET;
  if (!s || s.length < 16) {
    throw new Error('AUTH_SECRET is missing or too short (min 16 chars). See web/.env.example');
  }
  return new TextEncoder().encode(s);
}

export async function createSessionToken(user: SessionUser, remember: boolean): Promise<string> {
  const maxAge = remember ? 60 * 60 * 24 : 60 * 30; // 24h or 30m, same as the PHP panel
  return new SignJWT({ uid: user.id, un: user.username, lvl: user.level })
    .setProtectedHeader({ alg: 'HS256' })
    .setIssuedAt()
    .setExpirationTime(`${maxAge}s`)
    .sign(secret());
}

export async function setSessionCookie(token: string, remember: boolean) {
  const store = await cookies();
  store.set(COOKIE_NAME, token, {
    httpOnly: true,
    sameSite: 'lax',
    secure: process.env.NODE_ENV === 'production',
    path: '/',
    maxAge: remember ? 60 * 60 * 24 : 60 * 30,
  });
}

export async function clearSessionCookie() {
  const store = await cookies();
  store.delete(COOKIE_NAME);
}

export async function readSession(): Promise<SessionUser | null> {
  const store = await cookies();
  const token = store.get(COOKIE_NAME)?.value;
  if (!token) return null;
  try {
    const { payload } = await jwtVerify(token, secret());
    if (typeof payload.uid !== 'number') return null;
    return { id: payload.uid, username: String(payload.un), level: Number(payload.lvl) };
  } catch {
    return null;
  }
}

export type DbUser = {
  id_users: number;
  fullname: string | null;
  username: string;
  email: string;
  level: number;
  saldo: number;
  status: number;
  uplink: string | null;
  expiration_date: Date | null;
  created_at: Date | null;
  user_ip: string | null;
};

/** Session + fresh DB row. Redirects to /login when not signed in. */
export async function currentUser(): Promise<DbUser> {
  const session = await readSession();
  if (!session) redirect('/login');

  const user = await q1<DbUser>(
    `SELECT id_users, fullname, username, email, level, saldo, status, uplink,
            expiration_date, created_at, user_ip
       FROM users WHERE id_users = $1`,
    [session.id]
  );
  if (!user) redirect('/logout');
  if (!user.status) redirect('/login?m=Your+account+is+disabled.&t=danger');
  return user;
}

/** Same as currentUser but hard-fails instead of redirecting (API/server-action use). */
export async function currentUserOrNull(): Promise<DbUser | null> {
  const session = await readSession();
  if (!session) return null;
  return q1<DbUser>(
    `SELECT id_users, fullname, username, email, level, saldo, status, uplink,
            expiration_date, created_at, user_ip
       FROM users WHERE id_users = $1`,
    [session.id]
  );
}

export function levelName(level: number): string {
  switch (Number(level)) {
    case 1:
      return 'Owner';
    case 2:
      return 'Admin';
    case 3:
      return 'Reseller';
    default:
      return 'Unknown';
  }
}

export const isStaff = (u: { level: number }) => u.level === 1 || u.level === 2;
export const isOwner = (u: { level: number }) => u.level === 1;
