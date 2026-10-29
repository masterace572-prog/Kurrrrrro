/** Shared constants + formatting mirroring the old PHP panel. */

export const GAMES = { PUBG: 'PUBG Mobile' } as const;

/** duration (hours) -> label / price per device, same table as Keys.php */
export const DURATIONS: Record<number, { label: string; price: number }> = {
  2: { label: '2 Hours', price: 10 },
  5: { label: '5 Hours', price: 20 },
  24: { label: '1 Day', price: 80 },
  72: { label: '3 Days', price: 150 },
  168: { label: '7 Days', price: 250 },
  336: { label: '14 Days', price: 350 },
  720: { label: '30 Days', price: 500 },
};

export const KEY_BATCH_SIZES: Record<string, number> = { '1': 6, '2': 11, '3': 51, '4': 101, '5': 2 };

export function priceFor(durationHours: number, maxDevices: number): number {
  const p = DURATIONS[durationHours]?.price ?? 0;
  return p * Math.max(1, maxDevices);
}

export function durationLabel(hours: number): string {
  return DURATIONS[hours]?.label ?? `${hours} Hours`;
}

/** PHP printed datetimes as 'YYYY-MM-DD HH:MM:SS' (UTC server tz). */
export function fmtUtc(d: Date | string | null | undefined): string | null {
  if (!d) return null;
  const date = typeof d === 'string' ? new Date(d) : d;
  if (Number.isNaN(date.getTime())) return null;
  const p = (n: number) => String(n).padStart(2, '0');
  return (
    `${date.getUTCFullYear()}-${p(date.getUTCMonth() + 1)}-${p(date.getUTCDate())} ` +
    `${p(date.getUTCHours())}:${p(date.getUTCMinutes())}:${p(date.getUTCSeconds())}`
  );
}

export function fmtLocal(d: Date | string | null | undefined): string {
  if (!d) return '—';
  const date = typeof d === 'string' ? new Date(d) : d;
  if (Number.isNaN(date.getTime())) return '—';
  return date.toISOString().replace('T', ' ').slice(0, 16) + ' UTC';
}

export function randomKey(): string {
  const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
  let out = '';
  for (let i = 0; i < 5; i++) out += chars[Math.floor(Math.random() * chars.length)];
  return out;
}

/** Mirrors setDevice(): strip junk, dedupe, cap at max_devices. */
export function normalizeDevices(raw: string | null, max: number): string {
  if (!raw) return '';
  const items = raw
    .split(/[\r\n,]+/)
    .map((s) => s.replace(/[^A-Za-z0-9]/g, '').trim())
    .filter(Boolean);
  return Array.from(new Set(items)).slice(0, Math.max(0, max)).join(',');
}

export function devicesOf(raw: string | null): string[] {
  return (raw || '').split(',').map((s) => s.trim()).filter(Boolean);
}

export function genReferralCode(): string {
  const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
  let out = '';
  for (let i = 0; i < 6; i++) out += chars[Math.floor(Math.random() * chars.length)];
  return out;
}
