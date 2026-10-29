import Link from 'next/link';
import { redirect } from 'next/navigation';
import PageHeader from '@/components/PageHeader';
import { q1 } from '@/lib/db';
import { currentUser, isStaff } from '@/lib/session';
import { CopyField, CreditForm, MaintenanceForm, ModNameForm } from '@/components/forms/server';
import { Icon } from '@/components/ui';

export const metadata = { title: 'Server control · KURO PANEL' };
export const dynamic = 'force-dynamic';

export default async function ServerPage() {
  const me = await currentUser();
  if (!isStaff(me)) redirect('/dashboard?m=Access+denied.&t=danger');

  const [modname, onoff, ftext] = await Promise.all([
    q1<{ modname: string }>(`SELECT modname FROM modname WHERE id = 1`),
    q1<{ status: string; myinput: string }>(`SELECT status, myinput FROM onoff WHERE id = 1`),
    q1<{ _status: string; _ftext: string }>(`SELECT _status, _ftext FROM _ftext WHERE id = 1`),
  ]);

  const maintenance = onoff?.status === 'on';
  const base = process.env.PUBLIC_BASE_URL || 'https://<your-vercel-app>';

  return (
    <>
      <PageHeader
        title="Server control"
        subtitle="Picked up on the client's next check"
        breadcrumb={[{ label: 'Dashboard', href: '/dashboard' }, { label: 'Server control' }]}
        action={
          <div className="flex gap-2">
            <Link href="/docs/api" className="btn-ghost">
              <Icon name="book" /> API docs
            </Link>
            <Link href="/settings/server/releases" className="btn-ghost">
              <Icon name="upload" /> Lib
            </Link>
          </div>
        }
      />

      {maintenance ? (
        <div className="mb-4 flex items-center gap-3 rounded-xl border border-red-500/30 bg-red-500/10 px-4 py-3 text-sm text-red-300">
          <Icon name="warning" className="h-4 w-4 shrink-0" />
          <p>
            <span className="font-semibold">Maintenance is ON.</span> Every login is rejected.
          </p>
          <a href="#maintenance" className="ml-auto shrink-0 underline">
            Fix
          </a>
        </div>
      ) : null}

      <div className="grid gap-4 xl:grid-cols-2">
        <ModNameForm modname={modname?.modname ?? 'VIP MOD'} />

        <div id="maintenance">
          <MaintenanceForm status={onoff?.status ?? 'off'} message={onoff?.myinput ?? ''} />
        </div>

        <CreditForm status={ftext?._status ?? 'Safe'} credit={ftext?._ftext ?? ''} />

        <div className="card space-y-4 xl:col-span-2">
          <div className="flex items-center gap-2">
            <Icon name="server" className="h-4 w-4 text-brand-400" />
            <h2 className="font-semibold text-slate-200">Endpoint</h2>
          </div>
          <CopyField label="License check" value={`${base}/server`} />
          <div className="grid gap-3 sm:grid-cols-2">
            <CopyField label="Lib version" value={`${base}/api/version`} />
            <CopyField label="Lib download" value={`${base}/api/download`} />
          </div>
          <pre className="overflow-x-auto rounded-lg bg-ink-850 p-3 text-xs leading-relaxed text-slate-300">{`curl -X POST ${base}/server \\
  -d "game=PUBG&user_key=<key>&serial=<device_uid>"`}</pre>
          <p className="text-xs text-slate-500">
            Full request/response reference:{' '}
            <Link href="/docs/api" className="text-brand-400 underline">
              /docs/api
            </Link>
            .
          </p>
        </div>
      </div>
    </>
  );
}
