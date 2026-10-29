import Flash from '@/components/Flash';
import PageHeader from '@/components/PageHeader';
import { currentUser, levelName } from '@/lib/session';
import { updateProfileAction } from '@/app/actions';
import { fmtLocal } from '@/lib/domain';

export const metadata = { title: 'Settings · KURO PANEL' };
export const dynamic = 'force-dynamic';

export default async function SettingsPage({
  searchParams,
}: {
  searchParams: Promise<{ m?: string; t?: string }>;
}) {
  const { m, t } = await searchParams;
  const me = await currentUser();

  return (
    <>
      <PageHeader title="Account settings" subtitle={`${levelName(Number(me.level))} · ${me.username}`} />
      <Flash m={m} t={t} />

      <div className="grid gap-4 xl:grid-cols-3">
        <form action={updateProfileAction} className="card space-y-4 xl:col-span-2">
          <h2 className="font-semibold text-slate-200">Profile</h2>
          <div className="grid gap-4 sm:grid-cols-2">
            <div>
              <label className="label" htmlFor="fullname">
                Full name
              </label>
              <input id="fullname" name="fullname" defaultValue={me.fullname ?? ''} className="input" />
            </div>
            <div>
              <label className="label" htmlFor="email">
                Email
              </label>
              <input id="email" name="email" type="email" defaultValue={me.email} className="input" />
            </div>
          </div>

          <h2 className="pt-2 font-semibold text-slate-200">Change password</h2>
          <div className="grid gap-4 sm:grid-cols-3">
            <div>
              <label className="label" htmlFor="current_password">
                Current
              </label>
              <input id="current_password" name="current_password" type="password" className="input" />
            </div>
            <div>
              <label className="label" htmlFor="new_password">
                New
              </label>
              <input id="new_password" name="new_password" type="password" className="input" minLength={6} />
            </div>
            <div>
              <label className="label" htmlFor="new_password2">
                Repeat new
              </label>
              <input id="new_password2" name="new_password2" type="password" className="input" minLength={6} />
            </div>
          </div>

          <button className="btn-primary" type="submit">
            Save
          </button>
        </form>

        <div className="card space-y-2">
          <h2 className="font-semibold text-slate-200">Account</h2>
          <dl className="space-y-2 text-sm">
            <div className="flex justify-between">
              <dt className="text-slate-500">Username</dt>
              <dd className="font-mono text-slate-200">{me.username}</dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-slate-500">Balance</dt>
              <dd className="text-slate-200">{Number(me.saldo).toLocaleString()}</dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-slate-500">Access until</dt>
              <dd className="text-slate-200">{fmtLocal(me.expiration_date)}</dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-slate-500">Joined</dt>
              <dd className="text-slate-200">{fmtLocal(me.created_at)}</dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-slate-500">Last IP</dt>
              <dd className="font-mono text-slate-200">{me.user_ip || '—'}</dd>
            </div>
          </dl>
        </div>
      </div>
    </>
  );
}
