/* ══════════════════════════════════════════
   Inkwell — src/pages/Home.jsx
   ══════════════════════════════════════════ */

import { useState, useCallback, useRef } from 'react';
import TopBar      from '../components/TopBar';
import BottomNav   from '../components/BottomNav';
import Fab         from '../components/Fab';
import NoteCard    from '../components/NoteCard';
import EmptyState  from '../components/EmptyState';
import Toast       from '../components/Toast';
import { useAppStore } from '../store/AppContext';
import { getStats, sortNotes, saveNote, deleteNote, getNotes, saveNotes } from '../store/storage';
import { fmtNum } from '../utils/helpers';
import { showToast } from '../components/Toast';
import { haptic }    from '../utils/haptics';

export default function Home() {
  const { notes, sortOrder, setSortOrder, dispatch } = useAppStore();
  const stats  = getStats();
  const sorted = sortNotes(notes);
  const pinnedNotes = sorted.filter(n => n.pinned);
  const recentNotes = sorted.filter(n => !n.pinned);
  const draggingPinnedRef = useRef(null);

  // ── multi-select state ──────────────────
  const [selectMode, setSelectMode] = useState(false);
  const [selected,   setSelected]   = useState(new Set());
  const [deleteConfirmOpen, setDeleteConfirmOpen] = useState(false);

  const enterSelectMode = useCallback((firstId) => {
    setSelectMode(true);
    setSelected(new Set([firstId]));
    haptic('medium');
  }, []);

  const toggleSelect = useCallback((id) => {
    setSelected(prev => {
      const next = new Set(prev);
      next.has(id) ? next.delete(id) : next.add(id);
      return next;
    });
  }, []);

  function cancelSelectMode() {
    setDeleteConfirmOpen(false);
    setSelectMode(false);
    setSelected(new Set());
  }

  function handleBulkPin() {
    const now = new Date().toISOString();
    // If any selected note is already pinned, unpin all; otherwise pin all
    const anyPinned = [...selected].some(id => notes.find(n => n.id === id)?.pinned);
    let nextPinOrder = Math.max(-1, ...notes.filter(n => n.pinned && Number.isFinite(n.pinOrder)).map(n => n.pinOrder)) + 1;
    [...selected].forEach(id => {
      const n = notes.find(n => n.id === id);
      if (n) {
        const nextPinned = !anyPinned;
        saveNote({
          ...n,
          pinned: nextPinned,
          pinOrder: nextPinned ? (Number.isFinite(n.pinOrder) ? n.pinOrder : nextPinOrder++) : undefined,
          updatedAt: now,
        });
      }
    });
    dispatch({ type: 'RELOAD' });
    showToast(
      anyPinned
        ? `${selected.size} note${selected.size > 1 ? 's' : ''} unpinned`
        : `${selected.size} note${selected.size > 1 ? 's' : ''} pinned`,
      'fa-thumbtack'
    );
    haptic('light');
    cancelSelectMode();
  }

  function handleBulkDelete() {
    if (selected.size === 0) return;
    setDeleteConfirmOpen(true);
    haptic('light');
  }

  function confirmBulkDelete() {
    const deletedNotes = [...selected].map(id => notes.find(n => n.id === id)).filter(Boolean);
    const count = deletedNotes.length;
    if (!count) {
      setDeleteConfirmOpen(false);
      return;
    }

    deletedNotes.forEach(n => deleteNote(n.id));
    dispatch({ type: 'RELOAD' });
    setDeleteConfirmOpen(false);
    setSelectMode(false);
    setSelected(new Set());

    showToast(`${count} note${count > 1 ? 's' : ''} deleted`, 'fa-trash-can', {
      label: 'Undo',
      // Toast action stays visible for about 5 seconds.
      onClick: () => {
        deletedNotes.forEach(n => saveNote(n));
        dispatch({ type: 'RELOAD' });
        showToast(`${count} note${count > 1 ? 's' : ''} restored`, 'fa-rotate-left');
      },
    });
    haptic('heavy');
  }

  function movePinnedNote(activeId, overId) {
    if (!activeId || !overId || activeId === overId) return;

    const allNotes = getNotes();
    const pinned = sortNotes(allNotes.filter(n => n.pinned));
    const from = pinned.findIndex(n => n.id === activeId);
    const to = pinned.findIndex(n => n.id === overId);
    if (from < 0 || to < 0 || from === to) return;

    const nextPinned = [...pinned];
    const [moved] = nextPinned.splice(from, 1);
    nextPinned.splice(to, 0, moved);

    const now = new Date().toISOString();
    const pinOrderById = new Map(nextPinned.map((n, i) => [n.id, i]));
    saveNotes(allNotes.map(n => pinOrderById.has(n.id)
      ? { ...n, pinOrder: pinOrderById.get(n.id), updatedAt: n.updatedAt || now }
      : n
    ));
    dispatch({ type: 'RELOAD' });
  }

  const startPinnedDrag = useCallback((id, e) => {
    e?.preventDefault?.();
    e?.stopPropagation?.();
    draggingPinnedRef.current = id;
    haptic('light');

    const onMove = (ev) => {
      const el = document.elementFromPoint(ev.clientX, ev.clientY)?.closest?.('[data-pinned-note-id]');
      const overId = el?.getAttribute('data-pinned-note-id');
      if (overId && overId !== draggingPinnedRef.current) {
        movePinnedNote(draggingPinnedRef.current, overId);
      }
    };

    const onEnd = () => {
      draggingPinnedRef.current = null;
      window.removeEventListener('pointermove', onMove);
      window.removeEventListener('pointerup', onEnd);
      window.removeEventListener('pointercancel', onEnd);
      haptic('light');
    };

    window.addEventListener('pointermove', onMove, { passive: true });
    window.addEventListener('pointerup', onEnd, { once: true });
    window.addEventListener('pointercancel', onEnd, { once: true });
  }, [dispatch]);

  const SORTS = [
    { key: 'modified', label: 'Modified' },
    { key: 'created',  label: 'Created'  },
    { key: 'title',    label: 'Title'    },
    { key: 'tags',     label: 'Tags'     },
  ];

  // Determine label for bulk pin button
  const anyPinned = selectMode && [...selected].some(id => notes.find(n => n.id === id)?.pinned);

  return (
    <div className="app-shell">
      <TopBar greeting />

      <main className="page-content compact-page home-page">

        {/* ── Stats ── */}
        <div className="stats-row">
          <div className="stat-card">
            <span className="stat-num">{stats.notes}</span>
            <div className="stat-label">Notes</div>
          </div>
          <div className="stat-card">
            <span className="stat-num">{stats.notebooks}</span>
            <div className="stat-label">Books</div>
          </div>
          <div className="stat-card">
            <span className="stat-num">{fmtNum(stats.words)}</span>
            <div className="stat-label">Words</div>
          </div>
        </div>

        {/* ── Sort row ── */}
        {!selectMode && (
          <div className="sort-row">
            <span>Sort by</span>
            <button
              className="sort-btn select-now-btn"
              onClick={() => { setSelectMode(true); setSelected(new Set()); haptic('light'); }}
              title="Select notes on this screen"
            >
              <i className="fa-regular fa-circle-check" /> Select
            </button>
            {SORTS.map(s => (
              <button
                key={s.key}
                className={`sort-btn${sortOrder === s.key ? ' on' : ''}`}
                onClick={() => setSortOrder(s.key)}
              >
                {s.label}
              </button>
            ))}
          </div>
        )}

        {/* ── Multi-select toolbar ── */}
        {selectMode && (
          <div style={{
            display:        'flex',
            alignItems:     'center',
            gap:            8,
            padding:        '8px 0',
            marginBottom:   4,
          }}>
            {/* Cancel */}
            <button
              onClick={cancelSelectMode}
              style={{
                flex:         '0 0 auto',
                padding:      '7px 12px',
                borderRadius: 'var(--r)',
                border:       '1px solid var(--border)',
                background:   'transparent',
                color:        'var(--fg)',
                fontSize:     13,
                fontWeight:   600,
                cursor:       'pointer',
                WebkitTapHighlightColor: 'transparent',
              }}
            >
              <i className="fa-solid fa-xmark" style={{ marginRight: 5 }} />
              Cancel
            </button>

            {/* Count badge */}
            <span style={{ flex: 1, fontSize: 13, fontWeight: 600, color: 'var(--accent)' }}>
              {selected.size === 0 ? 'Tap notes to select' : `${selected.size} selected`}
            </span>

            {/* Pin / Unpin */}
            <button
              disabled={selected.size === 0}
              onClick={handleBulkPin}
              style={{
                flex:         '0 0 auto',
                padding:      '7px 12px',
                borderRadius: 'var(--r)',
                border:       'none',
                background:   'var(--accent)',
                color:        '#fff',
                fontSize:     13,
                fontWeight:   700,
                cursor:       selected.size === 0 ? 'default' : 'pointer',
                opacity:      selected.size === 0 ? 0.4 : 1,
                WebkitTapHighlightColor: 'transparent',
              }}
            >
              <i className="fa-solid fa-thumbtack" style={{ marginRight: 5, transform: anyPinned ? 'rotate(45deg)' : 'none', display: 'inline-block', transition: 'transform .2s' }} />
              {anyPinned ? 'Unpin' : 'Pin'}
            </button>

            {/* Delete */}
            <button
              disabled={selected.size === 0}
              onClick={handleBulkDelete}
              style={{
                flex:         '0 0 auto',
                padding:      '7px 12px',
                borderRadius: 'var(--r)',
                border:       'none',
                background:   '#e06060',
                color:        '#fff',
                fontSize:     13,
                fontWeight:   700,
                cursor:       selected.size === 0 ? 'default' : 'pointer',
                opacity:      selected.size === 0 ? 0.4 : 1,
                WebkitTapHighlightColor: 'transparent',
              }}
            >
              <i className="fa-regular fa-trash-can" style={{ marginRight: 5 }} />
              Delete
            </button>
          </div>
        )}

        {/* ── Notes ── */}
        {sorted.length === 0 ? (
          <EmptyState
            icon="fa-regular fa-note-sticky"
            title="No notes yet"
            sub="Tap the + button to write your first note."
          />
        ) : selectMode ? (
          sorted.map(n => (
            <NoteCard
              key={n.id}
              note={n}
              selectMode={selectMode}
              selected={selected.has(n.id)}
              onLongPress={enterSelectMode}
              onToggleSelect={toggleSelect}
            />
          ))
        ) : (
          <>
            {pinnedNotes.length > 0 && (
              <section className="notes-section pinned-notes-section">
                <div className="sec-header notes-group-header">
                  <div className="sec-title"><i className="fa-solid fa-thumbtack" /> Pinned notes <span className="count-badge">{pinnedNotes.length}</span></div>
                </div>
                {pinnedNotes.map(n => (
                  <NoteCard
                    key={n.id}
                    note={n}
                    selectMode={selectMode}
                    selected={selected.has(n.id)}
                    onLongPress={enterSelectMode}
                    onToggleSelect={toggleSelect}
                    draggablePinned
                    onPinnedDragStart={startPinnedDrag}
                  />
                ))}
              </section>
            )}

            <section className="notes-section recent-notes-section">
              <div className="sec-header notes-group-header">
                <div className="sec-title"><i className="fa-regular fa-clock" /> Recent notes <span className="count-badge">{recentNotes.length}</span></div>
              </div>
              {recentNotes.length > 0 ? recentNotes.map(n => (
                <NoteCard
                  key={n.id}
                  note={n}
                  selectMode={selectMode}
                  selected={selected.has(n.id)}
                  onLongPress={enterSelectMode}
                  onToggleSelect={toggleSelect}
                />
              )) : (
                <div className="empty-mini">No recent notes. Unpin a note or create a new one.</div>
              )}
            </section>
          </>
        )}

      </main>

      {!selectMode && <Fab />}
      {deleteConfirmOpen && (
        <div className="modal-overlay show select-delete-confirm" onClick={(e) => {
          if (e.target === e.currentTarget) setDeleteConfirmOpen(false);
        }}>
          <div className="modal select-delete-modal">
            <div className="select-delete-icon">
              <i className="fa-regular fa-trash-can" />
            </div>
            <div className="modal-title">Delete selected notes?</div>
            <div className="modal-sub">
              {selected.size} selected note{selected.size !== 1 ? 's' : ''} will be deleted.
              You can undo for 5 seconds after deleting.
            </div>
            <div className="modal-actions modal-actions-2col">
              <button className="btn btn-ghost" onClick={() => setDeleteConfirmOpen(false)}>
                Cancel
              </button>
              <button className="btn btn-danger" onClick={confirmBulkDelete}>
                Delete
              </button>
            </div>
          </div>
        </div>
      )}

      <BottomNav active="home" />
      <Toast />
    </div>
  );
}
