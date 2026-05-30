/* ══════════════════════════════════════════
   Inkwell — src/pages/Search.jsx
   ══════════════════════════════════════════ */

import { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import TopBar from '../components/TopBar';
import BottomNav from '../components/BottomNav';
import Toast  from '../components/Toast';
import {
  getSearches, addSearch, clearSearches, searchNotes, getNotebook, getNotebooks,
} from '../store/storage';
import { stripHtml, formatDate, escH } from '../utils/helpers';

const MODES = ['all', 'title', 'content', 'tags'];

// Highlight matching text in a string
function highlight(str, q) {
  if (!q || !str) return str;
  const escaped = q.replace(/[-[\]{}()*+?.,\\^$|#\s]/g, '\\$&');
  const parts = str.split(new RegExp(`(${escaped})`, 'gi'));
  return parts.map((p, i) =>
    p.toLowerCase() === q.toLowerCase()
      ? <span key={i} className="highlight-match">{p}</span>
      : p
  );
}

export default function Search() {
  const navigate  = useNavigate();
  const inputRef  = useRef(null);
  const notebooks = getNotebooks();

  const [query,        setQuery]        = useState('');
  const [mode,         setMode]         = useState('all');
  const [notebookFilter, setNotebookFilter] = useState(null); // null = all notebooks
  const [results,      setResults]      = useState(null);   // null = show recent
  const [recents,      setRecents]      = useState(getSearches);

  useEffect(() => { inputRef.current?.focus(); }, []);

  function runSearch(q, m, nbId) {
    if (!q.trim()) { setResults(null); return; }
    let res = searchNotes(q, m);
    if (nbId) res = res.filter(n => n.notebookId === nbId);
    setResults(res);
  }

  function handleInput(val) {
    setQuery(val);
    runSearch(val, mode, notebookFilter);
  }

  function handleModeChange(m) {
    setMode(m);
    if (query.trim()) runSearch(query, m, notebookFilter);
  }

  function handleNotebookFilter(nbId) {
    const next = nbId === notebookFilter ? null : nbId;
    setNotebookFilter(next);
    if (query.trim()) runSearch(query, mode, next);
  }

  function handleSearch(q) {
    if (!q.trim()) return;
    addSearch(q);
    setRecents(getSearches());
    runSearch(q, mode, notebookFilter);
  }

  function clearQ() {
    setQuery('');
    setResults(null);
    inputRef.current?.focus();
  }

  function useRecent(q) {
    setQuery(q);
    handleSearch(q);
  }

  function handleClearRecent() {
    clearSearches();
    setRecents([]);
  }

  function onKeyDown(e) {
    if (e.key === 'Enter') handleSearch(query);
  }

  return (
    <div className="app-shell" style={{ display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
      <TopBar back title="Search" />

      {/* ── Search bar ── */}
      <div className="search-wrap">
        <div className="search-bar">
          <i className="fa-solid fa-magnifying-glass" />
          <input
            ref={inputRef}
            type="search"
            placeholder="Search notes, tags…"
            value={query}
            onChange={e => handleInput(e.target.value)}
            onKeyDown={onKeyDown}
            autoComplete="off"
            autoCorrect="off"
            spellCheck={false}
          />
          {query && (
            <button className="clear-btn" onClick={clearQ}>
              <i className="fa-solid fa-xmark" />
            </button>
          )}
        </div>
      </div>

      {/* ── Filter chips ── */}
      <div className="filter-row">
        {MODES.map(m => (
          <div
            key={m}
            className={`f-chip${mode === m ? ' on' : ''}`}
            onClick={() => handleModeChange(m)}
          >
            {m.charAt(0).toUpperCase() + m.slice(1)}
          </div>
        ))}
      </div>

      {/* ── Notebook filter chips ── */}
      {notebooks.length > 0 && (
        <div className="filter-row" style={{ paddingTop: 0, gap: 6 }}>
          {notebooks.map(nb => (
            <div
              key={nb.id}
              className={`f-chip${notebookFilter === nb.id ? ' on' : ''}`}
              style={notebookFilter === nb.id ? { borderColor: nb.color, color: nb.color, background: `${nb.color}22` } : {}}
              onClick={() => handleNotebookFilter(nb.id)}
            >
              <i className="fa-solid fa-book-open" style={{ fontSize: 9, marginRight: 4 }} />
              {nb.name}
            </div>
          ))}
        </div>
      )}

      {/* ── Results count ── */}
      {results !== null && (
        <div className="search-results-count">
          {results.length} result{results.length !== 1 ? 's' : ''} for "{query}"
        </div>
      )}

      {/* ── Scrollable area ── */}
      <div className="search-page-content">

        {/* Recent searches */}
        {results === null && (
          <div>
            <div className="recent-label">
              <span>Recent</span>
              <button onClick={handleClearRecent}>Clear all</button>
            </div>
            {recents.length === 0 ? (
              <div className="no-results" style={{ padding: '30px 20px' }}>
                <i className="fa-solid fa-clock-rotate-left" />
                <p>No recent searches</p>
              </div>
            ) : (
              recents.map(s => (
                <div key={s} className="recent-search-item" onClick={() => useRecent(s)}>
                  <i className="fa-solid fa-clock-rotate-left" />
                  <span>{s}</span>
                  <i className="fa-solid fa-turn-up" style={{ fontSize: '12px', color: 'var(--text-3)', transform: 'rotate(90deg)' }} />
                </div>
              ))
            )}
          </div>
        )}

        {/* Results */}
        {results !== null && (
          <div style={{ padding: '0 18px 32px' }}>
            {results.length === 0 ? (
              <div className="no-results">
                <i className="fa-solid fa-face-frown-open" />
                <p>No notes match "<strong>{query}</strong>"</p>
              </div>
            ) : (
              results.map(n => {
                const nb      = n.notebookId ? getNotebook(n.notebookId) : null;
                const preview = stripHtml(n.content || '').slice(0, 120);
                return (
                  <div key={n.id} className="note-card" onClick={() => navigate(`/editor/${n.id}`)}>
                    <div className="n-title">{highlight(n.title || 'Untitled', query)}</div>
                    <div className="n-preview">{highlight(preview, query)}</div>
                    <div className="n-meta">
                      <span className="n-date">
                        <i className="fa-regular fa-clock" />{formatDate(n.updatedAt)}
                      </span>
                      {nb && (
                        <span className="n-tag" style={{ color: nb.color, background: `${nb.color}22` }}>
                          <i className="fa-solid fa-book-open" style={{ fontSize: '9px' }} />
                          {nb.name}
                        </span>
                      )}
                      {(n.tags || []).slice(0, 2).map(t => (
                        <span key={t} className="n-tag">{highlight(t, query)}</span>
                      ))}
                    </div>
                  </div>
                );
              })
            )}
          </div>
        )}
      </div>

      <BottomNav active="search" />
      <Toast />
    </div>
  );
}
