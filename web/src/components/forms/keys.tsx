'use client';

import Link from 'next/link';
import { useActionState, useMemo, useState } from 'react';
import {
  deleteKeysAction,
  generateKeysAction,
  resetDevicesAction,
  revokeDeviceAction,
  toggleKeyStatusAction,
  updateKeyAction,
} from '@/app/actions';
import type { FormState } from '@/lib/result';
import { DURATIONS, GAMES, KEY_BATCH_SIZES } from '@/lib/domain';
import { Alert, Field, FormMessage, Icon, SubmitButton } from '@/components/ui';

/* --------------------------------------------------------------- generate */

export function GenerateForm({ saldo }: { saldo: number }) {
  const [state, action] = useActionState<FormState, FormData>(generateKeysAction, null);
  const [duration, setDuration] = useState(24);
  const [devices, setDevices] = useState(1);
  const [batch, setBatch] = useState('1');
  const [mode, setMode] = useState('auto');

  const count = KEY_BATCH_SIZES[batch] ?? 6;
  const keys = count - 1;
  const unit = DURATIONS[duration]?.price ?? 0;

  const cost = useMemo(() => unit * Math.max(1, devices) * keys, [unit, devices, keys]);
  const affordable = saldo - cost >= 0;
  const err = (f: string) => (state?.field === f ? state.message : undefined);

  return (
    <form action={action} className="space-y-5" noValidate>
      <FormMessage state={state} />

      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Game" name="game">
          <select id="game" name="game" className="input" defaultValue="PUBG">
            {Object.entries(GAMES).map(([id, label]) => (
              <option key={id} value={id}>
                {label}
              </option>
            ))}
          </select>
        </Field>

        <Field label="Duration" name="duration" hint={`₹${unit} per device`} error={err('duration')}>
          <select
            id="duration"
            name="duration"
            className="input"
            value={duration}
            onChange={(e) => setDuration(Number(e.target.value))}
          >
            {Object.entries(DURATIONS).map(([hours, d]) => (
              <option key={hours} value={hours}>
                {d.label} — ₹{d.price}/device
              </option>
            ))}
          </select>
        </Field>
      </div>

      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Max devices per key" name="max_devices" error={err('max_devices')}>
          <input
            id="max_devices"
            name="max_devices"
            type="number"
            min={1}
            max={50}
            className="input"
            value={devices}
            onChange={(e) => setDevices(Math.min(50, Math.max(1, Number(e.target.value) || 1)))}
            required
          />
        </Field>

        <Field label="How many keys" name="loopcount" error={err('loopcount')}>
          <select
            id="loopcount"
            name="loopcount"
            className="input"
            value={batch}
            onChange={(e) => setBatch(e.target.value)}
          >
            <option value="5">1 key</option>
            <option value="1">5 keys</option>
            <option value="2">10 keys</option>
            <option value="3">50 keys</option>
            <option value="4">100 keys</option>
          </select>
        </Field>
      </div>

      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Key naming" name="custominput">
          <select id="custominput" name="custominput" className="input" value={mode} onChange={(e) => setMode(e.target.value)}>
            <option value="auto">Automatic (username-duration-xxxxx)</option>
            <option value="custom">Custom name</option>
          </select>
        </Field>

        {mode === 'custom' ? (
          <Field
            label="Custom key"
            name="cuslicense"
            hint="4-20 chars, letters/numbers/-/_"
            error={err('cuslicense')}
          >
            <input
              id="cuslicense"
              name="cuslicense"
              className="input font-mono"
              maxLength={20}
              minLength={4}
              placeholder="MYCUSTOMKEY"
              required
            />
          </Field>
        ) : (
          <div className="hidden sm:block" aria-hidden="true" />
        )}
      </div>

      <div
        className={`rounded-xl border p-4 ${affordable ? 'border-white/5 bg-ink-850' : 'border-red-500/30 bg-red-500/5'}`}
      >
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <p className="text-[10px] uppercase tracking-wider text-slate-500">Cost for this batch</p>
            <p className="text-2xl font-black text-slate-100">
              {cost.toLocaleString()} <span className="text-sm font-medium text-slate-500">credits</span>
            </p>
            <p className="mt-0.5 text-xs text-slate-500">
              {keys} key{keys > 1 ? 's' : ''} × {Math.max(1, devices)} device{devices > 1 ? 's' : ''} × ₹{unit}
            </p>
          </div>
          <div className="text-right">
            <p className="text-[10px] uppercase tracking-wider text-slate-500">Your balance</p>
            <p className={`text-lg font-bold ${affordable ? 'text-emerald-400' : 'text-red-400'}`}>
              {saldo.toLocaleString()}
            </p>
            <p className="text-xs text-slate-500">
              {affordable ? `after: ${(saldo - cost).toLocaleString()}` : `short by ${(cost - saldo).toLocaleString()}`}
            </p>
          </div>
        </div>
        {!affordable ? (
          <p className="mt-3 flex items-center gap-1.5 text-xs text-red-300">
            <Icon name="warning" className="h-3.5 w-3.5" /> Not enough balance — ask your admin to top up.
          </p>
        ) : null}
      </div>

      <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
        <SubmitButton className="btn-primary w-full sm:w-auto" pendingLabel="Generating…" disabled={!affordable}>
          <Icon name="plus" /> Generate keys
        </SubmitButton>
        <p className="text-xs text-slate-500">
          The new keys open on the <span className="text-slate-300">Keys</span> page, highlighted as
          <span className="mx-1 rounded bg-brand-500/20 px-1 text-[10px] font-bold uppercase text-brand-400">new</span>.
        </p>
      </div>
    </form>
  );
}

/* ------------------------------------------------------------- row actions */

export function ResetHwidButton({ userKey, disabled }: { userKey: string; disabled?: boolean }) {
  const [state, action] = useActionState<FormState, FormData>(resetDevicesAction, null);
  return (
    <form action={action} className="inline">
      <input type="hidden" name="user_key" value={userKey} />
      <SubmitButton
        className="btn-ghost px-2 py-1 text-xs"
        pendingLabel="…"
        disabled={disabled}
        confirm={`Unbind every device from ${userKey}? They will be re-bound on next login.`}
        title={disabled ? 'No devices are bound yet' : 'Unbind all devices'}
      >
        <Icon name="refresh" className="h-3.5 w-3.5" /> HWID
      </SubmitButton>
      {state && !state.ok ? <span className="sr-only">{state.message}</span> : null}
    </form>
  );
}

export function ToggleStatusButton({ userKey, status }: { userKey: string; status: number }) {
  const [, action] = useActionState<FormState, FormData>(toggleKeyStatusAction, null);
  return (
    <form action={action} className="inline">
      <input type="hidden" name="user_key" value={userKey} />
      <SubmitButton
        className="btn-ghost px-2 py-1 text-xs"
        pendingLabel="…"
        title={status ? 'Block this key' : 'Unblock this key'}
      >
        <Icon name={status ? 'close' : 'check'} className="h-3.5 w-3.5" />
        {status ? 'Block' : 'Unblock'}
      </SubmitButton>
    </form>
  );
}

export function DeleteKeyButton({ userKey }: { userKey: string }) {
  const [, action] = useActionState<FormState, FormData>(deleteKeysAction, null);
  return (
    <form action={action} className="inline">
      <input type="hidden" name="scope" value="one" />
      <input type="hidden" name="user_key" value={userKey} />
      <SubmitButton
        className="btn-danger px-2 py-1 text-xs"
        pendingLabel="…"
        confirm={`Delete ${userKey} permanently?`}
      >
        <Icon name="trash" className="h-3.5 w-3.5" />
      </SubmitButton>
    </form>
  );
}

export function BulkDeleteButton({ scope, label }: { scope: 'expired' | 'unused'; label: string }) {
  const [state, action] = useActionState<FormState, FormData>(deleteKeysAction, null);
  return (
    <form action={action} className="inline">
      <input type="hidden" name="scope" value={scope} />
      <SubmitButton
        className="btn-ghost px-3 py-1.5 text-xs"
        pendingLabel="Deleting…"
        confirm={`${label} — this cannot be undone. Continue?`}
      >
        <Icon name="trash" className="h-3.5 w-3.5" /> {label}
      </SubmitButton>
      {state && !state.ok ? <span className="ml-2 text-xs text-red-300">{state.message}</span> : null}
    </form>
  );
}

/* ---------------------------------------------------------------- edit key */

export function EditKeyForm({
  initial,
  owner,
}: {
  initial: {
    id_keys: number;
    game: string;
    user_key: string;
    duration: number;
    expired_date: string;
    max_devices: number;
    devices: string[];
    status: number;
    registrator: string | null;
  };
  owner: boolean;
}) {
  const [state, action] = useActionState<FormState, FormData>(updateKeyAction, null);
  const err = (f: string) => (state?.field === f ? state.message : undefined);

  return (
    <form action={action} className="space-y-5" noValidate>
      <input type="hidden" name="id_keys" value={initial.id_keys} />
      <FormMessage state={state} />

      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Key" name="user_key" error={err('user_key')}>
          <input
            id="user_key"
            name="user_key"
            defaultValue={initial.user_key}
            className="input font-mono"
            readOnly={!owner}
            required
          />
        </Field>
        <Field label="Status" name="status">
          <select id="status" name="status" defaultValue={String(initial.status)} className="input">
            <option value="1">Active — clients can log in</option>
            <option value="0">Blocked — rejected with “USER BLOCKED”</option>
          </select>
        </Field>
      </div>

      {owner ? (
        <>
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Game" name="game">
              <select id="game" name="game" defaultValue={initial.game} className="input">
                {Object.keys(GAMES).map((g) => (
                  <option key={g} value={g}>
                    {g}
                  </option>
                ))}
              </select>
            </Field>
            <Field label="Registrator (reseller)" name="registrator">
              <input id="registrator" name="registrator" defaultValue={initial.registrator ?? ''} className="input" />
            </Field>
          </div>

          <div className="grid gap-4 sm:grid-cols-3">
            <Field label="Duration (hours)" name="duration" hint="2 = 2h, 24 = 1 day, 720 = 30 days">
              <input id="duration" name="duration" type="number" min={1} defaultValue={initial.duration} className="input" />
            </Field>
            <Field label="Max devices" name="max_devices">
              <input id="max_devices" name="max_devices" type="number" min={1} max={50} defaultValue={initial.max_devices} className="input" />
            </Field>
            <Field
              label="Expires (UTC)"
              name="expired_date"
              hint="empty = starts on first login"
              error={err('expired_date')}
            >
              <input
                id="expired_date"
                name="expired_date"
                defaultValue={initial.expired_date}
                placeholder="2026-12-31 23:59:59"
                className="input font-mono text-xs"
              />
            </Field>
          </div>

          <Field label="Bound devices" name="devices" hint="One per line. Editing replaces the whole list.">
            <textarea
              id="devices"
              name="devices"
              rows={Math.min(8, Math.max(3, initial.devices.length + 1))}
              defaultValue={initial.devices.join('\n')}
              className="input font-mono text-xs"
              spellCheck={false}
            />
          </Field>
        </>
      ) : (
        <Alert tone="info">As a reseller you can only block or unblock this initial.</Alert>
      )}

      <div className="flex flex-wrap gap-2">
        <SubmitButton className="btn-primary" pendingLabel="Saving…">
          Save changes
        </SubmitButton>
        <Link href="/keys" className="btn-ghost">
          Back to keys
        </Link>
      </div>
    </form>
  );
}

export function RevokeDeviceButton({ userKey, device }: { userKey: string; device: string }) {
  const [, action] = useActionState<FormState, FormData>(revokeDeviceAction, null);
  return (
    <form action={action} className="inline">
      <input type="hidden" name="user_key" value={userKey} />
      <input type="hidden" name="device" value={device} />
      <SubmitButton className="btn-ghost px-2 py-0.5 text-[11px]" pendingLabel="…" confirm={`Unbind ${device}?`}>
        <Icon name="close" className="h-3 w-3" />
      </SubmitButton>
    </form>
  );
}
