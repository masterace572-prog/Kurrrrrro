import { redirect } from 'next/navigation';
import Flash from '@/components/Flash';
import PageHeader from '@/components/PageHeader';
import { q } from '@/lib/db';
import { currentUser, isStaff, levelName } from '@/lib/session';
import { createReferralAction } from '@/app/actions';
import { fmtLocal } from '@/lib/domain';

export const metadata = { title: 'Referrals · KURO PANEL' };
export const dynamic = 'force-dynamic';

export default async function ReferralPage({
  searchParams,
}: {
  searchParams: Promise<{ m?: string; t?: string }>;
}) {
  const { m, t } = await searchParams;
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
  }>(
    `SELECT id_reff, "Referral", level, set_saldo, used_by, created_by, acc_expiration
       FROM referral_code ORDER BY id_reff DESC LIMIT 50`
  );

  return (
    <>
      <PageHeader title="Referral codes" subtitle="Each code creates one account with a preset role and balance" />
      <Flash m={m} t={t} />

      <div className="grid gap-4 xl:grid-cols-3">
        <form action={createReferralAction} className="card space-y-4">
          <h2 className="font-semibold text-slate-200">New code</h2>
          <div>
            <label className="label" htmlFor="level">
              Role
            </label>
            <select id="level" name="level" defaultValue="3" className="input">
              <option value="1">Owner</option>
              <option value="2">Admin</option>
              <option value="3">Reseller</option>
            </select>
          </div>
          <div>
            <label className="label" htmlFor="set_saldo">
              Starting balance
            </label>
            <input id="set_saldo" name="set_saldo" type="number" min={0} defaultValue={0} className="input" />
          </div>
          <div>
            <label className="label" htmlFor="accExpire">
              Panel access (days)
            </label>
            <input id="accExpire" name="accExpire" type="number" min={1} defaultValue={30} className="input" />
          </div>
          <button className="btn-primary" type="submit">
            Create code
          </button>
        </form>

        <div className="card overflow-x-auto xl:col-span-2">
          <table className="table">
            <thead>
              <tr>
                <th>Code</th>
                <th>Role</th>
                <th>Balance</th>
                <th>Access until</th>
                <th>Used by</th>
                <th>Created by</th>
              </tr>
            </thead>
            <tbody>
              {codes.length === 0 ? (
                <tr>
                  <td colSpan={6} className="py-8 text-center text-slate-500">
                    No referral codes yet.
                  </td>
                </tr>
              ) : (
                codes.map((c) => (
                  <tr key={c.id_reff}>
                    <td className="font-mono text-xs text-emerald-300">{c.Referral}</td>
                    <td>{levelName(Number(c.level))}</td>
                    <td>{c.set_saldo}</td>
                    <td className="whitespace-nowrap text-slate-400">{fmtLocal(c.acc_expiration)}</td>
                    <td className={c.used_by ? 'text-slate-400' : 'text-emerald-300'}>{c.used_by || 'unused'}</td>
                    <td className="text-slate-400">{c.created_by}</td>
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
