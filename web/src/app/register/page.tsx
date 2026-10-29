import Link from 'next/link';
import { registerAction } from '@/app/actions';
import Flash from '@/components/Flash';

export const metadata = { title: 'Register · KURO PANEL' };

export default async function RegisterPage({
  searchParams,
}: {
  searchParams: Promise<{ m?: string; t?: string }>;
}) {
  const { m, t } = await searchParams;

  return (
    <main className="flex min-h-screen items-center justify-center p-5">
      <div className="w-full max-w-md">
        <div className="mb-6 text-center">
          <h1 className="text-2xl font-black tracking-[0.2em]">CREATE ACCOUNT</h1>
          <p className="mt-1 text-sm text-slate-400">A referral code from an admin is required</p>
        </div>

        <div className="card">
          <Flash m={m} t={t} />
          <form action={registerAction} className="space-y-4">
            <div>
              <label className="label" htmlFor="referral">
                Referral code
              </label>
              <input id="referral" name="referral" className="input" required minLength={6} maxLength={16} />
            </div>
            <div className="grid gap-4 sm:grid-cols-2">
              <div>
                <label className="label" htmlFor="username">
                  Username
                </label>
                <input id="username" name="username" className="input" required minLength={4} maxLength={25} />
              </div>
              <div>
                <label className="label" htmlFor="fullname">
                  Full name
                </label>
                <input id="fullname" name="fullname" className="input" required minLength={4} maxLength={25} />
              </div>
            </div>
            <div>
              <label className="label" htmlFor="email">
                Email
              </label>
              <input id="email" name="email" type="email" className="input" />
            </div>
            <div className="grid gap-4 sm:grid-cols-2">
              <div>
                <label className="label" htmlFor="password">
                  Password
                </label>
                <input id="password" name="password" type="password" className="input" required minLength={6} />
              </div>
              <div>
                <label className="label" htmlFor="password2">
                  Repeat password
                </label>
                <input id="password2" name="password2" type="password" className="input" required minLength={6} />
              </div>
            </div>
            <button className="btn-primary w-full" type="submit">
              Create account
            </button>
          </form>
          <p className="mt-4 text-center text-sm text-slate-400">
            Already registered?{' '}
            <Link href="/login" className="text-brand-400 hover:underline">
              Sign in
            </Link>
          </p>
        </div>
      </div>
    </main>
  );
}
