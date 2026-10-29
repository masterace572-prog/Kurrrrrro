'use client';

import { useEffect, useState } from 'react';
import { useSearchParams, useRouter, usePathname } from 'next/navigation';
import { Icon } from './ui';

/**
 * Floating toast driven by ?m=<message>&t=success|danger|warning.
 *
 * Important: the URL is only cleaned *after* the toast has been shown. Doing it
 * in a second effect on mount raced with the "open" state and removed ?m= before
 * anything painted, so the message never appeared.
 */
export default function Toast() {
  const params = useSearchParams();
  const router = useRouter();
  const pathname = usePathname();

  const incoming = params.get('m');
  const type = (params.get('t') || 'success') as 'success' | 'danger' | 'warning';
  const [message, setMessage] = useState<string | null>(incoming);

  // a new ?m= (e.g. after a redirect) replaces whatever is showing
  useEffect(() => {
    setMessage(incoming);
  }, [incoming]);

  useEffect(() => {
    if (!message) return;

    const dismiss = () => {
      setMessage(null);
      // strip m/t so a refresh does not repeat it
      const next = new URLSearchParams(window.location.search);
      next.delete('m');
      next.delete('t');
      const qs = next.toString();
      router.replace(qs ? `${pathname}?${qs}` : pathname, { scroll: false });
    };

    const timer = setTimeout(dismiss, type === 'danger' ? 10000 : 6000);
    return () => clearTimeout(timer);
  }, [message, type, pathname, router]);

  if (!message) return null;

  const tone =
    type === 'danger'
      ? 'border-red-500/30 bg-white text-red-300'
      : type === 'warning'
        ? 'border-amber-500/30 bg-white text-amber-300'
        : 'border-emerald-500/30 bg-white text-emerald-300';

  return (
    <div className="pointer-events-none fixed inset-x-0 bottom-4 z-[60] flex justify-center px-4">
      <div
        className={`pointer-events-auto flex w-full max-w-md items-start gap-3 rounded-xl border px-4 py-3 text-sm shadow-card animate-[fadeIn_.2s_ease-out] ${tone}`}
        role="status"
        aria-live="polite"
      >
        <Icon
          name={type === 'danger' ? 'alert' : type === 'warning' ? 'warning' : 'check'}
          className="mt-0.5 h-4 w-4 shrink-0"
        />
        <p className="flex-1">{message}</p>
        <button
          onClick={() => setMessage(null)}
          className="opacity-60 transition hover:opacity-100"
          aria-label="Dismiss"
        >
          <Icon name="close" className="h-4 w-4" />
        </button>
      </div>
    </div>
  );
}
