'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useState } from 'react';

const items = [
  { href: '/dashboard', label: 'Dashboard', icon: '▦', levels: [1, 2, 3] },
  { href: '/keys', label: 'Keys', icon: '⚿', levels: [1, 2, 3] },
  { href: '/keys/generate', label: 'Generate', icon: '＋', levels: [1, 2, 3] },
  { href: '/settings/server', label: 'Server', icon: '⚙', levels: [1, 2] },
  { href: '/admin/manage-users', label: 'Users', icon: '☰', levels: [1, 2] },
  { href: '/admin/create-referral', label: 'Referral', icon: '⤳', levels: [1, 2] },
  { href: '/settings', label: 'Settings', icon: '☺', levels: [1, 2, 3] },
];

export default function Sidebar({
  level,
  username,
  roleName,
}: {
  level: number;
  username: string;
  roleName: string;
}) {
  const pathname = usePathname();
  const [open, setOpen] = useState(false);
  const visible = items.filter((i) => i.levels.includes(level));

  return (
    <>
      <div className="flex items-center justify-between border-b border-line bg-ink-900 p-4 lg:hidden">
        <span className="font-black tracking-widest text-brand-400">KURO PANEL</span>
        <button className="btn-ghost px-3 py-1" onClick={() => setOpen((v) => !v)}>
          ☰
        </button>
      </div>

      <aside
        className={`${open ? 'block' : 'hidden'} w-full shrink-0 border-r border-line bg-ink-900 p-4 lg:block lg:w-64`}
      >
        <div className="mb-6 hidden items-center gap-2 px-2 lg:flex">
          <span className="grid h-9 w-9 place-items-center rounded-xl bg-brand-500 font-black text-white">
            K
          </span>
          <div>
            <p className="text-sm font-black tracking-widest">KURO</p>
            <p className="text-[10px] uppercase tracking-[0.2em] text-slate-500">panel v1</p>
          </div>
        </div>

        <div className="mb-5 rounded-xl border border-line bg-ink-850 p-3">
          <p className="truncate text-sm font-semibold text-slate-100">{username}</p>
          <p className="text-xs text-brand-400">{roleName}</p>
        </div>

        <nav className="space-y-1">
          {visible.map((item) => {
            const active =
              item.href === '/keys'
                ? pathname === '/keys' || /^\/keys\/\d+$/.test(pathname)
                : pathname === item.href;
            return (
              <Link
                key={item.href}
                href={item.href}
                onClick={() => setOpen(false)}
                className={`flex items-center gap-3 rounded-lg px-3 py-2 text-sm transition ${
                  active
                    ? 'bg-ink-800 text-brand-400 ring-1 ring-inset ring-ink-700'
                    : 'text-slate-300 hover:bg-ink-800'
                }`}
              >
                <span className="w-4 text-center opacity-80">{item.icon}</span>
                {item.label}
              </Link>
            );
          })}
        </nav>

        <form action="/logout" method="get" className="mt-6">
          <button className="btn-ghost w-full" type="submit">
            Sign out
          </button>
        </form>
      </aside>
    </>
  );
}
