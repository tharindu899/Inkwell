/* ══════════════════════════════════════════
   Inkwell — src/pages/Notebooks.jsx
   ══════════════════════════════════════════ */

import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import TopBar    from '../components/TopBar';
import BottomNav from '../components/BottomNav';
import Toast, { showToast } from '../components/Toast';
import EmptyState from '../components/EmptyState';
import { useAppStore } from '../store/AppContext';
import { noteCountForNotebook } from '../store/storage';
import { genId, escH } from '../utils/helpers';

const COLORS = ['#c9a96e','#6090e0','#60ad82','#e06060','#9b7de8','#e07860','#60c8d8','#d4b0ff'];
const ICONS  = [
  'fa-user','fa-briefcase','fa-lightbulb','fa-heart','fa-star','fa-book',
  'fa-music','fa-camera','fa-globe','fa-code','fa-plane','fa-coffee',
  'fa-rocket','fa-leaf','fa-fire','fa-flask','fa-palette','fa-graduation-cap',
];

const EMPTY_FORM = { name: '', color: COLORS[0], icon: ICONS[0] };

const NB_SORTS = [
  { key: 'name',    label: 'Name'    },
  { key: 'count',   label: 'Notes'   },
  { key: 'created', label: 'Created' },
];

export default function Notebooks() {
  const navigate = useNavigate();
  const { notebooks, saveNotebook, deleteNotebook } = useAppStore();

  const [modal,    setModal]    = useState(false);
  const [editingId,setEditingId]= useState(null);
  const [form,     setForm]     = useState(EMPTY_FORM);
  const [ctxId,    setCtxId]    = useState(null);
  const [deleteId, setDeleteId] = useState(null);
  const [delModal, setDelModal] = useState(false);
  const [nbSort,   setNbSort]   = useState('name');

  /* ── Sort notebooks ── */
  function sortedNotebooks() {
    const arr = [...notebooks];
    if (nbSort === 'name')    return arr.sort((a, b) => (a.name || '').localeCompare(b.name || ''));
    if (nbSort === 'count')   return arr.sort((a, b) => noteCountForNotebook(b.id) - noteCountForNotebook(a.id));
    if (nbSort === 'created') return arr.sort((a, b) => new Date(b.createdAt || 0) - new Date(a.createdAt || 0));
    return arr;
  }

  /* ── Modal helpers ── */
  function openModal(id = null) {
    const nb = id ? notebooks.find(n => n.id === id) : null;
    setEditingId(id);
    setForm(nb ? { name: nb.name, color: nb.color, icon: nb.icon } : EMPTY_FORM);
    setModal(true);
  }

  function submitNb() {
    if (!form.name.trim()) return;
    const nb = editingId
      ? { ...notebooks.find(n => n.id === editingId), ...form }
      : { id: genId(), createdAt: new Date().toISOString(), ...form };
    saveNotebook(nb);
    setModal(false);
    showToast(editingId ? 'Notebook updated' : 'Notebook created', 'fa-book-open');
  }

  /* ── Context sheet helpers ── */
  function doDelete() {
    if (!deleteId) return;
    deleteNotebook(deleteId);
    setCtxId(null);
    setDeleteId(null);
    setDelModal(false);
    showToast('Notebook deleted', 'fa-trash-can');
  }

  const actions = (
    <button className="icon-btn" onClick={() => openModal()} title="New notebook">
      <i className="fa-solid fa-plus" />
    </button>
  );

  return (
    <div className="app-shell">
      <TopBar back title="Notebooks" actions={actions} />

      <main className="page-content">

        {/* Total bar */}
        <div className="nb-total-bar">
          <span className="t-label">Total notebooks</span>
          <span className="t-num">{notebooks.length}</span>
        </div>

        {/* Sort bar */}
        <div className="sort-row" style={{ marginBottom: 8 }}>
          <span>Sort by</span>
          {NB_SORTS.map(s => (
            <button
              key={s.key}
              className={`sort-btn${nbSort === s.key ? ' on' : ''}`}
              onClick={() => setNbSort(s.key)}
            >{s.label}</button>
          ))}
        </div>

        {/* Grid */}
        {notebooks.length === 0 ? (
          <EmptyState
            icon="fa-solid fa-book-open"
            title="No notebooks yet"
            sub="Create a notebook to organise your notes by topic, project, or mood."
            action={
              <button className="btn btn-primary" onClick={() => openModal()}>
                Create Notebook
              </button>
            }
          />
        ) : (
          <div className="nb-grid">
            {sortedNotebooks().map((nb, i) => {
              const count = noteCountForNotebook(nb.id);
              return (
                <div
                  key={nb.id}
                  className="nb-card"
                  style={{ '--nb-c': nb.color, animationDelay: `${i * 0.04}s` }}
                  onClick={() => navigate(`/notebooks/${nb.id}`)}
                >
                  <button
                    className="nb-menu"
                    onClick={e => { e.stopPropagation(); setCtxId(nb.id); }}
                  >
                    <i className="fa-solid fa-ellipsis-vertical" />
                  </button>
                  <div className="nb-icon">
                    <i className={`fa-solid ${nb.icon}`} />
                  </div>
                  <div className="nb-name">{nb.name}</div>
                  <div className="nb-count">{count} note{count !== 1 ? 's' : ''}</div>
                </div>
              );
            })}
          </div>
        )}
      </main>

      {/* FAB */}
      <button className="fab" onClick={() => openModal()} title="New Notebook">
        <i className="fa-solid fa-plus" />
      </button>

      <BottomNav active="notebooks" />

      {/* ── Create / Edit Modal ── */}
      {modal && (
        <div className="modal-overlay show">
          <div className="modal">
            <div className="modal-title">{editingId ? 'Edit Notebook' : 'New Notebook'}</div>
            <div className="modal-sub">
              {editingId ? 'Update name, colour, or icon.' : 'Name it, pick a colour and an icon.'}
            </div>

            <div className="modal-label">Name</div>
            <input
              className="modal-input"
              placeholder="e.g. Work, Ideas…"
              maxLength={32}
              value={form.name}
              onChange={e => setForm(f => ({ ...f, name: e.target.value }))}
              autoFocus
            />

            <div className="modal-label">Colour</div>
            <div className="color-row">
              {COLORS.map(c => (
                <div
                  key={c}
                  className={`c-dot${form.color === c ? ' on' : ''}`}
                  style={{ background: c }}
                  onClick={() => setForm(f => ({ ...f, color: c }))}
                />
              ))}
            </div>

            <div className="modal-label">Icon</div>
            <div className="icon-grid">
              {ICONS.map(ic => (
                <div
                  key={ic}
                  className={`icon-opt${form.icon === ic ? ' on' : ''}`}
                  onClick={() => setForm(f => ({ ...f, icon: ic }))}
                >
                  <i className={`fa-solid ${ic}`} />
                </div>
              ))}
            </div>

            <div className="modal-actions">
              <button className="btn btn-ghost" onClick={() => setModal(false)}>Cancel</button>
              <button className="btn btn-primary" onClick={submitNb}>Save</button>
            </div>
          </div>
        </div>
      )}

      {/* ── Context Sheet ── */}
      {ctxId && (
        <>
          <div className="sheet-overlay show" onClick={() => setCtxId(null)} />
          <div className="sheet show">
            <div className="sheet-handle" />
            <div className="sheet-title">
              {notebooks.find(n => n.id === ctxId)?.name || 'Notebook'}
            </div>
            <div className="sh-item" onClick={() => { setCtxId(null); openModal(ctxId); }}>
              <i className="fa-regular fa-pen-to-square" />Edit notebook
            </div>
            <div className="sh-item" onClick={() => { setCtxId(null); navigate(`/notebooks/${ctxId}`); }}>
              <i className="fa-regular fa-folder-open" />View notes
            </div>
            <div className="sh-divider" />
            <div className="sh-item danger" onClick={() => { setDeleteId(ctxId); setCtxId(null); setDelModal(true); }}>
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
              Notes in this notebook will not be deleted — they'll just become unorganised.
            </div>
            <div className="modal-actions">
              <button className="btn btn-ghost" onClick={() => { setDelModal(false); setDeleteId(null); }}>Cancel</button>
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
