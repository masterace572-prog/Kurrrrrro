import Link from 'next/link';
import { redirect } from 'next/navigation';
import PageHeader from '@/components/PageHeader';
import { currentUser, isStaff } from '@/lib/session';
import {
  BumpVersionForm,
  CopyField,
  LibUploadForm,
  ReleaseActions,
} from '@/components/forms/server';
import { Alert, Icon } from '@/components/ui';
import { fmtLocal } from '@/lib/domain';
import { getCurrentRelease, humanSize, listReleases, MAX_UPLOAD_BYTES } from '@/lib/release';

export const metadata = { title: 'Lib updates · KURO PANEL' };
export const dynamic = 'force-dynamic';

export default async function ReleasesPage() {
  const me = await currentUser();
  if (!isStaff(me)) redirect('/dashboard?m=Access+denied.&t=danger');

  const [current, releases] = await Promise.all([getCurrentRelease(), listReleases(25)]);
  const base = process.env.PUBLIC_BASE_URL || 'https://<your-vercel-app>';
  const maxMb = Math.round(MAX_UPLOAD_BYTES / 1024 / 1024);

  return (
    <>
      <PageHeader
        title="Client lib updates"
        subtitle="Same version number = clients keep their local lib"
        breadcrumb={[
          { label: 'Dashboard', href: '/dashboard' },
          { label: 'Server control', href: '/settings/server' },
          { label: 'Lib updates' },
        ]}
        action={
          <Link href="/settings/server" className="btn-ghost">
            <Icon name="chevron" className="h-3.5 w-3.5 rotate-180" /> Back
          </Link>
        }
      />

      <div className="mb-4 grid grid-cols-3 gap-2 sm:gap-3">
        <div className="card px-3 py-2.5 sm:p-5">
          <p className="text-[10px] uppercase tracking-wider text-slate-500 sm:text-xs">Live version</p>
          <p className="mt-1 text-lg font-black leading-none text-slate-100 sm:text-2xl">
            {current ? `v${current.version}` : '—'}
          </p>
        </div>
        <div className="card px-3 py-2.5 sm:p-5">
          <p className="text-[10px] uppercase tracking-wider text-slate-500 sm:text-xs">Payload</p>
          <p className="mt-1 break-words text-xs font-semibold leading-snug text-slate-200 sm:text-sm">
            {current
              ? current.external_url
                ? 'external URL'
                : current.has_content
                  ? humanSize(current.file_size)
                  : 'no file'
              : '—'}
          </p>
          <p className="truncate text-[10px] text-slate-500 sm:text-[11px]">{current?.file_name ?? ''}</p>
        </div>
        <div className="card px-3 py-2.5 sm:p-5">
          <p className="text-[10px] uppercase tracking-wider text-slate-500 sm:text-xs">Published</p>
          <p className="mt-1 break-words text-xs font-semibold leading-snug text-slate-200 sm:text-sm">
            {current ? fmtLocal(current.created_at) : '—'}
          </p>
          <p className="truncate text-[10px] text-slate-500 sm:text-[11px]">by {current?.uploaded_by ?? '—'}</p>
        </div>
      </div>

      {!current ? (
        <Alert tone="warning" className="mb-4" title="Nothing published here yet">
          Loaders are using the GitHub mirror (<code>Version.txt</code> + the <code>Lib</code> release).
          Publish here to take over — the version you type and the bytes you upload stay together.
        </Alert>
      ) : null}

      <div className="grid grid-cols-1 gap-4 xl:grid-cols-2">
        <LibUploadForm maxMb={maxMb} />
        <div className="space-y-4">
          <BumpVersionForm current={current?.version ?? null} />

          <div className="card space-y-3">
            <div className="flex items-center gap-2">
              <Icon name="download" className="h-4 w-4 text-brand-400" />
              <h2 className="font-semibold text-slate-200">What the loader requests</h2>
            </div>
            <CopyField label="GET /api/version → plain text" value={`${base}/api/version`} />
            <CopyField label="GET /api/download → the zip" value={`${base}/api/download`} />
            <ol className="list-inside list-decimal space-y-1 text-xs text-slate-400">
              <li>On launch the loader reads the first line of <code>/api/version</code>.</li>
              <li>It compares that with the version it stored after the last successful download.</li>
              <li>Identical → nothing is fetched, the cached lib in <code>files/loader/</code> is used.</li>
              <li>Different → it downloads <code>/api/download</code> once, extracts, and stores the new version.</li>
            </ol>
            <p className="text-xs text-slate-500">
              Because of that, <strong>bumping the version is what pushes an update</strong> — you can re-upload the same
              zip or just change the number.
            </p>
          </div>
        </div>
      </div>

      <div className="card mt-4 min-w-0 p-0">
        <div className="px-4 py-4 sm:px-5">
          <h2 className="font-semibold text-slate-200">Release history</h2>
          <p className="mt-0.5 text-xs text-slate-500">“Use” sends every client back to that build.</p>
        </div>

        {/* phones: one block per release */}
        <div className="space-y-2 px-4 pb-4 sm:hidden">
          {releases.length === 0 ? (
            <p className="py-6 text-center text-sm text-slate-500">No releases yet.</p>
          ) : (
            releases.map((r) => (
              <div key={r.id} className="rounded-xl border border-line bg-ink-850 p-3.5">
                <div className="flex items-center justify-between gap-2">
                  <span className="font-mono text-sm font-semibold text-slate-100">v{r.version}</span>
                  {r.is_current ? <span className="badge bg-emerald-500/10 text-emerald-300">live</span> : null}
                </div>
                <p className="mt-1 truncate font-mono text-[11px] text-slate-500">{r.file_name}</p>
                <p className="mt-1 text-[11px] text-slate-500">
                  {r.external_url ? 'external' : r.has_content ? humanSize(r.file_size) : 'no file'} ·{' '}
                  {fmtLocal(r.created_at)}
                </p>
                <div className="mt-2.5">
                  <ReleaseActions id={r.id} isCurrent={Boolean(r.is_current)} />
                </div>
              </div>
            ))
          )}
        </div>

        <div className="hidden overflow-x-auto sm:block">
        <table className="table">
          <thead>
            <tr>
              <th className="pl-5">Version</th>
              <th>File</th>
              <th>Size</th>
              <th>Source</th>
              <th>Notes</th>
              <th>Published</th>
              <th className="pr-5 text-right">Actions</th>
            </tr>
          </thead>
          <tbody>
            {releases.length === 0 ? (
              <tr>
                <td colSpan={7} className="py-10 text-center text-sm text-slate-500">
                  No releases yet.
                </td>
              </tr>
            ) : (
              releases.map((r) => (
                <tr key={r.id} className={r.is_current ? 'bg-emerald-500/10/[0.04]' : ''}>
                  <td className="pl-5">
                    <span className="font-mono text-sm font-semibold text-slate-100">v{r.version}</span>
                    {r.is_current ? <span className="ml-2 badge bg-emerald-500/10 text-emerald-300">live</span> : null}
                  </td>
                  <td className="font-mono text-[11px] text-slate-400">{r.file_name}</td>
                  <td className="text-slate-400">{r.external_url ? '—' : humanSize(r.file_size)}</td>
                  <td className="text-slate-400">{r.external_url ? 'external' : r.has_content ? 'stored' : 'none'}</td>
                  <td className="max-w-[14rem] truncate text-xs text-slate-500" title={r.notes ?? ''}>
                    {r.notes || '—'}
                  </td>
                  <td className="whitespace-nowrap text-xs text-slate-500">{fmtLocal(r.created_at)}</td>
                  <td className="pr-5">
                    <ReleaseActions id={r.id} isCurrent={Boolean(r.is_current)} />
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
        </div>
      </div>
    </>
  );
}
