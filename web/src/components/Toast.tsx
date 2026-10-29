'use client';

import { useEffect, useState } from 'react';
import { useSearchParams, useRouter, usePathname } from 'next/navigation';
import { Icon } from './ui';

/**
 * Floating toast driven by ?m=<message>&t=success|danger|warning.
 * Auto-dismisses, removes the params from the URL so a refresh doesn't repeat it.
 */
export default function Toast() {
  const params = useSearchParams();
  const router = useRouter();
  const pathname = usePathname();
  const message = params.get('m');
  const type = (params.get('t') || 'success') as 'success' | 'danger' | 'warning';
  const [open, setOpen] = useState(false);

  useEffect(() => {
    if (!message) return;
    setOpen(true);
    const hide = setTimeout(() => setOpen(false), type === 'danger' ? 9000 : 5000);
    return () => clearTimeout(hide);
  }, [message, type]);

  useEffect(() => {
    if (!message || open) return;
    const next = new URLSearchParams(params.toString());
    next.delete('m');
    next.delete('t');
    const qs = next.toString();
    router.replace(qs ? `${pathname}?${qs}` : pathname, { scroll: false });
  }, [open, message, params, pathname, router]);

  if (!message || !open) return null;

  const tone =
    type === 'danger'
      ? 'border-red-500/40 bg-red-950/90 text-red-100'
      : type === 'warning'
        ? 'border-amber-500/40 bg-amber-950/90 text-amber-100'
        : 'border-emerald-500/40 bg-emerald-950/90 text-emerald-100';

  return (
    <div className="pointer-events-none fixed inset-x-0 top-4 z-50 flex justify-center px-4">
      <div
        className={`pointer-events-auto flex w-full max-w-md items-start gap-3 rounded-xl border px-4 py-3 text-sm shadow-2xl backdrop-blur animate-[fadeIn_.2s_ease-out] ${tone}`}
        role="status"
        aria-live="polite"
      >
        <Icon
          name={type === 'danger' ? 'alert' : type === 'warning' ? 'warning' : 'check'}
          className="mt-0.5 h-4 w-4 shrink-0"
        />
        <p className="flex-1">{message}</p>
        <button onClick={() => setOpen(false)} className="opacity-60 transition hover:opacity-100" aria-label="Dismiss">
          <Icon name="close" className="h-4 w-4" />
        </button>
      </div>
    </div>
  );
}
