/* ══════════════════════════════════════════
   Inkwell — src/pages/Settings.jsx
   ══════════════════════════════════════════ */

import { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import TopBar from '../components/TopBar';
import BottomNav from '../components/BottomNav';
import Toast, { showToast } from '../components/Toast';
import { useTheme, useFontSize } from '../hooks/useTheme';
import { useAppStore } from '../store/AppContext';
import { useAuth } from '../auth/AuthContext';
import { backupToDrive, restoreFromDrive, getBackupInfo } from '../auth/googleDrive';
import {
  getNotes, saveNotes, getNotebooks, saveNotebooks, getProfile, K,
} from '../store/storage';

const PREFS_KEY = 'iw_prefs';
const APP_VERSION = import.meta.env.VITE_APP_VERSION || '0.0.0';
const STORAGE_LIMIT_MB = 5;
function getPrefs()     { try { return JSON.parse(localStorage.getItem(PREFS_KEY) || '{}'); } catch { return {}; } }
function savePref(k, v) { const p = getPrefs(); p[k] = v; localStorage.setItem(PREFS_KEY, JSON.stringify(p)); }

function calcStorage() {
  let total = 0;
  for (let i = 0; i < localStorage.length; i++) {
    const k = localStorage.key(i);
    if (k?.startsWith('iw_')) total += (localStorage.getItem(k) || '').length * 2;
  }
  const kb = total / 1024;
  const mb = kb / 1024;
  const percent = Math.min(100, Math.max(2, (mb / STORAGE_LIMIT_MB) * 100));
  return { bytes: total, kb: kb.toFixed(1), mb: mb.toFixed(2), percent };
}

function fmtDate(iso) {
  if (!iso) return '—';
  return new Date(iso).toLocaleString('en', {
    dateStyle: 'medium', timeStyle: 'short',
  });
}

function ImportNotesIcon() {
  return (
    <svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true" focusable="false">
      <path fill="currentColor" d="M14 2H7a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V7l-5-5Zm0 2.5L16.5 7H14V4.5ZM12 18a1 1 0 0 1-.7-.29l-3-3a1 1 0 1 1 1.4-1.42L11 14.59V10a1 1 0 1 1 2 0v4.59l1.3-1.3a1 1 0 0 1 1.4 1.42l-3 3A1 1 0 0 1 12 18Z" />
    </svg>
  );
}

export default function Settings() {
  const navigate                  = useNavigate();
  const { notes, notebooks, dispatch } = useAppStore();
  const { user, getToken }        = useAuth();

  const { theme, setTheme }       = useTheme();
  const { fontSize, setFontSize } = useFontSize();

  const [prefs,       setPrefs]      = useState(getPrefs);
  const [storageInfo, setStorageInfo] = useState(() => calcStorage());
  const [confirm,     setConfirm]    = useState(null);

  // Drive sync state
  const [backupBusy,  setBackupBusy] = useState(false);
  const [restoreBusy, setRestoreBusy] = useState(false);
  const [backupInfo,  setBackupInfo] = useState(null);  // { modifiedTime, size } | null
  const [syncError,   setSyncError]  = useState(null);

  const tagCount = [...new Set(notes.flatMap(n => n.tags || []))].length;

  function checkForUpdates() {
    window.dispatchEvent(new CustomEvent('inkwell-check-updates'));
  }

  function setPref(k, v) {
    savePref(k, v);
    setPrefs(getPrefs());
  }

  // ── Load last backup info on mount ──
  useEffect(() => {
    async function load() {
      try {
        const token = await getToken();
        if (!token) return;
        const info = await getBackupInfo(token);
        setBackupInfo(info);
      } catch {}
    }
    load();
  }, [getToken]);

  // ── Backup to Drive ──
  const handleBackup = useCallback(async () => {
    setBackupBusy(true);
    setSyncError(null);
    try {
      const token = await getToken();
      if (!token) throw new Error('Not signed in');
      const payload = {
        version:    2,
        backedUpAt: new Date().toISOString(),
        notes:      getNotes(),
        notebooks:  getNotebooks(),
        profile:    getProfile(),
      };
      await backupToDrive(token, payload);
      showToast('Backed up to Google Drive', 'fa-cloud-arrow-up');
      // Refresh info separately — don't let this throw kill the finally
      try {
        const info = await getBackupInfo(token);
        setBackupInfo(info);
      } catch { /* non-fatal */ }
    } catch (err) {
      setSyncError(err.message || 'Backup failed');
      showToast('Backup failed', 'fa-circle-exclamation');
    } finally {
      setBackupBusy(false);
    }
  }, [getToken]);

  // ── Restore from Drive ──
  const handleRestore = useCallback(async () => {
    setConfirm({
      title: 'Restore from Google Drive?',
      sub:   'Your current local data will be replaced by the cloud backup. This cannot be undone.',
      danger: false,
      okLabel: 'Restore',
      onOk: async () => {
        setRestoreBusy(true);
        setSyncError(null);
        try {
          const token = await getToken();
          if (!token) throw new Error('Not signed in');
          const data = await restoreFromDrive(token);
          if (!data) {
            showToast('No backup found on Drive', 'fa-circle-info');
            // fall through to finally — do NOT return here
          } else {
            if (data.notes)     saveNotes(data.notes);
            if (data.notebooks) saveNotebooks(data.notebooks);
            dispatch({ type: 'RELOAD' });
            setStorageInfo(calcStorage());
            showToast(`Restored ${(data.notes || []).length} notes`, 'fa-cloud-arrow-down');
          }
        } catch (err) {
          setSyncError(err.message || 'Restore failed');
          showToast('Restore failed', 'fa-circle-exclamation');
        } finally {
          setRestoreBusy(false);
        }
      },
    });
  }, [getToken, dispatch]);

  /* ── Local Export ── */
  function exportData() {
    const data = {
      version: 1,
      exportedAt: new Date().toISOString(),
      notes: getNotes(),
      notebooks: getNotebooks(),
      profile: JSON.parse(localStorage.getItem(K.profile) || '{}'),
    };
    const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
    const url  = URL.createObjectURL(blob);
    const a    = document.createElement('a');
    a.href     = url;
    a.download = `inkwell-backup-${new Date().toISOString().slice(0, 10)}.json`;
    a.click();
    URL.revokeObjectURL(url);
    showToast('Export downloaded', 'fa-file-arrow-down');
  }

  /* ── Local Import ── */
  function importData() { document.getElementById('import-file').click(); }

  function processImport(e) {
    const file = e.target.files[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = ev => {
      try {
        const data = JSON.parse(ev.target.result);
        let addedNotes = 0, addedNotebooks = 0;
        if (data.notes) {
          const existing = getNotes();
          const existingIds = new Set(existing.map(n => n.id));
          const newNotes = data.notes.filter(n => !existingIds.has(n.id));
          addedNotes = newNotes.length;
          saveNotes([...existing, ...newNotes]);
        }
        if (data.notebooks) {
          const existing = getNotebooks();
          const existingIds = new Set(existing.map(n => n.id));
          const newNbs = data.notebooks.filter(n => !existingIds.has(n.id));
          addedNotebooks = newNbs.length;
          saveNotebooks([...existing, ...newNbs]);
        }
        dispatch({ type: 'RELOAD' });
        const skipped = (data.notes?.length || 0) - addedNotes;
        showToast(
          `Imported ${addedNotes} note${addedNotes !== 1 ? 's' : ''}${skipped ? ` (${skipped} duplicate${skipped !== 1 ? 's' : ''} skipped)` : ''}`,
          'fa-file-import'
        );
        setStorageInfo(calcStorage());
      } catch {
        showToast('Import failed — invalid file', 'fa-circle-exclamation');
      }
    };
    reader.readAsText(file);
    e.target.value = '';
  }

  /* ── Danger confirmations ── */
  function confirmClearNotes() {
    setConfirm({
      title:   'Delete all notes?',
      sub:     'Every note will be permanently removed. Notebooks will remain.',
      danger:  true,
      okLabel: 'Delete',
      onOk:    () => {
        saveNotes([]);
        dispatch({ type: 'RELOAD' });
        showToast('All notes deleted', 'fa-trash-can');
        setStorageInfo(calcStorage());
      },
    });
  }

  function confirmReset() {
    setConfirm({
      title:   'Reset everything?',
      sub:     'All notes, notebooks, settings, and profile will be wiped. This cannot be undone.',
      danger:  true,
      okLabel: 'Reset',
      onOk:    () => {
        Object.keys(localStorage).filter(k => k.startsWith('iw_')).forEach(k => localStorage.removeItem(k));
        showToast('Data cleared — reloading…', 'fa-rotate');
        setTimeout(() => navigate('/'), 1500);
      },
    });
  }

  return (
    <div className="app-shell">
      <TopBar back title="Settings" />

      <main className="page-content">

        {/* ── Google Drive Sync ── */}
        <div className="settings-group drive-sync-group">
          <div className="sg-label">
            <svg viewBox="0 0 48 48" width="13" height="13" style={{ marginRight: 5, verticalAlign: 'middle' }}>
              <path fill="#EA4335" d="M24 9.5c3.5 0 6.6 1.2 9.1 3.2l6.8-6.8C35.9 2.2 30.3 0 24 0 14.7 0 6.7 5.4 2.9 13.3l7.9 6.1C12.6 13.5 17.9 9.5 24 9.5z"/>
              <path fill="#4285F4" d="M46.5 24.5c0-1.6-.1-3.1-.4-4.5H24v8.5h12.7c-.6 3-2.3 5.5-4.8 7.2l7.5 5.8c4.4-4.1 7.1-10.1 7.1-17z"/>
              <path fill="#FBBC05" d="M10.8 28.6A14.4 14.4 0 0 1 9.5 24c0-1.6.3-3.2.8-4.6l-7.9-6.1A23.8 23.8 0 0 0 0 24c0 3.9.9 7.5 2.4 10.7l8.4-6.1z"/>
              <path fill="#34A853" d="M24 48c6.5 0 11.9-2.1 15.9-5.8l-7.5-5.8c-2.2 1.5-5 2.4-8.4 2.4-6.1 0-11.4-4.1-13.2-9.7l-8.4 6.1C6.8 42.7 14.8 48 24 48z"/>
            </svg>
            Google Drive Sync
          </div>

          {/* Account row */}
          <div className="si">
            <div className="si-icon ac" style={{ padding: 0, overflow: 'hidden' }}>
              {user?.picture
                ? <img src={user.picture} alt="" style={{ width: '100%', height: '100%', objectFit: 'cover' }} referrerPolicy="no-referrer" />
                : <i className="fa-brands fa-google" />
              }
            </div>
            <div className="si-body">
              <div className="si-label">{user?.name || 'Google Account'}</div>
              <div className="si-sub">{user?.email}</div>
            </div>
            <div className="si-right sync-dot-wrap">
              <span className="sync-dot online" title="Connected" />
            </div>
          </div>

          {/* Last backup */}
          <div className="si">
            <div className="si-icon"><i className="fa-solid fa-clock-rotate-left" /></div>
            <div className="si-body">
              <div className="si-label">Last backup</div>
              <div className="si-sub">
                {backupInfo ? fmtDate(backupInfo.modifiedTime) : 'No backup yet'}
              </div>
            </div>
          </div>

          {/* Backup button */}
          <div className={`si${backupBusy ? ' sync-busy' : ''}`} onClick={!backupBusy && !restoreBusy ? handleBackup : undefined}>
            <div className="si-icon success">
              {backupBusy
                ? <span className="spin-icon"><i className="fa-solid fa-spinner" /></span>
                : <i className="fa-solid fa-cloud-arrow-up" />
              }
            </div>
            <div className="si-body">
              <div className="si-label">Back up now</div>
              <div className="si-sub">Save all notes to your Google Drive</div>
            </div>
            {!backupBusy && <div className="si-right"><i className="fa-solid fa-chevron-right" /></div>}
          </div>

          {/* Restore button */}
          <div className={`si${restoreBusy ? ' sync-busy' : ''}`} onClick={!backupBusy && !restoreBusy ? handleRestore : undefined}>
            <div className="si-icon info">
              {restoreBusy
                ? <span className="spin-icon"><i className="fa-solid fa-spinner" /></span>
                : <i className="fa-solid fa-cloud-arrow-down" />
              }
            </div>
            <div className="si-body">
              <div className="si-label">Restore from Drive</div>
              <div className="si-sub">Replace local data with your cloud backup</div>
            </div>
            {!restoreBusy && <div className="si-right"><i className="fa-solid fa-chevron-right" /></div>}
          </div>

          {syncError && (
            <div className="sync-error-row">
              <i className="fa-solid fa-circle-exclamation" /> {syncError}
            </div>
          )}
        </div>

        {/* ── App Update ── */}
        <div className="settings-group">
          <div className="sg-label">App Update</div>

          <div className="si" onClick={checkForUpdates}>
            <div className="si-icon info"><i className="fa-solid fa-download" /></div>
            <div className="si-body">
              <div className="si-label">Check for updates</div>
              <div className="si-sub">Find the latest APK from GitHub Releases</div>
            </div>
            <div className="si-right"><i className="fa-solid fa-chevron-right" /></div>
          </div>
        </div>

        {/* ── Appearance ── */}
        <div className="settings-group">
          <div className="sg-label">Appearance</div>

          <div className="si theme-setting-row">
            <div className="si-icon ac"><i className="fa-brands fa-github" /></div>
            <div className="si-body">
              <div className="si-label">Theme</div>
              <div className="si-sub">Choose Light, Dark, or GitHub style</div>
            </div>
            <div className="si-right">
              <div className="seg-control theme-seg">
                {[
                  { key: 'light', label: 'Light', icon: 'fa-regular fa-sun' },
                  { key: 'dark', label: 'Dark', icon: 'fa-regular fa-moon' },
                  { key: 'github', label: 'GitHub', icon: 'fa-brands fa-github' },
                ].map(t => (
                  <button
                    key={t.key}
                    className={`seg-btn theme-seg-btn${theme === t.key ? ' on' : ''}`}
                    onClick={() => {
                      setTheme(t.key);
                      showToast(`${t.label} theme on`, t.icon);
                    }}
                  >
                    <i className={t.icon} />
                    <span>{t.label}</span>
                  </button>
                ))}
              </div>
            </div>
          </div>

          <div className="si">
            <div className="si-icon" style={{ background: 'var(--bg-elevated)', color: 'var(--accent)', borderColor: 'transparent' }}>
              <i className="fa-solid fa-text-height" />
            </div>
            <div className="si-body">
              <div className="si-label">Editor font size</div>
              <div className="si-sub">Applies to the note editor body text</div>
            </div>
            <div className="si-right">
              <div className="seg-control">
                {['small', 'medium', 'large'].map(s => (
                  <button
                    key={s}
                    className={`seg-btn${fontSize === s ? ' on' : ''}`}
                    onClick={() => { setFontSize(s); showToast(`Font size: ${s}`, 'fa-text-height'); }}
                  >
                    {s[0].toUpperCase()}
                  </button>
                ))}
              </div>
            </div>
          </div>
        </div>

        {/* ── Editor ── */}
        <div className="settings-group">
          <div className="sg-label">Editor</div>
          {[
            { id: 'spellcheck',   icon: 'fa-spell-check',           label: 'Spell check',     sub: 'Underline misspelled words',              def: true  },
            { id: 'autosave',     icon: 'fa-regular fa-floppy-disk', label: 'Auto-save',       sub: 'Save notes automatically while typing',   def: true  },
            { id: 'markdownMode', icon: 'fa-code',                   label: 'Markdown mode',   sub: 'Enable Markdown shortcuts by default',    def: true  },
            { id: 'serifBody',    icon: 'fa-font',                   label: 'Serif body font', sub: 'Use Lora instead of DM Sans for notes',   def: false },
          ].map(({ id, icon, label, sub, def }) => (
            <div key={id} className="si">
              <div className="si-icon"><i className={`fa-solid ${icon}`} /></div>
              <div className="si-body"><div className="si-label">{label}</div><div className="si-sub">{sub}</div></div>
              <label className="toggle">
                <input
                  type="checkbox"
                  checked={prefs[id] !== undefined ? !!prefs[id] : def}
                  onChange={e => setPref(id, e.target.checked)}
                />
                <div className="toggle-track"><div className="toggle-thumb" /></div>
              </label>
            </div>
          ))}
        </div>

        {/* ── Data (local) ── */}
        <div className="settings-group">
          <div className="sg-label">Local Data</div>
          <div className="si" onClick={exportData}>
            <div className="si-icon info"><i className="fa-solid fa-arrow-up-from-bracket" /></div>
            <div className="si-body"><div className="si-label">Export all notes</div><div className="si-sub">Download a JSON backup to this device</div></div>
            <div className="si-right"><i className="fa-solid fa-chevron-right" /></div>
          </div>
          <div className="si" onClick={importData}>
            <div className="si-icon success"><ImportNotesIcon /></div>
            <div className="si-body"><div className="si-label">Import notes</div><div className="si-sub">Restore from a local JSON backup file</div></div>
            <div className="si-right"><i className="fa-solid fa-chevron-right" /></div>
          </div>
          <div className="si" onClick={() => navigate('/tags')}>
            <div className="si-icon"><i className="fa-solid fa-tags" /></div>
            <div className="si-body">
              <div className="si-label">Manage tags</div>
              <div className="si-sub">{tagCount} unique tag{tagCount !== 1 ? 's' : ''} across all notes</div>
            </div>
            <div className="si-right"><i className="fa-solid fa-chevron-right" /></div>
          </div>
        </div>

        {/* ── Storage ── */}
        <div className="settings-group">
          <div className="sg-label">Storage</div>
          <div className="si">
            <div className="si-icon"><i className="fa-solid fa-database" /></div>
            <div className="si-body">
              <div className="si-label">Local usage</div>
              <div className="si-sub">{storageInfo.kb} KB / {storageInfo.mb} MB on this device</div>
              <div className="storage-meter" aria-label="Local storage usage"><span style={{ width: `${storageInfo.percent}%` }} /></div>
              <div className="storage-count-grid">
                <div><strong>{notes.length}</strong>Notes</div>
                <div><strong>{notebooks.length}</strong>Books</div>
                <div><strong>{tagCount}</strong>Tags</div>
              </div>
            </div>
            <div className="si-right" style={{ color: 'var(--accent)', fontWeight: 600 }}>
              {notes.length} note{notes.length !== 1 ? 's' : ''}
            </div>
          </div>
        </div>

        {/* ── Danger zone ── */}
        <div className="danger-zone">
          <div className="sg-label" style={{ color: 'var(--danger)' }}>Danger zone</div>
          <div className="si" onClick={confirmClearNotes}>
            <div className="si-icon danger"><i className="fa-regular fa-note-sticky" /></div>
            <div className="si-body">
              <div className="si-label" style={{ color: 'var(--danger)' }}>Delete all notes</div>
              <div className="si-sub">Permanently remove every note</div>
            </div>
          </div>
          <div className="si" onClick={confirmReset}>
            <div className="si-icon danger"><i className="fa-regular fa-trash-can" /></div>
            <div className="si-body">
              <div className="si-label" style={{ color: 'var(--danger)' }}>Reset all data</div>
              <div className="si-sub">Wipe all notes, notebooks, and settings</div>
            </div>
          </div>
        </div>

        {/* ── About ── */}
        <div className="settings-group">
          <div className="sg-label">About</div>
          <div className="si" onClick={() => navigate('/profile')}>
            <div className="si-icon"><i className="fa-regular fa-user" /></div>
            <div className="si-body"><div className="si-label">Profile</div><div className="si-sub">Your Google account & writing stats</div></div>
            <div className="si-right"><i className="fa-solid fa-chevron-right" /></div>
          </div>
          <div className="si">
            <div className="si-icon"><i className="fa-solid fa-circle-info" /></div>
            <div className="si-body"><div className="si-label">Inkwell</div><div className="si-sub">v{APP_VERSION} — Built with ♥</div></div>
          </div>
        </div>

        <div className="app-version">Inkwell v{APP_VERSION} · Synced via Google Drive · Data never leaves your accounts</div>

      </main>

      {/* ── Confirm Modal ── */}
      {confirm && (
        <div className="modal-overlay show">
          <div className="modal">
            <div className="modal-title">{confirm.title}</div>
            <div className="modal-sub">{confirm.sub}</div>
            <div className="modal-actions">
              <button className="btn btn-ghost" onClick={() => setConfirm(null)}>Cancel</button>
              <button
                className="btn btn-primary"
                style={confirm.danger ? { background: 'var(--danger)' } : {}}
                onClick={() => { const fn = confirm.onOk; setConfirm(null); fn?.(); }}
              >
                {confirm.okLabel || 'OK'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Hidden file input */}
      <input type="file" id="import-file" accept=".json" style={{ display: 'none' }} onChange={processImport} />

      <BottomNav active="settings" />
      <Toast />
    </div>
  );
}
