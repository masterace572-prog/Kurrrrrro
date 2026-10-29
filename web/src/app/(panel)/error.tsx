'use client';

import { useEffect } from 'react';
import { Alert, Icon } from '@/components/ui';

export default function PanelError({ error, reset }: { error: Error & { digest?: string }; reset: () => void }) {
  useEffect(() => {
    console.error('[panel]', error);
  }, [error]);

  const isDb = /DATABASE_URL|ECONNREFUSED|ETIMEDOUT|ENOTFOUND|relation .* does not exist|password authentication/i.test(
    error.message
  );

  return (
    <div className="mx-auto max-w-xl py-10">
      <div className="card space-y-4">
        <div className="flex items-center gap-3">
          <span className="grid h-10 w-10 place-items-center rounded-xl bg-red-500/15 text-red-300">
            <Icon name="alert" className="h-5 w-5" />
          </span>
          <div>
            <h2 className="font-semibold text-slate-100">Something broke on this page</h2>
            <p className="text-sm text-slate-400">The rest of the panel still works.</p>
          </div>
        </div>

        {isDb ? (
          <Alert tone="warning" title="This looks like a database problem">
            Check <code>DATABASE_URL</code>, then visit <a className="underline" href="/api/health">/api/health</a>.
            If the tables are missing, run <code>npm run db:push</code>.
          </Alert>
        ) : null}

        <details className="rounded-lg bg-ink-850 p-3 text-xs text-slate-400">
          <summary className="cursor-pointer font-medium text-slate-300">Technical details</summary>
          <pre className="mt-2 whitespace-pre-wrap break-words">{error.message}</pre>
          {error.digest ? <p className="mt-1 text-slate-600">digest: {error.digest}</p> : null}
        </details>

        <div className="flex gap-2">
          <button onClick={reset} className="btn-primary">
            Try again
          </button>
          <a href="/dashboard" className="btn-ghost">
            Back to dashboard
          </a>
        </div>
      </div>
    </div>
  );
}
