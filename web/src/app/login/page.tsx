import { redirect } from 'next/navigation';
import { readSession } from '@/lib/session';
import { AuthShell, LoginForm } from '@/components/forms/auth';
import { Alert } from '@/components/ui';

export const metadata = { title: 'Sign in · KURO PANEL' };

export default async function LoginPage({
  searchParams,
}: {
  searchParams: Promise<{ m?: string; t?: string; reason?: string }>;
}) {
  const session = await readSession();
  if (session) redirect('/dashboard');
  const { m, t, reason } = await searchParams;

  return (
    <AuthShell title="KURO PANEL" subtitle="Sign in to manage keys, resellers and updates">
      {m ? (
        <Alert
          tone={t === 'danger' ? 'danger' : t === 'warning' ? 'warning' : 'success'}
          className="mb-4"
        >
          {m}
        </Alert>
      ) : null}
      {reason === 'nodb' ? (
        <Alert tone="warning" title="Database not reachable" className="mb-4">
          The panel cannot reach Postgres. Check <code>DATABASE_URL</code> (see <code>/api/health</code>) and run{' '}
          <code>npm run db:push</code> if the tables are missing.
        </Alert>
      ) : null}
      <LoginForm />
    </AuthShell>
  );
}
