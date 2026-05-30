/* ══════════════════════════════════════════
   Inkwell — src/components/EmptyState.jsx

   Reusable empty-state illustration block.
   Replaces the inline HTML strings that each
   page used to inject into the DOM.

   Props:
     icon     string  — Font Awesome class  (default: 'fa-regular fa-note-sticky')
     title    string  — Bold heading        (default: 'Nothing here')
     sub      string  — Subtitle text       (default: '')
     action   node    — Optional CTA button (default: null)

   Example:
     <EmptyState
       icon="fa-regular fa-note-sticky"
       title="No notes yet"
       sub="Tap the + button to write your first note."
     />

     <EmptyState
       icon="fa-solid fa-magnifying-glass"
       title="No results"
       sub="Try a different search term."
     />
   ══════════════════════════════════════════ */

export default function EmptyState({
  icon   = 'fa-regular fa-note-sticky',
  title  = 'Nothing here',
  sub    = '',
  action = null,
}) {
  return (
    <div className="empty-state">
      <i className={`${icon} e-icon`} />
      <div className="e-title">{title}</div>
      {sub    && <div className="e-sub">{sub}</div>}
      {action && <div style={{ marginTop: '20px' }}>{action}</div>}
    </div>
  );
}
