import { redirect } from 'next/navigation';
import Flash from '@/components/Flash';
import PageHeader from '@/components/PageHeader';
import { q1 } from '@/lib/db';
import { currentUser, isStaff } from '@/lib/session';
import { saveServerAction } from '@/app/actions';

export const metadata = { title: 'Server · KURO PANEL' };
export const dynamic = 'force-dynamic';

const FEATURES: { key: string; label: string }[] = [
  { key: 'esp', label: 'ESP' },
  { key: 'item', label: 'Item' },
  { key: 'silentaim', label: 'Silent Aim' },
  { key: 'aim', label: 'AIM' },
  { key: 'bullettrack', label: 'Bullet Track' },
  { key: 'memory', label: 'Memory' },
  { key: 'floating', label: 'Floating' },
  { key: 'setting', label: 'Setting' },
];

export default async function ServerPage({
  searchParams,
}: {
  searchParams: Promise<{ m?: string; t?: string }>;
}) {
  const { m, t } = await searchParams;
  const me = await currentUser();
  if (!isStaff(me)) redirect('/dashboard?m=Access+denied.&t=danger');

  const [modname, onoff, ftext, feature] = await Promise.all([
    q1<{ modname: string }>(`SELECT modname FROM modname WHERE id = 1`),
    q1<{ status: string; myinput: string }>(`SELECT status, myinput FROM onoff WHERE id = 1`),
    q1<{ _status: string; _ftext: string }>(`SELECT _status, _ftext FROM _ftext WHERE id = 1`),
    q1<Record<string, string>>(`SELECT * FROM feature WHERE id = 1`),
  ]);

  const maintenance = onoff?.status === 'on';
  const base = process.env.PUBLIC_BASE_URL || 'https://<your-vercel-app>';

  return (
    <>
      <PageHeader
        title="Server control"
        subtitle="Everything here is served to the loader on its next /server check"
      />
      <Flash m={m} t={t} />

      <div className="grid gap-4 xl:grid-cols-2">
        <form action={saveServerAction} className="card space-y-4">
          <input type="hidden" name="section" value="modname" />
          <h2 className="font-semibold text-slate-200">Mod name</h2>
          <p className="text-xs text-slate-500">Shown inside the loader as the active mod title.</p>
          <input name="modname" defaultValue={modname?.modname ?? ''} className="input" maxLength={100} required />
          <button className="btn-primary" type="submit">
            Save mod name
          </button>
        </form>

        <form action={saveServerAction} className="card space-y-4">
          <input type="hidden" name="section" value="maintenance" />
          <h2 className="font-semibold text-slate-200">Maintenance mode</h2>
          <p className="text-xs text-slate-500">
            When on, every loader check is rejected and your message is returned as the reason.
          </p>
          <select name="status" defaultValue={maintenance ? 'on' : 'off'} className="input">
            <option value="off">Off — panel live</option>
            <option value="on">On — block all logins</option>
          </select>
          <input
            name="myinput"
            defaultValue={onoff?.myinput ?? ''}
            placeholder="Message shown to users, e.g. Panel updating, back in 10 min"
            className="input"
            maxLength={500}
          />
          <button className={maintenance ? 'btn-danger' : 'btn-primary'} type="submit">
            {maintenance ? 'Disable maintenance' : 'Enable maintenance'}
          </button>
        </form>

        <form action={saveServerAction} className="card space-y-4">
          <input type="hidden" name="section" value="credit" />
          <h2 className="font-semibold text-slate-200">Loader text</h2>
          <p className="text-xs text-slate-500">`credit` and `mod_status` fields returned to the client.</p>
          <div>
            <label className="label" htmlFor="mod_status">
              Mod status
            </label>
            <input id="mod_status" name="mod_status" defaultValue={ftext?._status ?? ''} className="input" maxLength={100} />
          </div>
          <div>
            <label className="label" htmlFor="credit">
              Credit line
            </label>
            <input id="credit" name="credit" defaultValue={ftext?._ftext ?? ''} className="input" maxLength={100} />
          </div>
          <button className="btn-primary" type="submit">
            Save text
          </button>
        </form>

        <form action={saveServerAction} className="card space-y-4">
          <input type="hidden" name="section" value="feature" />
          <h2 className="font-semibold text-slate-200">Feature flags</h2>
          <p className="text-xs text-slate-500">
            Sent as ESP / Item / SilentAim / AIM / BulletTrack / Memory / Floating / Setting.
            {Number(me.level) !== 1 ? ' Owner only.' : ''}
          </p>
          <div className="grid grid-cols-2 gap-2">
            {FEATURES.map((f) => (
              <label key={f.key} className="flex items-center gap-2 rounded-lg bg-ink-850 px-3 py-2 text-sm">
                <input
                  type="checkbox"
                  name={f.key}
                  value="on"
                  defaultChecked={(feature?.[f.key] ?? 'on') === 'on'}
                  className="h-4 w-4 rounded border-ink-600 bg-ink-900"
                  disabled={Number(me.level) !== 1}
                />
                {f.label}
              </label>
            ))}
          </div>
          <button className="btn-primary" type="submit" disabled={Number(me.level) !== 1}>
            Save features
          </button>
        </form>

        <div className="card xl:col-span-2">
          <h2 className="font-semibold text-slate-200">Endpoint</h2>
          <pre className="mt-2 overflow-x-auto rounded-lg bg-ink-850 p-3 text-xs text-emerald-300">{`POST ${base}/server
Content-Type: application/x-www-form-urlencoded

game=PUBG&user_key=<key>&serial=<device_uid>`}</pre>
          <p className="mt-2 text-xs text-slate-500">
            Change the host in <code>loader/app/src/main/jni/main.cpp</code> (Java_com_xlox_LogAct_Check) and rebuild the
            APK.
          </p>
        </div>
      </div>
    </>
  );
}
