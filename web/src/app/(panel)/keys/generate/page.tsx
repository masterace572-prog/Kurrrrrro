import Flash from '@/components/Flash';
import PageHeader from '@/components/PageHeader';
import { generateKeysAction } from '@/app/actions';
import { currentUser } from '@/lib/session';
import { DURATIONS, GAMES } from '@/lib/domain';

export const metadata = { title: 'Generate · KURO PANEL' };
export const dynamic = 'force-dynamic';

export default async function GeneratePage({
  searchParams,
}: {
  searchParams: Promise<{ m?: string; t?: string }>;
}) {
  const { m, t } = await searchParams;
  const me = await currentUser();

  return (
    <>
      <PageHeader title="Generate keys" subtitle="Keys are charged per device from your balance" />
      <Flash m={m} t={t} />

      <div className="grid gap-4 lg:grid-cols-3">
        <div className="card lg:col-span-2">
          <form action={generateKeysAction} className="space-y-4">
            <div className="grid gap-4 sm:grid-cols-2">
              <div>
                <label className="label" htmlFor="game">
                  Game
                </label>
                <select id="game" name="game" className="input">
                  {Object.entries(GAMES).map(([id, label]) => (
                    <option key={id} value={id}>
                      {label}
                    </option>
                  ))}
                </select>
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
                  max={50}
                  defaultValue={1}
                  className="input"
                  required
                />
              </div>
            </div>

            <div>
              <label className="label" htmlFor="duration">
                Duration
              </label>
              <select id="duration" name="duration" className="input" defaultValue={24}>
                {Object.entries(DURATIONS).map(([hours, d]) => (
                  <option key={hours} value={hours}>
                    {d.label} — ₹{d.price}/device
                  </option>
                ))}
              </select>
            </div>

            <div className="grid gap-4 sm:grid-cols-2">
              <div>
                <label className="label" htmlFor="loopcount">
                  How many keys
                </label>
                <select id="loopcount" name="loopcount" className="input" defaultValue="1">
                  <option value="1">5 keys</option>
                  <option value="2">10 keys</option>
                  <option value="3">50 keys</option>
                  <option value="4">100 keys</option>
                  <option value="5">1 key</option>
                </select>
              </div>
              <div>
                <label className="label" htmlFor="custominput">
                  Key name
                </label>
                <select id="custominput" name="custominput" className="input" defaultValue="auto">
                  <option value="auto">Automatic (reseller-DURATION-xxxxx)</option>
                  <option value="custom">Custom key</option>
                </select>
              </div>
            </div>

            <div>
              <label className="label" htmlFor="cuslicense">
                Custom key (only if selected above)
              </label>
              <input id="cuslicense" name="cuslicense" className="input" placeholder="4-20 characters" maxLength={20} />
            </div>

            <button className="btn-primary" type="submit">
              Generate
            </button>
          </form>
        </div>

        <div className="space-y-4">
          <div className="card">
            <p className="text-xs font-semibold uppercase tracking-wider text-slate-500">Your balance</p>
            <p className="mt-2 text-3xl font-black text-slate-100">{Number(me.saldo).toLocaleString()}</p>
            <p className="mt-1 text-xs text-slate-500">credits</p>
          </div>
          <div className="card">
            <p className="text-xs font-semibold uppercase tracking-wider text-slate-500">Pricing</p>
            <ul className="mt-2 space-y-1 text-sm text-slate-400">
              {Object.entries(DURATIONS).map(([hours, d]) => (
                <li key={hours} className="flex justify-between">
                  <span>{d.label}</span>
                  <span className="text-slate-200">₹{d.price}</span>
                </li>
              ))}
            </ul>
            <p className="mt-3 text-xs text-slate-500">Prices are per device and multiplied by your device count.</p>
          </div>
        </div>
      </div>
    </>
  );
}
