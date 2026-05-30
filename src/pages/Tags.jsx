/* ══════════════════════════════════════════
   Inkwell — src/pages/Tags.jsx
   ══════════════════════════════════════════ */

import { useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import TopBar    from '../components/TopBar';
import BottomNav from '../components/BottomNav';
import Fab       from '../components/Fab';
import NoteCard  from '../components/NoteCard';
import EmptyState from '../components/EmptyState';
import Toast     from '../components/Toast';
import { useAppStore } from '../store/AppContext';
import { sortNotes } from '../store/storage';

export default function Tags() {
  const navigate      = useNavigate();
  const [params]      = useSearchParams();
  const { notes }     = useAppStore();

  const [activeTag, setActiveTag] = useState(params.get('tag') || null);

  // Build tag map: { tagName: count }
  const tagMap = {};
  notes.forEach(n => (n.tags || []).forEach(t => { tagMap[t] = (tagMap[t] || 0) + 1; }));
  const tags = Object.entries(tagMap).sort((a, b) => a[0].localeCompare(b[0]));

  const allSorted = sortNotes(notes);
  const filtered  = activeTag ? allSorted.filter(n => (n.tags || []).includes(activeTag)) : [];

  function selectTag(t) {
    setActiveTag(prev => prev === t ? null : t);
  }

  const actions = (
    <button className="icon-btn" onClick={() => navigate('/search')} title="Search">
      <i className="fa-solid fa-magnifying-glass" />
    </button>
  );

  return (
    <div className="app-shell">
      <TopBar back title="Tags" actions={actions} />

      <main className="page-content compact-page tags-page">

        {/* ── Tag cloud ── */}
        <div className="sec-header" style={{ marginTop: 0 }}>
          <div className="sec-title">
            All tags <span className="count-badge">{tags.length}</span>
          </div>
        </div>

        {tags.length === 0 ? (
          <div className="tags-help-card">
            <i className="fa-solid fa-circle-info" />
            No tags yet. Add tags while editing a note.
          </div>
        ) : (
          <div className="all-tags-chip-row">
            {tags.map(([t, c]) => (
              <button
                type="button"
                key={t}
                className={`all-tag-chip${activeTag === t ? ' active' : ''}`}
                onClick={() => selectTag(t)}
                title={t}
              >
                <i className="fa-solid fa-tag" />
                <span className="all-tag-name">{t}</span>
                <span className="all-tag-count">{c}</span>
              </button>
            ))}
          </div>
        )}

        {/* ── Filtered results ── */}
        {activeTag ? (
          <div>
            <div className="tag-header-row">
              <div className="sec-title">Notes</div>
              <span className="count-badge">{filtered.length}</span>
            </div>

            <div style={{ marginBottom: '14px' }}>
              <span className="active-tag-label active-note-tag">
                <i className="fa-solid fa-tag" />
                <span>{activeTag}</span>
                <span className="rm" onClick={() => setActiveTag(null)}>
                  <i className="fa-solid fa-xmark" />
                </span>
              </span>
            </div>

            {filtered.length === 0 ? (
              <EmptyState
                icon="fa-solid fa-tag"
                title="No notes with this tag"
                sub={`Add the tag "${activeTag}" to a note in the editor.`}
              />
            ) : (
              filtered.map(n => <NoteCard key={n.id} note={n} />)
            )}
          </div>
        ) : (
          /* ── All notes (default view) ── */
          <div>
            <div className="sec-header">
              <div className="sec-title">
                All notes <span className="count-badge">{allSorted.length}</span>
              </div>
            </div>
            {allSorted.length === 0 ? (
              <EmptyState
                icon="fa-regular fa-note-sticky"
                title="No notes yet"
                sub="Tap + to write your first note."
              />
            ) : (
              allSorted.map(n => <NoteCard key={n.id} note={n} />)
            )}
          </div>
        )}
      </main>

      <Fab />
      <BottomNav active="tags" />
      <Toast />
    </div>
  );
}
