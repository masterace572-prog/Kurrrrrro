import Link from 'next/link';
import { redirect } from 'next/navigation';
import Flash from '@/components/Flash';
import PageHeader from '@/components/PageHeader';
import { q } from '@/lib/db';
import { currentUser, isStaff, levelName } from '@/lib/session';
import { fmtLocal } from '@/lib/domain';

export const metadata = { title: 'Users · KURO PANEL' };
export const dynamic = 'force-dynamic';

export default async function ManageUsersPage({
  searchParams,
}: {
  searchParams: Promise<{ m?: string; t?: string }>;
}) {
  const { m, t } = await searchParams;
  const me = await currentUser();
  if (!isStaff(me)) redirect('/dashboard?m=Access+denied.&t=danger');

  const users = await q<{
    id_users: number;
    username: string;
    fullname: string | null;
    email: string;
    level: number;
    saldo: number;
    status: number;
    uplink: string | null;
    expiration_date: Date | null;
    created_at: Date | null;
  }>(
    `SELECT id_users, username, fullname, email, level, saldo, status, uplink, expiration_date, created_at
       FROM users WHERE ($1::boolean OR uplink = $2) ORDER BY id_users ASC`,
    [Number(me.level) === 1, me.username]
  );

  return (
    <>
      <PageHeader title="Users" subtitle={`${users.length} account(s)`} />
      <Flash m={m} t={t} />

      <div className="card overflow-x-auto">
        <table className="table">
          <thead>
            <tr>
              <th>ID</th>
              <th>Username</th>
              <th>Role</th>
              <th>Balance</th>
              <th>Uplink</th>
              <th>Access until</th>
              <th>Status</th>
              <th className="text-right">Actions</th>
            </tr>
          </thead>
          <tbody>
            {users.map((u) => (
              <tr key={u.id_users}>
                <td className="text-slate-500">{u.id_users}</td>
                <td>
                  <p className="font-semibold text-slate-100">{u.username}</p>
                  <p className="text-xs text-slate-500">{u.email || u.fullname || '—'}</p>
                </td>
                <td>
                  <span
                    className={`badge ${
                      Number(u.level) === 1
                        ? 'bg-brand-500/15 text-brand-400'
                        : Number(u.level) === 2
                          ? 'bg-sky-500/15 text-sky-300'
                          : 'bg-slate-500/15 text-slate-300'
                    }`}
                  >
                    {levelName(Number(u.level))}
                  </span>
                </td>
                <td>{Number(u.saldo).toLocaleString()}</td>
                <td className="text-slate-400">{u.uplink ?? '—'}</td>
                <td className="whitespace-nowrap text-slate-400">{fmtLocal(u.expiration_date)}</td>
                <td>
                  {u.status ? (
                    <span className="badge bg-emerald-500/15 text-emerald-300">active</span>
                  ) : (
                    <span className="badge bg-red-500/15 text-red-300">disabled</span>
                  )}
                </td>
                <td className="text-right">
                  <Link href={`/admin/user/${u.id_users}`} className="btn-ghost px-2 py-1 text-xs">
                    Edit
                  </Link>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  );
}
