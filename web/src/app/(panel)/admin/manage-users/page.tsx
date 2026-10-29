import Link from 'next/link';
import { redirect } from 'next/navigation';
import PageHeader from '@/components/PageHeader';
import { q } from '@/lib/db';
import { currentUser, isStaff, levelName } from '@/lib/session';
import { fmtLocal } from '@/lib/domain';

export const metadata = { title: 'Users · KURO PANEL' };
export const dynamic = 'force-dynamic';

export default async function ManageUsersPage() {
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
    keys: string;
  }>(
    `SELECT u.id_users, u.username, u.fullname, u.email, u.level, u.saldo, u.status, u.uplink,
            u.expiration_date, u.created_at,
            (SELECT count(*)::text FROM keys_code k WHERE k.registrator = u.username) AS keys
       FROM users u
      WHERE ($1::boolean OR u.uplink = $2)
      ORDER BY u.level ASC, u.id_users ASC`,
    [Number(me.level) === 1, me.username]
  );

  const roleTone = (level: number) =>
    level === 1 ? 'bg-brand-500/15 text-brand-400' : level === 2 ? 'bg-sky-500/15 text-sky-300' : 'bg-slate-500/15 text-slate-300';

  return (
    <>
      <PageHeader
        title="Users"
        subtitle={Number(me.level) === 1 ? 'Every account on the panel' : 'Your downline'}
        breadcrumb={[{ label: 'Dashboard', href: '/dashboard' }, { label: 'Users' }]}
        action={
          <Link href="/admin/create-referral" className="btn-primary">
            Invite user
          </Link>
        }
      />

      <div className="card overflow-x-auto p-0">
        <table className="table">
          <thead>
            <tr>
              <th className="pl-5">User</th>
              <th>Role</th>
              <th>Balance</th>
              <th>Keys</th>
              <th>Uplink</th>
              <th>Access until</th>
              <th>Status</th>
              <th className="pr-5 text-right">Actions</th>
            </tr>
          </thead>
          <tbody>
            {users.length === 0 ? (
              <tr>
                <td colSpan={8} className="py-12 text-center text-sm text-slate-500">
                  No users found.
                </td>
              </tr>
            ) : (
              users.map((u) => {
                const expired = u.expiration_date ? new Date(u.expiration_date) < new Date() : false;
                return (
                  <tr key={u.id_users} className="transition hover:bg-white/[0.02]">
                    <td className="pl-5">
                      <p className="font-semibold text-slate-100">{u.username}</p>
                      <p className="truncate text-xs text-slate-500">{u.email || u.fullname || '—'}</p>
                    </td>
                    <td>
                      <span className={`badge ${roleTone(Number(u.level))}`}>{levelName(Number(u.level))}</span>
                    </td>
                    <td className="text-slate-300">{Number(u.saldo).toLocaleString()}</td>
                    <td className="text-slate-400">{u.keys}</td>
                    <td className="text-xs text-slate-400">{u.uplink ?? '—'}</td>
                    <td className={`whitespace-nowrap text-xs ${expired ? 'text-red-300' : 'text-slate-400'}`}>
                      {fmtLocal(u.expiration_date)}
                    </td>
                    <td>
                      {u.status ? (
                        <span className="badge bg-emerald-500/15 text-emerald-300">active</span>
                      ) : (
                        <span className="badge bg-red-500/15 text-red-300">disabled</span>
                      )}
                    </td>
                    <td className="pr-5 text-right">
                      <Link href={`/admin/user/${u.id_users}`} className="btn-ghost px-2 py-1 text-xs">
                        Edit
                      </Link>
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
