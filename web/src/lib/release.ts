import { createHash } from 'node:crypto';
import { q, q1 } from './db';

/**
 * Client (lib) update channel.
 *
 * The loader does two requests:
 *   GET /api/version   → plain text, first line = version string
 *   GET /api/download  → the zip, extracted into filesDir/loader/
 *
 * It keeps the version it last installed in SharedPreferences. If the text is
 * unchanged nothing is downloaded — so bumping the version is the *only* way to
 * push an update, exactly as intended.
 */

export type ClientRelease = {
  id: number;
  version: string;
  file_name: string;
  file_size: number;
  sha256: string;
  external_url: string | null;
  notes: string | null;
  is_current: boolean;
  uploaded_by: string | null;
  created_at: Date;
  has_content?: boolean;
};

/** Vercel rejects request bodies > 4.5 MB; keep a safe default. */
export const MAX_UPLOAD_BYTES = Number(process.env.MAX_LIB_UPLOAD_MB ?? 4) * 1024 * 1024;
export const FALLBACK_VERSION = process.env.FALLBACK_LIB_VERSION || '1.0';

export function isVersion(s: string): boolean {
  return /^[A-Za-z0-9._-]{1,32}$/.test(s);
}

export async function getCurrentRelease(): Promise<ClientRelease | null> {
  return q1<ClientRelease>(
    `SELECT id, version, file_name, file_size, sha256, external_url, notes, is_current,
            uploaded_by, created_at, (content IS NOT NULL) AS has_content
       FROM client_release WHERE is_current = true LIMIT 1`
  );
}

/** Version text for /api/version. Never throws — a DB hiccup must not brick the loader. */
export async function getVersionString(): Promise<string> {
  try {
    const rel = await getCurrentRelease();
    return rel?.version || FALLBACK_VERSION;
  } catch (e) {
    console.error('[release] version lookup failed, using fallback', e);
    return FALLBACK_VERSION;
  }
}

export async function listReleases(limit = 20): Promise<ClientRelease[]> {
  return q<ClientRelease>(
    `SELECT id, version, file_name, file_size, sha256, external_url, notes, is_current,
            uploaded_by, created_at, (content IS NOT NULL) AS has_content
       FROM client_release ORDER BY id DESC LIMIT $1`,
    [limit]
  );
}

export type NewRelease = {
  version: string;
  fileName: string;
  content?: Buffer;
  externalUrl?: string;
  notes?: string;
  uploadedBy: string;
};

export async function publishRelease(input: NewRelease): Promise<number> {
  if (!isVersion(input.version)) throw new Error('Invalid version string.');
  if (!input.content && !input.externalUrl) throw new Error('Provide a zip or an external URL.');

  const size = input.content?.byteLength ?? 0;
  if (size > MAX_UPLOAD_BYTES) {
    throw new Error(
      `File is ${(size / 1024 / 1024).toFixed(1)} MB, the limit is ${(MAX_UPLOAD_BYTES / 1024 / 1024).toFixed(0)} MB. ` +
        'Host bigger libs on GitHub Releases and use the "external URL" option.'
    );
  }
  const sha = input.content ? createHash('sha256').update(input.content).digest('hex') : '';

  // single "current" row is enforced by a partial unique index, so demote first
  await q(`UPDATE client_release SET is_current = false WHERE is_current = true`);
  const row = await q1<{ id: number }>(
    `INSERT INTO client_release (version, file_name, file_size, sha256, content, external_url, notes, is_current, uploaded_by)
     VALUES ($1,$2,$3,$4,$5,$6,$7,true,$8) RETURNING id`,
    [
      input.version,
      input.fileName,
      size,
      sha,
      input.content ?? null,
      input.externalUrl || null,
      input.notes || null,
      input.uploadedBy,
    ]
  );
  return row!.id;
}

/** Change the version of the current release without re-uploading: forces a re-download. */
export async function bumpCurrentVersion(version: string, actor: string): Promise<void> {
  if (!isVersion(version)) throw new Error('Invalid version string.');
  const rel = await getCurrentRelease();
  if (!rel) throw new Error('No release is published yet.');

  const clash = await q1(`SELECT 1 FROM client_release WHERE version = $1 AND id <> $2`, [version, rel.id]);
  if (clash) throw new Error(`Version ${version} already exists in the history.`);

  await q(`UPDATE client_release SET version = $1, uploaded_by = $2 WHERE id = $3`, [version, actor, rel.id]);
}

export async function setCurrentRelease(id: number): Promise<void> {
  const rel = await q1(`SELECT id FROM client_release WHERE id = $1`, [id]);
  if (!rel) throw new Error('Release not found.');
  await q(`UPDATE client_release SET is_current = false WHERE is_current = true`);
  await q(`UPDATE client_release SET is_current = true WHERE id = $1`, [id]);
}

export async function deleteRelease(id: number): Promise<void> {
  await q(`DELETE FROM client_release WHERE id = $1 AND is_current = false`, [id]);
}

export function humanSize(bytes: number): string {
  if (!bytes) return '—';
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(2)} MB`;
}
