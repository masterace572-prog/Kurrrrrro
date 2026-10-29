'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useEffect, useState } from 'react';
import { Icon, type IconName } from './ui';

type NavItem = { href: string; label: string; icon: IconName; levels: number[]; exact?: boolean };

const NAV: { section: string; items: NavItem[] }[] = [
  {
    section: 'Overview',
    items: [
      { href: '/dashboard', label: 'Dashboard', icon: 'grid', levels: [1, 2, 3] },
      { href: '/keys', label: 'Keys', icon: 'key', levels: [1, 2, 3] },
      { href: '/keys/generate', label: 'Generate keys', icon: 'plus', levels: [1, 2, 3] },
    ],
  },
  {
    section: 'Administration',
    items: [
      { href: '/admin/manage-users', label: 'Users', icon: 'users', levels: [1, 2] },
      { href: '/admin/create-referral', label: 'Referrals', icon: 'gift', levels: [1, 2] },
    ],
  },
  {
    section: 'Configuration',
    items: [
      { href: '/settings/server', label: 'Server control', icon: 'server', levels: [1, 2] },
      { href: '/settings/server/releases', label: 'Lib updates', icon: 'upload', levels: [1, 2] },
      { href: '/settings', label: 'Account', icon: 'user', levels: [1, 2, 3] },
    ],
  },
];

export default function Shell({
  level,
  username,
  roleName,
  saldo,
  children,
}: {
  level: number;
  username: string;
  roleName: string;
  saldo: number;
  children: React.ReactNode;
}) {
  const pathname = usePathname();
  const [open, setOpen] = useState(false);

  useEffect(() => setOpen(false), [pathname]);

  useEffect(() => {
    document.body.style.overflow = open ? 'hidden' : '';
    return () => {
      document.body.style.overflow = '';
    };
  }, [open]);

  const isActive = (item: NavItem) =>
    item.href === '/keys'
      ? pathname === '/keys' || /^\/keys\/\d+$/.test(pathname)
      : item.href === '/settings'
        ? pathname === '/settings'
        : pathname === item.href || pathname.startsWith(`${item.href}/`);

  const nav = (
    <nav className="flex h-full flex-col">
      <div className="flex items-center gap-2.5 px-2 pb-6">
        <span className="grid h-10 w-10 place-items-center rounded-xl bg-gradient-to-br from-brand-400 to-brand-600 text-lg font-black text-white shadow-glow">
          K
        </span>
        <div className="min-w-0">
          <p className="truncate text-sm font-black tracking-[0.2em] text-slate-100">KURO</p>
          <p className="text-[10px] uppercase tracking-[0.2em] text-slate-500">control panel</p>
        </div>
        <button
          className="ml-auto rounded-lg p-2 text-slate-400 hover:bg-ink-800 hover:text-slate-200 lg:hidden"
          onClick={() => setOpen(false)}
          aria-label="Close menu"
        >
          <Icon name="close" />
        </button>
      </div>

      <div className="mb-6 rounded-xl border border-white/5 bg-ink-850/80 p-3">
        <div className="flex items-center gap-2.5">
          <span className="grid h-9 w-9 shrink-0 place-items-center rounded-full bg-brand-500/15 text-sm font-bold text-brand-400">
            {username.slice(0, 1).toUpperCase()}
          </span>
          <div className="min-w-0">
            <p className="truncate text-sm font-semibold text-slate-100">{username}</p>
            <p className="text-[11px] font-medium text-brand-400">{roleName}</p>
          </div>
        </div>
        <div className="mt-3 flex items-center justify-between rounded-lg bg-ink-900/70 px-2.5 py-1.5">
          <span className="text-[10px] uppercase tracking-wider text-slate-500">Balance</span>
          <span className="text-xs font-bold text-slate-200">{saldo.toLocaleString()}</span>
        </div>
      </div>

      <div className="flex-1 space-y-5 overflow-y-auto">
        {NAV.map((group) => {
          const items = group.items.filter((i) => i.levels.includes(level));
          if (!items.length) return null;
          return (
            <div key={group.section}>
              <p className="mb-1.5 px-2 text-[10px] font-semibold uppercase tracking-[0.15em] text-slate-600">
                {group.section}
              </p>
              <div className="space-y-0.5">
                {items.map((item) => {
                  const active = isActive(item);
                  return (
                    <Link
                      key={item.href}
                      href={item.href}
                      aria-current={active ? 'page' : undefined}
                      className={`group flex items-center gap-3 rounded-lg px-2.5 py-2 text-sm transition ${
                        active
                          ? 'bg-brand-500/15 font-semibold text-brand-400 ring-1 ring-inset ring-brand-500/25'
                          : 'text-slate-400 hover:bg-ink-800 hover:text-slate-200'
                      }`}
                    >
                      <Icon name={item.icon} className="h-4 w-4 shrink-0" />
                      <span className="truncate">{item.label}</span>
                      {active ? <span className="ml-auto h-1.5 w-1.5 rounded-full bg-brand-400" /> : null}
                    </Link>
                  );
                })}
              </div>
            </div>
          );
        })}
      </div>

      <a
        href="/logout"
        className="mt-4 flex items-center gap-3 rounded-lg px-2.5 py-2 text-sm text-slate-400 transition hover:bg-red-500/10 hover:text-red-300"
      >
        <Icon name="logout" className="h-4 w-4" />
        Sign out
      </a>
    </nav>
  );

  return (
    <div className="min-h-screen">
      {/* mobile top bar */}
      <header className="sticky top-0 z-30 flex items-center gap-3 border-b border-white/5 bg-ink-950/85 px-4 py-3 backdrop-blur lg:hidden">
        <button
          onClick={() => setOpen(true)}
          className="rounded-lg border border-ink-600 bg-ink-850 p-2 text-slate-300"
          aria-label="Open menu"
        >
          <Icon name="menu" />
        </button>
        <span className="text-sm font-black tracking-[0.2em] text-brand-400">KURO</span>
        <span className="ml-auto truncate text-xs text-slate-500">{username}</span>
      </header>

      {/* mobile drawer */}
      {open ? (
        <div className="fixed inset-0 z-40 lg:hidden">
          <div className="absolute inset-0 bg-black/60 backdrop-blur-sm" onClick={() => setOpen(false)} />
          <aside className="absolute inset-y-0 left-0 w-72 max-w-[85%] border-r border-white/5 bg-ink-900 p-4 shadow-2xl">
            {nav}
          </aside>
        </div>
      ) : null}

      <div className="flex">
        <aside className="sticky top-0 hidden h-screen w-64 shrink-0 border-r border-white/5 bg-ink-900/50 p-4 lg:block">
          {nav}
        </aside>
        <main className="min-w-0 flex-1 px-4 py-5 sm:px-6 sm:py-7 lg:px-8">{children}</main>
      </div>
    </div>
  );
}
