import { notFound } from 'next/navigation';
import PageHeader from '@/components/PageHeader';
import { q1 } from '@/lib/db';
import { currentUser, isStaff, levelName } from '@/lib/session';
import { fmtUtc, fmtLocal } from '@/lib/domain';
import { UserForm } from '@/components/forms/admin';
import { Alert } from '@/components/ui';

export const dynamic = 'force-dynamic';

export default async function UserEditPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const me = await currentUser();
  if (!isStaff(me)) notFound();

  const numericId = Number(id);
  if (!Number.isInteger(numericId)) notFound();

  const user = await q1<{
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
  }>(`SELECT id_users, username, fullname, email, level, saldo, status, uplink, expiration_date, created_at
        FROM users WHERE id_users = $1`, [numericId]);

  if (!user) notFound();

  const owner = Number(me.level) === 1;
  if (!owner && user.uplink !== me.username) {
    return (
      <div className="mx-auto max-w-xl">
        <Alert tone="danger" title="Not in your downline">
          {user.username} belongs to {user.uplink ?? 'another uplink'}.
        </Alert>
      </div>
    );
  }

  const keys = await q1<{ n: number; active: number }>(
    `SELECT count(*)::int AS n,
            count(*) FILTER (WHERE status = 1 AND (expired_date IS NULL OR expired_date > now()))::int AS active
       FROM keys_code WHERE registrator = $1`,
    [user.username]
  );

  return (
    <>
      <PageHeader
        title={`Edit ${user.username}`}
        subtitle={`${levelName(Number(user.level))} · joined ${fmtLocal(user.created_at)}`}
        breadcrumb={[{ label: 'Users', href: '/admin/manage-users' }, { label: user.username }]}
      />

      <div className="grid gap-4 xl:grid-cols-3">
        <div className="xl:col-span-2">
          <UserForm
            user={{
              id_users: user.id_users,
              username: user.username,
              fullname: user.fullname ?? '',
              email: user.email ?? '',
              level: Number(user.level),
              saldo: Number(user.saldo),
              status: Number(user.status),
              expiration_date: fmtUtc(user.expiration_date) ?? '',
            }}
            owner={owner}
          />
        </div>

        <div className="card h-fit space-y-2">
          <p className="text-xs font-semibold uppercase tracking-wider text-slate-500">Account</p>
          <dl className="space-y-2 text-sm">
            {[
              ['ID', `#${user.id_users}`],
              ['Uplink', user.uplink ?? '—'],
              ['Keys created', `${keys?.n ?? 0} (${keys?.active ?? 0} active)`],
              ['Balance', Number(user.saldo).toLocaleString()],
              ['Access until', fmtLocal(user.expiration_date)],
            ].map(([k, v]) => (
              <div key={k} className="flex justify-between gap-3">
                <dt className="text-slate-500">{k}</dt>
                <dd className="text-right text-slate-200">{v}</dd>
              </div>
            ))}
          </dl>
        </div>
      </div>
    </>
  );
}
