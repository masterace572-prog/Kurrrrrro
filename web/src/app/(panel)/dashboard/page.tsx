import Link from 'next/link';
import Flash from '@/components/Flash';
import PageHeader from '@/components/PageHeader';
import { q, q1 } from '@/lib/db';
import { currentUser, levelName, isStaff } from '@/lib/session';
import { fmtLocal } from '@/lib/domain';

export const metadata = { title: 'Dashboard · KURO PANEL' };
export const dynamic = 'force-dynamic';

export default async function DashboardPage({
  searchParams,
}: {
  searchParams: Promise<{ m?: string; t?: string }>;
}) {
  const { m, t } = await searchParams;
  const me = await currentUser();
  const scoped = isStaff(me) ? '' : 'WHERE registrator = $1';
  const args = isStaff(me) ? [] : [me.username];

  const [stats, keys, history] = await Promise.all([
    q1<{ total: string; active: string; expired: string; devices: string }>(
      `SELECT count(*)::text AS total,
              count(*) FILTER (WHERE status = 1)::text AS active,
              count(*) FILTER (WHERE expired_date IS NOT NULL AND expired_date < now())::text AS expired,
              coalesce(sum(array_length(string_to_array(nullif(devices,''), ','), 1)), 0)::text AS devices
         FROM keys_code ${scoped}`,
      args
    ),
    q<{ user_key: string; game: string; expired_date: Date | null; status: number; max_devices: number }>(
      `SELECT user_key, game, expired_date, status, max_devices FROM keys_code ${scoped}
        ORDER BY id_keys DESC LIMIT 6`,
      args
    ),
    q<{ info: string; user_do: string; created_at: Date }>(
      `SELECT info, user_do, created_at FROM history ORDER BY id_history DESC LIMIT 8`
    ),
  ]);

  const cards = [
    { label: 'Total keys', value: stats?.total ?? '0' },
    { label: 'Active', value: stats?.active ?? '0' },
    { label: 'Expired', value: stats?.expired ?? '0' },
    { label: 'Bound devices', value: stats?.devices ?? '0' },
  ];

  return (
    <>
      <PageHeader
        title={`Welcome back, ${me.fullname || me.username}`}
        subtitle={`${levelName(Number(me.level))} · panel access until ${fmtLocal(me.expiration_date)}`}
        action={
          <Link href="/keys/generate" className="btn-primary">
            Generate keys
          </Link>
        }
      />
      <Flash m={m} t={t} />

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {cards.map((c) => (
          <div key={c.label} className="card">
            <p className="text-xs font-semibold uppercase tracking-wider text-slate-500">{c.label}</p>
            <p className="mt-2 text-3xl font-black text-slate-100">{c.value}</p>
          </div>
        ))}
      </div>

      <div className="mt-4 grid gap-4 lg:grid-cols-3">
        <div className="card lg:col-span-2">
          <div className="mb-3 flex items-center justify-between">
            <h2 className="font-semibold text-slate-200">Latest keys</h2>
            <Link href="/keys" className="text-xs text-brand-400 hover:underline">
              View all
            </Link>
          </div>
          <div className="overflow-x-auto">
            <table className="table">
              <thead>
                <tr>
                  <th>Key</th>
                  <th>Game</th>
                  <th>Expires</th>
                  <th>Devices</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {keys.length === 0 ? (
                  <tr>
                    <td colSpan={5} className="py-6 text-center text-slate-500">
                      No keys yet.
                    </td>
                  </tr>
                ) : (
                  keys.map((k) => {
                    const expired = k.expired_date ? new Date(k.expired_date) < new Date() : false;
                    return (
                      <tr key={k.user_key}>
                        <td className="font-mono text-xs text-slate-200">{k.user_key}</td>
                        <td>{k.game}</td>
                        <td className="text-slate-400">{k.expired_date ? fmtLocal(k.expired_date) : 'not started'}</td>
                        <td>{k.max_devices}</td>
                        <td>
                          {!k.status ? (
                            <span className="badge bg-slate-500/15 text-slate-300">blocked</span>
                          ) : expired ? (
                            <span className="badge bg-red-500/15 text-red-300">expired</span>
                          ) : (
                            <span className="badge bg-emerald-500/15 text-emerald-300">active</span>
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
          <ul className="space-y-3">
            {history.length === 0 ? (
              <li className="text-sm text-slate-500">Nothing logged yet.</li>
            ) : (
              history.map((h, i) => (
                <li key={i} className="border-b border-white/5 pb-2 last:border-0">
                  <p className="text-xs text-slate-400">
                    <span className="font-semibold text-slate-200">{h.user_do || 'system'}</span> ·{' '}
                    {fmtLocal(h.created_at)}
                  </p>
                  <p className="mt-0.5 break-words font-mono text-[11px] text-slate-400">{h.info}</p>
                </li>
              ))
            )}
          </ul>
        </div>
      </div>

      <div className="card mt-4">
        <h2 className="font-semibold text-slate-200">Loader endpoint</h2>
        <p className="mt-1 text-sm text-slate-400">
          Point the XLOX loader at this URL (POST, form-urlencoded: <code>game</code>, <code>user_key</code>,{' '}
          <code>serial</code>):
        </p>
        <pre className="mt-3 overflow-x-auto rounded-lg bg-ink-850 p-3 text-xs text-emerald-300">
          {`${process.env.PUBLIC_BASE_URL || 'https://<your-vercel-app>'}/server`}
        </pre>
      </div>
    </>
  );
}
