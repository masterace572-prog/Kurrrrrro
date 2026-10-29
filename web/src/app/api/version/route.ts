import { NextResponse } from 'next/server';
import { getCurrentRelease, getVersionString, FALLBACK_VERSION } from '@/lib/release';

export const dynamic = 'force-dynamic';
export const runtime = 'nodejs';

const PLAIN = {
  'Content-Type': 'text/plain; charset=utf-8',
  // the loader reads the first line only; never let a CDN serve a stale value
  'Cache-Control': 'no-store, no-cache, must-revalidate, max-age=0',
  'Access-Control-Allow-Origin': '*',
};

/**
 * GET /api/version
 *
 *   → 1.4            (plain text, first line, what Downtwo.getServerVersion() reads)
 *
 * `?format=json` returns details for debugging.
 */
export async function GET(req: Request) {
  const url = new URL(req.url);
  const wantsJson = url.searchParams.get('format') === 'json';

  try {
    if (wantsJson) {
      const rel = await getCurrentRelease();
      return NextResponse.json(
        {
          version: rel?.version ?? FALLBACK_VERSION,
          file_name: rel?.file_name ?? null,
          file_size: rel?.file_size ?? 0,
          sha256: rel?.sha256 ?? null,
          external: Boolean(rel?.external_url),
          uploaded_at: rel?.created_at ?? null,
          download: '/api/download',
        },
        { headers: { 'Cache-Control': 'no-store', 'Access-Control-Allow-Origin': '*' } }
      );
    }

    const version = await getVersionString();
    return new NextResponse(`${version}\n`, { headers: PLAIN });
  } catch (e) {
    // Never fail the update check loudly: fall back so clients keep working.
    console.error('[api/version]', e);
    return new NextResponse(`${FALLBACK_VERSION}\n`, { headers: PLAIN });
  }
}

export async function OPTIONS() {
  return new NextResponse(null, {
    status: 204,
    headers: { 'Access-Control-Allow-Origin': '*', 'Access-Control-Allow-Methods': 'GET, OPTIONS' },
  });
}
