import Link from 'next/link';
import Flash from '@/components/Flash';
import PageHeader from '@/components/PageHeader';
import { q } from '@/lib/db';
import { currentUser, isStaff } from '@/lib/session';
import { fmtLocal, devicesOf } from '@/lib/domain';
import { deleteKeysAction, resetDevicesAction } from '@/app/actions';

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
};

export default async function KeysPage({
  searchParams,
}: {
  searchParams: Promise<{ m?: string; t?: string; q?: string }>;
}) {
  const { m, t, q: search } = await searchParams;
  const me = await currentUser();
  const staff = isStaff(me);

  const term = (search ?? '').trim();
  const rows = await q<Row>(
    `SELECT id_keys, game, user_key, duration, expired_date, max_devices, devices, status, registrator
       FROM keys_code
      WHERE ($1::boolean OR registrator = $2)
        AND ($3 = '' OR user_key ILIKE '%' || $3 || '%' OR registrator ILIKE '%' || $3 || '%')
      ORDER BY id_keys DESC LIMIT 300`,
    [staff, me.username, term]
  );

  return (
    <>
      <PageHeader
        title="Keys"
        subtitle={`${rows.length} key(s) shown${staff ? ' · all resellers' : ' · your keys only'}`}
        action={
          <Link href="/keys/generate" className="btn-primary">
            Generate
          </Link>
        }
      />
      <Flash m={m} t={t} />

      <div className="card mb-4">
        <div className="flex flex-wrap items-center gap-2">
          <form className="flex flex-1 gap-2" action="/keys" method="get">
            <input name="q" defaultValue={term} placeholder="Search key or reseller…" className="input" />
            <button className="btn-ghost" type="submit">
              Search
            </button>
          </form>
          <form action={deleteKeysAction}>
            <input type="hidden" name="scope" value="expired" />
            <button className="btn-danger" type="submit">
              Delete expired
            </button>
          </form>
          <form action={deleteKeysAction}>
            <input type="hidden" name="scope" value="unused" />
            <button className="btn-danger" type="submit">
              Delete unused
            </button>
          </form>
        </div>
      </div>

      <div className="card overflow-x-auto">
        <table className="table">
          <thead>
            <tr>
              <th>Key</th>
              <th>Game</th>
              <th>Duration</th>
              <th>Expires</th>
              <th>Devices</th>
              <th>Reseller</th>
              <th>Status</th>
              <th className="text-right">Actions</th>
            </tr>
          </thead>
          <tbody>
            {rows.length === 0 ? (
              <tr>
                <td colSpan={8} className="py-10 text-center text-slate-500">
                  No keys found.
                </td>
              </tr>
            ) : (
              rows.map((k) => {
                const expired = k.expired_date ? new Date(k.expired_date) < new Date() : false;
                const dev = devicesOf(k.devices);
                return (
                  <tr key={k.id_keys}>
                    <td className="whitespace-nowrap font-mono text-xs text-slate-200">{k.user_key}</td>
                    <td>{k.game}</td>
                    <td className="text-slate-400">{k.duration}h</td>
                    <td className="whitespace-nowrap text-slate-400">
                      {k.expired_date ? fmtLocal(k.expired_date) : <span className="text-slate-500">not started</span>}
                    </td>
                    <td>
                      <span className="text-slate-300">
                        {dev.length}/{k.max_devices}
                      </span>
                    </td>
                    <td className="text-slate-400">{k.registrator ?? '—'}</td>
                    <td>
                      {!k.status ? (
                        <span className="badge bg-slate-500/15 text-slate-300">blocked</span>
                      ) : expired ? (
                        <span className="badge bg-red-500/15 text-red-300">expired</span>
                      ) : (
                        <span className="badge bg-emerald-500/15 text-emerald-300">active</span>
                      )}
                    </td>
                    <td>
                      <div className="flex justify-end gap-2">
                        <Link href={`/keys/${k.id_keys}`} className="btn-ghost px-2 py-1 text-xs">
                          Edit
                        </Link>
                        <form action={resetDevicesAction}>
                          <input type="hidden" name="user_key" value={k.user_key} />
                          <button className="btn-ghost px-2 py-1 text-xs" type="submit" disabled={dev.length === 0}>
                            Reset HWID
                          </button>
                        </form>
                        <form action={deleteKeysAction}>
                          <input type="hidden" name="scope" value="one" />
                          <input type="hidden" name="user_key" value={k.user_key} />
                          <button className="btn-danger px-2 py-1 text-xs" type="submit">
                            Delete
                          </button>
                        </form>
                      </div>
                    </td>
                  </tr>
                );
              })
            )}
          </tbody>
        </table>
      </div>
    </>
  );
}
