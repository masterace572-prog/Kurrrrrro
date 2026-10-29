import PageHeader from '@/components/PageHeader';
import { GenerateForm } from '@/components/forms/keys';
import { currentUser } from '@/lib/session';
import { DURATIONS } from '@/lib/domain';

export const metadata = { title: 'Generate keys · KURO PANEL' };
export const dynamic = 'force-dynamic';

export default async function GeneratePage() {
  const me = await currentUser();

  return (
    <>
      <PageHeader
        title="Generate keys"
        subtitle="Charged per device from your balance"
        breadcrumb={[{ label: 'Keys', href: '/keys' }, { label: 'Generate' }]}
      />

      <div className="grid gap-4 xl:grid-cols-3">
        <div className="card xl:col-span-2">
          <GenerateForm saldo={Number(me.saldo)} />
        </div>

        <div className="space-y-4">
          <div className="card">
            <div className="flex items-center gap-3">
              <span className="grid h-10 w-10 place-items-center rounded-xl bg-ink-800 text-brand-400">
                <svg viewBox="0 0 24 24" className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth="1.8">
                  <circle cx="8" cy="15" r="4" />
                  <path d="M10.8 12.2 20 3m-3 3 2.5 2.5M14 6l2.5 2.5" strokeLinecap="round" />
                </svg>
              </span>
              <div>
                <p className="text-xs uppercase tracking-wider text-slate-500">Balance</p>
                <p className="text-2xl font-black text-slate-100">{Number(me.saldo).toLocaleString()}</p>
              </div>
            </div>
            <p className="mt-3 text-xs text-slate-500">
              Credits are deducted when keys are generated. Top-ups are handled by your upline.
            </p>
          </div>

          <div className="card">
            <p className="mb-2 text-xs font-semibold uppercase tracking-wider text-slate-500">Price list</p>
            <ul className="divide-y divide-line text-sm">
              {Object.entries(DURATIONS).map(([hours, d]) => (
                <li key={hours} className="flex items-center justify-between py-1.5">
                  <span className="text-slate-400">{d.label}</span>
                  <span className="font-semibold text-slate-200">₹{d.price}</span>
                </li>
              ))}
            </ul>
            <p className="mt-2 text-xs text-slate-500">Per device, per key.</p>
          </div>

          <div className="card">
            <p className="text-xs font-semibold uppercase tracking-wider text-slate-500">How the loader uses a key</p>
            <ol className="mt-2 list-inside list-decimal space-y-1 text-xs text-slate-400">
              <li>First successful login binds the device (HWID).</li>
              <li>Expiry starts at that first login if it was empty.</li>
              <li>Extra devices are rejected with “MAX DEVICE REACHED”.</li>
              <li>Use the HWID button to free a slot after a reinstall.</li>
            </ol>
          </div>
        </div>
      </div>
    </>
  );
}
