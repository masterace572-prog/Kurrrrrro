import { createHash } from 'node:crypto';
import { NextResponse } from 'next/server';
import { q1 } from '@/lib/db';
import { getCurrentRelease } from '@/lib/release';

export const dynamic = 'force-dynamic';
export const runtime = 'nodejs';

/**
 * GET /api/download   → the current lib zip (what Downtwo.Link() points at)
 * GET /api/download?all=1 → the whole release history (zip of zips is overkill;
 *   this is only used for the "download latest by id" case)
 *
 * If the release was published as an external URL (GitHub Releases, Supabase
 * Storage, …) we redirect there instead of proxying bytes through the lambda.
 */
export async function GET(req: Request) {
  try {
    const rel = await getCurrentRelease();

    if (!rel) {
      return NextResponse.json(
        { status: false, reason: 'No lib published yet. Upload one in the panel under Server → Client updates.' },
        { status: 404, headers: { 'Cache-Control': 'no-store' } }
      );
    }

    if (rel.external_url) {
      return NextResponse.redirect(rel.external_url, {
        status: 302,
        headers: { 'Cache-Control': 'no-store' },
      });
    }

    const row = await q1<{ content: Buffer | null; file_name: string; file_size: number; version: string }>(
      `SELECT content, file_name, file_size, version FROM client_release WHERE id = $1`,
      [rel.id]
    );

    if (!row?.content || row.content.byteLength === 0) {
      return NextResponse.json(
        { status: false, reason: 'The current release has no zip attached.' },
        { status: 404, headers: { 'Cache-Control': 'no-store' } }
      );
    }

    const body = new Uint8Array(row.content);
    return new NextResponse(body, {
      status: 200,
      headers: {
        'Content-Type': 'application/zip',
        'Content-Length': String(body.byteLength),
        'Content-Disposition': `attachment; filename="${row.file_name || `lib-${row.version}.zip`}"`,
        'Cache-Control': 'no-store',
        'Access-Control-Allow-Origin': '*',
        'X-Lib-Version': row.version,
        ETag: `"${createHash('sha256').update(body).digest('hex').slice(0, 32)}"`,
      },
    });
  } catch (e) {
    console.error('[api/download]', e);
    return NextResponse.json(
      { status: false, reason: 'SERVER ERROR' },
      { status: 500, headers: { 'Cache-Control': 'no-store' } }
    );
  }
}

export async function OPTIONS() {
  return new NextResponse(null, {
    status: 204,
    headers: { 'Access-Control-Allow-Origin': '*', 'Access-Control-Allow-Methods': 'GET, OPTIONS' },
  });
}
