/* ══════════════════════════════════════════
   Inkwell — src/components/Fab.jsx

   Floating Action Button — opens Create menu.
   Actions: new note, new notebook, new tag, image/voice placeholder.
   ══════════════════════════════════════════ */

import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAppStore } from '../store/AppContext';
import { genId } from '../utils/helpers';

const COLORS = ['#E07B39', '#5B6AF0', '#2E9E6B', '#7C4FD4', '#D64040', '#E8A820'];

export default function Fab() {
  const navigate = useNavigate();
  const { saveNotebook } = useAppStore();
  const [open, setOpen] = useState(false);
  const [quickModal, setQuickModal] = useState(null); // 'notebook' | 'tag' | null
  const [quickValue, setQuickValue] = useState('');

  function closeMenu() { setOpen(false); }
  function closeQuickModal() { setQuickModal(null); setQuickValue(''); }

  function createNote() {
    closeMenu();
    navigate('/editor?new=1');
  }

  function createNotebook() {
    closeMenu();
    setQuickValue('');
    setQuickModal('notebook');
  }

  function createTag() {
    closeMenu();
    setQuickValue('');
    setQuickModal('tag');
  }

  function submitQuickModal() {
    const raw = quickValue.trim();
    if (!raw) return closeQuickModal();

    if (quickModal === 'notebook') {
      saveNotebook({
        id: genId(),
        name: raw.slice(0, 32),
        color: COLORS[Math.floor(Math.random() * COLORS.length)],
        icon: 'fa-book-open',
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      });
      closeQuickModal();
      navigate('/notebooks');
      return;
    }

    if (quickModal === 'tag') {
      const clean = raw.replace(/^#/, '').replace(/,/g, '').toLowerCase().slice(0, 24);
      closeQuickModal();
      if (clean) navigate(`/editor?new=1&tag=${encodeURIComponent(clean)}`);
    }
  }

  function quickModalKeydown(e) {
    if (e.key === 'Enter') {
      e.preventDefault();
      submitQuickModal();
    }
    if (e.key === 'Escape') closeQuickModal();
  }

  return (
    <>
      {open && <div className="create-menu-backdrop" onClick={closeMenu} />}

      <div className={`create-menu${open ? ' open' : ''}`} role="dialog" aria-label="Create new">
        <div className="create-menu-handle" />
        <div className="create-menu-title">Create New</div>

        <button className="create-menu-item" onClick={createNote}>
          <span className="create-menu-icon accent"><i className="fa-solid fa-file-circle-plus" /></span>
          <span><strong>Create new note</strong><small>Start a blank note</small></span>
        </button>

        <button className="create-menu-item" onClick={createNotebook}>
          <span className="create-menu-icon blue"><i className="fa-solid fa-folder-plus" /></span>
          <span><strong>Create notebook</strong><small>Make a new notebook/category</small></span>
        </button>

        <button className="create-menu-item" onClick={createTag}>
          <span className="create-menu-icon green"><i className="fa-solid fa-tag" /></span>
          <span><strong>Create tag</strong><small>Open a new note with this tag</small></span>
        </button>

        <button className="create-menu-item" onClick={() => { closeMenu(); navigate('/search'); }}>
          <span className="create-menu-icon purple"><i className="fa-solid fa-magnifying-glass" /></span>
          <span><strong>Search notes</strong><small>Find notes, notebooks, and tags</small></span>
        </button>

        <div className="create-menu-row">
          <button className="create-mini" onClick={() => { closeMenu(); navigate('/editor?new=1&type=image'); }}><i className="fa-solid fa-image" /> Image</button>
          <button className="create-mini" onClick={() => { closeMenu(); navigate('/editor?new=1&type=voice'); }}><i className="fa-solid fa-microphone" /> Voice</button>
          <button className="create-mini" onClick={() => { closeMenu(); navigate('/editor?new=1&type=checklist'); }}><i className="fa-solid fa-square-check" /> Checklist</button>
        </div>
      </div>

      <button
        className={`fab${open ? ' open' : ''}`}
        onClick={() => setOpen(v => !v)}
        title="Create New"
        aria-label="Create New"
        aria-expanded={open}
      >
        <i className={`fa-solid ${open ? 'fa-xmark' : 'fa-plus'}`} />
      </button>

      {quickModal && (
        <div className="modal-overlay show" onClick={(e) => { if (e.target === e.currentTarget) closeQuickModal(); }}>
          <div className="modal quick-create-modal">
            <div className="quick-create-head">
              <div className={`quick-create-icon ${quickModal === 'notebook' ? 'accent' : 'green'}`}>
                <i className={`fa-solid ${quickModal === 'notebook' ? 'fa-book-open' : 'fa-tag'}`} />
              </div>
              <div>
                <div className="modal-title">{quickModal === 'notebook' ? 'Create notebook' : 'Create tag'}</div>
                <div className="modal-sub">
                  {quickModal === 'notebook'
                    ? 'Add a new notebook that matches the Inkwell UI.'
                    : 'Create a new note and save it with this tag.'}
                </div>
              </div>
            </div>

            <div className="modal-label">{quickModal === 'notebook' ? 'Notebook name' : 'Tag name'}</div>
            <input
              className="modal-input"
              placeholder={quickModal === 'notebook' ? 'e.g. Work, Ideas…' : 'e.g. code, github…'}
              value={quickValue}
              onChange={(e) => setQuickValue(e.target.value)}
              onKeyDown={quickModalKeydown}
              autoFocus
              maxLength={quickModal === 'notebook' ? 32 : 24}
            />

            <div className="modal-actions">
              <button className="btn btn-ghost" onClick={closeQuickModal}>Cancel</button>
              <button className="btn btn-primary" onClick={submitQuickModal}>Save</button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
