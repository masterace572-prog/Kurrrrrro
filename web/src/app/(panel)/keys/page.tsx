import Link from 'next/link';
import PageHeader from '@/components/PageHeader';
import { q } from '@/lib/db';
import { currentUser, isStaff } from '@/lib/session';
import { fmtLocal, devicesOf, durationLabel } from '@/lib/domain';
import {
  BulkDeleteButton,
  DeleteKeyButton,
  ResetHwidButton,
  ToggleStatusButton,
} from '@/components/forms/keys';
import { Icon } from '@/components/ui';

export const metadata = { title: 'Keys · KURO PANEL' };
export const dynamic = 'force-dynamic';

type Row = {
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
};

export default async function KeysPage({
  searchParams,
}: {
  searchParams: Promise<{ q?: string }>;
}) {
  const { q: search } = await searchParams;
  const me = await currentUser();
  const staff = isStaff(me);
  const term = (search ?? '').trim().slice(0, 64);

  const rows = await q<Row>(
    `SELECT id_keys, game, user_key, duration, expired_date, max_devices, devices, status, registrator, created_at
       FROM keys_code
      WHERE ($1::boolean OR registrator = $2)
        AND ($3 = '' OR user_key ILIKE '%' || $3 || '%' OR coalesce(registrator,'') ILIKE '%' || $3 || '%'
             OR coalesce(devices,'') ILIKE '%' || $3 || '%')
      ORDER BY id_keys DESC
      LIMIT 300`,
    [staff, me.username, term]
  );

  const counts = await q<{ total: string; active: string; expired: string }>(
    `SELECT count(*)::text AS total,
            count(*) FILTER (WHERE status = 1 AND (expired_date IS NULL OR expired_date > now()))::text AS active,
            count(*) FILTER (WHERE expired_date IS NOT NULL AND expired_date < now())::text AS expired
       FROM keys_code WHERE ($1::boolean OR registrator = $2)`,
    [staff, me.username]
  );
  const stats = counts[0];

  return (
    <>
      <PageHeader
        title="Keys"
        subtitle={staff ? 'Every reseller in one place' : 'Your keys only'}
        breadcrumb={[{ label: 'Dashboard', href: '/dashboard' }, { label: 'Keys' }]}
        action={
          <Link href="/keys/generate" className="btn-primary">
            <Icon name="plus" /> Generate
          </Link>
        }
      />

      <div className="mb-4 grid gap-3 sm:grid-cols-3">
        {[
          { label: 'Total', value: stats?.total ?? '0', tone: 'text-slate-100' },
          { label: 'Active', value: stats?.active ?? '0', tone: 'text-emerald-300' },
          { label: 'Expired', value: stats?.expired ?? '0', tone: 'text-red-300' },
        ].map((s) => (
          <div key={s.label} className="card flex items-center justify-between py-3">
            <span className="text-xs uppercase tracking-wider text-slate-500">{s.label}</span>
            <span className={`text-xl font-black ${s.tone}`}>{s.value}</span>
          </div>
        ))}
      </div>

      <div className="card mb-4">
        <div className="flex flex-col gap-3 lg:flex-row lg:items-center">
          <form className="flex flex-1 gap-2" action="/keys" method="get">
            <div className="relative flex-1">
              <Icon name="search" className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-500" />
              <input
                name="q"
                defaultValue={term}
                placeholder="Search by key, reseller or device ID…"
                className="input pl-9"
                maxLength={64}
              />
            </div>
            <button className="btn-ghost" type="submit">
              Search
            </button>
            {term ? (
              <Link href="/keys" className="btn-ghost" title="Clear search">
                <Icon name="close" />
              </Link>
            ) : null}
          </form>
          <div className="flex flex-wrap gap-2">
            <BulkDeleteButton scope="expired" label="Delete expired" />
            <BulkDeleteButton scope="unused" label="Delete unused" />
          </div>
        </div>
      </div>

      {/* phones get rows, not a horizontally scrolled table */}
      <div className="space-y-2.5 sm:hidden">
        {rows.length === 0 ? (
          <div className="card text-center">
            <p className="text-sm font-semibold text-slate-300">{term ? 'No keys match' : 'No keys yet'}</p>
            <p className="mt-1 text-xs text-slate-500">
              {term ? (
                <Link href="/keys" className="text-brand-400">Clear search</Link>
              ) : (
                <Link href="/keys/generate" className="text-brand-400">Generate one</Link>
              )}
            </p>
          </div>
        ) : (
          rows.map((k) => {
            const expired = k.expired_date ? new Date(k.expired_date) < new Date() : false;
            const dev = devicesOf(k.devices);
            const state = !k.status ? 'blocked' : expired ? 'expired' : k.expired_date ? 'active' : 'unused';
            const tone =
              state === 'blocked' ? 'bg-ink-800 text-slate-300'
                : state === 'expired' ? 'bg-red-500/10 text-red-300'
                  : state === 'active' ? 'bg-emerald-500/10 text-emerald-300'
                    : 'bg-sky-500/10 text-sky-300';
            return (
              <div key={k.id_keys} className="rounded-xl border border-line bg-ink-900 p-3.5">
                <div className="flex items-start justify-between gap-2">
                  <Link href={`/keys/${k.id_keys}`} className="min-w-0 truncate font-mono text-xs text-slate-200">
                    {k.user_key}
                  </Link>
                  <span className={`badge shrink-0 ${tone}`}>{state}</span>
                </div>
                <div className="mt-2 flex flex-wrap items-center gap-x-3 gap-y-1 text-[11px] text-slate-500">
                  <span>{k.game}</span>
                  <span>{durationLabel(k.duration)}</span>
                  <span>{dev.length}/{k.max_devices} devices</span>
                  <span>{k.expired_date ? fmtLocal(k.expired_date) : 'not started'}</span>
                </div>
                <div className="mt-2.5 flex items-center gap-1.5">
                  <Link href={`/keys/${k.id_keys}`} className="btn-ghost flex-1 px-2 py-1.5 text-xs">Open</Link>
                  <ToggleStatusButton userKey={k.user_key} status={Number(k.status)} />
                  <DeleteKeyButton userKey={k.user_key} />
                </div>
              </div>
            );
          })
        )}
      </div>

      <div className="card hidden min-w-0 overflow-x-auto p-0 sm:block">
        <table className="table">
          <thead>
            <tr>
              <th className="pl-5">Key</th>
              <th>Game</th>
              <th>Duration</th>
              <th>Expires</th>
              <th>Devices</th>
              {staff ? <th>Reseller</th> : null}
              <th>Status</th>
              <th className="pr-5 text-right">Actions</th>
            </tr>
          </thead>
          <tbody>
            {rows.length === 0 ? (
              <tr>
                <td colSpan={staff ? 8 : 7} className="py-14 text-center">
                  <p className="font-semibold text-slate-300">{term ? 'No keys match that search' : 'No keys yet'}</p>
                  <p className="mt-1 text-sm text-slate-500">
                    {term ? (
                      <Link href="/keys" className="text-brand-400 hover:underline">
                        Clear the search
                      </Link>
                    ) : (
                      <Link href="/keys/generate" className="text-brand-400 hover:underline">
                        Generate your first key
                      </Link>
                    )}
                  </p>
                </td>
              </tr>
            ) : (
              rows.map((k) => {
                const expired = k.expired_date ? new Date(k.expired_date) < new Date() : false;
                const dev = devicesOf(k.devices);
                const fresh = k.created_at ? Date.now() - new Date(k.created_at).getTime() < 10 * 60_000 : false;
                return (
                  <tr key={k.id_keys} className="transition hover:bg-white/[0.03]">
                    <td className="pl-5">
                      <Link href={`/keys/${k.id_keys}`} className="font-mono text-xs text-slate-200 hover:text-brand-400">
                        {k.user_key}
                      </Link>
                      {fresh ? (
                        <span className="ml-2 rounded bg-ink-800 px-1.5 py-0.5 text-[10px] font-bold uppercase tracking-wide text-brand-400">
                          new
                        </span>
                      ) : null}
                    </td>
                    <td className="text-slate-400">{k.game}</td>
                    <td className="whitespace-nowrap text-slate-400">{durationLabel(k.duration)}</td>
                    <td className="whitespace-nowrap text-xs text-slate-400">
                      {k.expired_date ? fmtLocal(k.expired_date) : <span className="text-slate-600">not started</span>}
                    </td>
                    <td>
                      <span
                        className={`text-sm ${dev.length >= k.max_devices ? 'text-amber-300' : 'text-slate-300'}`}
                        title={dev.join(', ') || 'no devices bound'}
                      >
                        {dev.length}/{k.max_devices}
                      </span>
                    </td>
                    {staff ? <td className="text-xs text-slate-400">{k.registrator ?? '—'}</td> : null}
                    <td>
                      {!k.status ? (
                        <span className="badge bg-ink-800 text-slate-300">blocked</span>
                      ) : expired ? (
                        <span className="badge bg-red-500/10 text-red-300">expired</span>
                      ) : k.expired_date ? (
                        <span className="badge bg-emerald-500/10 text-emerald-300">active</span>
                      ) : (
                        <span className="badge bg-sky-500/10 text-sky-300">unused</span>
                      )}
                    </td>
                    <td className="pr-5">
                      <div className="flex items-center justify-end gap-1.5">
                        <Link href={`/keys/${k.id_keys}`} className="btn-ghost px-2 py-1 text-xs">
                          Edit
                        </Link>
                        <ResetHwidButton userKey={k.user_key} disabled={dev.length === 0} />
                        <ToggleStatusButton userKey={k.user_key} status={Number(k.status)} />
                        <DeleteKeyButton userKey={k.user_key} />
                      </div>
                    </td>
                  </tr>
                );
              })
            )}
          </tbody>
        </table>
      </div>

      {rows.length === 300 ? (
        <p className="mt-3 text-center text-xs text-slate-500">
          Showing the newest 300 keys — use the search box to narrow down.
        </p>
      ) : null}
    </>
  );
}
