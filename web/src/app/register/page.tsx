import { redirect } from 'next/navigation';
import { readSession } from '@/lib/session';
import { AuthShell, RegisterForm } from '@/components/forms/auth';
import { Alert } from '@/components/ui';

export const metadata = { title: 'Register · KURO PANEL' };

export default async function RegisterPage({
  searchParams,
}: {
  searchParams: Promise<{ m?: string; t?: string }>;
}) {
  const session = await readSession();
  if (session) redirect('/dashboard');
  const { m, t } = await searchParams;

  return (
    <AuthShell title="CREATE ACCOUNT" subtitle="Needs a referral code">
      {m ? (
        <Alert tone={t === 'danger' ? 'danger' : 'success'} className="mb-4">
          {m}
        </Alert>
      ) : null}
      <RegisterForm />
    </AuthShell>
  );
}
