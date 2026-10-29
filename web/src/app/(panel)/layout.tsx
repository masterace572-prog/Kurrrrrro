import { Suspense } from 'react';
import Shell from '@/components/Shell';
import Toast from '@/components/Toast';
import { currentUser, levelName } from '@/lib/session';

export default async function PanelLayout({ children }: { children: React.ReactNode }) {
  const user = await currentUser();

  return (
    <>
      <Suspense fallback={null}>
        <Toast />
      </Suspense>
      <Shell
        level={Number(user.level)}
        username={user.username}
        roleName={levelName(Number(user.level))}
        saldo={Number(user.saldo)}
      >
        {children}
      </Shell>
    </>
  );
}
