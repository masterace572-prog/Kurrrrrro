import { redirect } from 'next/navigation';
import PageHeader from '@/components/PageHeader';
import { q } from '@/lib/db';
import { currentUser, isStaff, levelName } from '@/lib/session';
import { ReferralForm } from '@/components/forms/admin';
import { fmtLocal } from '@/lib/domain';

export const metadata = { title: 'Referrals · KURO PANEL' };
export const dynamic = 'force-dynamic';

export default async function ReferralPage() {
  const me = await currentUser();
  if (!isStaff(me)) redirect('/dashboard?m=Access+denied.&t=danger');

  const codes = await q<{
    id_reff: number;
    Referral: string;
    level: number;
    set_saldo: number;
    used_by: string;
    created_by: string;
    acc_expiration: Date | null;
    created_at: Date | null;
  }>(
    `SELECT id_reff, "Referral", level, set_saldo, used_by, created_by, acc_expiration, created_at
       FROM referral_code ORDER BY id_reff DESC LIMIT 50`
  );

  return (
    <>
      <PageHeader
        title="Referral codes"
        subtitle="Each code creates exactly one account with a preset role and balance"
        breadcrumb={[{ label: 'Dashboard', href: '/dashboard' }, { label: 'Referrals' }]}
      />

      <div className="grid gap-4 xl:grid-cols-3">
        <ReferralForm isOwner={Number(me.level) === 1} />

        <div className="card overflow-x-auto p-0 xl:col-span-2">
          <table className="table">
            <thead>
              <tr>
                <th className="pl-5">Code</th>
                <th>Role</th>
                <th>Balance</th>
                <th>Access until</th>
                <th>Used by</th>
                <th className="pr-5">Created by</th>
              </tr>
            </thead>
            <tbody>
              {codes.length === 0 ? (
                <tr>
                  <td colSpan={6} className="py-12 text-center text-sm text-slate-500">
                    No referral codes yet — create the first one on the left.
                  </td>
                </tr>
              ) : (
                codes.map((c) => (
                  <tr key={c.id_reff} className="transition hover:bg-white/[0.02]">
                    <td className="pl-5 font-mono text-xs font-semibold tracking-widest text-emerald-300">{c.Referral}</td>
                    <td>
                      <span className="badge bg-slate-500/15 text-slate-300">{levelName(Number(c.level))}</span>
                    </td>
                    <td className="text-slate-300">{c.set_saldo}</td>
                    <td className="whitespace-nowrap text-xs text-slate-400">{fmtLocal(c.acc_expiration)}</td>
                    <td className={c.used_by ? 'text-slate-400' : 'text-emerald-300'}>
                      {c.used_by || 'unused'}
                    </td>
                    <td className="pr-5 text-xs text-slate-400">{c.created_by}</td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>
    </>
  );
}
