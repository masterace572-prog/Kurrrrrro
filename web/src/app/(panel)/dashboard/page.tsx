import Link from 'next/link';
import PageHeader from '@/components/PageHeader';
import { q, q1 } from '@/lib/db';
import { currentUser, levelName, isStaff } from '@/lib/session';
import { fmtLocal, durationLabel, devicesOf } from '@/lib/domain';
import { Icon } from '@/components/ui';

export const metadata = { title: 'Dashboard · KURO PANEL' };
export const dynamic = 'force-dynamic';

export default async function DashboardPage() {
  const me = await currentUser();
  const staff = isStaff(me);

  const [stats, keys, history, lib] = await Promise.all([
    q1<{ total: string; active: string; expired: string; devices: string; resellers: string }>(
      `SELECT count(*)::text AS total,
              count(*) FILTER (WHERE status = 1 AND (expired_date IS NULL OR expired_date > now()))::text AS active,
              count(*) FILTER (WHERE expired_date IS NOT NULL AND expired_date < now())::text AS expired,
              coalesce(sum(array_length(string_to_array(nullif(devices,''), ','), 1)), 0)::text AS devices,
              (SELECT count(*)::text FROM users ${staff ? '' : 'WHERE uplink = $1'}) AS resellers
         FROM keys_code ${staff ? '' : 'WHERE registrator = $1'}`,
      staff ? [] : [me.username]
    ),
    q<{ id_keys: number; user_key: string; game: string; duration: number; expired_date: Date | null; max_devices: number; devices: string | null; status: number }>(
      `SELECT id_keys, user_key, game, duration, expired_date, max_devices, devices, status
         FROM keys_code ${staff ? '' : 'WHERE registrator = $1'} ORDER BY id_keys DESC LIMIT 6`,
      staff ? [] : [me.username]
    ),
    q<{ info: string; user_do: string; created_at: Date }>(
      `SELECT info, user_do, created_at FROM history ORDER BY id_history DESC LIMIT 8`
    ),
    q1<{ version: string; created_at: Date }>(`SELECT version, created_at FROM client_release WHERE is_current LIMIT 1`),
  ]);

  const cards = [
    { label: 'Total keys', value: stats?.total ?? '0', href: '/keys', tone: 'text-slate-100' },
    { label: 'Active', value: stats?.active ?? '0', href: '/keys', tone: 'text-emerald-300' },
    { label: 'Expired', value: stats?.expired ?? '0', href: '/keys', tone: 'text-red-300' },
    { label: staff ? 'Accounts' : 'Devices', value: staff ? (stats?.resellers ?? '0') : (stats?.devices ?? '0'), href: staff ? '/admin/manage-users' : '/keys', tone: 'text-sky-300' },
  ];

  return (
    <>
      <PageHeader
        title={`Welcome back, ${me.fullname || me.username}`}
        subtitle={`${levelName(Number(me.level))} · panel access until ${fmtLocal(me.expiration_date)}`}
        action={
          <Link href="/keys/generate" className="btn-primary">
            <Icon name="plus" /> Generate keys
          </Link>
        }
      />

      {/* two-up on a phone, so four figures stay one glance instead of four cards */}
      <div className="grid grid-cols-2 gap-3 sm:gap-4 xl:grid-cols-4">
        {cards.map((c) => (
          <Link
            key={c.label}
            href={c.href}
            className="card px-3 py-3 transition hover:border-line hover:bg-ink-850 sm:p-5"
          >
            <p className="text-[11px] font-semibold uppercase tracking-wider text-slate-500 sm:text-xs">{c.label}</p>
            <p className={`mt-1 text-xl font-black leading-none sm:mt-2 sm:text-3xl ${c.tone}`}>{c.value}</p>
          </Link>
        ))}
      </div>

      <div className="mt-4 grid gap-4 lg:grid-cols-3">
        <div className="card min-w-0 p-0 lg:col-span-2">
          <div className="flex items-center justify-between px-5 py-4">
            <h2 className="font-semibold text-slate-200">Latest keys</h2>
            <Link href="/keys" className="flex items-center gap-1 text-xs text-brand-400 hover:underline">
              View all <Icon name="chevron" className="h-3 w-3" />
            </Link>
          </div>
          <div className="space-y-2 px-4 pb-4 md:hidden">
            {keys.length === 0 ? (
              <p className="py-6 text-center text-sm text-slate-500">
                No keys yet —{' '}
                <Link href="/keys/generate" className="text-brand-400">generate one</Link>.
              </p>
            ) : (
              keys.map((k) => {
                const expired = k.expired_date ? new Date(k.expired_date) < new Date() : false;
                return (
                  <Link
                    key={k.id_keys}
                    href={`/keys/${k.id_keys}`}
                    className="block rounded-xl border border-line bg-ink-850 px-3 py-2.5"
                  >
                    <div className="flex items-center justify-between gap-2">
                      <span className="min-w-0 truncate font-mono text-[11px] text-slate-200">{k.user_key}</span>
                      {!k.status ? (
                        <span className="badge shrink-0 bg-ink-800 text-slate-300">blocked</span>
                      ) : expired ? (
                        <span className="badge shrink-0 bg-red-500/10 text-red-300">expired</span>
                      ) : k.expired_date ? (
                        <span className="badge shrink-0 bg-emerald-500/10 text-emerald-300">active</span>
                      ) : (
                        <span className="badge shrink-0 bg-sky-500/10 text-sky-300">unused</span>
                      )}
                    </div>
                    <p className="mt-1 text-[11px] text-slate-500">
                      {durationLabel(k.duration)} · {k.expired_date ? fmtLocal(k.expired_date) : 'not started'}
                    </p>
                  </Link>
                );
              })
            )}
          </div>

          <div className="hidden overflow-x-auto md:block">
            <table className="table">
              <thead>
                <tr>
                  <th className="pl-5">Key</th>
                  <th>Duration</th>
                  <th>Expires</th>
                  <th>Devices</th>
                  <th className="pr-5">Status</th>
                </tr>
              </thead>
              <tbody>
                {keys.length === 0 ? (
                  <tr>
                    <td colSpan={5} className="py-10 text-center text-sm text-slate-500">
                      No keys yet —{' '}
                      <Link href="/keys/generate" className="text-brand-400 hover:underline">
                        generate one
                      </Link>
                      .
                    </td>
                  </tr>
                ) : (
                  keys.map((k) => {
                    const expired = k.expired_date ? new Date(k.expired_date) < new Date() : false;
                    return (
                      <tr key={k.id_keys} className="transition hover:bg-white/[0.03]">
                        <td className="pl-5">
                          <Link href={`/keys/${k.id_keys}`} className="font-mono text-xs text-slate-200 hover:text-brand-400">
                            {k.user_key}
                          </Link>
                        </td>
                        <td className="whitespace-nowrap text-slate-400">{durationLabel(k.duration)}</td>
                        <td className="whitespace-nowrap text-xs text-slate-400">
                          {k.expired_date ? fmtLocal(k.expired_date) : <span className="text-slate-600">not started</span>}
                        </td>
                        <td className="text-slate-400">
                          {devicesOf(k.devices).length}/{k.max_devices}
                        </td>
                        <td className="pr-5">
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
                      </tr>
                    );
                  })
                )}
              </tbody>
            </table>
          </div>
        </div>

        <div className="card">
          <h2 className="mb-3 font-semibold text-slate-200">Recent activity</h2>
          {history.length === 0 ? (
            <p className="text-sm text-slate-500">Nothing logged yet.</p>
          ) : (
            <ul className="space-y-3">
              {history.map((h, i) => (
                <li key={i} className="border-b border-line pb-2 last:border-0 last:pb-0">
                  <p className="text-xs text-slate-400">
                    <span className="font-semibold text-slate-200">{h.user_do || 'system'}</span> · {fmtLocal(h.created_at)}
                  </p>
                  <p className="mt-0.5 break-words font-mono text-[11px] text-slate-500">{h.info}</p>
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>

      <div className="mt-4 grid gap-4 lg:grid-cols-2">
        <div className="card">
          <div className="flex items-center gap-2">
            <Icon name="server" className="h-4 w-4 text-brand-400" />
            <h2 className="font-semibold text-slate-200">Loader endpoint</h2>
          </div>
          <p className="mt-1 text-xs text-slate-500">POST · game, user_key, serial</p>
          <pre className="mt-3 overflow-x-auto rounded-lg bg-ink-850 p-3 text-xs text-emerald-300">
            {`${process.env.PUBLIC_BASE_URL || 'https://<your-vercel-app>'}/server`}
          </pre>
          <div className="mt-3 flex flex-wrap gap-2">
            <a href="/api/health" className="btn-ghost text-xs" target="_blank" rel="noreferrer">
              Health check
            </a>
            <a href="/api/version" className="btn-ghost text-xs" target="_blank" rel="noreferrer">
              Lib version
            </a>
          </div>
        </div>

        <div className="card">
          <div className="flex items-center gap-2">
            <Icon name="upload" className="h-4 w-4 text-brand-400" />
            <h2 className="font-semibold text-slate-200">Client lib</h2>
          </div>
          {lib ? (
            <>
              <p className="mt-2 text-2xl font-black text-slate-100">v{lib.version}</p>
              <p className="text-xs text-slate-500">published {fmtLocal(lib.created_at)}</p>
            </>
          ) : (
            <p className="mt-2 text-sm text-slate-400">No lib published yet.</p>
          )}
          {Number(me.level) === 1 || Number(me.level) === 2 ? (
            <Link href="/settings/server/releases" className="btn-ghost mt-3 text-xs">
              Manage lib updates
            </Link>
          ) : null}
        </div>
      </div>
    </>
  );
}
