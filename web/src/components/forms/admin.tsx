'use client';

import Link from 'next/link';
import { useActionState } from 'react';
import {
  createReferralAction,
  updateProfileAction,
  updateUserAction,
} from '@/app/actions';
import type { FormState } from '@/lib/result';
import { Alert, Field, FormMessage, Icon, SubmitButton } from '@/components/ui';

/* --------------------------------------------------------------- edit user */

export function UserForm({
  user,
  owner,
}: {
  user: {
    id_users: number;
    username: string;
    fullname: string;
    email: string;
    level: number;
    saldo: number;
    status: number;
    expiration_date: string;
  };
  owner: boolean;
}) {
  const [state, action] = useActionState<FormState, FormData>(updateUserAction, null);
  const err = (f: string) => (state?.field === f ? state.message : undefined);

  return (
    <form action={action} className="card space-y-5" noValidate>
      <input type="hidden" name="id_users" value={user.id_users} />
      <FormMessage state={state} />

      {!owner ? (
        <Alert tone="info">
          As an admin you can change the balance, status and password of accounts in your own downline. Everything else is
          owner-only.
        </Alert>
      ) : null}

      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Username" name="username">
          <input value={user.username} className="input opacity-60" readOnly />
        </Field>
        <Field label="Full name" name="fullname" error={err('fullname')}>
          <input id="fullname" name="fullname" defaultValue={user.fullname} className="input" readOnly={!owner} />
        </Field>
      </div>

      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Email" name="email" error={err('email')}>
          <input id="email" name="email" type="email" defaultValue={user.email} className="input" readOnly={!owner} />
        </Field>
        <Field label="Role" name="level" error={err('level')}>
          <select id="level" name="level" defaultValue={String(user.level)} className="input" disabled={!owner}>
            <option value="1">Owner — full control</option>
            <option value="2">Admin — manages downline</option>
            <option value="3">Reseller — sells keys</option>
          </select>
        </Field>
      </div>

      <div className="grid gap-4 sm:grid-cols-3">
        <Field label="Balance" name="saldo" error={err('saldo')}>
          <input id="saldo" name="saldo" type="number" min={0} defaultValue={user.saldo} className="input" />
        </Field>
        <Field label="Status" name="status" error={err('status')}>
          <select id="status" name="status" defaultValue={String(user.status)} className="input">
            <option value="1">Active</option>
            <option value="0">Disabled</option>
          </select>
        </Field>
        <Field
          label="Access until (UTC)"
          name="expiration_date"
          hint="2030-01-01 00:00:00"
          error={err('expiration_date')}
        >
          <input
            id="expiration_date"
            name="expiration_date"
            defaultValue={user.expiration_date}
            className="input font-mono text-xs"
            readOnly={!owner}
          />
        </Field>
      </div>

      <Field
        label="Reset password"
        name="password"
        hint="Leave empty to keep the current one. Minimum 6 characters."
        error={err('password')}
      >
        <input id="password" name="password" type="password" className="input" minLength={6} autoComplete="new-password" />
      </Field>

      <div className="flex gap-2">
        <SubmitButton className="btn-primary" pendingLabel="Saving…">
          Save user
        </SubmitButton>
        <Link href="/admin/manage-users" className="btn-ghost">
          Cancel
        </Link>
      </div>
    </form>
  );
}

/* ------------------------------------------------------------ new referral */

export function ReferralForm({ isOwner }: { isOwner: boolean }) {
  const [state, action] = useActionState<FormState, FormData>(createReferralAction, null);
  const err = (f: string) => (state?.field === f ? state.message : undefined);
  const created = state?.ok ? state.message.match(/[A-Za-z0-9]{6}\b/g)?.pop() : undefined;

  return (
    <form action={action} className="card space-y-4" noValidate>
      <div className="flex items-center gap-2">
        <Icon name="gift" className="h-4 w-4 text-brand-400" />
        <h2 className="font-semibold text-slate-200">New referral code</h2>
      </div>
      <FormMessage state={state} />

      {created ? (
        <div className="rounded-xl border border-emerald-200 bg-emerald-50 p-3 text-center">
          <p className="text-[10px] uppercase tracking-widest text-emerald-700/80">Share this code</p>
          <p className="my-1 font-mono text-2xl font-black tracking-[0.3em] text-emerald-800">{created}</p>
          <p className="text-[11px] text-emerald-700/70">Single use · expires with the access window below</p>
        </div>
      ) : null}

      <Field label="Role" name="level" error={err('level')}>
        <select id="level" name="level" defaultValue="3" className="input">
          <option value="3">Reseller</option>
          <option value="2">Admin</option>
          {isOwner ? <option value="1">Owner</option> : null}
        </select>
      </Field>

      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Starting balance" name="set_saldo" error={err('set_saldo')}>
          <input id="set_saldo" name="set_saldo" type="number" min={0} defaultValue={0} className="input" />
        </Field>
        <Field label="Access (days)" name="accExpire" error={err('accExpire')}>
          <input id="accExpire" name="accExpire" type="number" min={1} max={3650} defaultValue={30} className="input" />
        </Field>
      </div>

      <SubmitButton className="btn-primary w-full" pendingLabel="Creating…">
        <Icon name="plus" /> Create code
      </SubmitButton>
    </form>
  );
}

/* -------------------------------------------------------------- my account */

export function ProfileForm({
  fullname,
  email,
}: {
  fullname: string;
  email: string;
}) {
  const [state, action] = useActionState<FormState, FormData>(updateProfileAction, null);
  const err = (f: string) => (state?.field === f ? state.message : undefined);

  return (
    <form action={action} className="card space-y-5" noValidate>
      <FormMessage state={state} />

      <div>
        <h2 className="font-semibold text-slate-200">Profile</h2>
        <p className="text-xs text-slate-500">This is what other staff see next to your keys.</p>
      </div>

      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Full name" name="fullname" error={err('fullname')}>
          <input id="fullname" name="fullname" defaultValue={fullname} className="input" maxLength={155} />
        </Field>
        <Field label="Email" name="email" error={err('email')}>
          <input id="email" name="email" type="email" defaultValue={email} className="input" />
        </Field>
      </div>

      <div className="border-t border-line pt-4">
        <h2 className="font-semibold text-slate-200">Change password</h2>
        <p className="text-xs text-slate-500">Leave all three fields empty to keep your current password.</p>
      </div>

      <div className="grid gap-4 sm:grid-cols-3">
        <Field label="Current password" name="current_password" error={err('current_password')}>
          <input id="current_password" name="current_password" type="password" className="input" autoComplete="current-password" />
        </Field>
        <Field label="New password" name="new_password" error={err('new_password')}>
          <input id="new_password" name="new_password" type="password" className="input" minLength={6} autoComplete="new-password" />
        </Field>
        <Field label="Repeat new" name="new_password2" error={err('new_password2')}>
          <input id="new_password2" name="new_password2" type="password" className="input" minLength={6} autoComplete="new-password" />
        </Field>
      </div>

      <SubmitButton className="btn-primary" pendingLabel="Saving…">
        Save changes
      </SubmitButton>
    </form>
  );
}
