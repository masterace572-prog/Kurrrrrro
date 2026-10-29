'use client';

import { useEffect, useState } from 'react';
import { useFormStatus } from 'react-dom';
import type { FormState } from '@/lib/result';

/* ------------------------------------------------------------------ icons */

export function Icon({ name, className = 'h-4 w-4' }: { name: IconName; className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"
      strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      {PATHS[name]}
    </svg>
  );
}

export type IconName =
  | 'grid' | 'key' | 'plus' | 'cog' | 'users' | 'gift' | 'user' | 'logout' | 'menu' | 'close'
  | 'check' | 'alert' | 'info' | 'warning' | 'copy' | 'download' | 'upload' | 'refresh' | 'trash'
  | 'shield' | 'server' | 'search' | 'chevron' | 'book' | 'phone';

const PATHS: Record<IconName, React.ReactNode> = {
  grid: <><rect x="3" y="3" width="7" height="7" rx="1.5" /><rect x="14" y="3" width="7" height="7" rx="1.5" /><rect x="3" y="14" width="7" height="7" rx="1.5" /><rect x="14" y="14" width="7" height="7" rx="1.5" /></>,
  key: <><circle cx="8" cy="15" r="4" /><path d="M10.8 12.2 20 3m-3 3 2.5 2.5M14 6l2.5 2.5" /></>,
  plus: <path d="M12 5v14M5 12h14" />,
  cog: <><circle cx="12" cy="12" r="3" /><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.9l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.9-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1A1.7 1.7 0 0 0 9 19.4a1.7 1.7 0 0 0-1.9.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.9 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1A1.7 1.7 0 0 0 4.6 9a1.7 1.7 0 0 0-.3-1.9l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.9.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.9-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.9V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1Z" /></>,
  users: <><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" /><circle cx="9" cy="7" r="4" /><path d="M23 21v-2a4 4 0 0 0-3-3.9M16 3.1a4 4 0 0 1 0 7.8" /></>,
  gift: <><rect x="3" y="8" width="18" height="13" rx="2" /><path d="M12 8v13M3 12h18" /><path d="M12 8S10 3 8 4.5 9 8 12 8Zm0 0s2-5 4-3.5S15 8 12 8Z" /></>,
  user: <><circle cx="12" cy="8" r="4" /><path d="M4 21v-1a7 7 0 0 1 14 0v1" /></>,
  logout: <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4M16 17l5-5-5-5M21 12H9" />,
  menu: <path d="M4 6h16M4 12h16M4 18h16" />,
  close: <path d="M18 6 6 18M6 6l12 12" />,
  check: <path d="m20 6-11 11-5-5" />,
  alert: <><circle cx="12" cy="12" r="9" /><path d="M12 8v5M12 16.5v.5" /></>,
  info: <><circle cx="12" cy="12" r="9" /><path d="M12 11v6M12 7.5v.5" /></>,
  warning: <><path d="M10.3 3.9 1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0Z" /><path d="M12 9v4M12 17v.5" /></>,
  copy: <><rect x="9" y="9" width="12" height="12" rx="2" /><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" /></>,
  download: <path d="M12 3v12m0 0 4-4m-4 4-4-4M4 21h16" />,
  upload: <path d="M12 21V9m0 0 4 4m-4-4-4 4M4 3h16" />,
  refresh: <path d="M21 12a9 9 0 1 1-3-6.7M21 3v6h-6" />,
  trash: <path d="M3 6h18M8 6V4a1 1 0 0 1 1-1h6a1 1 0 0 1 1 1v2m2 0v14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2V6" />,
  shield: <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10Z" />,
  server: <><rect x="2" y="3" width="20" height="7" rx="2" /><rect x="2" y="14" width="20" height="7" rx="2" /><path d="M6 6.5v.01M6 17.5v.01" /></>,
  search: <><circle cx="11" cy="11" r="7" /><path d="m20 20-3.5-3.5" /></>,
  chevron: <path d="m9 18 6-6-6-6" />,
  book: <><path d="M4 4.5A2.5 2.5 0 0 1 6.5 2H20v18H6.5A2.5 2.5 0 0 0 4 22.5Z" /><path d="M4 19.5V4.5M9 7h7M9 11h7" /></>,
  phone: <><rect x="6" y="2" width="12" height="20" rx="2.5" /><path d="M11 18.5h2" /></>,
};

/* ------------------------------------------------------------------ alert */

const TONES = {
  success: 'border-emerald-500/30 bg-emerald-500/10 text-emerald-300',
  danger: 'border-red-500/30 bg-red-500/10 text-red-300',
  warning: 'border-amber-500/30 bg-amber-500/10 text-amber-300',
  info: 'border-sky-500/30 bg-sky-500/10 text-sky-300',
} as const;

export function Alert({
  tone = 'info',
  title,
  children,
  className = '',
}: {
  tone?: keyof typeof TONES;
  title?: string;
  children?: React.ReactNode;
  className?: string;
}) {
  const icon: IconName = tone === 'success' ? 'check' : tone === 'danger' ? 'alert' : tone === 'warning' ? 'warning' : 'info';
  return (
    <div className={`flex items-start gap-3 rounded-xl border px-4 py-3 text-sm ${TONES[tone]} ${className}`} role="alert">
      <Icon name={icon} className="mt-0.5 h-4 w-4 shrink-0" />
      <div className="min-w-0">
        {title ? <p className="font-semibold">{title}</p> : null}
        {children ? <div className={title ? 'mt-0.5 opacity-90' : ''}>{children}</div> : null}
      </div>
    </div>
  );
}

/**
 * Result of a server action (FormState) as a floating toast.
 *
 * It used to render inline at the top of the form, which on a long form (and on
 * a phone) put the answer above the fold — you tap "Generate", nothing seems to
 * happen. A fixed toast is always visible wherever you are on the page.
 */
export function FormMessage({ state, className = '' }: { state: FormState; className?: string }) {
  const [visible, setVisible] = useState(true);

  useEffect(() => {
    setVisible(true);
    if (!state?.message) return;
    const t = setTimeout(() => setVisible(false), state.ok ? 6000 : 10000);
    return () => clearTimeout(t);
  }, [state]);

  if (!state?.message || !visible) return null;

  const tone = state.ok ? 'success' : 'danger';
  return (
    <div className={`pointer-events-none fixed inset-x-0 bottom-4 z-50 flex justify-center px-4 ${className}`}>
      <div className="pointer-events-auto w-full max-w-md animate-[fadeIn_.2s_ease-out]">
        <div className="relative shadow-card">
          <Alert tone={tone}>{state.message}</Alert>
          <button
            type="button"
            onClick={() => setVisible(false)}
            className="absolute right-2 top-2 rounded p-1 opacity-60 transition hover:opacity-100"
            aria-label="Dismiss"
          >
            <Icon name="close" className="h-3.5 w-3.5" />
          </button>
        </div>
      </div>
    </div>
  );
}

/* --------------------------------------------------------------- buttons */

export function SubmitButton({
  children,
  pendingLabel,
  className = 'btn-primary',
  disabled = false,
  confirm,
  title,
}: {
  children: React.ReactNode;
  pendingLabel?: string;
  className?: string;
  disabled?: boolean;
  confirm?: string;
  title?: string;
}) {
  const { pending } = useFormStatus();
  return (
    <button
      type="submit"
      className={className}
      disabled={pending || disabled}
      title={title}
      onClick={(e) => {
        if (confirm && !window.confirm(confirm)) {
          e.preventDefault();
        }
      }}
    >
      {pending ? (
        <>
          <span className="h-3.5 w-3.5 animate-spin rounded-full border-2 border-current border-t-transparent" />
          {pendingLabel ?? 'Working…'}
        </>
      ) : (
        children
      )}
    </button>
  );
}

/* ------------------------------------------------------------ copy button */

/**
 * Copies a string to the clipboard.
 *
 * The async Clipboard API only exists in a secure context, so a panel opened
 * over plain http (a phone on the LAN, a self-hosted box) silently did nothing
 * with it. The textarea fallback covers that case.
 */
export async function copyText(value: string): Promise<boolean> {
  try {
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(value);
      return true;
    }
  } catch {
    /* fall through to the selection trick */
  }
  try {
    const area = document.createElement('textarea');
    area.value = value;
    area.setAttribute('readonly', '');
    area.style.position = 'fixed';
    area.style.top = '-1000px';
    area.style.opacity = '0';
    document.body.appendChild(area);
    area.select();
    area.setSelectionRange(0, value.length);
    const ok = document.execCommand('copy');
    document.body.removeChild(area);
    return ok;
  } catch {
    return false;
  }
}

/**
 * "Copy" for anything the user has to paste somewhere else: a licence key, an
 * endpoint. Shows a tick for a moment instead of firing a toast, so copying a
 * key from a list does not cover the list.
 */
export function CopyButton({
  value,
  label = 'Copy',
  iconOnly = false,
  className = 'btn-ghost px-2 py-1 text-xs',
  title,
  align = 'center',
}: {
  value: string;
  label?: string;
  iconOnly?: boolean;
  className?: string;
  title?: string;
  align?: 'center' | 'left';
}) {
  const [result, setResult] = useState<'idle' | 'ok' | 'fail'>('idle');

  useEffect(() => {
    if (result === 'idle') return;
    const timer = setTimeout(() => setResult('idle'), 1600);
    return () => clearTimeout(timer);
  }, [result]);

  const text = result === 'ok' ? 'Copied' : result === 'fail' ? 'Press ⌘C' : label;
  const tone = result === 'ok' ? 'text-emerald-300' : result === 'fail' ? 'text-amber-300' : '';

  if (iconOnly) {
    return (
      <button
        type="button"
        onClick={() => copyText(value).then((ok) => setResult(ok ? 'ok' : 'fail'))}
        className={`inline-flex shrink-0 items-center justify-center rounded p-1 text-slate-500 transition hover:bg-ink-800 hover:text-slate-200 ${tone}`}
        title={result === 'fail' ? 'Copy failed — select the key and copy it' : title ?? label}
        aria-label={title ?? label}
      >
        <Icon name={result === 'ok' ? 'check' : 'copy'} className="h-3.5 w-3.5" />
      </button>
    );
  }

  return (
    <button
      type="button"
      onClick={() => copyText(value).then((ok) => setResult(ok ? 'ok' : 'fail'))}
      className={`${className} ${align === 'left' ? 'justify-start' : ''} ${tone}`}
      title={result === 'fail' ? 'Copy failed — select the key and copy it' : title ?? label}
    >
      <Icon name={result === 'ok' ? 'check' : 'copy'} />
      {text}
    </button>
  );
}

/* ---------------------------------------------------------------- inputs */

export function Field({
  label,
  name,
  hint,
  error,
  children,
  className = '',
}: {
  label: string;
  name?: string;
  hint?: string;
  error?: string;
  children: React.ReactNode;
  className?: string;
}) {
  return (
    <div className={className}>
      <label className="label" htmlFor={name}>
        {label}
      </label>
      {children}
      {hint && !error ? <p className="mt-1 text-xs text-slate-500">{hint}</p> : null}
      {error ? (
        <p className="mt-1 flex items-center gap-1 text-xs text-red-300">
          <Icon name="alert" className="h-3 w-3" />
          {error}
        </p>
      ) : null}
    </div>
  );
}
