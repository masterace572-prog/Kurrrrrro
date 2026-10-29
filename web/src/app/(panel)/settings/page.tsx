import Link from 'next/link';
import PageHeader from '@/components/PageHeader';
import { currentUser, levelName, isStaff } from '@/lib/session';
import { ProfileForm } from '@/components/forms/admin';
import { fmtLocal } from '@/lib/domain';
import { Icon } from '@/components/ui';

export const metadata = { title: 'Account · KURO PANEL' };
export const dynamic = 'force-dynamic';

export default async function SettingsPage() {
  const me = await currentUser();
  const staff = isStaff(me);

  return (
    <>
      <PageHeader
        title="Account"
        subtitle={`${levelName(Number(me.level))} · ${me.username}`}
        breadcrumb={[{ label: 'Dashboard', href: '/dashboard' }, { label: 'Account' }]}
      />

      <div className="grid grid-cols-1 gap-4 xl:grid-cols-3">
        <div className="xl:col-span-2">
          <ProfileForm fullname={me.fullname ?? ''} email={me.email ?? ''} />
        </div>

        <div className="space-y-4">
          <div className="card space-y-2">
            <p className="text-xs font-semibold uppercase tracking-wider text-slate-500">Account summary</p>
            <dl className="space-y-2 text-sm">
              {[
                ['Username', me.username],
                ['Role', levelName(Number(me.level))],
                ['Balance', Number(me.saldo).toLocaleString()],
                ['Access until', fmtLocal(me.expiration_date)],
                ['Joined', fmtLocal(me.created_at)],
                ['Last IP', me.user_ip || '—'],
              ].map(([k, v]) => (
                <div key={k} className="flex justify-between gap-3">
                  <dt className="text-slate-500">{k}</dt>
                  <dd className="truncate text-right text-slate-200">{v}</dd>
                </div>
              ))}
            </dl>
          </div>

          <div className="card space-y-2">
            <p className="text-xs font-semibold uppercase tracking-wider text-slate-500">Shortcuts</p>
            <Link href="/keys" className="flex items-center gap-2 rounded-lg px-2 py-1.5 text-sm text-slate-300 hover:bg-ink-800">
              <Icon name="key" className="h-4 w-4 text-slate-500" /> My keys
            </Link>
            <Link
              href="/keys/generate"
              className="flex items-center gap-2 rounded-lg px-2 py-1.5 text-sm text-slate-300 hover:bg-ink-800"
            >
              <Icon name="plus" className="h-4 w-4 text-slate-500" /> Generate keys
            </Link>
            {staff ? (
              <Link
                href="/settings/server"
                className="flex items-center gap-2 rounded-lg px-2 py-1.5 text-sm text-slate-300 hover:bg-ink-800"
              >
                <Icon name="server" className="h-4 w-4 text-slate-500" /> Server control
              </Link>
            ) : null}
            <a
              href="/logout"
              className="flex items-center gap-2 rounded-lg px-2 py-1.5 text-sm text-red-300 hover:bg-red-500/10"
            >
              <Icon name="logout" className="h-4 w-4" /> Sign out
            </a>
          </div>
        </div>
      </div>
    </>
  );
}
