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

  function closeMenu() { setOpen(false); }

  function createNote() {
    closeMenu();
    navigate('/editor?new=1');
  }

  function createNotebook() {
    const name = window.prompt('Notebook name');
    if (!name || !name.trim()) return closeMenu();
    saveNotebook({
      id: genId(),
      name: name.trim(),
      color: COLORS[Math.floor(Math.random() * COLORS.length)],
      icon: 'fa-book-open',
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    });
    closeMenu();
    navigate('/notebooks');
  }

  function createTag() {
    const tag = window.prompt('Tag name');
    if (!tag || !tag.trim()) return closeMenu();
    const clean = tag.trim().replace(/^#/, '').replace(/,/g, '').toLowerCase();
    closeMenu();
    navigate(`/editor?new=1&tag=${encodeURIComponent(clean)}`);
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
    </>
  );
}
