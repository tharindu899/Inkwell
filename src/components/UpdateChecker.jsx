/* ══════════════════════════════════════════
   Inkwell — src/components/UpdateChecker.jsx

   Checks GitHub Releases for APK updates.
   Auto check: shows a popup only when a newer APK release exists.
   Manual check: always shows latest APK info.
   Download button supports in-app MB/percent progress on Android/WebView,
   and falls back to the system browser when streaming is blocked.
   ══════════════════════════════════════════ */

import { useEffect, useState, useRef, useCallback } from 'react';
import { createPortal } from 'react-dom';
import { showToast } from './Toast';

const LOCAL_VERSION = import.meta.env.VITE_APP_VERSION || '0.0.0';
const DEFAULT_GITHUB_REPO = 'tharindu899/Inkwell';
const trimQuotes = (v = '') => String(v).trim().replace(/^[\'\"]+|[\'\"]+$/g, '');
const GITHUB_REPO = trimQuotes(import.meta.env.VITE_GITHUB_REPO || DEFAULT_GITHUB_REPO);
const GITHUB_TOKEN = (import.meta.env.VITE_GITHUB_TOKEN || '').trim();

function parseSemver(tag = '') {
  const nums = String(tag).replace(/^v/i, '').match(/\d+/g) || [0, 0, 0];
  return [Number(nums[0] || 0), Number(nums[1] || 0), Number(nums[2] || 0)];
}

function isNewer(remote, local) {
  const r = parseSemver(remote);
  const l = parseSemver(local);
  for (let i = 0; i < 3; i++) {
    if (r[i] > l[i]) return true;
    if (r[i] < l[i]) return false;
  }
  return false;
}

function fmtMb(bytes = 0) {
  if (!bytes || bytes < 0) return '—';
  return `${(bytes / 1024 / 1024).toFixed(bytes > 10 * 1024 * 1024 ? 1 : 2)} MB`;
}

function safeFileName(name = '') {
  const clean = String(name || '').replace(/[^a-zA-Z0-9._-]+/g, '-').replace(/-+/g, '-');
  return clean.toLowerCase().endsWith('.apk') ? clean : `Inkwell-v${LOCAL_VERSION}.apk`;
}

function findApkAsset(release) {
  return release?.assets?.find(a => String(a?.name || '').toLowerCase().endsWith('.apk')) || null;
}

async function githubJson(url) {
  const headers = {
    Accept: 'application/vnd.github+json',
    'X-GitHub-Api-Version': '2022-11-28',
  };
  if (GITHUB_TOKEN) headers.Authorization = `Bearer ${GITHUB_TOKEN}`;

  const res = await fetch(url, { headers });
  if (!res.ok) {
    const error = new Error(`GitHub ${res.status}`);
    error.status = res.status;
    throw error;
  }
  return res.json();
}

async function fetchLatestRelease() {
  const base = `https://api.github.com/repos/${GITHUB_REPO}`;
  try {
    return await githubJson(`${base}/releases/latest`);
  } catch (err) {
    try {
      const releases = await githubJson(`${base}/releases?per_page=10`);
      return releases.find(r => !r.draft && findApkAsset(r)) || releases.find(r => !r.draft) || null;
    } catch (listErr) {
      listErr.status = listErr.status || err.status;
      throw listErr;
    }
  }
}

async function openSystemUrl(url) {
  try {
    const { Capacitor } = await import('@capacitor/core');
    if (Capacitor.isNativePlatform()) {
      window.open(url, '_system');
      return;
    }
  } catch { /* browser */ }
  window.open(url, '_blank', 'noopener,noreferrer');
}

function blobToBase64(blob) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onerror = reject;
    reader.onload = () => resolve(String(reader.result).split(',')[1] || '');
    reader.readAsDataURL(blob);
  });
}

async function saveAndShareApk(blob, fileName) {
  const { Filesystem, Directory } = await import('@capacitor/filesystem');
  const { Share } = await import('@capacitor/share');
  const data = await blobToBase64(blob);
  const saved = await Filesystem.writeFile({
    path: fileName,
    data,
    directory: Directory.Documents,
    recursive: true,
  });
  await Share.share({
    title: 'Inkwell APK update',
    text: 'Downloaded Inkwell update APK. Open it to install manually.',
    url: saved.uri,
    dialogTitle: 'Open or share APK',
  });
  return saved.uri;
}

export default function UpdateChecker() {
  const [update, setUpdate] = useState(null);
  const [visible, setVisible] = useState(false);
  const [dismissedVersion, setDismissedVersion] = useState(null);
  const [downloading, setDownloading] = useState(false);
  const [downloadDone, setDownloadDone] = useState(false);
  const [progress, setProgress] = useState({ loaded: 0, total: 0, percent: 0, label: '' });
  const checkedRef = useRef(false);

  const showReleaseSheet = useCallback((release, newer, manual = false) => {
    const apkAsset = findApkAsset(release);
    const downloadUrl = apkAsset?.browser_download_url || release.html_url;
    const remoteVersion = String(release.tag_name || release.name || '').replace(/^v/i, '') || 'latest';

    setUpdate({
      mode: newer ? 'update' : 'latest',
      version: remoteVersion,
      downloadUrl,
      releaseUrl: release.html_url,
      body: release.body || '',
      apkName: apkAsset?.name || 'Open GitHub Release',
      apkSize: apkAsset?.size || 0,
      downloadCount: apkAsset?.download_count || 0,
      repo: GITHUB_REPO,
      hasApk: !!apkAsset,
    });
    setProgress({ loaded: 0, total: apkAsset?.size || 0, percent: 0, label: '' });
    setDownloadDone(false);
    setDownloading(false);
    setDismissedVersion(null);
    setVisible(true);

    if (manual) showToast(newer ? 'Update available' : 'Latest APK found', newer ? 'fa-download' : 'fa-circle-check');
  }, []);

  const checkForUpdate = useCallback(async (options = {}) => {
    const manual = !!options.manual;
    try {
      if (manual) showToast('Checking GitHub release…', 'fa-rotate');
      const release = await fetchLatestRelease();
      if (!release) {
        if (manual) showToast('No published GitHub release found', 'fa-circle-info');
        return;
      }
      const remoteVersion = String(release.tag_name || release.name || '').replace(/^v/i, '');
      const newer = isNewer(remoteVersion, LOCAL_VERSION);
      if (!manual && dismissedVersion && dismissedVersion === remoteVersion) return;
      if (newer) return showReleaseSheet(release, true, manual);
      if (manual) showReleaseSheet(release, false, true);
    } catch (err) {
      if (!manual) return;
      if (err.status === 404) {
        const hint = GITHUB_TOKEN
          ? `Repo "${GITHUB_REPO}" not found — check VITE_GITHUB_REPO`
          : `Repo "${GITHUB_REPO}" not found — repo must be public, or add VITE_GITHUB_TOKEN`;
        showToast(hint, 'fa-circle-exclamation');
      } else if (err.status === 403) {
        showToast('GitHub rate-limited — add VITE_GITHUB_TOKEN', 'fa-circle-exclamation');
      } else {
        showToast(`Update check failed (${err.status || 'network error'})`, 'fa-circle-exclamation');
      }
    }
  }, [dismissedVersion, showReleaseSheet]);

  const downloadApk = useCallback(async () => {
    if (!update?.downloadUrl) return;

    // If release has no APK asset, open release page instead.
    if (!update.hasApk) return openSystemUrl(update.downloadUrl);

    setDownloading(true);
    setDownloadDone(false);
    setProgress({ loaded: 0, total: update.apkSize || 0, percent: 0, label: 'Starting…' });

    try {
      const headers = GITHUB_TOKEN ? { Authorization: `Bearer ${GITHUB_TOKEN}` } : undefined;
      const res = await fetch(update.downloadUrl, { headers });
      if (!res.ok || !res.body) throw new Error('stream blocked');

      const total = Number(res.headers.get('content-length')) || update.apkSize || 0;
      const reader = res.body.getReader();
      const chunks = [];
      let loaded = 0;

      for (;;) {
        const { done, value } = await reader.read();
        if (done) break;
        chunks.push(value);
        loaded += value.length;
        const percent = total ? Math.min(100, Math.round((loaded / total) * 100)) : 0;
        setProgress({ loaded, total, percent, label: `${fmtMb(loaded)} / ${total ? fmtMb(total) : '…'}` });
      }

      const blob = new Blob(chunks, { type: 'application/vnd.android.package-archive' });
      setProgress({ loaded, total: total || blob.size, percent: 100, label: `${fmtMb(blob.size)} downloaded` });
      setDownloadDone(true);

      try {
        const { Capacitor } = await import('@capacitor/core');
        if (Capacitor.isNativePlatform()) {
          await saveAndShareApk(blob, safeFileName(update.apkName));
          showToast('APK downloaded — open it to install', 'fa-circle-check');
          return;
        }
      } catch { /* fallback below */ }

      const objectUrl = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = objectUrl;
      a.download = safeFileName(update.apkName);
      a.click();
      setTimeout(() => URL.revokeObjectURL(objectUrl), 30000);
      showToast('APK downloaded', 'fa-circle-check');
    } catch {
      showToast('Opening system download…', 'fa-up-right-from-square');
      await openSystemUrl(update.downloadUrl);
    } finally {
      setDownloading(false);
    }
  }, [update]);

  useEffect(() => {
    if (checkedRef.current) return;
    checkedRef.current = true;
    checkForUpdate();
  }, [checkForUpdate]);

  useEffect(() => {
    let removeListener;
    let active = true;
    async function setupResume() {
      try {
        const { App } = await import('@capacitor/app');
        if (!active) return;
        const handle = await App.addListener('resume', checkForUpdate);
        if (!active) { handle.remove(); return; }
        removeListener = handle.remove;
      } catch {
        if (!active) return;
        const handler = () => { if (document.visibilityState === 'visible') checkForUpdate(); };
        document.addEventListener('visibilitychange', handler);
        removeListener = () => document.removeEventListener('visibilitychange', handler);
      }
    }
    setupResume();
    return () => { active = false; removeListener?.(); };
  }, [checkForUpdate]);

  useEffect(() => {
    const handler = () => checkForUpdate({ manual: true });
    window.addEventListener('inkwell-check-updates', handler);
    return () => window.removeEventListener('inkwell-check-updates', handler);
  }, [checkForUpdate]);

  if (!visible || !update) return null;
  const isRealUpdate = update.mode === 'update';
  const pct = progress.percent || 0;

  return createPortal(
    <div className="modal-overlay show update-overlay" onClick={() => { setVisible(false); setDismissedVersion(update?.version ?? null); }} style={{ zIndex: 9999 }}>
      <div className="modal update-sheet" onClick={e => e.stopPropagation()}>
        <div className="update-header">
          <span className="update-icon"><i className={`fa-solid ${isRealUpdate ? 'fa-rocket' : 'fa-circle-check'}`} /></span>
          <div>
            <div className="modal-title">{isRealUpdate ? 'New update available' : 'Latest APK'}</div>
            <div className="modal-sub">
              {isRealUpdate ? (
                <>v<strong>{update.version}</strong> is ready — installed v<strong>{LOCAL_VERSION}</strong></>
              ) : (
                <>Installed v<strong>{LOCAL_VERSION}</strong> · Latest v<strong>{update.version}</strong></>
              )}
            </div>
          </div>
        </div>

        <div className="update-meta-grid">
          <div className="update-meta"><span>APK size</span><strong>{fmtMb(update.apkSize)}</strong></div>
          <div className="update-meta"><span>Downloads</span><strong>{update.downloadCount}</strong></div>
          <div className="update-meta"><span>Repo</span><strong>{update.repo}</strong></div>
        </div>

        {(downloading || downloadDone) && (
          <div className="update-progress-box">
            <div className="update-progress-top">
              <span>{downloadDone ? 'Download complete' : 'Downloading APK…'}</span>
              <strong>{progress.total ? `${pct}%` : progress.label}</strong>
            </div>
            <div className={`update-progress${progress.total ? '' : ' indeterminate'}`}>
              <span style={{ width: `${pct}%` }} />
            </div>
            <div className="update-progress-sub">{progress.label || 'Preparing download…'}</div>
          </div>
        )}

        {update.body && (
          <div className="update-notes">
            {update.body.split('\n').filter(l => l.trim()).slice(0, 4).map((line, i) => (
              <div key={i} className="update-note-line">{line.replace(/^[-*#]+\s*/, '')}</div>
            ))}
          </div>
        )}

        <div className="modal-actions update-actions">
          <button className="btn btn-ghost" disabled={downloading} onClick={() => { setVisible(false); setDismissedVersion(update?.version ?? null); }}>Later</button>
          <button className="btn btn-primary" disabled={downloading} onClick={downloadApk}>
            <i className={`fa-solid ${downloading ? 'fa-spinner fa-spin' : 'fa-download'}`} style={{ marginRight: 6 }} />
            {downloading ? 'Downloading…' : isRealUpdate ? 'Download update' : 'Download APK'}
          </button>
        </div>

        <div className="update-version-pill">v{LOCAL_VERSION}</div>
      </div>
    </div>,
    document.body
  );
}
