/* ══════════════════════════════════════════
   Inkwell — src/auth/googleDrive.js
   Google Drive REST API v3 (no SDK, no Firebase).
   Uses the hidden "appDataFolder" space so the
   backup file never appears in the user's My Drive.
   ══════════════════════════════════════════ */

const DRIVE    = 'https://www.googleapis.com/drive/v3';
const UPLOAD   = 'https://www.googleapis.com/upload/drive/v3';
const FILENAME = 'inkwell-sync.json';

// ─── Internal helpers ─────────────────────
function auth(token) {
  return { Authorization: `Bearer ${token}` };
}

async function expectOk(res, label) {
  if (!res.ok) {
    const text = await res.text().catch(() => res.status);
    throw new Error(`[Drive] ${label} → ${res.status}: ${text}`);
  }
}

// ─── Find the sync file ───────────────────
export async function findSyncFile(token) {
  const url = new URL(`${DRIVE}/files`);
  url.searchParams.set('spaces', 'appDataFolder');
  url.searchParams.set('q', `name='${FILENAME}' and trashed=false`);
  url.searchParams.set('fields', 'files(id,name,modifiedTime,size)');
  url.searchParams.set('pageSize', '1');

  const res = await fetch(url.toString(), { headers: auth(token) });
  await expectOk(res, 'list');
  const data = await res.json();
  return data.files?.[0] || null;
}

// ─── Upload (backup) ──────────────────────
// Creates or updates the inkwell-sync.json in appDataFolder.
export async function backupToDrive(token, payload) {
  const existing = await findSyncFile(token);
  const body     = JSON.stringify(payload, null, 2);
  const blob     = new Blob([body], { type: 'application/json' });

  if (existing) {
    // ── Update existing file content ──
    const res = await fetch(`${UPLOAD}/files/${existing.id}?uploadType=media`, {
      method:  'PATCH',
      headers: { ...auth(token), 'Content-Type': 'application/json' },
      body:    blob,
    });
    await expectOk(res, 'update');
    return res.json();
  }

  // ── Create new file via multipart ──
  const meta = JSON.stringify({ name: FILENAME, parents: ['appDataFolder'] });
  const form = new FormData();
  form.append('metadata', new Blob([meta], { type: 'application/json' }));
  form.append('file', blob);

  const res = await fetch(`${UPLOAD}/files?uploadType=multipart`, {
    method:  'POST',
    headers: auth(token),           // let browser set Content-Type + boundary
    body:    form,
  });
  await expectOk(res, 'create');
  return res.json();
}

// ─── Download (restore) ───────────────────
export async function restoreFromDrive(token) {
  const file = await findSyncFile(token);
  if (!file) return null;

  const res = await fetch(`${DRIVE}/files/${file.id}?alt=media`, {
    headers: auth(token),
  });
  await expectOk(res, 'download');
  const data = await res.json();
  return { ...data, _syncedAt: file.modifiedTime, _fileId: file.id };
}

// ─── Get backup metadata only ─────────────
export async function getBackupInfo(token) {
  return findSyncFile(token);
}

// ─── Delete backup ────────────────────────
export async function deleteBackup(token) {
  const file = await findSyncFile(token);
  if (!file) return;
  const res = await fetch(`${DRIVE}/files/${file.id}`, {
    method:  'DELETE',
    headers: auth(token),
  });
  // 204 = success
  if (res.status !== 204 && !res.ok) {
    throw new Error(`[Drive] delete → ${res.status}`);
  }
}
