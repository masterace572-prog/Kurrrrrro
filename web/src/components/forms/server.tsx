'use client';

import { useActionState, useState } from 'react';
import {
  bumpVersionAction,
  deleteReleaseAction,
  saveServerAction,
  setCurrentReleaseAction,
  uploadLibAction,
} from '@/app/actions';
import type { FormState } from '@/lib/result';
import { Alert, Field, FormMessage, Icon, SubmitButton } from '@/components/ui';

/* --------------------------------------------------------------- mod name */

export function ModNameForm({ modname }: { modname: string }) {
  const [state, action] = useActionState<FormState, FormData>(saveServerAction, null);
  return (
    <form action={action} className="card space-y-4">
      <input type="hidden" name="section" value="modname" />
      <SectionTitle icon="cog" title="Mod name" hint="Shown in the loader as the active mod title." />
      <FormMessage state={state} />
      <Field label="Mod name" name="modname" error={state?.field === 'modname' ? state.message : undefined}>
        <input id="modname" name="modname" defaultValue={modname} className="input" maxLength={100} required />
      </Field>
      <SubmitButton className="btn-primary" pendingLabel="Saving…">
        Save mod name
      </SubmitButton>
    </form>
  );
}

/* ------------------------------------------------------------ maintenance */

export function MaintenanceForm({ status, message }: { status: string; message: string }) {
  const [state, action] = useActionState<FormState, FormData>(saveServerAction, null);
  const [on, setOn] = useState(status === 'on');
  const err = (f: string) => (state?.field === f ? state.message : undefined);

  return (
    <form action={action} className="card space-y-4">
      <input type="hidden" name="section" value="maintenance" />
      <SectionTitle
        icon="warning"
        title="Maintenance mode"
        hint="When on, every loader check is rejected and your message is returned as the reason."
      />
      <FormMessage state={state} />

      <div
        className={`flex items-center justify-between rounded-xl border px-4 py-3 transition ${
          on ? 'border-red-500/30 bg-red-500/10' : 'border-line bg-ink-850'
        }`}
      >
        <div>
          <p className="text-sm font-semibold text-slate-100">{on ? 'Maintenance is ON' : 'Panel is live'}</p>
          <p className="text-xs text-slate-400">
            {on ? 'All logins are blocked right now.' : 'Clients can log in normally.'}
          </p>
        </div>
        <label className="relative inline-flex cursor-pointer items-center">
          <input
            type="checkbox"
            name="status"
            value="on"
            defaultChecked={on}
            onChange={(e) => setOn(e.target.checked)}
            className="peer sr-only"
          />
          <span className="h-6 w-11 rounded-full bg-ink-600 transition peer-checked:bg-brand-500" />
          <span className="absolute left-0.5 h-5 w-5 rounded-full bg-white transition peer-checked:translate-x-5" />
        </label>
      </div>

      <Field
        label="Message shown to users"
        name="myinput"
        hint="Required while maintenance is on."
        error={err('myinput')}
      >
        <input
          id="myinput"
          name="myinput"
          defaultValue={message}
          placeholder="Panel is updating, back in 10 minutes"
          className="input"
          maxLength={500}
        />
      </Field>

      <SubmitButton className={on ? 'btn-danger' : 'btn-primary'} pendingLabel="Saving…">
        {on ? 'Enable maintenance' : 'Disable maintenance'}
      </SubmitButton>
    </form>
  );
}

/* ----------------------------------------------------------------- credit */

export function CreditForm({ status, credit }: { status: string; credit: string }) {
  const [state, action] = useActionState<FormState, FormData>(saveServerAction, null);
  const err = (f: string) => (state?.field === f ? state.message : undefined);

  return (
    <form action={action} className="card space-y-4">
      <input type="hidden" name="section" value="credit" />
      <SectionTitle icon="shield" title="Loader text" hint="The `mod_status` and `credit` fields returned to the client." />
      <FormMessage state={state} />
      <Field label="Mod status" name="mod_status" hint='e.g. "Safe" — some loaders colour this green.'>
        <input id="mod_status" name="mod_status" defaultValue={status} className="input" maxLength={100} />
      </Field>
      <Field label="Credit line" name="credit" error={err('credit')}>
        <input id="credit" name="credit" defaultValue={credit} className="input" maxLength={100} required />
      </Field>
      <SubmitButton className="btn-primary" pendingLabel="Saving…">
        Save text
      </SubmitButton>
    </form>
  );
}

/* -------------------------------------------------------------- lib upload */

export function LibUploadForm({ maxMb }: { maxMb: number }) {
  const [state, action] = useActionState<FormState, FormData>(uploadLibAction, null);
  const [fileName, setFileName] = useState('');
  const [size, setSize] = useState(0);
  const [mode, setMode] = useState<'file' | 'url'>('file');
  const err = (f: string) => (state?.field === f ? state.message : undefined);
  const tooBig = size > maxMb * 1024 * 1024;

  return (
    <form action={action} className="card space-y-4">
      <SectionTitle
        icon="upload"
        title="Publish a lib update"
        hint="The loader downloads this only when the version below differs from the one it already has."
      />
      <FormMessage state={state} />

      <Field
        label="Version"
        name="version"
        hint="Anything that changes between builds: 1.4, 1.5, 2026.10.29 …"
        error={err('version')}
      >
        <input id="version" name="version" className="input font-mono" placeholder="1.4" maxLength={32} required />
      </Field>

      <div className="flex gap-2 rounded-lg border border-line bg-ink-850 p-1 text-xs">
        {(['file', 'url'] as const).map((m) => (
          <button
            key={m}
            type="button"
            onClick={() => setMode(m)}
            className={`flex-1 rounded-md px-3 py-1.5 font-semibold transition ${
              mode === m ? 'bg-brand-500 text-ink-950' : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            {m === 'file' ? `Upload zip (≤ ${maxMb} MB)` : 'External URL'}
          </button>
        ))}
      </div>

      {mode === 'file' ? (
        <div>
          <label
            htmlFor="libfile"
            className={`flex cursor-pointer flex-col items-center justify-center gap-1.5 rounded-xl border border-dashed px-4 py-8 text-center transition ${
              tooBig ? 'border-red-500/30 bg-red-500/10/5' : 'border-ink-600 bg-ink-850 hover:border-brand-500/50'
            }`}
          >
            <Icon name="upload" className="h-6 w-6 text-slate-500" />
            <span className="text-sm font-medium text-slate-200">
              {fileName || 'Click to choose the lib zip'}
            </span>
            <span className="text-xs text-slate-500">
              {fileName ? `${(size / 1024).toFixed(0)} KB` : 'must be a .zip containing the lib files'}
            </span>
            <input
              id="libfile"
              name="libfile"
              type="file"
              accept=".zip,application/zip,application/x-zip-compressed"
              className="sr-only"
              onChange={(e) => {
                const f = e.target.files?.[0];
                setFileName(f?.name ?? '');
                setSize(f?.size ?? 0);
              }}
            />
          </label>
          {tooBig ? (
            <p className="mt-2 text-xs text-red-300">
              That file is {(size / 1024 / 1024).toFixed(1)} MB — over the {maxMb} MB serverless limit. Use the external
              URL tab and host it on GitHub Releases instead.
            </p>
          ) : null}
          {err('libfile') ? <p className="mt-2 text-xs text-red-300">{err('libfile')}</p> : null}
        </div>
      ) : (
        <Field label="Download URL" name="external_url" error={err('external_url')} hint="GitHub Releases, Supabase Storage, any direct file link.">
          <input
            id="external_url"
            name="external_url"
            className="input"
            placeholder="https://github.com/you/repo/releases/download/v1.4/lib.zip"
            type="url"
          />
        </Field>
      )}

      <Field label="Release notes (optional)" name="notes">
        <textarea id="notes" name="notes" rows={2} className="input" maxLength={500} placeholder="What changed in this build?" />
      </Field>

      <SubmitButton className="btn-primary" pendingLabel="Publishing…" disabled={tooBig}>
        <Icon name="upload" /> Publish update
      </SubmitButton>
    </form>
  );
}

/* --------------------------------------------------------------- bump only */

export function BumpVersionForm({ current }: { current: string | null }) {
  const [state, action] = useActionState<FormState, FormData>(bumpVersionAction, null);
  return (
    <form action={action} className="card space-y-4">
      <input type="hidden" name="section" value="bump" />
      <SectionTitle
        icon="refresh"
        title="Push an update without re-uploading"
        hint="Only the version changes — clients that already have an older copy download the current one once."
      />
      <FormMessage state={state} />
      {current ? (
        <Alert tone="info">
          Current live version is <span className="font-mono font-semibold">{current}</span>. Set something different to
          force a re-download.
        </Alert>
      ) : (
        <Alert tone="warning">No release published yet — upload a zip first.</Alert>
      )}
      <Field label="New version" name="version" error={state?.field === 'version' ? state.message : undefined}>
        <input id="version" name="version" className="input font-mono" placeholder="1.5" maxLength={32} required disabled={!current} />
      </Field>
      <SubmitButton className="btn-primary" pendingLabel="Pushing…" disabled={!current}>
        <Icon name="refresh" /> Bump version
      </SubmitButton>
    </form>
  );
}

/* ---------------------------------------------------------- release actions */

export function ReleaseActions({ id, isCurrent }: { id: number; isCurrent: boolean }) {
  const [stateSet, setAction] = useActionState<FormState, FormData>(setCurrentReleaseAction, null);
  const [stateDel, delAction] = useActionState<FormState, FormData>(deleteReleaseAction, null);
  const message = stateSet?.message ?? stateDel?.message;

  return (
    <div className="flex items-center justify-end gap-1.5">
      {!isCurrent ? (
        <>
          <form action={setAction} className="inline">
            <input type="hidden" name="id" value={id} />
            <SubmitButton className="btn-ghost px-2 py-1 text-xs" pendingLabel="…" confirm="Roll back clients to this build?">
              <Icon name="refresh" className="h-3.5 w-3.5" /> Use
            </SubmitButton>
          </form>
          <form action={delAction} className="inline">
            <input type="hidden" name="id" value={id} />
            <SubmitButton className="btn-danger px-2 py-1 text-xs" pendingLabel="…" confirm="Delete this release from history?">
              <Icon name="trash" className="h-3.5 w-3.5" />
            </SubmitButton>
          </form>
        </>
      ) : (
        <span className="text-[11px] uppercase tracking-wider text-emerald-300">live</span>
      )}
      {message && !(stateSet?.ok ?? stateDel?.ok) ? (
        <span className="text-xs text-red-300">{message}</span>
      ) : null}
    </div>
  );
}

/* ------------------------------------------------------------------ misc */

export function CopyField({ value, label }: { value: string; label: string }) {
  const [copied, setCopied] = useState(false);
  return (
    <div>
      <p className="label">{label}</p>
      <div className="flex items-center gap-2 rounded-lg border border-ink-600 bg-ink-850 px-3 py-2">
        <code className="min-w-0 flex-1 truncate text-xs text-emerald-300">{value}</code>
        <button
          type="button"
          className="rounded p-1 text-slate-400 transition hover:text-slate-100"
          onClick={async () => {
            try {
              await navigator.clipboard.writeText(value);
              setCopied(true);
              setTimeout(() => setCopied(false), 1500);
            } catch {
              /* clipboard blocked */
            }
          }}
          aria-label={`Copy ${label}`}
        >
          <Icon name={copied ? 'check' : 'copy'} className="h-4 w-4" />
        </button>
      </div>
    </div>
  );
}

function SectionTitle({ icon, title, hint }: { icon: 'cog' | 'warning' | 'shield' | 'grid' | 'upload' | 'refresh'; title: string; hint: string }) {
  return (
    <div className="flex items-start gap-2.5">
      <span className="mt-0.5 grid h-8 w-8 shrink-0 place-items-center rounded-lg bg-ink-800 text-brand-400">
        <Icon name={icon} className="h-4 w-4" />
      </span>
      <div>
        <h2 className="font-semibold text-slate-100">{title}</h2>
        <p className="text-xs leading-relaxed text-slate-500">{hint}</p>
      </div>
    </div>
  );
}
