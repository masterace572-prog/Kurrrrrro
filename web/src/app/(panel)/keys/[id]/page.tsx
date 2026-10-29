import Link from 'next/link';
import { notFound } from 'next/navigation';
import PageHeader from '@/components/PageHeader';
import { q1 } from '@/lib/db';
import { currentUser } from '@/lib/session';
import { devicesOf, fmtLocal, fmtUtc } from '@/lib/domain';
import { EditKeyForm, RevokeDeviceButton } from '@/components/forms/keys';
import { Alert, Icon } from '@/components/ui';

export const dynamic = 'force-dynamic';

export default async function EditKeyPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const numericId = Number(id);
  if (!Number.isInteger(numericId)) notFound();

  const me = await currentUser();
  const key = await q1<{
    id_keys: number;
    game: string;
    user_key: string;
    duration: number;
    expired_date: Date | null;
    max_devices: number;
    devices: string | null;
    status: number;
    registrator: string | null;
    created_at: Date | null;
  }>(`SELECT * FROM keys_code WHERE id_keys = $1`, [numericId]);

  if (!key) notFound();

  if (me.level !== 1 && key.registrator !== me.username) {
    return (
      <div className="mx-auto max-w-xl">
        <Alert tone="danger" title="Not your key">
          This key belongs to {key.registrator ?? 'another reseller'}. Ask the owner if you need access.
        </Alert>
        <Link href="/keys" className="btn-ghost mt-4">
          Back to keys
        </Link>
      </div>
    );
  }

  const devices = devicesOf(key.devices);
  const owner = Number(me.level) === 1;
  const expired = key.expired_date ? new Date(key.expired_date) < new Date() : false;

  return (
    <>
      <PageHeader
        title="Edit key"
        subtitle={key.user_key}
        breadcrumb={[{ label: 'Keys', href: '/keys' }, { label: 'Edit' }]}
        action={
          <div className="flex items-center gap-2">
            {!key.status ? (
              <span className="badge bg-ink-800 text-slate-300">blocked</span>
            ) : expired ? (
              <span className="badge bg-red-500/10 text-red-300">expired</span>
            ) : key.expired_date ? (
              <span className="badge bg-emerald-500/10 text-emerald-300">active</span>
            ) : (
              <span className="badge bg-sky-500/10 text-sky-300">unused</span>
            )}
          </div>
        }
      />

      <div className="grid gap-4 xl:grid-cols-3">
        <div className="card xl:col-span-2">
          <EditKeyForm
            initial={{
              id_keys: key.id_keys,
              game: key.game,
              user_key: key.user_key,
              duration: Number(key.duration),
              expired_date: fmtUtc(key.expired_date) ?? '',
              max_devices: Number(key.max_devices),
              devices,
              status: Number(key.status),
              registrator: key.registrator,
            }}
            owner={owner}
          />
        </div>

        <div className="space-y-4">
          <div className="card">
            <p className="text-xs font-semibold uppercase tracking-wider text-slate-500">Summary</p>
            <dl className="mt-2 space-y-2 text-sm">
              {[
                ['Game', key.game],
                ['Duration', `${key.duration} hours`],
                ['Created', fmtLocal(key.created_at)],
                ['Expires', key.expired_date ? fmtLocal(key.expired_date) : 'starts on first login'],
                ['Reseller', key.registrator ?? '—'],
                ['HWID slots', `${devices.length} / ${key.max_devices} used`],
              ].map(([k, v]) => (
                <div key={k} className="flex justify-between gap-3">
                  <dt className="text-slate-500">{k}</dt>
                  <dd className="text-right text-slate-200">{v}</dd>
                </div>
              ))}
            </dl>
          </div>

          <div className="card">
            <p className="text-xs font-semibold uppercase tracking-wider text-slate-500">Bound devices</p>
            {devices.length === 0 ? (
              <p className="mt-2 text-sm text-slate-500">Nothing bound yet — the first loader login claims a slot.</p>
            ) : (
              <ul className="mt-2 space-y-1.5">
                {devices.map((d) => (
                  <li key={d} className="flex items-center gap-2 rounded-lg bg-ink-850 px-2.5 py-1.5">
                    <Icon name="shield" className="h-3.5 w-3.5 shrink-0 text-slate-500" />
                    <span className="min-w-0 flex-1 truncate font-mono text-[11px] text-slate-300" title={d}>
                      {d}
                    </span>
                    <RevokeDeviceButton userKey={key.user_key} device={d} />
                  </li>
                ))}
              </ul>
            )}
          </div>

          <div className="card">
            <p className="text-xs font-semibold uppercase tracking-wider text-slate-500">Test this key</p>
            <pre className="mt-2 overflow-x-auto rounded-lg bg-ink-850 p-3 text-[11px] leading-relaxed text-emerald-300">{`curl -X POST \\
  ${process.env.PUBLIC_BASE_URL || 'https://your-app.vercel.app'}/server \\
  -d "game=PUBG&user_key=${key.user_key}&serial=TESTDEVICE"`}</pre>
          </div>
        </div>
      </div>
    </>
  );
}
