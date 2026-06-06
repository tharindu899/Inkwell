/* ══════════════════════════════════════════
   Inkwell — src/components/UpdateChecker.jsx

   GitHub Releases APK updater.
   Manual check shows either:
   - New update sheet with Install
   - Already updated sheet, no download button
   Android: downloads APK in-app, opens installer, then removes temp cache file.
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

function versionLabel(v = '') {
  const clean = String(v || '').replace(/^v/i, '').trim();
  return clean ? `v${clean}` : 'latest';
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

function cleanReleaseLine(line = '') {
  return String(line)
    .replace(/`/g, '')
    .replace(/\[(.*?)\]\((.*?)\)/g, '$1')
    .replace(/^[-*#>\s]+/, '')
    .trim();
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

async function nativeDownloadInstallApk(url, fileName) {
  const { Capacitor } = await import('@capacitor/core');
  const installer = Capacitor?.Plugins?.ApkInstaller;
  if (!Capacitor.isNativePlatform() || !installer?.installFromUrl) {
    throw new Error('native installer unavailable');
  }
  return installer.installFromUrl({
    url,
    fileName: safeFileName(fileName),
  });
}

async function webDownloadApk(url, fileName, onProgress) {
  const headers = GITHUB_TOKEN ? { Authorization: `Bearer ${GITHUB_TOKEN}` } : undefined;
  const res = await fetch(url, { headers });
  if (!res.ok || !res.body) throw new Error('download blocked');

  const total = Number(res.headers.get('content-length')) || 0;
  const reader = res.body.getReader();
  const chunks = [];
  let loaded = 0;

  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    chunks.push(value);
    loaded += value.length;
    const percent = total ? Math.min(100, Math.round((loaded / total) * 100)) : 0;
    onProgress?.({ loaded, total, percent, label: `${fmtMb(loaded)} / ${total ? fmtMb(total) : '…'}` });
  }

  return new Blob(chunks, { type: 'application/vnd.android.package-archive' });
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
    const current = parseSemver(LOCAL_VERSION).join('.');
    const remote = parseSemver(remoteVersion).join('.');

    setUpdate({
      mode: newer ? 'update' : 'current',
      version: remoteVersion,
      downloadUrl,
      releaseUrl: release.html_url,
      body: release.body || '',
      apkName: apkAsset?.name || 'Open GitHub Release',
      apkSize: apkAsset?.size || 0,
      downloadCount: apkAsset?.download_count || 0,
      repo: GITHUB_REPO,
      hasApk: !!apkAsset,
      sameVersion: current === remote,
    });
    setProgress({ loaded: 0, total: apkAsset?.size || 0, percent: 0, label: '' });
    setDownloadDone(false);
    setDownloading(false);
    setDismissedVersion(null);
    setVisible(true);

    if (manual) showToast(newer ? 'Update available' : 'You are already updated', newer ? 'fa-download' : 'fa-circle-check');
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
      if (manual) return showReleaseSheet(release, false, true);
    } catch (err) {
      if (!manual) return;
      if (err.status === 404) {
        showToast(`Repo not found: ${GITHUB_REPO}. Make repo public and publish an APK release.`, 'fa-circle-exclamation');
      } else if (err.status === 403) {
        showToast('GitHub rate-limited — try again later', 'fa-circle-exclamation');
      } else {
        showToast(`Update check failed (${err.status || 'network error'})`, 'fa-circle-exclamation');
      }
    }
  }, [dismissedVersion, showReleaseSheet]);

  const downloadApk = useCallback(async () => {
    if (!update?.downloadUrl || update.mode !== 'update') return;

    if (!update.hasApk) {
      showToast('This release has no APK file attached', 'fa-circle-exclamation');
      return;
    }

    setDownloading(true);
    setDownloadDone(false);
    setProgress({
      loaded: 0,
      total: update.apkSize || 0,
      percent: 0,
      label: update.apkSize ? `0 MB / ${fmtMb(update.apkSize)}` : 'Preparing download…',
    });

    try {
      const { Capacitor } = await import('@capacitor/core');

      if (Capacitor.isNativePlatform()) {
        // Native Android path: no browser, no GitHub popup.
        // Java plugin downloads the APK from GitHub Releases into cache,
        // opens Android Package Installer, then deletes the temp APK.
        setProgress({ loaded: 0, total: update.apkSize || 0, percent: 0, label: 'Downloading inside app…' });
        await nativeDownloadInstallApk(update.downloadUrl, update.apkName);
        setProgress({
          loaded: update.apkSize || 0,
          total: update.apkSize || 0,
          percent: 100,
          label: 'Installer opened — temporary APK will be deleted',
        });
        setDownloadDone(true);
        showToast('Android installer opened', 'fa-box-open');
        return;
      }

      // Web preview fallback only.
      const blob = await webDownloadApk(update.downloadUrl, safeFileName(update.apkName), setProgress);
      setProgress({ loaded: blob.size, total: blob.size, percent: 100, label: `${fmtMb(blob.size)} downloaded` });
      setDownloadDone(true);

      const objectUrl = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = objectUrl;
      a.download = safeFileName(update.apkName);
      a.click();
      setTimeout(() => URL.revokeObjectURL(objectUrl), 30000);
      showToast('APK downloaded', 'fa-circle-check');
    } catch (err) {
      setProgress({ loaded: 0, total: update.apkSize || 0, percent: 0, label: 'Install failed' });
      showToast('In-app install failed — check unknown-app install permission', 'fa-circle-exclamation');
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
  const releaseLines = String(update.body || '')
    .split('\n')
    .map(cleanReleaseLine)
    .filter(Boolean)
    .filter(line => !/^https?:\/\//i.test(line))
    .filter(line => !/commit|actions\/runs|github\.com/i.test(line))
    .map(line => line.length > 70 ? `${line.slice(0, 70)}…` : line)
    .slice(0, 3);

  return createPortal(
    <div className="modal-overlay show update-overlay" onClick={() => { setVisible(false); setDismissedVersion(update?.version ?? null); }} style={{ zIndex: 9999 }}>
      <div className="modal update-sheet clean-update-sheet" onClick={e => e.stopPropagation()}>
        <button className="update-close" disabled={downloading} onClick={() => { setVisible(false); setDismissedVersion(update?.version ?? null); }} aria-label="Close">
          <i className="fa-solid fa-xmark" />
        </button>

        <div className="update-hero">
          <span className={`update-icon ${isRealUpdate ? 'hot' : 'ok'}`}>
            <i className={`fa-solid ${isRealUpdate ? 'fa-download' : 'fa-circle-check'}`} />
          </span>
          <div className="update-version-pill">{isRealUpdate ? versionLabel(update.version) : 'Up to date'}</div>
        </div>

        <div className="update-title">
          {isRealUpdate ? 'New APK update available' : 'You are already updated'}
        </div>
        <div className="update-subtitle">
          {isRealUpdate ? (
            <>Latest {versionLabel(update.version)} · Current {versionLabel(LOCAL_VERSION)}</>
          ) : (
            <>Current {versionLabel(LOCAL_VERSION)} matches latest {versionLabel(update.version)}</>
          )}
        </div>

        <div className="update-meta-grid">
          <div className="update-meta"><span>Size</span><strong>{fmtMb(update.apkSize)}</strong></div>
          <div className="update-meta"><span>Downloads</span><strong>{update.downloadCount}</strong></div>
          <div className="update-meta repo"><span>Repo</span><strong>{update.repo}</strong></div>
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

        {isRealUpdate && releaseLines.length > 0 && (
          <div className="update-notes">
            {releaseLines.map((line, i) => (
              <div key={i} className="update-note-line">{line}</div>
            ))}
          </div>
        )}

        {!isRealUpdate && (
          <div className="update-current-box">
            <i className="fa-solid fa-shield-check" />
            <span>No download needed. Your installed APK is the latest release.</span>
          </div>
        )}

        <div className="modal-actions update-actions">
          <button className="btn btn-ghost" disabled={downloading} onClick={() => { setVisible(false); setDismissedVersion(update?.version ?? null); }}>
            {isRealUpdate ? 'Later' : 'Close'}
          </button>
          {isRealUpdate ? (
            <button className="btn btn-primary" disabled={downloading} onClick={downloadApk}>
              <i className={`fa-solid ${downloading ? 'fa-spinner fa-spin' : 'fa-download'}`} style={{ marginRight: 6 }} />
              {downloading ? 'Downloading…' : 'Install'}
            </button>
          ) : (
            <button className="btn btn-primary" disabled={downloading} onClick={() => setVisible(false)}>
              <i className="fa-solid fa-circle-check" style={{ marginRight: 6 }} />
              Done
            </button>
          )}
        </div>
      </div>
    </div>,
    document.body
  );
}
