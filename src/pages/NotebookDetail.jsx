/* ══════════════════════════════════════════
   Inkwell — src/pages/NotebookDetail.jsx
   ══════════════════════════════════════════ */

import { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import TopBar    from '../components/TopBar';
import BottomNav from '../components/BottomNav';
import NoteCard  from '../components/NoteCard';
import EmptyState from '../components/EmptyState';
import Toast, { showToast } from '../components/Toast';
import { useAppStore } from '../store/AppContext';
import { sortNotes } from '../store/storage';

export default function NotebookDetail() {
  const { id }       = useParams();
  const navigate     = useNavigate();
  const { notes, notebooks, sortOrder, setSortOrder, deleteNotebook } = useAppStore();

  const [ctxOpen,   setCtxOpen]   = useState(false);
  const [delModal,  setDelModal]  = useState(false);

  const nb = notebooks.find(n => n.id === id);
  if (!nb) { navigate('/notebooks', { replace: true }); return null; }

  const nbNotes  = sortNotes(notes.filter(n => n.notebookId === id));
  const count    = nbNotes.length;

  const SORTS = [
    { key: 'modified', label: 'Modified' },
    { key: 'created',  label: 'Created'  },
    { key: 'title',    label: 'Title'    },
    { key: 'tags',     label: 'Tags'     },
  ];

  function doDelete() {
    deleteNotebook(id);
    navigate('/notebooks');
  }

  const actions = (
    <button className="icon-btn" onClick={() => setCtxOpen(true)} title="Options">
      <i className="fa-solid fa-ellipsis-vertical" />
    </button>
  );

  const heroStyle = { '--nb-color': nb.color };

  return (
    <div className="app-shell">
      <TopBar back title={nb.name} actions={actions} />

      {/* ── Notebook hero ── */}
      <div className="nb-hero" style={heroStyle}>
        <div
          className="nb-hero-icon"
          style={{ color: nb.color, background: `${nb.color}18`, border: `1px solid ${nb.color}44` }}
        >
          <i className={`fa-solid ${nb.icon}`} />
        </div>
        <div>
          <div className="nb-hero-name">{nb.name}</div>
          <div className="nb-hero-count">{count} note{count !== 1 ? 's' : ''}</div>
        </div>
      </div>

      <main className="page-content notebook-detail-page">

        {/* Sort row */}
        <div className="sort-row" style={{ marginTop: '6px' }}>
          <span>Sort by</span>
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

        {/* Notes */}
        {nbNotes.length === 0 ? (
          <EmptyState
            icon="fa-regular fa-note-sticky"
            title="No notes yet"
            sub="Tap + to write your first note in this notebook."
          />
        ) : (
          nbNotes.map(n => <NoteCard key={n.id} note={n} />)
        )}
      </main>

      {/* FAB — new note pre-assigned to this notebook */}
      <button
        className="fab"
        onClick={() => navigate(`/editor?new=1&notebook=${id}`)}
        title="New Note in Notebook"
      >
        <i className="fa-solid fa-plus" />
      </button>

      <BottomNav active="notebooks" />

      {/* ── Context Sheet ── */}
      {ctxOpen && (
        <>
          <div className="sheet-overlay show" onClick={() => setCtxOpen(false)} />
          <div className="sheet show">
            <div className="sheet-handle" />
            <div className="sheet-title">{nb.name}</div>
            <div className="sh-item" onClick={() => navigate('/notebooks')}>
              <i className="fa-solid fa-table-cells-large" />All notebooks
            </div>
            <div className="sh-item" onClick={() => { setCtxOpen(false); navigate('/search'); }}>
              <i className="fa-solid fa-magnifying-glass" />Search in all notes
            </div>
            <div className="sh-divider" />
            <div className="sh-item danger" onClick={() => { setCtxOpen(false); setDelModal(true); }}>
              <i className="fa-regular fa-trash-can" />Delete notebook
            </div>
          </div>
        </>
      )}

      {/* ── Delete Confirm ── */}
      {delModal && (
        <div className="modal-overlay show">
          <div className="modal">
            <div className="modal-title">Delete notebook?</div>
            <div className="modal-sub">
              Notes in this notebook will stay but won't be in any notebook.
            </div>
            <div className="modal-actions">
              <button className="btn btn-ghost" onClick={() => setDelModal(false)}>Cancel</button>
              <button className="btn btn-primary" style={{ background: 'var(--danger)' }} onClick={doDelete}>
                Delete
              </button>
            </div>
          </div>
        </div>
      )}

      <Toast />
    </div>
  );
}
