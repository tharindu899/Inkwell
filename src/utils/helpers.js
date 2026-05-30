/* ══════════════════════════════════════════
   Inkwell — src/utils/helpers.js
   Pure utility functions — no DOM, no React.
   ══════════════════════════════════════════ */

// ─── ID Generator ─────────────────────────
export function genId() {
  return Date.now().toString(36) + Math.random().toString(36).slice(2, 7);
}

// ─── HTML Escape ──────────────────────────
export function escH(str = '') {
  return str
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

// ─── Strip HTML tags ──────────────────────
// Works in browser (DOMParser) and avoids direct
// document.createElement so it is SSR-safe.
export function stripHtml(html = '') {
  if (typeof document === 'undefined') {
    // SSR / test fallback — strip tags with regex
    return html.replace(/<[^>]*>/g, ' ').replace(/\s+/g, ' ').trim();
  }
  const d = document.createElement('div');
  d.innerHTML = html;
  return (d.textContent || d.innerText || '').replace(/\s+/g, ' ').trim();
}

// ─── Word Count ───────────────────────────
export function countWords(text = '') {
  const t = text.trim();
  return t ? t.split(/\s+/).length : 0;
}

// ─── Relative Date Formatter ──────────────
export function formatDate(iso) {
  if (!iso) return '';
  const d    = new Date(iso);
  const diff = Date.now() - d.getTime();
  if (diff < 60_000)     return 'Just now';
  if (diff < 3_600_000)  return `${Math.floor(diff / 60_000)}m ago`;
  if (diff < 86_400_000) return `${Math.floor(diff / 3_600_000)}h ago`;
  if (diff < 604_800_000)return `${Math.floor(diff / 86_400_000)}d ago`;
  return d.toLocaleDateString('en', { month: 'short', day: 'numeric' });
}

// ─── Number Formatter ─────────────────────
export function fmtNum(n) {
  return n > 999 ? (n / 1000).toFixed(1) + 'k' : String(n);
}

// ─── URL Param Helper ─────────────────────
// Usage: urlParam('id')  →  '...' | null
export function urlParam(key) {
  return new URLSearchParams(window.location.search).get(key);
}
