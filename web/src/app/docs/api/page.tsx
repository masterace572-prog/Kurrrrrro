import Link from 'next/link';
import { Icon } from '@/components/ui';

export const metadata = {
  title: 'API · KURO PANEL',
  description: 'How a client authenticates against the panel and what it gets back',
};

/**
 * Public, read-only reference for anything that talks to this panel: the loader,
 * an ImGui overlay, a test script. Deliberately one screen per endpoint with a
 * copyable request and the real response shape, because that is what a client
 * author actually needs — nothing here is behind a login.
 */

const BASE = process.env.PUBLIC_BASE_URL || 'https://kuro-panel-psi.vercel.app';

function Code({ children }: { children: string }) {
  return (
    <pre className="overflow-x-auto rounded-xl border border-line bg-ink-950 p-3 text-[12px] leading-relaxed text-slate-300">
      <code>{children}</code>
    </pre>
  );
}

function Endpoint({
  method,
  path,
  title,
  children,
}: {
  method: 'POST' | 'GET';
  path: string;
  title: string;
  children: React.ReactNode;
}) {
  return (
    <section className="card space-y-3">
      <div className="flex flex-wrap items-center gap-2">
        <span
          className={`badge ${
            method === 'POST'
              ? 'bg-emerald-500/10 text-emerald-300'
              : 'bg-sky-500/10 text-sky-300'
          }`}
        >
          {method}
        </span>
        <code className="text-sm font-semibold text-slate-100">{path}</code>
      </div>
      <p className="text-sm text-slate-400">{title}</p>
      {children}
    </section>
  );
}

function Row({ k, v }: { k: string; v: string }) {
  return (
    <>
      <dt className="text-slate-200">{k}</dt>
      <dd className="text-slate-400">{v}</dd>
    </>
  );
}

export default function ApiDocsPage() {
  return (
    <div className="mx-auto max-w-3xl px-4 py-8 sm:px-6 sm:py-12">
      <header className="mb-8">
        <Link href="/" className="mb-6 inline-flex items-center gap-2 text-xs text-slate-500 hover:text-slate-300">
          <Icon name="chevron" className="h-3.5 w-3.5 rotate-180" /> KURO PANEL
        </Link>
        <h1 className="text-2xl font-bold tracking-tight text-slate-50">Client API</h1>
        <p className="mt-2 text-sm text-slate-400">
          One endpoint for auth, two for the lib. No API key, no session — the licence key is the credential.
        </p>
        <p className="mt-1 text-xs text-slate-500">
          Base URL <code className="text-slate-300">{BASE}</code>
        </p>
      </header>

      <div className="space-y-4">
        <Endpoint
          method="POST"
          path="/server"
          title="Check a licence key and bind this device. Answers JSON, always HTTP 200."
        >
          <p className="text-xs font-semibold uppercase tracking-wider text-slate-500">Request</p>
          <p className="text-sm text-slate-400">
            <code className="text-slate-200">application/x-www-form-urlencoded</code> — JSON body works too.
          </p>
          <Code>{`game=PUBG&user_key=XXXX-XXXX&serial=8f3a-…device-id`}</Code>
          <dl className="grid grid-cols-1 gap-x-6 gap-y-1 text-sm sm:grid-cols-[140px_1fr]">
            <Row k="game" v="Game name, A-Z a-z 0-9 _ - (the loader sends PUBG)" />
            <Row k="serial" v="Stable per-install device id — it is what the device limit counts" />
            <Row k="user_key" v="The licence key, max 64 chars" />
          </dl>

          <p className="pt-1 text-xs font-semibold uppercase tracking-wider text-slate-500">Success</p>
          <Code>{`{
  "status": true,
  "data": {
    "real":     "PUBG-…",              // md5, same value as token
    "token":    "8b1c…",               // md5( game-key-serial-salt )
    "rng":      1761740000,            // unix seconds, must satisfy rng + 30 > now
    "modname":  "VIP MOD",             // the name to show in your UI
    "servername": "VIP MOD",           // same value, for clients that read this key
    "mod_status": "Safe",              // status line under the name
    "credit":   "t.me/…",              // credit line
    "EXP":      "2026-11-30 18:00:00", // expiry, UTC — count down to this
    "expired_date": "…", "exdate": "…",// identical, older field names
    "device":   2,                     // max devices on this key
    "duration": 24                     // hours bought
  }
}`}</Code>
          <p className="text-xs text-slate-500">
            <code className="text-slate-300">EXP</code>, <code className="text-slate-300">expired_date</code> and{' '}
            <code className="text-slate-300">exdate</code> always carry the same string, and{' '}
            <code className="text-slate-300">modname</code> is repeated as{' '}
            <code className="text-slate-300">servername</code>: different client bases read different names,
            so all of them are sent.
          </p>

          <p className="pt-1 text-xs font-semibold uppercase tracking-wider text-slate-500">Failure</p>
          <Code>{`{ "status": false, "reason": "EXPIRED KEY" }`}</Code>
          <dl className="grid grid-cols-1 gap-x-6 gap-y-1 text-sm sm:grid-cols-[220px_1fr]">
            <Row k="Bad Parameter" v="Missing or malformed game / user_key / serial" />
            <Row k="USER OR GAME NOT REGISTERED" v="No key with that game + key" />
            <Row k="USER BLOCKED" v="Key disabled in the panel" />
            <Row k="EXPIRED KEY" v="Past the expiry date" />
            <Row k="MAX DEVICE REACHED" v="Already bound to as many devices as allowed" />
            <Row k="SERVER UNDER MAINTENANCE" v="Maintenance mode — the text set in the panel replaces this line" />
            <Row k="SERVER ERROR" v="Database problem; try again" />
          </dl>
        </Endpoint>

        <Endpoint method="GET" path="/api/version" title="Plain text, first line = the lib version. No caching.">
          <Code>{`GET ${BASE}/api/version\n→ 1.4`}</Code>
          <p className="text-sm text-slate-400">
            Cache the version on the device and download again only when it changes.{' '}
            <code className="text-slate-200">?format=json</code> gives size, sha256 and publish time.
          </p>
        </Endpoint>

        <Endpoint method="GET" path="/api/download" title="The lib zip itself, or a 302 to it.">
          <p className="text-sm text-slate-400">
            Extracted into the client&apos;s private folder. The loader expects{' '}
            <code className="text-slate-200">libbgmi.so</code> (arm64-v8a) inside.
          </p>
        </Endpoint>

        <Endpoint method="GET" path="/server" title="Panel state, no key required — for a status banner or a ping.">
          <Code>{`{
  "web_info": { "_client": "KURO PANEL", "version": "1.0.0",
                "status": "online" | "maintenance" },
  "mod":      { "modname": "VIP MOD" },
  "endpoint": { "auth": "/server", "method": "POST",
                "fields": ["game", "user_key", "serial"] },
  "server_time": 1761740000
}`}</Code>
          <p className="text-sm text-slate-400">
            Check <code className="text-slate-200">status</code> before showing a login form — that way a
            maintenance window shows the panel&apos;s message instead of a wrong-key error.
          </p>
        </Endpoint>

        <section className="card space-y-3">
          <h2 className="flex items-center gap-2 text-sm font-semibold text-slate-100">
            <Icon name="phone" className="h-4 w-4 text-brand-400" /> Minimal C++ client (ImGui / NDK)
          </h2>
          <p className="text-sm text-slate-400">
            What matters: post the three fields, check{' '}
            <code className="text-slate-200">status</code>, keep{' '}
            <code className="text-slate-200">EXP</code> and{' '}
            <code className="text-slate-200">modname</code>. A device id can be any stable string.
          </p>
          <Code>{`// serial: anything stable per install — UUID from android_id + model works
std::string body = "game=" + game + "&user_key=" + key + "&serial=" + serial;

CURL *c = curl_easy_init();
curl_easy_setopt(c, CURLOPT_URL, "${BASE}/server");
curl_easy_setopt(c, CURLOPT_POSTFIELDS, body.c_str());
curl_easy_setopt(c, CURLOPT_POSTFIELDSIZE, (long)body.size());
curl_easy_setopt(c, CURLOPT_WRITEFUNCTION, writeCb);
curl_easy_setopt(c, CURLOPT_WRITEDATA, &out);

if (curl_easy_perform(c) == CURLE_OK) {
    json j = json::parse(out);
    if (j["status"] == true) {
        std::string token  = j["data"]["token"];      // optional: compare your md5
        time_t      rng    = j["data"]["rng"];        // reject if rng + 30 <= now
        std::string exp    = j["data"]["EXP"];        // count down to this
        std::string name   = j["data"]["modname"];    // window title
        std::string status = j["data"]["mod_status"];
        bValid = (rng + 30 > time(nullptr));
    } else {
        errMsg = j["reason"];                         // e.g. "EXPIRED KEY"
    }
}`}</Code>
          <p className="text-xs text-slate-500">
            The token is <code className="text-slate-300">md5(game + &quot;-&quot; + key + &quot;-&quot; + serial + &quot;-&quot; + salt)</code>{' '}
            with the salt from <code className="text-slate-300">LOADER_LICENSE_SALT</code> (default{' '}
            <code className="text-slate-300">Vm8Lk7Uj2JmsjCPVPVjrLa7zgfx3uz9E</code>). Verifying it locally is
            optional — the loader does, because a modified client must not be able to fake a success.
          </p>
        </section>

        <section className="card space-y-2">
          <h2 className="text-sm font-semibold text-slate-100">Notes</h2>
          <ul className="list-disc space-y-1 pl-5 text-sm text-slate-400">
            <li>Times are UTC strings (<code className="text-slate-200">YYYY-MM-DD HH:MM:SS</code>).</li>
            <li>Every answer is HTTP 200; read <code className="text-slate-200">status</code>, not the code.</li>
            <li>Device binding happens on first success — reset it from the key row in the panel.</li>
            <li>CORS is open, so a browser tool can call these too.</li>
          </ul>
        </section>
      </div>

      <footer className="mt-8 flex flex-wrap items-center gap-3 text-xs text-slate-500">
        <Link href="/login" className="hover:text-slate-300">
          Panel login
        </Link>
        <span>·</span>
        <a href="/api/health" className="hover:text-slate-300">
          /api/health
        </a>
        <span>·</span>
        <span>KURO PANEL</span>
      </footer>
    </div>
  );
}
