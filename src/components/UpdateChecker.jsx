/* ══════════════════════════════════════════
   Inkwell — src/components/UpdateChecker.jsx

   Checks GitHub Releases for APK updates.
   Auto check: only shows popup when newer version exists.
   Manual check from Settings: shows latest release/download even
   when the installed version is already latest.
   ══════════════════════════════════════════ */

import { useEffect, useState, useRef, useCallback } from 'react';
import { createPortal } from 'react-dom';
import { showToast } from './Toast';

const LOCAL_VERSION = import.meta.env.VITE_APP_VERSION || '0.0.0';

// Default repo is baked in so APK update works even when the secret is missing.
// trimQuotes fixes secrets entered as "owner/repo" with quotation marks.
const DEFAULT_GITHUB_REPO = 'tharindu899/Inkwell';
const trimQuotes = (v = '') => String(v).trim().replace(/^['\"]+|['\"]+$/g, '');
const GITHUB_REPO = trimQuotes(import.meta.env.VITE_GITHUB_REPO || DEFAULT_GITHUB_REPO);

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

function findApkAsset(release) {
  return release?.assets?.find(a => String(a?.name || '').toLowerCase().endsWith('.apk')) || null;
}

// For private repos set VITE_GITHUB_TOKEN to a PAT with contents:read.
// Public repos work without a token.
const GITHUB_TOKEN = (import.meta.env.VITE_GITHUB_TOKEN || '').trim();

async function githubJson(url) {
  const headers = {
    Accept: 'application/vnd.github+json',
    'X-GitHub-Api-Version': '2022-11-28',
  };
  if (GITHUB_TOKEN) headers['Authorization'] = `Bearer ${GITHUB_TOKEN}`;

  const res = await fetch(url, { headers });
  if (!res.ok) {
    const error = new Error(`GitHub ${res.status}`);
    error.status = res.status;
    throw error;
  }
  return res.json();
}

async function fetchLatestRelease() {
  if (!GITHUB_REPO) return null;
  const base = `https://api.github.com/repos/${GITHUB_REPO}`;

  try {
    return await githubJson(`${base}/releases/latest`);
  } catch (err) {
    // /latest can be 404 if GitHub has releases but no "latest" published release.
    // Fall back to listing releases and pick the first published release with an APK.
    try {
      const releases = await githubJson(`${base}/releases?per_page=10`);
      return releases.find(r => !r.draft && findApkAsset(r)) || releases.find(r => !r.draft) || null;
    } catch (listErr) {
      listErr.status = listErr.status || err.status;
      throw listErr;
    }
  }
}

async function openUrl(url) {
  try {
    const { Capacitor } = await import('@capacitor/core');
    if (Capacitor.isNativePlatform()) {
      // On Android, '_system' routes through ACTION_VIEW so the OS download
      // manager handles the APK — the in-app Browser plugin can't install them.
      window.open(url, '_system');
      return;
    }
  } catch { /* not in Capacitor environment */ }
  window.open(url, '_blank', 'noopener,noreferrer');
}

export default function UpdateChecker() {
  const [update, setUpdate] = useState(null);
  const [visible, setVisible] = useState(false);
  // Track the specific version the user dismissed rather than a boolean so that
  // a newly-released version still surfaces on the next auto-check or resume.
  const [dismissedVersion, setDismissedVersion] = useState(null);
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
      repo: GITHUB_REPO,
    });
    setDismissedVersion(null);
    setVisible(true);

    if (manual) {
      showToast(newer ? 'Update available' : 'Latest APK found', newer ? 'fa-download' : 'fa-circle-check');
    }
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

      // Auto-check: stay quiet when this specific version was already dismissed.
      // A NEW release (different version) will still surface after the next resume.
      if (!manual && dismissedVersion && dismissedVersion === remoteVersion) return;

      if (newer) {
        showReleaseSheet(release, true, manual);
        return;
      }

      // Auto check stays quiet when already latest.
      // Manual check still opens the latest release/download sheet.
      if (manual) {
        showReleaseSheet(release, false, true);
      }
    } catch (err) {
      if (manual) {
        if (err.status === 404) {
          // Tell the user exactly which repo was checked so they can verify it.
          const repoLabel = GITHUB_REPO || DEFAULT_GITHUB_REPO;
          const hint = GITHUB_TOKEN
            ? `Repo "${repoLabel}" not found — check VITE_GITHUB_REPO`
            : `Repo "${repoLabel}" not found — check VITE_GITHUB_REPO, or add VITE_GITHUB_TOKEN for private repos`;
          showToast(hint, 'fa-circle-exclamation');
        } else if (err.status === 403) {
          showToast('GitHub rate-limited — add VITE_GITHUB_TOKEN to get 5 000 req/hr', 'fa-circle-exclamation');
        } else {
          showToast(`Update check failed (${err.status || 'network error'})`, 'fa-circle-exclamation');
        }
      }
    }
  }, [dismissedVersion, showReleaseSheet]);

  useEffect(() => {
    if (checkedRef.current) return;
    checkedRef.current = true;
    checkForUpdate();
  }, [checkForUpdate]);

  useEffect(() => {
    let removeListener;
    let active = true; // guards against cleanup running before the async setup resolves

    async function setupResume() {
      try {
        const { App } = await import('@capacitor/app');
        if (!active) return;
        const handle = await App.addListener('resume', checkForUpdate);
        if (!active) { handle.remove(); return; }
        removeListener = handle.remove;
      } catch {
        if (!active) return;
        const handler = () => {
          if (document.visibilityState === 'visible') checkForUpdate();
        };
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

  return createPortal(
    <div
      className="modal-overlay show update-overlay"
      onClick={() => { setVisible(false); setDismissedVersion(update?.version ?? null); }}
      style={{ zIndex: 9999 }}
    >
      <div className="modal update-sheet" onClick={e => e.stopPropagation()}>
        <div className="update-header">
          <span className="update-icon">
            <i className={`fa-solid ${isRealUpdate ? 'fa-rocket' : 'fa-circle-check'}`} />
          </span>
          <div>
            <div className="modal-title">
              {isRealUpdate ? 'Update available' : 'Latest APK'}
            </div>
            <div className="modal-sub">
              {isRealUpdate ? (
                <>Version <strong>{update.version}</strong> is ready — you have <strong>{LOCAL_VERSION}</strong></>
              ) : (
                <>You are on <strong>v{LOCAL_VERSION}</strong>. Latest release: <strong>v{update.version}</strong></>
              )}
            </div>
          </div>
        </div>

        {update.body && (
          <div className="update-notes">
            {update.body
              .split('\n')
              .filter(l => l.trim())
              .slice(0, 4)
              .map((line, i) => (
                <div key={i} className="update-note-line">
                  {line.replace(/^[-*#]+\s*/, '')}
                </div>
              ))}
          </div>
        )}

        <div className="update-notes" style={{ marginTop: 10 }}>
          <div className="update-note-line">Repo: {update.repo}</div>
          <div className="update-note-line">APK: {update.apkName}</div>
        </div>

        <div className="modal-actions update-actions">
          <button
            className="btn btn-ghost"
            onClick={() => { setVisible(false); setDismissedVersion(update?.version ?? null); }}
          >
            Later
          </button>
          <button className="btn btn-primary" onClick={() => openUrl(update.downloadUrl)}>
            <i className="fa-solid fa-download" style={{ marginRight: 6 }} />
            {isRealUpdate ? 'Download update' : 'Download APK'}
          </button>
        </div>

        <div className="update-version-pill">v{LOCAL_VERSION}</div>
      </div>
    </div>,
    document.body
  );
}
