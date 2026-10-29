import { NextResponse } from 'next/server';
import { checkLicense, panelInfo } from '@/lib/gate';

export const dynamic = 'force-dynamic';
export const runtime = 'nodejs';

const CORS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'Content-Type, Accept',
  'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
};

/** GET /server — public health/info payload (what Connect::index used to print). */
export async function GET() {
  try {
    const info = await panelInfo();
    return NextResponse.json(info, { headers: CORS });
  } catch (e) {
    return NextResponse.json(
      { status: false, reason: 'SERVER ERROR', detail: (e as Error).message },
      { status: 200, headers: CORS }
    );
  }
}

/**
 * POST /server — the endpoint baked into the XLOX loader.
 *
 * The loader sends (form-urlencoded):
 *   game=PUBG&user_key=<key>&serial=<device uuid>
 *
 * and expects:
 *   {"status":true,"data":{"token":"<md5>","rng":<unix ts>,"EXP":"...","modname":..,..}}
 *
 * It verifies token === md5(`PUBG-<user_key>-<serial>-<LICENSE_SALT>`) and
 * requires rng + 30 > now(). Both are handled in lib/gate.ts.
 */
export async function POST(req: Request) {
  let payload: Record<string, string> = {};

  try {
    const ct = (req.headers.get('content-type') || '').toLowerCase();
    if (ct.includes('application/json')) {
      const body = (await req.json()) as Record<string, unknown>;
      payload = Object.fromEntries(Object.entries(body).map(([k, v]) => [k, String(v ?? '')]));
    } else {
      const form = await req.formData();
      for (const [k, v] of form.entries()) payload[k] = typeof v === 'string' ? v : '';
    }
  } catch {
    return NextResponse.json({ status: false, reason: 'Bad Parameter' }, { headers: CORS });
  }

  // Tolerate a few aliases so older/newer clients keep working.
  const input = {
    game: payload.game || payload.Game || '',
    user_key: payload.user_key || payload.userkey || payload.key || '',
    serial: payload.serial || payload.device || payload.hwid || '',
  };

  try {
    const result = await checkLicense(input);
    return NextResponse.json(result, { headers: CORS, status: 200 });
  } catch (e) {
    console.error('[server] license check failed', e);
    return NextResponse.json(
      { status: false, reason: 'SERVER ERROR' },
      { status: 200, headers: CORS }
    );
  }
}

export async function OPTIONS() {
  return new NextResponse(null, { status: 204, headers: CORS });
}
