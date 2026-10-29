import Sidebar from '@/components/Sidebar';
import { currentUser, levelName } from '@/lib/session';

export default async function PanelLayout({ children }: { children: React.ReactNode }) {
  const user = await currentUser();

  return (
    <div className="flex min-h-screen flex-col lg:flex-row">
      <Sidebar level={Number(user.level)} username={user.username} roleName={levelName(Number(user.level))} />
      <main className="min-w-0 flex-1 p-4 sm:p-6 lg:p-8">{children}</main>
    </div>
  );
}
