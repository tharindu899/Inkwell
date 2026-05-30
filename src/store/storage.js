/* ══════════════════════════════════════════
   Inkwell — src/store/storage.js
   All localStorage keys + CRUD for notes,
   notebooks, profile, and recent searches.
   ══════════════════════════════════════════ */

// ─── Storage Keys ─────────────────────────
export const K = {
  notes:     'iw_notes',
  notebooks: 'iw_notebooks',
  theme:     'iw_theme',
  profile:   'iw_profile',
  searches:  'iw_searches',
  sortOrder: 'iw_sort',
  fontSize:  'iw_fontSize',
};

// ─── Sort Order ───────────────────────────
export function getSortOrder() {
  return localStorage.getItem(K.sortOrder) || 'modified';
}
export function setSortOrder(s) {
  localStorage.setItem(K.sortOrder, s);
}

// ─── Notes CRUD ───────────────────────────
export function getNotes() {
  try { return JSON.parse(localStorage.getItem(K.notes) || '[]'); }
  catch { return []; }
}
export function saveNotes(arr) {
  localStorage.setItem(K.notes, JSON.stringify(arr));
}
export function getNote(id) {
  return getNotes().find(n => n.id === id) || null;
}
export function saveNote(note) {
  const arr = getNotes();
  const i   = arr.findIndex(n => n.id === note.id);
  if (i >= 0) arr[i] = note; else arr.push(note);
  saveNotes(arr);
}
export function deleteNote(id) {
  saveNotes(getNotes().filter(n => n.id !== id));
}

// ─── Notebooks CRUD ───────────────────────
export function getNotebooks() {
  try { return JSON.parse(localStorage.getItem(K.notebooks) || '[]'); }
  catch { return []; }
}
export function saveNotebooks(arr) {
  localStorage.setItem(K.notebooks, JSON.stringify(arr));
}
export function getNotebook(id) {
  return getNotebooks().find(n => n.id === id) || null;
}
export function saveNotebook(nb) {
  const arr = getNotebooks();
  const i   = arr.findIndex(n => n.id === nb.id);
  if (i >= 0) arr[i] = nb; else arr.push(nb);
  saveNotebooks(arr);
}
export function deleteNotebook(id) {
  saveNotebooks(getNotebooks().filter(n => n.id !== id));
  // Detach notes that belonged to this notebook
  saveNotes(
    getNotes().map(n => n.notebookId === id ? { ...n, notebookId: null } : n)
  );
}
export function noteCountForNotebook(id) {
  return getNotes().filter(n => n.notebookId === id).length;
}

// ─── Profile ──────────────────────────────
const DEFAULT_PROFILE = {
  name:     'Your Name',
  email:    'you@inkwell.app',
  joinDate: null,
};

export function getProfile() {
  try {
    const p = JSON.parse(localStorage.getItem(K.profile) || '{}');
    return { ...DEFAULT_PROFILE, ...p };
  } catch {
    return { ...DEFAULT_PROFILE };
  }
}
export function saveProfile(p) {
  if (!p.joinDate) p.joinDate = new Date().toISOString();
  localStorage.setItem(K.profile, JSON.stringify(p));
}

// ─── Recent Searches ──────────────────────
export function getSearches() {
  try { return JSON.parse(localStorage.getItem(K.searches) || '[]'); }
  catch { return []; }
}
export function addSearch(q) {
  if (!q.trim()) return;
  let arr = getSearches().filter(s => s !== q.trim());
  arr.unshift(q.trim());
  localStorage.setItem(K.searches, JSON.stringify(arr.slice(0, 8)));
}
export function clearSearches() {
  localStorage.removeItem(K.searches);
}

// ─── Search / Filter ──────────────────────
import { stripHtml } from '../utils/helpers';

export function searchNotes(query, mode = 'all') {
  if (!query.trim()) return [];
  const q = query.trim().toLowerCase();
  return getNotes().filter(n => {
    if (mode === 'title')   return (n.title || '').toLowerCase().includes(q);
    if (mode === 'content') return stripHtml(n.content || '').toLowerCase().includes(q);
    if (mode === 'tags')    return (n.tags || []).some(t => t.toLowerCase().includes(q));
    // mode === 'all'
    return (
      (n.title || '').toLowerCase().includes(q) ||
      stripHtml(n.content || '').toLowerCase().includes(q) ||
      (n.tags || []).some(t => t.toLowerCase().includes(q))
    );
  });
}

// ─── Stats ────────────────────────────────
export function getStats() {
  const notes     = getNotes();
  const notebooks = getNotebooks();
  const words     = notes.reduce((s, n) => s + (n.wordCount || 0), 0);
  return { notes: notes.length, notebooks: notebooks.length, words };
}

// ─── Sort Notes ───────────────────────────
export function sortNotes(arr) {
  const order = getSortOrder();
  return [...arr].sort((a, b) => {
    // Pinned notes always float to the top regardless of sort order.
    // When the user manually reorders pinned notes, pinOrder controls that top group.
    if (!!b.pinned !== !!a.pinned) return b.pinned ? 1 : -1;
    if (a.pinned && b.pinned) {
      const ao = Number.isFinite(a.pinOrder) ? a.pinOrder : 999999;
      const bo = Number.isFinite(b.pinOrder) ? b.pinOrder : 999999;
      if (ao !== bo) return ao - bo;
    }

    if (order === 'created') return new Date(b.createdAt) - new Date(a.createdAt);
    if (order === 'title')   return (a.title || '').localeCompare(b.title || '');
    if (order === 'tags') {
      const at = ((a.tags || [])[0] || '').toLowerCase();
      const bt = ((b.tags || [])[0] || '').toLowerCase();
      const byTag = at.localeCompare(bt);
      if (byTag !== 0) return byTag;
      return new Date(b.updatedAt) - new Date(a.updatedAt);
    }
    return new Date(b.updatedAt) - new Date(a.updatedAt); // default: modified
  });
}
