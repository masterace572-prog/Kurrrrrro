import { notFound } from 'next/navigation';
import Flash from '@/components/Flash';
import PageHeader from '@/components/PageHeader';
import { q1 } from '@/lib/db';
import { currentUser } from '@/lib/session';
import { updateKeyAction } from '@/app/actions';
import { GAMES, DURATIONS, fmtUtc, devicesOf } from '@/lib/domain';

export const dynamic = 'force-dynamic';

export default async function EditKeyPage({
  params,
  searchParams,
}: {
  params: Promise<{ id: string }>;
  searchParams: Promise<{ m?: string; t?: string }>;
}) {
  const { id } = await params;
  const { m, t } = await searchParams;
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
  }>(`SELECT * FROM keys_code WHERE id_keys = $1`, [Number(id)]);

  if (!key) notFound();
  if (me.level !== 1 && key.registrator !== me.username) {
    return (
      <div className="card">
        <p className="text-red-300">This key belongs to another reseller.</p>
      </div>
    );
  }

  const owner = Number(me.level) === 1;

  return (
    <>
      <PageHeader title="Edit key" subtitle={key.user_key} />
      <Flash m={m} t={t} />

      <form action={updateKeyAction} className="card max-w-2xl space-y-4">
        <input type="hidden" name="id_keys" value={key.id_keys} />

        <div className="grid gap-4 sm:grid-cols-2">
          <div>
            <label className="label" htmlFor="user_key">
              Key
            </label>
            <input
              id="user_key"
              name="user_key"
              defaultValue={key.user_key}
              className="input"
              readOnly={!owner}
              required
            />
          </div>
          <div>
            <label className="label" htmlFor="status">
              Status
            </label>
            <select id="status" name="status" defaultValue={String(key.status)} className="input">
              <option value="1">Active</option>
              <option value="0">Blocked</option>
            </select>
          </div>
        </div>

        {owner ? (
          <>
            <div className="grid gap-4 sm:grid-cols-2">
              <div>
                <label className="label" htmlFor="game">
                  Game
                </label>
                <select id="game" name="game" defaultValue={key.game} className="input">
                  {Object.keys(GAMES).map((g) => (
                    <option key={g} value={g}>
                      {g}
                    </option>
                  ))}
                </select>
              </div>
              <div>
                <label className="label" htmlFor="registrator">
                  Reseller
                </label>
                <input id="registrator" name="registrator" defaultValue={key.registrator ?? ''} className="input" />
              </div>
            </div>

            <div className="grid gap-4 sm:grid-cols-3">
              <div>
                <label className="label" htmlFor="duration">
                  Duration (hours)
                </label>
                <input
                  id="duration"
                  name="duration"
                  type="number"
                  min={1}
                  defaultValue={key.duration}
                  className="input"
                  list="durations"
                />
                <datalist id="durations">
                  {Object.entries(DURATIONS).map(([h, d]) => (
                    <option key={h} value={h}>
                      {d.label}
                    </option>
                  ))}
                </datalist>
              </div>
              <div>
                <label className="label" htmlFor="max_devices">
                  Max devices
                </label>
                <input
                  id="max_devices"
                  name="max_devices"
                  type="number"
                  min={1}
                  defaultValue={key.max_devices}
                  className="input"
                />
              </div>
              <div>
                <label className="label" htmlFor="expired_date">
                  Expires (UTC, empty = on first login)
                </label>
                <input
                  id="expired_date"
                  name="expired_date"
                  defaultValue={fmtUtc(key.expired_date) ?? ''}
                  placeholder="2026-12-31 23:59:59"
                  className="input"
                />
              </div>
            </div>

            <div>
              <label className="label" htmlFor="devices">
                Bound devices (one per line)
              </label>
              <textarea
                id="devices"
                name="devices"
                rows={4}
                defaultValue={devicesOf(key.devices).join('\n')}
                className="input font-mono text-xs"
              />
            </div>
          </>
        ) : (
          <p className="text-sm text-slate-500">As a reseller you can only block or unblock this key.</p>
        )}

        <button className="btn-primary" type="submit">
          Save changes
        </button>
      </form>
    </>
  );
}
