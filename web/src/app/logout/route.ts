import { NextResponse } from 'next/server';
import { COOKIE_NAME } from '@/lib/session';

export async function GET(req: Request) {
  const res = NextResponse.redirect(new URL('/login?m=Signed+out.&t=success', req.url));
  res.cookies.set(COOKIE_NAME, '', { path: '/', maxAge: 0 });
  return res;
}
