/* ══════════════════════════════════════════
   Inkwell — src/store/AppContext.jsx
   Global state via React Context + useReducer.
   Wraps the app so any component can read or
   mutate notes / notebooks without prop-drilling.
   ══════════════════════════════════════════ */

import { createContext, useContext, useReducer, useEffect, useRef } from 'react';
import {
  getNotes,
  saveNotes,
  saveNote,
  deleteNote,
  getNotebooks,
  saveNotebooks,
  saveNotebook,
  deleteNotebook,
  getSortOrder,
  getProfile,
  saveProfile,
  setSortOrder,
  sortNotes,
} from './storage';
import { seedIfEmpty } from '../utils/seed';
import { useAuth } from '../auth/AuthContext';
import { backupToDrive, restoreFromDrive } from '../auth/googleDrive';

// ─── Initial state loader ─────────────────
function loadState() {
  seedIfEmpty();
  return {
    notes:     getNotes(),
    notebooks: getNotebooks(),
    sortOrder: getSortOrder(),
  };
}

// ─── Reducer ──────────────────────────────
function reducer(state, action) {
  switch (action.type) {

    case 'SAVE_NOTE': {
      saveNote(action.payload);
      return { ...state, notes: getNotes() };
    }

    case 'DELETE_NOTE': {
      deleteNote(action.payload);
      return { ...state, notes: getNotes() };
    }

    case 'SAVE_NOTEBOOK': {
      saveNotebook(action.payload);
      return { ...state, notebooks: getNotebooks() };
    }

    case 'DELETE_NOTEBOOK': {
      deleteNotebook(action.payload);
      // deleteNotebook also detaches notes → reload both
      return { ...state, notebooks: getNotebooks(), notes: getNotes() };
    }

    case 'SET_SORT': {
      setSortOrder(action.payload);
      return { ...state, sortOrder: action.payload };
    }

    case 'RELOAD': {
      return { ...state, notes: getNotes(), notebooks: getNotebooks() };
    }

    default:
      return state;
  }
}

// ─── Context ──────────────────────────────
const AppContext = createContext(null);

function isWelcomeOnly(notes, notebooks) {
  const realNotes = (notes || []).filter(n => n?.id !== 'welcome-note');
  return realNotes.length === 0 && (notebooks || []).length === 0;
}

function newestItem(a, b) {
  const at = new Date(a?.updatedAt || a?.createdAt || 0).getTime();
  const bt = new Date(b?.updatedAt || b?.createdAt || 0).getTime();
  return bt > at ? b : a;
}

function mergeById(local = [], cloud = []) {
  const map = new Map();
  local.forEach(item => item?.id && map.set(item.id, item));
  cloud.forEach(item => {
    if (!item?.id) return;
    map.set(item.id, map.has(item.id) ? newestItem(map.get(item.id), item) : item);
  });
  return [...map.values()];
}

// ─── Provider ─────────────────────────────
export function AppProvider({ children }) {
  const [state, dispatch] = useReducer(reducer, null, loadState);
  const { user, isLoggedIn, getToken, getCachedToken } = useAuth();
  const backupTimerRef = useRef(null);
  const firstBackupRunRef = useRef(true);
  const loginSyncKeyRef = useRef(null);

  // Sync across browser tabs
  useEffect(() => {
    const handler = (e) => {
      if (['iw_notes', 'iw_notebooks'].includes(e.key)) {
        dispatch({ type: 'RELOAD' });
      }
    };
    window.addEventListener('storage', handler);
    return () => window.removeEventListener('storage', handler);
  }, []);

  // Automatic login/new-device sync.
  // On first login/open, restore the Drive backup and merge it with local data.
  // This makes a fresh install pull notes automatically, while protecting local notes.
  useEffect(() => {
    if (!isLoggedIn || !user?.email) return undefined;

    const syncKey = `${user.email}:${user.savedAt || ''}`;
    if (loginSyncKeyRef.current === syncKey) return undefined;
    loginSyncKeyRef.current = syncKey;

    let cancelled = false;

    async function runLoginSync() {
      try {
        const token = await getToken();
        if (!token || cancelled) return;

        const cloud = await restoreFromDrive(token);
        if (!cloud || cancelled) return;

        const localNotes = getNotes();
        const localNotebooks = getNotebooks();
        const cloudNotes = Array.isArray(cloud.notes) ? cloud.notes : [];
        const cloudNotebooks = Array.isArray(cloud.notebooks) ? cloud.notebooks : [];

        const freshLocal = isWelcomeOnly(localNotes, localNotebooks);
        const mergedNotes = freshLocal
          ? cloudNotes
          : mergeById(localNotes.filter(n => n?.id !== 'welcome-note'), cloudNotes);
        const mergedNotebooks = freshLocal
          ? cloudNotebooks
          : mergeById(localNotebooks, cloudNotebooks);

        if (cloudNotes.length || cloudNotebooks.length) {
          saveNotes(mergedNotes);
          saveNotebooks(mergedNotebooks);
          if (cloud.profile) saveProfile(cloud.profile);
          localStorage.setItem('iw_last_auto_restore', new Date().toISOString());
          localStorage.setItem('iw_last_cloud_sync', cloud._syncedAt || '');
          dispatch({ type: 'RELOAD' });

          // Write the merged result back so this device and Drive become equal.
          await backupToDrive(token, {
            version: 2,
            app: 'Inkwell',
            backedUpAt: new Date().toISOString(),
            notes: getNotes(),
            notebooks: getNotebooks(),
            profile: getProfile(),
          });
          localStorage.setItem('iw_last_auto_backup', new Date().toISOString());
        }
      } catch (err) {
        console.warn('[Inkwell] Login sync failed:', err);
      }
    }

    runLoginSync();
    return () => { cancelled = true; };
  }, [isLoggedIn, user?.email, user?.savedAt, getToken]);

  // Automatic Drive backup after every local note/notebook save.
  // Debounced so auto-save typing still uploads the newest version only.
  useEffect(() => {
    if (firstBackupRunRef.current) {
      firstBackupRunRef.current = false;
      return undefined;
    }

    if (!isLoggedIn) return undefined;

    clearTimeout(backupTimerRef.current);
    backupTimerRef.current = setTimeout(async () => {
      try {
        const token = getCachedToken();
        if (!token) return;

        window.dispatchEvent(new CustomEvent('iw-sync', { detail: { status: 'syncing' } }));

        await backupToDrive(token, {
          version: 2,
          app: 'Inkwell',
          backedUpAt: new Date().toISOString(),
          notes: getNotes(),
          notebooks: getNotebooks(),
          profile: getProfile(),
        });

        localStorage.setItem('iw_last_auto_backup', new Date().toISOString());
        window.dispatchEvent(new CustomEvent('iw-sync', { detail: { status: 'synced' } }));
      } catch (err) {
        console.warn('[Inkwell] Auto backup failed:', err);
        window.dispatchEvent(new CustomEvent('iw-sync', { detail: { status: 'error' } }));
      }
    }, 1200);

    return () => clearTimeout(backupTimerRef.current);
  }, [state.notes, state.notebooks, isLoggedIn, getCachedToken]);

  // Convenience selectors
  const sortedNotes = sortNotes(state.notes);

  const value = {
    notes:         state.notes,
    sortedNotes,
    notebooks:     state.notebooks,
    sortOrder:     state.sortOrder,
    dispatch,
    // Shorthand action creators
    saveNote:      (note)   => dispatch({ type: 'SAVE_NOTE',      payload: note }),
    deleteNote:    (id)     => dispatch({ type: 'DELETE_NOTE',    payload: id   }),
    saveNotebook:  (nb)     => dispatch({ type: 'SAVE_NOTEBOOK',  payload: nb   }),
    deleteNotebook:(id)     => dispatch({ type: 'DELETE_NOTEBOOK',payload: id   }),
    setSortOrder:  (order)  => dispatch({ type: 'SET_SORT',       payload: order}),
  };

  return <AppContext.Provider value={value}>{children}</AppContext.Provider>;
}

// ─── Consumer hook ────────────────────────
export function useAppStore() {
  const ctx = useContext(AppContext);
  if (!ctx) throw new Error('useAppStore must be used inside <AppProvider>');
  return ctx;
}
