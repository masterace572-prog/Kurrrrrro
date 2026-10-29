import { NextResponse } from 'next/server';
import { checkLicense, panelInfo } from '@/lib/gate';

// Alias of /server — kept so older loader builds pointed at /api/server work.

const CORS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'Content-Type, Accept',
  'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
};

export async function GET() {
  try {
    return NextResponse.json(await panelInfo(), { headers: CORS });
  } catch (e) {
    return NextResponse.json({ status: false, reason: 'SERVER ERROR', detail: (e as Error).message }, { headers: CORS });
  }
}

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

  try {
    const result = await checkLicense({
      game: payload.game || payload.Game || '',
      user_key: payload.user_key || payload.userkey || payload.key || '',
      serial: payload.serial || payload.device || payload.hwid || '',
    });
    return NextResponse.json(result, { headers: CORS });
  } catch (e) {
    console.error('[api/server]', e);
    return NextResponse.json({ status: false, reason: 'SERVER ERROR' }, { headers: CORS });
  }
}

export async function OPTIONS() {
  return new NextResponse(null, { status: 204, headers: CORS });
}
