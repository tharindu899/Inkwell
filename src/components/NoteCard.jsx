/* ══════════════════════════════════════════
   Inkwell — src/components/NoteCard.jsx

   Displays a single note as a tappable card.
   Long-press enters multi-select mode OR (if
   already in multi-select) toggles this card.
   While in single-card menu mode a slide-up
   strip shows Pin / Delete actions.

   Props:
     note           {object}   — note object from storage
     selectMode     {bool}     — true while multi-select is active
     selected       {bool}     — true if this card is currently selected
     onLongPress    {fn()}     — called to enter select mode (first long-press)
     onToggleSelect {fn(id)}   — toggle this card's selection
   ══════════════════════════════════════════ */

import { useNavigate }                       from 'react-router-dom';
import { useRef, useState, useEffect }       from 'react';
import { getNotebook, saveNote, deleteNote, getNotes } from '../store/storage';
import { stripHtml, formatDate }             from '../utils/helpers';
import { useAppStore }                       from '../store/AppContext';
import { showToast }                         from './Toast';
import { haptic }                            from '../utils/haptics';

// ─── tuning ────────────────────────────────
const LONG_PRESS_MS  = 480;
const MOVE_CANCEL_PX = 9;

function stripMarkdownForPreview(source = '') {
  let text = String(source || '').replace(/\r\n/g, '\n');

  // README/GitHub HTML that was pasted as source should become readable preview text.
  text = text
    .replace(/<img\b[^>]*alt=["']([^"']*)["'][^>]*>/gi, '$1')
    .replace(/<img\b[^>]*>/gi, 'image')
    .replace(/<a\b[^>]*href=["']([^"']+)["'][^>]*>([\s\S]*?)<\/a>/gi, (m, href, label) => String(label || href).replace(/<[^>]+>/g, ' ').trim() || href)
    .replace(/<summary\b[^>]*>([\s\S]*?)<\/summary>/gi, '$1')
    .replace(/<br\s*\/?\s*>/gi, '\n')
    .replace(/<\/p>|<\/div>|<\/h[1-6]>|<\/li>|<\/tr>/gi, '\n')
    .replace(/<[^>]+>/g, ' ');

  text = text
    .replace(/```[\s\S]*?```/g, ' code block ')
    .replace(/!\[([^\]]*)\]\([^)]*\)/g, '$1')
    .replace(/\[([^\]]+)\]\([^)]*\)/g, '$1')
    .replace(/^\s{0,3}#{1,6}\s+/gm, '')
    .replace(/^\s{0,3}>\s?/gm, '')
    .replace(/^\s{0,3}[-*+]\s+\[[ xX]\]\s+/gm, '')
    .replace(/^\s{0,3}[-*+]\s+/gm, '')
    .replace(/^\s{0,3}\d+[.)]\s+/gm, '')
    .replace(/[*_~`|]/g, ' ')
    .replace(/-{3,}/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();

  return text;
}

function getNotePreview(note) {
  const rawText = stripHtml(note.content || '');
  const rawLooksLikeSource = /<\/?(div|p|img|a|table|h[1-6]|ul|ol|li|blockquote|pre|code)\b/i.test(rawText)
    || /!\[[^\]]*\]\([^)]*\)|\[[^\]]+\]\([^)]*\)|^\s{0,3}#{1,6}\s+/m.test(rawText);

  if (rawLooksLikeSource || (note.markdown && note.markdown.trim())) {
    const cleaned = stripMarkdownForPreview(note.markdown || rawText);
    if (cleaned) return cleaned;
  }

  return rawText || 'No content yet.';
}

export default function NoteCard({
  note,
  selectMode     = false,
  selected       = false,
  onLongPress    = null,
  onToggleSelect = null,
  draggablePinned = false,
  onPinnedDragStart = null,
}) {
  const navigate     = useNavigate();
  const { dispatch } = useAppStore();
  const nb           = note.notebookId ? getNotebook(note.notebookId) : null;
  const preview      = getNotePreview(note);
  const tags         = (note.tags || []).slice(0, 2);

  const [menuOpen,  setMenuOpen]  = useState(false);
  const [dismissed, setDismissed] = useState(false);
  const [pressing,  setPressing]  = useState(false);

  const timerRef      = useRef(null);
  const startPos      = useRef(null);
  const didLongPress  = useRef(false);
  // Track whether the action-strip buttons are being pressed so the
  // outside-click handler doesn't fire before the button click registers.
  const actionFocused = useRef(false);

  // Close menu when tapping outside — but NOT when a button inside is pressed
  useEffect(() => {
    if (!menuOpen) return;
    function onOutside(e) {
      if (actionFocused.current) return; // button is being pressed — ignore
      setMenuOpen(false);
    }
    document.addEventListener('pointerdown', onOutside, { capture: true, once: true });
    return () => document.removeEventListener('pointerdown', onOutside, { capture: true });
  }, [menuOpen]);

  // Close single-card menu whenever multi-select mode activates
  useEffect(() => {
    if (selectMode) setMenuOpen(false);
  }, [selectMode]);

  // ── touch / pointer handlers ──────────────────────────────────────────────
  function onTouchStart(e) {
    const t = e.touches[0];
    startPos.current     = { x: t.clientX, y: t.clientY };
    didLongPress.current = false;
    setPressing(true);

    timerRef.current = setTimeout(() => {
      didLongPress.current = true;
      setPressing(false);
      haptic('medium');

      if (selectMode) {
        // Already in select mode — just toggle this card
        onToggleSelect?.(note.id);
      } else if (onLongPress) {
        // Enter multi-select and select this card as the first item
        onLongPress(note.id);
      } else {
        // Standalone usage (Search, NotebookDetail) — show inline menu
        setMenuOpen(true);
      }
    }, LONG_PRESS_MS);
  }

  function onTouchMove(e) {
    if (!startPos.current) return;
    const t  = e.touches[0];
    const dx = Math.abs(t.clientX - startPos.current.x);
    const dy = Math.abs(t.clientY - startPos.current.y);
    if (dx > MOVE_CANCEL_PX || dy > MOVE_CANCEL_PX) {
      clearTimeout(timerRef.current);
      setPressing(false);
    }
  }

  function onTouchEnd() {
    clearTimeout(timerRef.current);
    setPressing(false);
    startPos.current = null;
  }

  function onTouchCancel() {
    clearTimeout(timerRef.current);
    setPressing(false);
    startPos.current = null;
  }

  function handleCardClick(e) {
    if (didLongPress.current) { didLongPress.current = false; return; }

    if (selectMode) {
      onToggleSelect?.(note.id);
      return;
    }

    if (menuOpen) { setMenuOpen(false); return; }

    navigate(`/editor/${note.id}`);
  }

  // ── single-card actions ───────────────────────────────────────────────────
  function handlePin(e) {
    e.stopPropagation();
    const nextPinned = !note.pinned;
    const maxOrder = Math.max(-1, ...getNotes().filter(n => n.pinned && Number.isFinite(n.pinOrder)).map(n => n.pinOrder));
    const updated = {
      ...note,
      pinned: nextPinned,
      pinOrder: nextPinned ? (Number.isFinite(note.pinOrder) ? note.pinOrder : maxOrder + 1) : undefined,
      updatedAt: new Date().toISOString(),
    };
    saveNote(updated);
    dispatch({ type: 'RELOAD' });
    showToast(note.pinned ? 'Note unpinned' : 'Note pinned', 'fa-thumbtack');
    setMenuOpen(false);
    haptic('light');
  }

  function handleDelete(e) {
    e.stopPropagation();
    setMenuOpen(false);
    setDismissed(true);
    haptic('heavy');
    deleteNote(note.id);
    dispatch({ type: 'RELOAD' });
    showToast('Note deleted', 'fa-trash-can', {
      label: 'Undo',
      onClick: () => {
        saveNote(note);
        dispatch({ type: 'RELOAD' });
        setDismissed(false);
        showToast('Note restored', 'fa-rotate-left');
      },
    });
  }

  if (dismissed) return null;

  // ── styles ─────────────────────────────────────────────────────────────────
  const isScaled  = menuOpen || pressing;
  const cardStyle = {
    position:     'relative',
    marginBottom: 10,
    transform:    menuOpen ? 'scale(0.975)' : pressing ? 'scale(0.985)' : 'scale(1)',
    transition:   'transform 0.15s ease',
    borderRadius: 'var(--r)',
    overflow:     'visible',
    userSelect:   'none',
  };

  const actionStripStyle = {
    display:    'flex',
    alignItems: 'stretch',
    borderTop:  menuOpen ? '1px solid var(--border)' : '0',
    background: 'var(--bg-card, var(--surface2))',
    maxHeight:  menuOpen ? '56px' : '0',
    minHeight:  0,
    overflow:   'hidden',
    opacity:    menuOpen ? 1 : 0,
    transition: 'max-height 0.22s cubic-bezier(0.16, 1, 0.3, 1), opacity 0.12s ease',
  };

  const actionBtnBase = {
    flex:           1,
    display:        'flex',
    alignItems:     'center',
    justifyContent: 'center',
    gap:            7,
    padding:        '12px 0',
    border:         'none',
    background:     'transparent',
    cursor:         'pointer',
    fontSize:       13,
    fontWeight:     700,
    letterSpacing:  '0.01em',
    WebkitTapHighlightColor: 'transparent',
  };

  return (
    <div
      style={cardStyle}
      data-pinned-note-id={draggablePinned ? note.id : undefined}
      className={draggablePinned ? 'pinned-draggable-wrap' : undefined}
    >
      {/* ── card body ── */}
      <div
        className="note-card"
        style={{
          marginBottom: 0,
          borderBottomLeftRadius:  menuOpen ? 0 : undefined,
          borderBottomRightRadius: menuOpen ? 0 : undefined,
          // Clean select style: soft tint + small check chip, no heavy orange outline.
          background: selected ? 'color-mix(in srgb, var(--accent) 7%, var(--bg-card, var(--surface2)))' : undefined,
          borderColor: selected ? 'color-mix(in srgb, var(--accent) 42%, var(--border))' : undefined,
          boxShadow: menuOpen ? 'inset 0 0 0 1px var(--accent)' : 'none',
        }}
        onClick={handleCardClick}
        onTouchStart={onTouchStart}
        onTouchMove={onTouchMove}
        onTouchEnd={onTouchEnd}
        onTouchCancel={onTouchCancel}
        onContextMenu={e => e.preventDefault()}
        role="button"
        tabIndex={0}
        onKeyDown={e => e.key === 'Enter' && !selectMode && navigate(`/editor/${note.id}`)}
      >
        {/* Selection checkbox shown in multi-select mode */}
        {selectMode && (
          <span
            style={{
              position:     'absolute',
              top:          10,
              right:        10,
              width:        24,
              height:       24,
              borderRadius: '50%',
              border:       `1.5px solid ${selected ? 'var(--accent)' : 'var(--border)'}`,
              background:   selected ? 'var(--accent)' : 'var(--surface3)',
              display:      'flex',
              alignItems:   'center',
              justifyContent: 'center',
              transition:   'background 0.15s, border-color 0.15s',
              pointerEvents: 'none',
            }}
          >
            {selected && <i className="fa-solid fa-check" style={{ color: '#fff', fontSize: 11 }} />}
          </span>
        )}

        {draggablePinned && !selectMode && (
          <button
            className="pin-drag-handle"
            title="Drag to reorder pinned note"
            aria-label="Drag to reorder pinned note"
            onPointerDown={(e) => onPinnedDragStart?.(note.id, e)}
            onClick={(e) => e.stopPropagation()}
          >
            <i className="fa-solid fa-grip-lines" />
          </button>
        )}

        {note.pinned && !selectMode && !draggablePinned && (
          <i className="fa-solid fa-thumbtack pin-icon" />
        )}

        <div className="n-title">{note.title || 'Untitled'}</div>
        <div className="n-preview">{preview}</div>

        <div className="n-meta">
          <span className="n-date">
            <i className="fa-regular fa-clock" />
            {formatDate(note.updatedAt)}
          </span>
          {nb && (
            <span className="n-tag" style={{ color: nb.color, background: `${nb.color}22` }}>
              <i className="fa-solid fa-book-open" style={{ fontSize: '9px' }} />
              {nb.name}
            </span>
          )}
          {tags.map(t => <span key={t} className="n-tag">{t}</span>)}
        </div>
      </div>

      {/* ── long-press action strip — render only when open so no hidden divider line remains ── */}
      {!selectMode && menuOpen && (
        <div className="note-card-action-strip" style={actionStripStyle} onClick={e => e.stopPropagation()}>
          <button
            style={{ ...actionBtnBase, color: 'var(--accent)' }}
            onPointerDown={() => { actionFocused.current = true; }}
            onPointerUp={() => { actionFocused.current = false; }}
            onPointerCancel={() => { actionFocused.current = false; }}
            onClick={handlePin}
          >
            <i
              className="fa-solid fa-thumbtack"
              style={{ fontSize: 14, transform: note.pinned ? 'rotate(45deg)' : 'none', transition: 'transform .2s' }}
            />
            {note.pinned ? 'Unpin' : 'Pin'}
          </button>

          <div style={{ width: 1, background: 'var(--border)', margin: '10px 0' }} />

          <button
            style={{ ...actionBtnBase, color: '#e06060' }}
            onPointerDown={() => { actionFocused.current = true; }}
            onPointerUp={() => { actionFocused.current = false; }}
            onPointerCancel={() => { actionFocused.current = false; }}
            onClick={handleDelete}
          >
            <i className="fa-regular fa-trash-can" style={{ fontSize: 14 }} />
            Delete
          </button>
        </div>
      )}
    </div>
  );
}
