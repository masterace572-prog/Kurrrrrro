'use client';

import Link from 'next/link';
import { useActionState, useState } from 'react';
import { loginAction, registerAction } from '@/app/actions';
import type { FormState } from '@/lib/result';
import { Alert, Field, FormMessage, Icon, SubmitButton } from '@/components/ui';

export function LoginForm() {
  const [state, action] = useActionState<FormState, FormData>(loginAction, null);
  const [show, setShow] = useState(false);

  return (
    <form action={action} className="space-y-4" noValidate>
      <FormMessage state={state} />
      <Field label="Username" name="username" error={state?.field === 'username' ? state.message : undefined}>
        <input
          id="username"
          name="username"
          className="input"
          autoComplete="username"
          autoCapitalize="none"
          spellCheck={false}
          required
          minLength={4}
          maxLength={25}
          placeholder="your username"
        />
      </Field>

      <Field label="Password" name="password" error={state?.field === 'password' ? state.message : undefined}>
        <div className="relative">
          <input
            id="password"
            name="password"
            type={show ? 'text' : 'password'}
            className="input pr-11"
            autoComplete="current-password"
            required
            minLength={6}
            placeholder="••••••••"
          />
          <button
            type="button"
            onClick={() => setShow((v) => !v)}
            className="absolute right-2 top-1/2 -translate-y-1/2 rounded p-1.5 text-slate-400 transition hover:text-slate-200"
            aria-label={show ? 'Hide password' : 'Show password'}
          >
            <Icon name={show ? 'close' : 'shield'} />
          </button>
        </div>
      </Field>

      <label className="flex cursor-pointer items-center gap-2 text-sm text-slate-400">
        <input type="checkbox" name="stay_log" value="1" className="h-4 w-4 rounded border-ink-600 bg-ink-850 accent-brand-500" />
        Keep me signed in for 24 hours
      </label>

      <SubmitButton className="btn-primary w-full" pendingLabel="Signing in…">
        Sign in
      </SubmitButton>

      <p className="pt-1 text-center text-sm text-slate-400">
        No account?{' '}
        <Link href="/register" className="font-semibold text-brand-400 hover:underline">
          Register with a code
        </Link>
      </p>
    </form>
  );
}

export function RegisterForm() {
  const [state, action] = useActionState<FormState, FormData>(registerAction, null);
  const err = (f: string) => (state?.field === f ? state.message : undefined);

  return (
    <form action={action} className="space-y-4" noValidate>
      <FormMessage state={state} />

      <Field label="Referral code" name="referral" hint="Ask your admin for a 6-character code." error={err('referral')}>
        <input
          id="referral"
          name="referral"
          className="input font-mono tracking-widest uppercase"
          required
          minLength={4}
          maxLength={16}
          autoCapitalize="characters"
          spellCheck={false}
          placeholder="ABC123"
        />
      </Field>

      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Username" name="username" error={err('username')}>
          <input id="username" name="username" className="input" required minLength={4} maxLength={25} autoComplete="username" />
        </Field>
        <Field label="Full name" name="fullname" error={err('fullname')}>
          <input id="fullname" name="fullname" className="input" required minLength={4} maxLength={25} />
        </Field>
      </div>

      <Field label="Email (optional)" name="email" error={err('email')}>
        <input id="email" name="email" type="email" className="input" autoComplete="email" placeholder="you@example.com" />
      </Field>

      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Password" name="password" error={err('password')}>
          <input id="password" name="password" type="password" className="input" required minLength={6} autoComplete="new-password" />
        </Field>
        <Field label="Repeat password" name="password2" error={err('password2')}>
          <input id="password2" name="password2" type="password" className="input" required minLength={6} autoComplete="new-password" />
        </Field>
      </div>

      <SubmitButton className="btn-primary w-full" pendingLabel="Creating account…">
        Create account
      </SubmitButton>

      <p className="pt-1 text-center text-sm text-slate-400">
        Already have one?{' '}
        <Link href="/login" className="font-semibold text-brand-400 hover:underline">
          Sign in
        </Link>
      </p>
    </form>
  );
}

export function AuthShell({ title, subtitle, children }: { title: string; subtitle: string; children: React.ReactNode }) {
  return (
    <main className="relative flex min-h-screen items-center justify-center overflow-hidden p-5">
      <div className="relative w-full max-w-md">
        <div className="mb-7 text-center">
          <div className="mx-auto mb-4 grid h-14 w-14 place-items-center rounded-2xl bg-brand-500 text-2xl font-black text-white">
            K
          </div>
          <h1 className="text-2xl font-black tracking-[0.25em] text-slate-100">{title}</h1>
          <p className="mt-1.5 text-sm text-slate-400">{subtitle}</p>
        </div>
        <div className="card">{children}</div>
        <p className="mt-5 text-center text-[11px] uppercase tracking-[0.2em] text-slate-600">
          Kuro Panel · secure key management
        </p>
      </div>
    </main>
  );
}

export { Alert };
