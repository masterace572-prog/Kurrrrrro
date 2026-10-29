import Link from 'next/link';
import { redirect } from 'next/navigation';
import { loginAction } from '@/app/actions';
import Flash from '@/components/Flash';
import { readSession } from '@/lib/session';

export const metadata = { title: 'Sign in · KURO PANEL' };

export default async function LoginPage({
  searchParams,
}: {
  searchParams: Promise<{ m?: string; t?: string }>;
}) {
  const session = await readSession();
  if (session) redirect('/dashboard');
  const { m, t } = await searchParams;

  return (
    <main className="flex min-h-screen items-center justify-center p-5">
      <div className="w-full max-w-md">
        <div className="mb-8 text-center">
          <div className="mx-auto mb-4 grid h-14 w-14 place-items-center rounded-2xl bg-brand-500 text-2xl font-black text-white shadow-glow">
            K
          </div>
          <h1 className="text-2xl font-black tracking-[0.2em]">KURO PANEL</h1>
          <p className="mt-1 text-sm text-slate-400">Key &amp; license management</p>
        </div>

        <div className="card">
          <Flash m={m} t={t} />
          <form action={loginAction} className="space-y-4">
            <div>
              <label className="label" htmlFor="username">
                Username
              </label>
              <input id="username" name="username" className="input" autoComplete="username" required minLength={4} maxLength={25} />
            </div>
            <div>
              <label className="label" htmlFor="password">
                Password
              </label>
              <input
                id="password"
                name="password"
                type="password"
                className="input"
                autoComplete="current-password"
                required
                minLength={6}
              />
            </div>
            <label className="flex items-center gap-2 text-sm text-slate-400">
              <input type="checkbox" name="stay_log" value="1" className="h-4 w-4 rounded border-ink-600 bg-ink-850" />
              Stay signed in for 24 hours
            </label>
            <button className="btn-primary w-full" type="submit">
              Sign in
            </button>
          </form>
          <p className="mt-4 text-center text-sm text-slate-400">
            Need access?{' '}
            <Link href="/register" className="text-brand-400 hover:underline">
              Register with a referral code
            </Link>
          </p>
        </div>
      </div>
    </main>
  );
}
