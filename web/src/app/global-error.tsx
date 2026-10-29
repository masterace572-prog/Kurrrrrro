'use client';

export default function GlobalError({ error, reset }: { error: Error & { digest?: string }; reset: () => void }) {
  return (
    <html lang="en">
      <body style={{ background: '#08090d', color: '#e2e8f0', fontFamily: 'system-ui, sans-serif', margin: 0 }}>
        <main style={{ minHeight: '100vh', display: 'grid', placeItems: 'center', padding: 24 }}>
          <div
            style={{
              maxWidth: 460,
              border: '1px solid rgba(255,255,255,.07)',
              background: '#0d0f14',
              borderRadius: 16,
              padding: 24,
            }}
          >
            <h1 style={{ margin: 0, fontSize: 18 }}>KURO PANEL is down</h1>
            <p style={{ color: '#94a3b8', fontSize: 14 }}>
              A fatal error stopped the app from rendering. Most often this means <code>DATABASE_URL</code> or{' '}
              <code>AUTH_SECRET</code> is missing in the deployment environment.
            </p>
            <pre style={{ fontSize: 11, color: '#94a3b8', overflowX: 'auto' }}>{error.message}</pre>
            <button
              onClick={reset}
              style={{
                marginTop: 12,
                background: '#ff3355',
                color: '#fff',
                border: 0,
                borderRadius: 8,
                padding: '8px 14px',
                fontWeight: 600,
                cursor: 'pointer',
              }}
            >
              Try again
            </button>
          </div>
        </main>
      </body>
    </html>
  );
}
