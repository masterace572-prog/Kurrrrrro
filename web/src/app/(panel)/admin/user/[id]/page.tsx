import { notFound } from 'next/navigation';
import Flash from '@/components/Flash';
import PageHeader from '@/components/PageHeader';
import { q1 } from '@/lib/db';
import { currentUser, isStaff } from '@/lib/session';
import { updateUserAction } from '@/app/actions';
import { fmtUtc } from '@/lib/domain';

export const dynamic = 'force-dynamic';

export default async function UserEditPage({
  params,
  searchParams,
}: {
  params: Promise<{ id: string }>;
  searchParams: Promise<{ m?: string; t?: string }>;
}) {
  const { id } = await params;
  const { m, t } = await searchParams;
  const me = await currentUser();
  if (!isStaff(me)) notFound();

  const user = await q1<{
    id_users: number;
    username: string;
    fullname: string | null;
    email: string;
    level: number;
    saldo: number;
    status: number;
    expiration_date: Date | null;
  }>(`SELECT id_users, username, fullname, email, level, saldo, status, expiration_date FROM users WHERE id_users = $1`, [
    Number(id),
  ]);

  if (!user) notFound();
  const owner = Number(me.level) === 1;

  return (
    <>
      <PageHeader title={`Edit ${user.username}`} subtitle={`Account #${user.id_users}`} />
      <Flash m={m} t={t} />

      <form action={updateUserAction} className="card max-w-2xl space-y-4">
        <input type="hidden" name="id_users" value={user.id_users} />

        <div className="grid gap-4 sm:grid-cols-2">
          <div>
            <label className="label" htmlFor="fullname">
              Full name
            </label>
            <input id="fullname" name="fullname" defaultValue={user.fullname ?? ''} className="input" readOnly={!owner} />
          </div>
          <div>
            <label className="label" htmlFor="email">
              Email
            </label>
            <input id="email" name="email" type="email" defaultValue={user.email} className="input" readOnly={!owner} />
          </div>
        </div>

        <div className="grid gap-4 sm:grid-cols-3">
          <div>
            <label className="label" htmlFor="saldo">
              Balance
            </label>
            <input id="saldo" name="saldo" type="number" defaultValue={user.saldo} className="input" />
          </div>
          <div>
            <label className="label" htmlFor="status">
              Status
            </label>
            <select id="status" name="status" defaultValue={String(user.status)} className="input">
              <option value="1">Active</option>
              <option value="0">Disabled</option>
            </select>
          </div>
          <div>
            <label className="label" htmlFor="level">
              Role
            </label>
            <select id="level" name="level" defaultValue={String(user.level)} className="input" disabled={!owner}>
              <option value="1">Owner</option>
              <option value="2">Admin</option>
              <option value="3">Reseller</option>
            </select>
          </div>
        </div>

        <div>
          <label className="label" htmlFor="expiration_date">
            Panel access until (UTC)
          </label>
          <input
            id="expiration_date"
            name="expiration_date"
            defaultValue={fmtUtc(user.expiration_date) ?? ''}
            placeholder="2050-01-01 00:00:00"
            className="input"
            readOnly={!owner}
          />
        </div>

        {owner ? (
          <div>
            <label className="label" htmlFor="password">
              Reset password (leave empty to keep)
            </label>
            <input id="password" name="password" type="password" className="input" minLength={6} />
          </div>
        ) : null}

        <button className="btn-primary" type="submit">
          Save user
        </button>
      </form>

      <p className="mt-4 text-xs text-slate-500">
        Disabled fields are read-only for admins. An admin can only manage accounts whose uplink is their own username.
      </p>
    </>
  );
}
