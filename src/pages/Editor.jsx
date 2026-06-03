/* ══════════════════════════════════════════
   Inkwell — src/pages/Editor.jsx

   Full React port of pages/editor.html.

   Routes
   ──────
   /editor          → new note  (no :id param)
   /editor/:id      → edit existing note

   Features ported
   ───────────────
   • Rich contenteditable body with full toolbar
   • Toolbar customiser (show/hide individual tools)
   • Markdown-shortcut mode (# → h1, - → ul, ``` → code block, etc.)
   • Inline-code & fenced code blocks with Copy / Bottom / Delete actions
   • Link popover, checklist, table, HR, highlight, date-time insert
   • Undo / Redo, align, indent/outdent, clear formatting
   • Tags row (add with Enter or comma, backspace to remove last)
   • Notebook bottom-sheet picker
   • Pin / Delete (confirm modal) / Reading mode
   • Word-count + char-count footer
   • Auto-save (2 s debounce) + Ctrl/Cmd-S manual save
   • Saved-range preservation so toolbar taps don't lose selection
   ══════════════════════════════════════════ */

import { useState, useEffect, useRef, useCallback, useMemo } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import Toast, { showToast } from '../components/Toast';
import { useAppStore }      from '../store/AppContext';
import {
  getNote, saveNote, deleteNote,
  getNotebook, getNotebooks,
} from '../store/storage';
import { genId, escH, stripHtml, countWords } from '../utils/helpers';
import { exportNoteFile } from '../utils/exportNote';
import { haptic } from '../utils/haptics';
import { getNotes } from '../store/storage';

/* ─────────────────────────────────────────
   Toolbar button catalogue
   Each entry: { key, title, icon, group }
   ───────────────────────────────────────── */
const TOOLBAR_GROUPS = [
  {
    id: 'history',
    buttons: [
      { key: 'undo',     title: 'Undo',          icon: 'fa-solid fa-rotate-left'    },
      { key: 'redo',     title: 'Redo',           icon: 'fa-solid fa-rotate-right'   },
    ],
  },
  {
    id: 'mode',
    buttons: [
      { key: 'markdown', title: 'Markdown shortcuts on/off', icon: 'fa-brands fa-markdown' },
    ],
  },
  {
    id: 'inline',
    buttons: [
      { key: 'bold',         title: 'Bold',          icon: 'fa-solid fa-bold'           },
      { key: 'italic',       title: 'Italic',         icon: 'fa-solid fa-italic'         },
      { key: 'underline',    title: 'Underline',      icon: 'fa-solid fa-underline'      },
      { key: 'strike',       title: 'Strikethrough',  icon: 'fa-solid fa-strikethrough'  },
    ],
  },
  {
    id: 'blocks',
    buttons: [
      { key: 'h1', title: 'Heading 1', customLabel: <b style={{ fontSize: 11 }}>H1</b> },
      { key: 'h2', title: 'Heading 2', customLabel: <b style={{ fontSize: 11 }}>H2</b> },
      { key: 'h3', title: 'Heading 3', customLabel: <b style={{ fontSize: 11 }}>H3</b> },
      { key: 'p',  title: 'Paragraph', icon: 'fa-solid fa-paragraph'                   },
    ],
  },
  {
    id: 'lists',
    buttons: [
      { key: 'ul',    title: 'Bullet list',  icon: 'fa-solid fa-list-ul'       },
      { key: 'ol',    title: 'Ordered list', icon: 'fa-solid fa-list-ol'       },
      { key: 'check', title: 'Checklist',    icon: 'fa-solid fa-square-check'  },
      { key: 'bq',    title: 'Blockquote',   icon: 'fa-solid fa-quote-left'    },
    ],
  },
  {
    id: 'align',
    buttons: [
      { key: 'left',    title: 'Align left',   icon: 'fa-solid fa-align-left'   },
      { key: 'center',  title: 'Align center', icon: 'fa-solid fa-align-center' },
      { key: 'right',   title: 'Align right',  icon: 'fa-solid fa-align-right'  },
      { key: 'indent',  title: 'Indent',       icon: 'fa-solid fa-indent'       },
      { key: 'outdent', title: 'Outdent',      icon: 'fa-solid fa-outdent'      },
    ],
  },
  {
    id: 'extras',
    buttons: [
      { key: 'highlight', title: 'Highlight',       icon: 'fa-solid fa-highlighter'   },
      { key: 'table',     title: 'Insert table',    icon: 'fa-solid fa-table-cells'   },
      { key: 'datetime',  title: 'Insert date/time',icon: 'fa-regular fa-clock'       },
      { key: 'sup',       title: 'Superscript',     icon: 'fa-solid fa-superscript'   },
      { key: 'sub',       title: 'Subscript',       icon: 'fa-solid fa-subscript'     },
    ],
  },
  {
    id: 'insert',
    buttons: [
      { key: 'link',      title: 'Insert link',  icon: 'fa-solid fa-link'         },
      { key: 'photo',     title: 'Add photo',    icon: 'fa-regular fa-image'      },
      { key: 'draw',      title: 'Draw sketch',  icon: 'fa-solid fa-pen-ruler'    },
      { key: 'hr',        title: 'Divider',      icon: 'fa-solid fa-minus'        },
      { key: 'code',      title: 'Inline code',  icon: 'fa-solid fa-code'         },
      { key: 'codeblock', title: 'Code block',   icon: 'fa-solid fa-terminal'     },
      { key: 'clear',     title: 'Clear format', icon: 'fa-solid fa-text-slash'   },
    ],
  },
];


const NB_COLORS = ['#c9a96e','#6090e0','#60ad82','#e06060','#9b7de8','#e07860','#60c8d8','#d4b0ff'];
const NB_ICONS  = [
  'fa-user','fa-briefcase','fa-lightbulb','fa-heart','fa-star','fa-book',
  'fa-music','fa-camera','fa-globe','fa-code','fa-plane','fa-coffee',
  'fa-rocket','fa-leaf','fa-fire','fa-flask','fa-palette','fa-graduation-cap',
];
const EMPTY_NOTEBOOK_FORM = { name: '', color: NB_COLORS[0], icon: NB_ICONS[0] };

const TOOLBAR_PREF_KEY = 'iw_toolbar_hidden_tools';
const PREFS_KEY = 'iw_prefs';
function getPrefs() {
  try { return JSON.parse(localStorage.getItem(PREFS_KEY) || '{}'); }
  catch { return {}; }
}
function savePrefs(prefs) {
  localStorage.setItem(PREFS_KEY, JSON.stringify(prefs || {}));
  window.dispatchEvent(new CustomEvent('inkwell-prefs-changed', { detail: prefs || {} }));
}
function savePref(k, v) {
  const p = getPrefs();
  p[k] = v;
  savePrefs(p);
}

function getHiddenTools() {
  try { return new Set(JSON.parse(localStorage.getItem(TOOLBAR_PREF_KEY) || '[]')); }
  catch { return new Set(); }
}
function saveHiddenTools(set) {
  localStorage.setItem(TOOLBAR_PREF_KEY, JSON.stringify([...set]));
}

/* ─────────────────────────────────────────
   HTML ↔ Markdown helpers
   ───────────────────────────────────────── */
function escAttr(str = '') {
  return String(str || '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

function htmlToMarkdown(html = '') {
  const wrap = document.createElement('div');
  wrap.innerHTML = html;
  wrap.querySelectorAll('br').forEach(br => br.replaceWith('\n'));
  wrap.querySelectorAll('hr').forEach(hr => hr.replaceWith('\n---\n'));
  wrap.querySelectorAll('pre').forEach(pre =>
    pre.replaceWith('\n```\n' + (pre.innerText || '').replace(/\n+$/, '') + '\n```\n'));
  wrap.querySelectorAll('h1').forEach(e => e.replaceWith('# ' + e.innerText.trim() + '\n'));
  wrap.querySelectorAll('h2').forEach(e => e.replaceWith('## ' + e.innerText.trim() + '\n'));
  wrap.querySelectorAll('h3').forEach(e => e.replaceWith('### ' + e.innerText.trim() + '\n'));
  wrap.querySelectorAll('blockquote').forEach(e =>
    e.replaceWith('> ' + e.innerText.trim().replace(/\n/g, '\n> ') + '\n'));
  wrap.querySelectorAll('strong,b').forEach(e => e.replaceWith('**' + e.innerText + '**'));
  wrap.querySelectorAll('em,i').forEach(e => e.replaceWith('*' + e.innerText + '*'));
  wrap.querySelectorAll('s,strike,del').forEach(e => e.replaceWith('~~' + e.innerText + '~~'));
  wrap.querySelectorAll('u').forEach(e => e.replaceWith('<u>' + e.innerText + '</u>'));
  wrap.querySelectorAll('a').forEach(e => e.replaceWith('[' + e.innerText + '](' + e.href + ')'));
  wrap.querySelectorAll('code').forEach(e =>
    e.closest('pre') ? null : e.replaceWith('`' + e.innerText + '`'));
  wrap.querySelectorAll('ol').forEach(ol =>
    [...ol.children].forEach((li, i) => { li.dataset.mdPrefix = (i + 1) + '. '; }));
  wrap.querySelectorAll('ul').forEach(ul =>
    [...ul.children].forEach(li => { if (!li.dataset.mdPrefix) li.dataset.mdPrefix = '- '; }));
  wrap.querySelectorAll('li').forEach(li => {
    const input = li.querySelector('input[type="checkbox"]');
    const box = li.querySelector('.check-box');
    const checked = input ? input.checked : (box?.dataset.checked === 'true');
    const isTask = !!input || !!box || !!li.closest('ul.check-list');
    const prefix = isTask
      ? (checked ? '- [x] ' : '- [ ] ')
      : (li.dataset.mdPrefix || '- ');
    if (input) input.remove();
    if (box) box.remove();
    li.replaceWith(prefix + li.innerText.trim() + '\n');
  });
  wrap.querySelectorAll('p,div').forEach(e => e.append('\n'));

  return normalizeEditorMarkdownSpacing(wrap.innerText || wrap.textContent || '');
}

function normalizeEditorMarkdownSpacing(markdown = '') {
  const lines = String(markdown || '').replace(/\r\n/g, '\n').split('\n');
  const out = [];
  let inFence = false;

  for (let i = 0; i < lines.length; i += 1) {
    const line = lines[i].replace(/[ \t]+$/g, '');
    if (/^\s*(```|~~~)/.test(line)) {
      if (out.length && out[out.length - 1] !== '') out.push('');
      out.push(line);
      inFence = !inFence;
      continue;
    }
    if (inFence) { out.push(line); continue; }
    if (!line.trim()) {
      const prev = out[out.length - 1];
      const next = lines.slice(i + 1).find(v => String(v).trim());
      if (!prev || !next) continue;
      const prevIsFence = /^\s*(```|~~~)/.test(prev);
      const nextIsFence = /^\s*(```|~~~)/.test(String(next));
      if ((prevIsFence || nextIsFence) && out[out.length - 1] !== '') out.push('');
      continue;
    }
    out.push(line);
  }

  return out.join('\n')
    .replace(/\n{3,}/g, '\n\n')
    .replace(/^\n+|\n+$/g, '')
    .trim();
}

function removeMarkdownTypingGaps(markdown = '') {
  const source = String(markdown || '').replace(/\r\n/g, '\n');
  if (!source.trim()) return '';

  // Avoid rewriting advanced Markdown where blank lines can be intentional.
  if (/(^|\n)\s*(```|~~~|[-*+]\s+\[[ xX]\]\s+|[-*+]\s+|\d+[.)]\s+|>\s?|\|.*\||-{3,}|\*{3,}|_{3,})/m.test(source)) {
    return source.replace(/\n{3,}/g, '\n\n').trim();
  }

  const lines = source.split('\n');
  const nonEmpty = lines.filter(l => l.trim()).length;
  const empty = lines.length - nonEmpty;

  // Fix old notes created by the previous Markdown toggle bug: every normal
  // typed line was saved with an extra blank line between it.
  if (nonEmpty >= 2 && empty >= Math.max(1, nonEmpty - 1)) {
    return lines.filter(l => l.trim()).join('\n').trim();
  }

  return source.replace(/\n{3,}/g, '\n\n').trim();
}

function safeUrlAttr(url = '') {
  const u = String(url || '').trim();
  if (/^(https?:\/\/|data:image\/|\.?\.?\/|[\w.-]+\/)/i.test(u)) return escAttr(u);
  return '';
}

function preprocessPastedMarkdown(md = '') {
  let text = String(md || '').replace(/\r\n/g, '\n');

  // Convert the README-style raw HTML that GitHub accepts into normal Markdown first.
  // Without this, one <div> at the top makes the whole paste stay as visible source code.
  text = text
    .replace(/<\/?div\b[^>]*>/gi, '\n')
    .replace(/<\/?center\b[^>]*>/gi, '\n')
    .replace(/<\/?p\b[^>]*>/gi, '\n')
    .replace(/<br\s*\/?\s*>/gi, '\n')
    .replace(/<\/?details\b[^>]*>/gi, '\n')
    .replace(/<summary\b[^>]*>([\s\S]*?)<\/summary>/gi, '\n**$1**\n')
    .replace(/<\/?table\b[^>]*>|<\/?tr\b[^>]*>|<\/?td\b[^>]*>|<\/?th\b[^>]*>|<\/?tbody\b[^>]*>|<\/?thead\b[^>]*>/gi, '\n')
    .replace(/<\/?sub\b[^>]*>|<\/?sup\b[^>]*>/gi, '')
    .replace(/<u\b[^>]*>([\s\S]*?)<\/u>/gi, '$1')
    .replace(/<strong\b[^>]*>([\s\S]*?)<\/strong>/gi, '**$1**')
    .replace(/<b\b[^>]*>([\s\S]*?)<\/b>/gi, '**$1**')
    .replace(/<em\b[^>]*>([\s\S]*?)<\/em>/gi, '*$1*')
    .replace(/<i\b[^>]*>([\s\S]*?)<\/i>/gi, '*$1*')
    .replace(/<s\b[^>]*>([\s\S]*?)<\/s>/gi, '~~$1~~')
    .replace(/<code\b[^>]*>([\s\S]*?)<\/code>/gi, '`$1`');

  text = text.replace(/<a\b[^>]*href=["']([^"']+)["'][^>]*>\s*(<img\b[^>]*>)\s*<\/a>/gi, (m, href, img) => {
    const src = (img.match(/src=["']([^"']+)["']/i) || [,''])[1];
    const alt = (img.match(/alt=["']([^"']*)["']/i) || [,'image'])[1] || 'image';
    return src ? `[![${alt}](${src})](${href})` : '';
  });
  text = text.replace(/<img\b[^>]*src=["']([^"']+)["'][^>]*>/gi, (m, src) => {
    const alt = (m.match(/alt=["']([^"']*)["']/i) || [,'image'])[1] || 'image';
    return `![${alt}](${src})`;
  });
  text = text.replace(/<a\b[^>]*href=["']([^"']+)["'][^>]*>([\s\S]*?)<\/a>/gi, (m, href, label) => {
    const clean = String(label || href).replace(/<[^>]+>/g, '').trim() || href;
    return `[${clean}](${href})`;
  });

  return text.replace(/\n{3,}/g, '\n\n').trim();
}

function inlineMarkdown(text = '') {
  const imgUrl = String.raw`((?:https?:\/\/|data:image\/|\.?\.?\/|[\w.-]+\/)[^\s)]+)`;
  let html = escH(text);

  html = html.replace(new RegExp('\\[!\\[([^\\]]*)\\]\\(' + imgUrl + '\\)\\]\\((https?:\\/\\/[^\\s)]+)\\)', 'g'), (m, alt, src, href) => {
    const s = safeUrlAttr(src);
    const h = safeUrlAttr(href);
    return s && h ? `<a href="${h}" target="_blank" rel="noreferrer"><img src="${s}" alt="${escAttr(alt)}" loading="lazy"></a>` : escH(alt);
  });
  html = html.replace(new RegExp('!\\[([^\\]]*)\\]\\(' + imgUrl + '\\)', 'g'), (m, alt, src) => {
    const s = safeUrlAttr(src);
    return s ? `<img src="${s}" alt="${escAttr(alt)}" loading="lazy">` : escH(alt);
  });
  return html
    .replace(/`([^`\n]+)`/g, '<code>$1</code>')
    .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
    .replace(/__([^_]+)__/g, '<strong>$1</strong>')
    .replace(/(^|[^*])\*([^*\n]+)\*(?!\*)/g, '$1<em>$2</em>')
    .replace(/~~([^~]+)~~/g, '<s>$1</s>')
    .replace(/\[([^\]]+)\]\((https?:\/\/[^\s)]+)\)/g, '<a href="$2" target="_blank" rel="noreferrer">$1</a>');
}


function normalizeMarkdownTables(md = '') {
  // Mobile keyboards / some paste sources can collapse Markdown tables into one line.
  // Rebuild obvious GitHub-style pipe table row boundaries before parsing.
  return String(md)
    .replace(/\r\n/g, '\n')
    .replace(/\|\s+\|(?=\s*:?-{3,}:?\s*\|)/g, '|\n|')
    .replace(/(\|\s*:?-{3,}:?\s*(?:\|\s*:?-{3,}:?\s*)+\|?)\s+\|/g, '$1\n|')
    .replace(/\|\s+\|(?=\s*[^|\n]+?\s*\|)/g, '|\n|');
}

function splitMarkdownTableRow(row = '') {
  let r = row.trim();
  if (r.startsWith('|')) r = r.slice(1);
  if (r.endsWith('|')) r = r.slice(0, -1);
  const cells = [];
  let cell = '';
  let escaped = false;
  for (const ch of r) {
    if (escaped) { cell += ch; escaped = false; continue; }
    if (ch === '\\') { escaped = true; cell += ch; continue; }
    if (ch === '|') { cells.push(cell.trim()); cell = ''; continue; }
    cell += ch;
  }
  cells.push(cell.trim());
  return cells;
}

function isMarkdownTableSeparator(line = '') {
  const cells = splitMarkdownTableRow(line);
  return cells.length >= 2 && cells.every(c => /^:?-{3,}:?$/.test(c.trim()));
}

function isLikelyMarkdownTableRow(line = '') {
  return line.includes('|') && splitMarkdownTableRow(line).length >= 2;
}

function markdownToHtml(md = '') {
  const prepared = preprocessPastedMarkdown(md);
  const lines = normalizeMarkdownTables(prepared).split('\n');
  const out = [];
  let i = 0;
  let inCode = false;
  let code = [];
  let list = null;

  const closeList = () => {
    if (!list) return;
    out.push(`<${list.type}${list.task ? ' class="check-list" data-type="taskList"' : ''}>${list.items.join('')}</${list.type}>`);
    list = null;
  };

  const addListItem = (type, html, task = false, checked = false) => {
    if (!list || list.type !== type || list.task !== task) closeList();
    if (!list) list = { type, task, items: [] };
    if (task) {
      list.items.push(`<li data-checked="${checked ? 'true' : 'false'}"><label><span class="check-box" role="checkbox" tabindex="-1" contenteditable="false" data-checked="${checked ? 'true' : 'false'}" aria-checked="${checked ? 'true' : 'false'}"></span> <span class="check-text">${html || '<br>'}</span></label></li>`);
    } else {
      list.items.push(`<li>${html || '<br>'}</li>`);
    }
  };

  while (i < lines.length) {
    const raw = lines[i];
    const line = raw.replace(/\t/g, '  ');

    if (/^\s*(```|~~~)/.test(line)) {
      closeList();
      if (!inCode) { inCode = true; code = []; }
      else {
        out.push(`<pre><code>${escH(code.join('\n')) || '<br>'}</code></pre>`);
        inCode = false; code = [];
      }
      i += 1; continue;
    }
    if (inCode) { code.push(raw); i += 1; continue; }

    if (!line.trim()) { closeList(); i += 1; continue; }

    const h = line.match(/^\s{0,3}(#{1,6})\s+(.+)$/);
    if (h) { closeList(); const level = Math.min(h[1].length, 6); out.push(`<h${level}>${inlineMarkdown(h[2].trim())}</h${level}>`); i += 1; continue; }

    const quote = line.match(/^\s{0,3}>\s?(.*)$/);
    if (quote) {
      closeList();
      const parts = [];
      while (i < lines.length) {
        const q = lines[i].match(/^\s{0,3}>\s?(.*)$/);
        if (!q) break;
        parts.push(q[1]); i += 1;
      }
      out.push(`<blockquote>${inlineMarkdown(parts.join('<br>'))}</blockquote>`);
      continue;
    }


    // GitHub-style pipe table
    if (isLikelyMarkdownTableRow(line) && i + 1 < lines.length && isMarkdownTableSeparator(lines[i + 1])) {
      closeList();
      const headers = splitMarkdownTableRow(line);
      i += 2; // skip header + separator
      const rows = [];
      while (i < lines.length && isLikelyMarkdownTableRow(lines[i]) && lines[i].trim()) {
        rows.push(splitMarkdownTableRow(lines[i]));
        i += 1;
      }
      const headHtml = headers.map(h => `<th>${inlineMarkdown(h)}</th>`).join('');
      const bodyHtml = rows.map(row => {
        const cells = headers.map((_, idx) => `<td>${inlineMarkdown(row[idx] || '')}</td>`).join('');
        return `<tr>${cells}</tr>`;
      }).join('');
      out.push(`<div class="md-table-wrap"><table><thead><tr>${headHtml}</tr></thead><tbody>${bodyHtml}</tbody></table></div>`);
      continue;
    }

    const task = line.match(/^\s{0,3}[-*+]\s+\[( |x|X)\]\s*(.*)$/);
    if (task) { addListItem('ul', inlineMarkdown(task[2].trim()), true, task[1].toLowerCase() === 'x'); i += 1; continue; }

    const ul = line.match(/^\s{0,3}[-*+]\s+(.+)$/);
    if (ul) { addListItem('ul', inlineMarkdown(ul[1].trim()), false); i += 1; continue; }

    const ol = line.match(/^\s{0,3}\d+[.)]\s+(.+)$/);
    if (ol) { addListItem('ol', inlineMarkdown(ol[1].trim()), false); i += 1; continue; }

    const hr = line.match(/^\s{0,3}(-{3,}|\*{3,}|_{3,})\s*$/);
    if (hr) { closeList(); out.push('<hr>'); i += 1; continue; }

    closeList();
    const para = [line.trim()];
    i += 1;
    while (i < lines.length && lines[i].trim() && !/^\s{0,3}(#{1,6})\s+/.test(lines[i]) && !/^\s{0,3}([-*+]\s+|\d+[.)]\s+|>\s?|```|~~~)/.test(lines[i]) && !(isLikelyMarkdownTableRow(lines[i]) && i + 1 < lines.length && isMarkdownTableSeparator(lines[i + 1]))) {
      para.push(lines[i].trim()); i += 1;
    }
    out.push(`<p>${para.map(part => inlineMarkdown(part)).join('<br>')}</p>`);
  }
  closeList();
  if (inCode) out.push(`<pre><code>${escH(code.join('\n')) || '<br>'}</code></pre>`);
  return out.join('') || '<p><br></p>';
}

function looksLikeMarkdown(text = '') {
  if (!text || !text.trim()) return false;
  return /(^|\n)\s{0,3}(#{1,6}\s+|[-*+]\s+|\d+[.)]\s+|>\s?|```|~~~|[-*+]\s+\[[ xX]\]\s+)/.test(text)
    || /\|\s*:?-{3,}:?\s*\|/.test(normalizeMarkdownTables(text))
    || /(!?\[[^\]]+\]\(https?:\/\/[^\s)]+\)|`[^`\n]+`|\*\*[^*]+\*\*|__[^_]+__)/.test(text);
}

function looksLikeHtmlSource(text = '') {
  if (!text || !text.trim()) return false;
  return /<\/?(div|p|h[1-6]|img|a|br|ul|ol|li|table|thead|tbody|tr|th|td|blockquote|pre|code|strong|b|em|i|u|s|span|center|hr)\b[^>]*>/i.test(text);
}

function contentLooksLikeRawSource(html = '') {
  const plain = stripHtml(html || '');
  return looksLikeHtmlSource(plain) || looksLikeMarkdown(plain);
}

function normalizeSourceCompareText(text = '') {
  return String(text || '')
    .replace(/\u00a0/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
    .toLowerCase();
}

function markdownSourceLooksStale(contentHtml = '', markdown = '') {
  const savedMd = String(markdown || '');
  if (!contentHtml || !savedMd.trim()) return false;
  if (contentLooksLikeRawSource(contentHtml)) return false;

  try {
    const contentText = normalizeSourceCompareText(stripHtml(contentHtml));
    const mdText = normalizeSourceCompareText(stripHtml(markdownToHtml(savedMd)));
    if (!contentText || !mdText || contentText === mdText) return false;

    // A previously bad save could keep an old Markdown source while the visual
    // editor HTML contains newer typed lines. Prefer the visible content when
    // it has clear extra text that the Markdown source does not contain.
    if (contentText.length > mdText.length + 10 && !mdText.includes(contentText)) return true;

    const contentTokens = contentText.split(' ').filter(Boolean);
    const mdTokenSet = new Set(mdText.split(' ').filter(Boolean));
    const missing = contentTokens.filter(t => !mdTokenSet.has(t)).length;
    return contentTokens.length >= 4 && missing >= 3 && missing / contentTokens.length > 0.18;
  } catch {
    return false;
  }
}

function getNoteMarkdownSource(note = {}) {
  const savedMd = String(note.markdown || '');
  const content = String(note.content || '');
  const rawText = stripHtml(content);

  if (savedMd.trim() && !markdownSourceLooksStale(content, savedMd)) return removeMarkdownTypingGaps(savedMd);
  if (content && (looksLikeHtmlSource(rawText) || looksLikeMarkdown(rawText))) return removeMarkdownTypingGaps(rawText);
  return htmlToMarkdown(content);
}

function renderStoredMarkdownSource(note = {}) {
  const source = getNoteMarkdownSource(note);
  return markdownToHtml(source);
}

function sanitizeEditorHtml(raw = '') {
  const doc = new DOMParser().parseFromString(String(raw), 'text/html');
  doc.querySelectorAll('script,style,iframe,object,embed,form,input,button,textarea,select,meta,link').forEach(n => n.remove());
  const allowed = new Set(['P','DIV','BR','H1','H2','H3','H4','H5','H6','UL','OL','LI','BLOCKQUOTE','PRE','CODE','STRONG','B','EM','I','U','S','DEL','A','IMG','TABLE','THEAD','TBODY','TR','TH','TD','HR','SPAN','CENTER']);

  const cleanNode = (node) => {
    if (node.nodeType === Node.TEXT_NODE) return document.createTextNode(node.textContent || '');
    if (node.nodeType !== Node.ELEMENT_NODE) return document.createTextNode('');

    const tag = allowed.has(node.tagName) ? node.tagName.toLowerCase() : 'span';
    const el = document.createElement(tag === 'center' ? 'div' : tag);
    if (tag === 'center') el.style.textAlign = 'center';

    if (tag === 'a') {
      const href = node.getAttribute('href') || '';
      if (/^https?:\/\//i.test(href)) {
        el.setAttribute('href', href);
        el.setAttribute('target', '_blank');
        el.setAttribute('rel', 'noreferrer');
      }
    }
    if (tag === 'img') {
      const src = node.getAttribute('src') || '';
      if (/^(https?:\/\/|data:image\/)/i.test(src)) {
        el.setAttribute('src', src);
        el.setAttribute('alt', node.getAttribute('alt') || 'image');
        el.setAttribute('loading', 'lazy');
      } else {
        return document.createTextNode(node.getAttribute('alt') || '');
      }
    }
    const align = node.getAttribute('align') || node.style?.textAlign || '';
    if (/^(left|center|right|justify)$/i.test(align)) el.style.textAlign = align.toLowerCase();

    [...node.childNodes].forEach(child => el.appendChild(cleanNode(child)));
    return el;
  };

  const wrap = document.createElement('div');
  [...doc.body.childNodes].forEach(child => wrap.appendChild(cleanNode(child)));
  return wrap.innerHTML || '<p><br></p>';
}

function renderPastedText(text = '') {
  return markdownToHtml(text);
}

function htmlToPlainSourceHtml(text = '') {
  const escaped = escH(String(text || ''));
  if (!escaped.trim()) return '<p><br></p>';
  return escaped
    .split(/\n{2,}/)
    .map(part => `<p>${part.replace(/\n/g, '<br>') || '<br>'}</p>`)
    .join('');
}

/* ─────────────────────────────────────────
   Code-block helpers (DOM mutations)
   ───────────────────────────────────────── */
function enhanceCodeBlocks(bodyEl) {
  if (!bodyEl) return;
  bodyEl.querySelectorAll('pre').forEach(pre => {
    let code = pre.querySelector('code');
    if (!code) {
      code = document.createElement('code');
      const old = pre.innerText || pre.textContent || '';
      pre.textContent = '';
      code.textContent = old.replace(/^(Copy|Delete|Bottom)\s*/g, '');
      pre.appendChild(code);
    }
    code.setAttribute('contenteditable', 'true');

    // Keep the action buttons outside of the horizontal code scroller.
    // This makes Copy / Bottom / Delete stay visible while code scrolls sideways.
    let scroll = code.closest('.code-scroll');
    if (!scroll) {
      scroll = document.createElement('div');
      scroll.className = 'code-scroll';
      code.parentNode.insertBefore(scroll, code);
      scroll.appendChild(code);
    }

    if (!pre.querySelector('.code-actions')) {
      const actions = document.createElement('div');
      actions.className = 'code-actions';
      actions.contentEditable = 'false';
      actions.innerHTML = `
        <button type="button" class="code-action-btn code-copy-btn" title="Copy code">
          <i class="fa-regular fa-copy"></i><span>Copy</span>
        </button>
        <button type="button" class="code-action-btn code-bottom-btn" title="Jump to bottom">
          <i class="fa-solid fa-arrow-down"></i><span>Bottom</span>
        </button>
        <button type="button" class="code-action-btn code-delete-btn danger" title="Delete code block">
          <i class="fa-regular fa-trash-can"></i><span>Delete</span>
        </button>`;
      pre.insertBefore(actions, scroll);
    }
  });
}


function enhanceCheckLists(bodyEl) {
  if (!bodyEl) return;
  bodyEl.querySelectorAll('ul.check-list li').forEach(li => {
    let label = li.querySelector('label');
    if (!label) {
      label = document.createElement('label');
      while (li.firstChild) label.appendChild(li.firstChild);
      li.appendChild(label);
    }

    const input = label.querySelector('input[type="checkbox"]');
    let box = label.querySelector('.check-box');
    if (!box) {
      box = document.createElement('span');
      box.className = 'check-box';
      box.setAttribute('contenteditable', 'false');
      box.setAttribute('role', 'checkbox');
      box.setAttribute('tabindex', '-1');
      box.dataset.checked = input && input.checked ? 'true' : 'false';
      box.setAttribute('aria-checked', box.dataset.checked);
      if (input) input.replaceWith(box);
      else label.insertBefore(box, label.firstChild);
    } else {
      box.setAttribute('contenteditable', 'false');
      box.setAttribute('role', 'checkbox');
      box.setAttribute('tabindex', '-1');
      box.setAttribute('aria-checked', box.dataset.checked === 'true' ? 'true' : 'false');
    }
    if (input && input.parentElement) input.remove();

    let textSpan = label.querySelector('.check-text');
    if (!textSpan) {
      textSpan = label.querySelector('span:not(.check-box)');
      if (textSpan) textSpan.classList.add('check-text');
      else {
        textSpan = document.createElement('span');
        textSpan.className = 'check-text';
        const nodes = [...label.childNodes].filter(n => n !== box);
        if (nodes.length) nodes.forEach(n => textSpan.appendChild(n));
        else textSpan.innerHTML = '<br>';
        label.appendChild(textSpan);
      }
    }
    label.setAttribute('data-check-label', 'true');
  });
}

function createChecklistItem() {
  const li = document.createElement('li');
  li.innerHTML = '<label data-check-label="true"><span class="check-box" contenteditable="false" role="checkbox" aria-checked="false" data-checked="false" tabindex="-1"></span><span class="check-text"><br></span></label>';
  return li;
}

function listCollapseToggle(li) {
  return [...li.children].find(ch => ch.classList?.contains('li-collapse-toggle')) || null;
}

function normalizeNestedLists(bodyEl) {
  if (!bodyEl) return;

  // Android/Chrome contenteditable sometimes creates invalid list DOM such as:
  // <ul><li>Parent</li><ul>Child...</ul></ul>
  // Move those child lists inside the previous <li> so collapse/expand is reliable.
  bodyEl.querySelectorAll('ul,ol').forEach(list => {
    [...list.children].forEach(child => {
      if (!child.matches?.('ul,ol')) return;
      let prev = child.previousElementSibling;
      while (prev && !prev.matches?.('li')) prev = prev.previousElementSibling;
      if (prev) prev.appendChild(child);
    });
  });

  // Remove empty list wrappers that may remain after mobile edits.
  bodyEl.querySelectorAll('ul,ol').forEach(list => {
    if (!list.children.length && list.parentElement) list.remove();
  });
}

function nestedListsForLi(li) {
  return [...li.children].filter(ch => ch.matches?.('ul,ol'));
}

function enhanceCollapsibleLists(bodyEl) {
  if (!bodyEl) return;
  normalizeNestedLists(bodyEl);

  bodyEl.querySelectorAll('li').forEach(li => {
    if (li.closest('ul.check-list') || li.querySelector('.check-box')) return;

    const subLists = nestedListsForLi(li);
    let toggle = listCollapseToggle(li);

    if (!subLists.length) {
      if (toggle) toggle.remove();
      li.classList.remove('li-collapsible', 'li-collapsed');
      delete li.dataset.collapsed;
      return;
    }

    li.classList.add('li-collapsible');
    if (!toggle) {
      toggle = document.createElement('button');
      toggle.type = 'button';
      toggle.className = 'li-collapse-toggle';
      toggle.setAttribute('contenteditable', 'false');
      toggle.setAttribute('tabindex', '-1');
      li.insertBefore(toggle, li.firstChild);
    }

    const collapsed = li.dataset.collapsed === 'true';
    li.classList.toggle('li-collapsed', collapsed);
    toggle.setAttribute('aria-expanded', collapsed ? 'false' : 'true');
    toggle.setAttribute('aria-label', collapsed ? 'Expand list' : 'Collapse list');
    toggle.title = collapsed ? 'Expand' : 'Collapse';
    toggle.innerHTML = `<i class="fa-solid ${collapsed ? 'fa-chevron-right' : 'fa-chevron-down'}"></i>`;

    // Never save inline display:none. CSS handles hide/show from .li-collapsed.
    subLists.forEach(list => { list.style.display = ''; });
  });
}

function attachCodeBlockHandlers(bodyEl, onDirty) {
  if (!bodyEl) return;
  bodyEl.querySelectorAll('pre').forEach(pre => {
    const actions = pre.querySelector('.code-actions');
    if (!actions || actions.dataset.bound) return;
    actions.dataset.bound = '1';

    const copyBtn   = actions.querySelector('.code-copy-btn');
    const bottomBtn = actions.querySelector('.code-bottom-btn');
    const deleteBtn = actions.querySelector('.code-delete-btn');

    if (copyBtn) copyBtn.onclick = (e) => {
      e.preventDefault(); e.stopPropagation();
      const code = pre.querySelector('code');
      const text = code ? (code.innerText || '').replace(/\n$/, '') : '';
      const done = () => {
        const old = copyBtn.innerHTML;
        copyBtn.innerHTML = '<i class="fa-solid fa-check"></i><span>Copied</span>';
        setTimeout(() => { copyBtn.innerHTML = old; }, 900);
      };
      if (navigator.clipboard && window.isSecureContext) {
        navigator.clipboard.writeText(text).then(done).catch(() => {
          fallbackCopy(text); done();
        });
      } else { fallbackCopy(text); done(); }
    };

    if (bottomBtn) bottomBtn.onclick = (e) => {
      e.preventDefault(); e.stopPropagation();
      const code = pre.querySelector('code');
      if (!code) return;
      code.focus();
      const range = document.createRange();
      range.selectNodeContents(code);
      range.collapse(false);
      const sel = window.getSelection();
      sel.removeAllRanges();
      sel.addRange(range);
      pre.scrollIntoView({ block: 'nearest' });
    };

    if (deleteBtn) deleteBtn.onclick = (e) => {
      e.preventDefault(); e.stopPropagation();
      const p = document.createElement('p');
      p.innerHTML = '<br>';
      pre.replaceWith(p);
      placeCaretAtEnd(p);
      onDirty();
    };
  });
}

function fallbackCopy(text) {
  const ta = document.createElement('textarea');
  ta.value = text;
  ta.style.cssText = 'position:fixed;left:-9999px';
  ta.dataset.inkwellCopyOk = '1';
  document.body.appendChild(ta);
  ta.focus(); ta.select();
  try { document.execCommand('copy'); } catch {}
  ta.remove();
}

function placeCaretAtEnd(el) {
  if (!el) return;
  const editorRoot = el.closest?.('[contenteditable="true"]') ||
    (el.getAttribute?.('contenteditable') === 'true' ? el : document.querySelector('.editor-body[contenteditable="true"]'));
  if (editorRoot && document.activeElement !== editorRoot) {
    try { editorRoot.focus({ preventScroll: true }); } catch { editorRoot.focus(); }
  }
  const range = document.createRange();
  range.selectNodeContents(el);
  range.collapse(false);
  const sel = window.getSelection();
  if (!sel) return;
  sel.removeAllRanges();
  sel.addRange(range);
}

function placeCaretInCode(code) {
  const range = document.createRange();
  const sel = window.getSelection();
  if (!sel) return;
  if (!(code.textContent || '').trim()) {
    code.innerHTML = '<br>';
    range.selectNodeContents(code);
    range.collapse(true);
  } else {
    range.selectNodeContents(code);
    range.collapse(false);
  }
  sel.removeAllRanges();
  sel.addRange(range);
}

/* ─────────────────────────────────────────
   Selection / caret helpers
   ───────────────────────────────────────── */
function closestNode(nameOrClass, bodyEl) {
  const sel = window.getSelection();
  if (!sel || !sel.anchorNode) return null;
  let n = sel.anchorNode.nodeType === 1 ? sel.anchorNode : sel.anchorNode.parentElement;
  while (n && n !== bodyEl) {
    if (typeof nameOrClass === 'string') {
      if (n.nodeName === nameOrClass.toUpperCase()) return n;
      if (n.classList && n.classList.contains(nameOrClass)) return n;
    }
    n = n.parentElement;
  }
  return null;
}

function currentBlockEl(bodyEl) {
  const sel = window.getSelection();
  if (!sel || !sel.anchorNode) return bodyEl;
  let node = sel.anchorNode.nodeType === 1 ? sel.anchorNode : sel.anchorNode.parentElement;
  while (node && node !== bodyEl) {
    if (/^(P|DIV|H1|H2|H3|LI|BLOCKQUOTE|PRE)$/.test(node.nodeName)) return node;
    node = node.parentElement;
  }
  return bodyEl;
}

function currentBlockTag() {
  const raw = (document.queryCommandValue('formatBlock') || '').toLowerCase().replace(/[<>]/g, '');
  if (!raw || raw === 'div') return 'p';
  return raw;
}

function insideCodeBlock(bodyEl) {
  const pre = closestNode('PRE', bodyEl);
  return pre && pre.querySelector('code') ? pre : null;
}

function getAncestorStyle(prop) {
  const sel = window.getSelection();
  if (!sel || !sel.anchorNode) return '';
  let n = sel.anchorNode.nodeType === 1 ? sel.anchorNode : sel.anchorNode.parentElement;
  while (n) {
    const v = n.style && n.style[prop];
    if (v) return v;
    n = n.parentElement;
  }
  return '';
}

/* ─────────────────────────────────────────
   Main Component
   ───────────────────────────────────────── */
export default function Editor() {
  const { id: paramId }    = useParams();
  const [searchParams]     = useSearchParams();
  const navigate           = useNavigate();
  const { dispatch, saveNotebook, notes } = useAppStore();

  /* ── refs ── */
  const bodyRef       = useRef(null);   // contenteditable div
  const titleRef      = useRef(null);   // textarea
  const savedRangeRef = useRef(null);   // preserved selection
  const autoSaveTimer = useRef(null);
  const noteRef       = useRef(null);
  const imageInputRef = useRef(null);
  const drawCanvasRef = useRef(null);
  const drawStateRef    = useRef({ drawing: false, moved: false, lastX: 0, lastY: 0 });
  const drawColorRef    = useRef('#f28c40');
  const drawLineWidthRef = useRef(3);
  const undoStackRef  = useRef([]);
  const redoStackRef  = useRef([]);
  const lastHistoryRef = useRef('');
  const historyTimerRef = useRef(null);
  const restoringHistoryRef = useRef(false);
  const saveNowRef = useRef(null);
  const collapseTimerRef = useRef(null);
  const isMountedRef = useRef(true);
  const savingLockRef = useRef(false);
  const lastSavedHashRef = useRef('');

  const [autoSaveEnabled, setAutoSaveEnabled] = useState(() => {
    const p = getPrefs();
    return p.autosave !== undefined ? !!p.autosave : true;
  });
  const [markdownEnabled, setMarkdownEnabled] = useState(() => {
    const p = getPrefs();
    return p.markdownMode !== undefined ? !!p.markdownMode : true;
  });
  const sourceViewRef = useRef(false);
  const readRestoreSourceModeRef = useRef(false);
  // Keeps the original Markdown source stable while switching between
  // Render mode and Text mode. Toggling must not re-convert the note and
  // change tables, links, badges, code fences, or raw GitHub HTML.
  const markdownSourceRef = useRef('');
  // True when the user has typed/edited in rendered Markdown view.
  // Before switching to text view or saving, rebuild the Markdown source from
  // the visible DOM so ON/OFF never drops freshly typed lines.
  const renderedDomDirtyRef = useRef(false);

  const [spellcheckEnabled, setSpellcheckEnabled] = useState(() => {
    const p = getPrefs();
    return p.spellcheck !== undefined ? !!p.spellcheck : true;
  });
  const [serifBody, setSerifBody] = useState(() => {
    const p = getPrefs();
    return p.serifBody !== undefined ? !!p.serifBody : false;
  });

  // v41 editor lifecycle cleanup
  useEffect(() => {
    isMountedRef.current = true;
    return () => {
      isMountedRef.current = false;
      clearTimeout(autoSaveTimer.current);
      clearTimeout(historyTimerRef.current);
      clearTimeout(collapseTimerRef.current);
    };
  }, []);

  function getStableMarkdownSource(fallbackNote = noteRef.current) {
    const body = bodyRef.current;

    // Rendered view is editable. If the user typed there, the old stable source
    // is no longer trustworthy; rebuild from the visible editor before any
    // mode switch/read/save operation.
    if (renderedDomDirtyRef.current && body && !sourceViewRef.current) {
      const fresh = htmlToMarkdown(getCleanHtml());
      markdownSourceRef.current = fresh;
      return fresh;
    }

    const existing = String(markdownSourceRef.current || '').trim();
    if (existing) return markdownSourceRef.current;
    const source = getNoteMarkdownSource(fallbackNote || {});
    markdownSourceRef.current = source || '';
    return markdownSourceRef.current;
  }

  function setMarkdownSourceClass(isSource) {
    const body = bodyRef.current;
    if (!body) return;
    body.classList.toggle('markdown-source-mode', !!isSource);
    body.setAttribute('data-md-mode', isSource ? 'source' : 'render');
  }

  function applyMarkdownView(renderedMode) {
    const body = bodyRef.current;
    if (!body) return;

    const currentlySource = sourceViewRef.current;
    if (renderedMode && currentlySource) {
      const source = body.textContent || body.innerText || '';
      markdownSourceRef.current = source;
      body.innerHTML = markdownToHtml(source);
      sourceViewRef.current = false;
      renderedDomDirtyRef.current = false;
      setMarkdownSourceClass(false);
      enhanceCodeBlocks(body);
      enhanceCheckLists(body);
      enhanceCollapsibleLists(body);
      enhanceMediaItems(body);
      attachCodeBlockHandlers(body, markDirty);
    } else if (!renderedMode && !currentlySource) {
      // If the user typed in rendered mode, rebuild source from the visible DOM.
      // If nothing changed, keep the exact original source so README/code/table
      // Markdown stays stable across ON/OFF toggles.
      const source = getStableMarkdownSource();
      markdownSourceRef.current = source;
      body.textContent = source;
      sourceViewRef.current = true;
      renderedDomDirtyRef.current = false;
      setMarkdownSourceClass(true);
    }

    updateWC();
    requestAnimationFrame(() => {
      resetEditorHistory();
      updateTbState();
    });
  }

  function renderForReadingMode() {
    const body = bodyRef.current;
    if (!body) return;
    // Remember the user's edit mode so leaving Reading Mode returns to the same view.
    readRestoreSourceModeRef.current = sourceViewRef.current || !markdownEnabled;

    const source = sourceViewRef.current
      ? (body.textContent || body.innerText || '')
      : getStableMarkdownSource();
    markdownSourceRef.current = source || '';

    body.innerHTML = markdownToHtml(source || '');
    sourceViewRef.current = false;
    renderedDomDirtyRef.current = false;
    setMarkdownSourceClass(false);
    enhanceCodeBlocks(body);
    enhanceCheckLists(body);
    enhanceCollapsibleLists(body);
    enhanceMediaItems(body);
    normalizeFigurePositions(body);
    attachCodeBlockHandlers(body, markDirty);

    // If a wide table/code block was horizontally scrolled in edit mode, reset it.
    requestAnimationFrame(() => {
      body.querySelectorAll('.md-table-wrap, pre, .ed-code').forEach(el => { el.scrollLeft = 0; });
      updateWC();
    });
  }

  function restoreAfterReadingMode() {
    const body = bodyRef.current;
    if (!body) return;
    if (readRestoreSourceModeRef.current || !markdownEnabled) {
      const source = getStableMarkdownSource();
      markdownSourceRef.current = source || '';
      body.textContent = source || '';
      sourceViewRef.current = true;
      renderedDomDirtyRef.current = false;
      setMarkdownSourceClass(true);
    } else {
      applyMarkdownView(true);
    }
    requestAnimationFrame(updateTbState);
  }

  // Apply spellcheck to the editor body whenever it changes
  useEffect(() => {
    if (bodyRef.current) {
      bodyRef.current.spellcheck = spellcheckEnabled;
    }
  }, [spellcheckEnabled]);

  // Apply serif font class to the editor body whenever it changes
  useEffect(() => {
    if (bodyRef.current) {
      bodyRef.current.classList.toggle('editor-body-serif', serifBody);
    }
  }, [serifBody]);

  // Sync pref changes when the user updates Settings in another tab or on the fly
  useEffect(() => {
    const syncPrefs = (e) => {
      if (e && e.key && e.key !== PREFS_KEY) return;
      const p = getPrefs();
      setAutoSaveEnabled(p.autosave !== undefined ? !!p.autosave : true);
      // Markdown render/text is local to the currently opened note.
      // Settings only chooses the default for the next note open.
      setSpellcheckEnabled(p.spellcheck !== undefined ? !!p.spellcheck : true);
      setSerifBody(p.serifBody !== undefined ? !!p.serifBody : false);
    };
    window.addEventListener('storage', syncPrefs);
    window.addEventListener('inkwell-prefs-changed', syncPrefs);
    return () => {
      window.removeEventListener('storage', syncPrefs);
      window.removeEventListener('inkwell-prefs-changed', syncPrefs);
    };
  }, []);

  useEffect(() => {
    if (!noteRef.current || !bodyRef.current) return;
    applyMarkdownView(markdownEnabled);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [markdownEnabled]);

  /* ── note state ── */
  const [note,      setNote]      = useState(null);
  const [dirty,     setDirty]     = useState(false);
  const [saveState, setSaveState] = useState('saved');  // 'saved' | 'saving'
  useEffect(() => { noteRef.current = note; }, [note]);

  function updateNote(next) {
    const value = typeof next === 'function' ? next(noteRef.current) : next;
    noteRef.current = value;
    setNote(value);
    return value;
  }

  /* ── UI state ── */
  const [isReadMode,  setIsReadMode]  = useState(false);
  const [linkPopover, setLinkPopover] = useState(false);
  const [linkUrl,     setLinkUrl]     = useState('');
  const [linkText,    setLinkText]    = useState('');
  const [imageModal,  setImageModal]  = useState(false);
  const [drawModal,   setDrawModal]   = useState(false);
  const [drawColor,     setDrawColor]     = useState('#f28c40');
  const [drawLineWidth, setDrawLineWidth] = useState(3);

  // Keep refs in sync — canvas event handlers read these refs so changing
  // color/brush never re-initialises the canvas (and wipes the drawing).
  useEffect(() => { drawColorRef.current     = drawColor;     }, [drawColor]);
  useEffect(() => { drawLineWidthRef.current = drawLineWidth; }, [drawLineWidth]);
  const [nbPicker,    setNbPicker]    = useState(false);
  const [delModal,    setDelModal]    = useState(false);
  const [tbModal,     setTbModal]     = useState(false);
  const [exportModal, setExportModal] = useState(false);
  const [nbCreateModal, setNbCreateModal] = useState(false);
  const [nbForm, setNbForm] = useState(EMPTY_NOTEBOOK_FORM);

  /* ── tags input ── */
  const [tagInput, setTagInput] = useState('');
  const [tagManageModal, setTagManageModal] = useState(false);

  const savedTagOptions = useMemo(() => {
    const current = new Set((note?.tags || []).map(t => String(t).toLowerCase()));
    const all = Array.from(new Set(
      (notes || [])
        .flatMap(n => n?.tags || [])
        .map(t => String(t).trim().toLowerCase())
        .filter(Boolean)
    )).sort((a, b) => a.localeCompare(b));
    return all.filter(t => !current.has(t)).slice(0, 12);
  }, [notes, note?.tags]);

  /* ── toolbar active-states ── */
  const [tbState, setTbState] = useState({});

  /* ── hidden toolbar tools ── */
  const [hiddenTools, setHiddenTools] = useState(getHiddenTools);

  /* ── word / char counts ── */
  const [wc, setWc] = useState(0);
  const [cc, setCc] = useState(0);
  const [selectedWords, setSelectedWords] = useState(0);

  /* ── writing streak ── */
  const [streak] = useState(() => {
    const notes = getNotes();
    const days = new Set(notes.map(n => (n.updatedAt || n.createdAt || '').slice(0, 10)));
    const today = new Date();
    let s = 0;
    for (let i = 0; i < 365; i++) {
      const d = new Date(today);
      d.setDate(d.getDate() - i);
      if (days.has(d.toISOString().slice(0, 10))) s++;
      else if (i > 0) break;
    }
    return s;
  });

  /* ─────────────────── Init ── */
  useEffect(() => {
    const notebookId = searchParams.get('notebook') || null;
    const startTag   = (searchParams.get('tag') || '').trim().replace(/^#/, '').replace(/,/g, '').toLowerCase();
    const startType  = searchParams.get('type') || '';

    if (paramId) {
      const existing = getNote(paramId);
      if (!existing) { navigate('/', { replace: true }); return; }
      updateNote(existing);
    } else {
      let content = '';
      if (startType === 'checklist') {
        content = '<ul class="check-list" data-type="taskList"><li data-checked="false"><label><span class="check-box" role="checkbox" tabindex="-1" contenteditable="false" data-checked="false" aria-checked="false"></span> <span class="check-text">New task</span></label></li></ul>';
      } else if (startType === 'image') {
        content = '<p>Image note</p><div class="image-placeholder"><i class="fa-solid fa-image"></i><span>Add image here</span></div>';
      } else if (startType === 'voice') {
        content = '<p>Voice note</p><blockquote>Record or paste your voice note text here.</blockquote>';
      }

      updateNote({
        id:         genId(),
        title:      '',
        content,
        tags:       startTag ? [startTag] : [],
        pinned:     false,
        notebookId,
        createdAt:  new Date().toISOString(),
        updatedAt:  new Date().toISOString(),
        wordCount:  0,
      });
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  /* ─────────────────── Load note into DOM ── */
  useEffect(() => {
    if (!note || !bodyRef.current) return;
    const source = getNoteMarkdownSource(note);
    const content = String(note.content || '');
    const shouldUseSavedHtml = !!content.trim() && !contentLooksLikeRawSource(content);
    markdownSourceRef.current = source || '';
    renderedDomDirtyRef.current = false;
    if (markdownEnabled) {
      bodyRef.current.innerHTML = shouldUseSavedHtml
        ? sanitizeEditorHtml(content)
        : markdownToHtml(source);
      sourceViewRef.current = false;
      setMarkdownSourceClass(false);
      enhanceCodeBlocks(bodyRef.current);
      enhanceCheckLists(bodyRef.current);
      enhanceCollapsibleLists(bodyRef.current);
      enhanceMediaItems(bodyRef.current);
      normalizeFigurePositions(bodyRef.current);
      attachCodeBlockHandlers(bodyRef.current, markDirty);
    } else {
      bodyRef.current.textContent = source;
      sourceViewRef.current = true;
      setMarkdownSourceClass(true);
    }
    if (titleRef.current) {
      titleRef.current.value = note.title || '';
      autoResize(titleRef.current);
    }
    updateWC();
    requestAnimationFrame(resetEditorHistory);
    if (!paramId) titleRef.current?.focus();
  // We intentionally only run this when `note` first becomes non-null
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [note?.id]);


  /* ─────────────────── Save flush on background / unmount ── */
  useEffect(() => {
    const flush = () => {
      if (dirty) saveNowRef.current?.(true);
    };

    const onVisibility = () => {
      if (document.visibilityState === 'hidden') flush();
    };

    let appPauseHandle;
    let cancelled = false;

    async function setupPauseSave() {
      try {
        const { App: CapacitorApp } = await import('@capacitor/app');
        if (cancelled) return;
        appPauseHandle = await CapacitorApp.addListener('pause', flush);
      } catch {
        // Browser preview.
      }
    }

    setupPauseSave();
    window.addEventListener('pagehide', flush);
    document.addEventListener('visibilitychange', onVisibility);

    return () => {
      cancelled = true;
      flush();
      window.removeEventListener('pagehide', flush);
      document.removeEventListener('visibilitychange', onVisibility);
      appPauseHandle?.remove?.();
    };
  }, [dirty]);

  /* ─────────────────── Track selection for toolbar state ── */
  useEffect(() => {
    const handler = () => {
      const sel = window.getSelection();
      if (!sel || !sel.rangeCount) return;
      const body = bodyRef.current;
      const node = sel.anchorNode;
      if (body && node && body.contains(node)) {
        savedRangeRef.current = sel.getRangeAt(0).cloneRange();
        setSelectedWords(countWords(sel.toString() || ''));
      } else {
        setSelectedWords(0);
      }
      if (document.activeElement === body) updateTbState();
    };
    document.addEventListener('selectionchange', handler);
    return () => document.removeEventListener('selectionchange', handler);
  }, []);

  useEffect(() => {
    const hideMediaControls = (e) => {
      const body = bodyRef.current;
      if (!body) return;
      const inSelectedMedia = e.target?.closest?.('figure.editor-figure');
      if (inSelectedMedia && body.contains(inSelectedMedia)) return;
      const inMediaControl = e.target?.closest?.('.media-delete-btn,.media-resize-handle,.media-drag-chip');
      if (inMediaControl && body.contains(inMediaControl)) return;
      removeMediaSelected();
    };
    document.addEventListener('pointerdown', hideMediaControls, true);
    return () => document.removeEventListener('pointerdown', hideMediaControls, true);
  }, []);

  /* ─────────────────── Keyboard shortcuts (global) ── */
  useEffect(() => {
    const handler = (e) => {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'z') {
        e.preventDefault();
        if (e.shiftKey) redoEditor();
        else undoEditor();
        return;
      }
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'y') {
        e.preventDefault();
        redoEditor();
        return;
      }
      if ((e.ctrlKey || e.metaKey) && e.key === 's') {
        e.preventDefault();
        saveNow();
      }
      if (e.key === 'Escape') {
        setLinkPopover(false);
        setImageModal(false);
        setDrawModal(false);
        setNbPicker(false);
      }
    };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [note]);

  useEffect(() => () => { clearTimeout(autoSaveTimer.current); clearTimeout(historyTimerRef.current); clearTimeout(collapseTimerRef.current); }, []);

  /* ─────────────────── Mobile keyboard safe bottom panel ── */
  useEffect(() => {
    const root = document.documentElement;
    const updateKeyboardOffset = () => {
      const vv = window.visualViewport;
      let offset = 0;
      if (vv) {
        offset = Math.max(0, window.innerHeight - vv.height - vv.offsetTop);
      }
      const visibleHeight = vv ? Math.max(0, vv.height) : window.innerHeight;
      root.style.setProperty('--keyboard-offset', `${Math.round(offset)}px`);
      root.style.setProperty('--visible-viewport-h', `${Math.round(visibleHeight)}px`);
      root.classList.toggle('keyboard-open', offset > 80);
    };
    updateKeyboardOffset();
    window.visualViewport?.addEventListener('resize', updateKeyboardOffset);
    window.visualViewport?.addEventListener('scroll', updateKeyboardOffset);
    window.addEventListener('resize', updateKeyboardOffset);
    return () => {
      root.style.removeProperty('--keyboard-offset');
      root.style.removeProperty('--visible-viewport-h');
      root.classList.remove('keyboard-open');
      window.visualViewport?.removeEventListener('resize', updateKeyboardOffset);
      window.visualViewport?.removeEventListener('scroll', updateKeyboardOffset);
      window.removeEventListener('resize', updateKeyboardOffset);
    };
  }, []);

  useEffect(() => {
    if (!drawModal || !drawCanvasRef.current) return;
    const canvas = drawCanvasRef.current;

    // rAF ensures the modal is fully painted before measuring — getBoundingClientRect
    // returns 0 if called synchronously right after the modal first renders.
    let rafId = requestAnimationFrame(() => {
      const rect  = canvas.getBoundingClientRect();
      const ratio = Math.max(window.devicePixelRatio || 1, 1);
      canvas.width  = Math.max(320, Math.floor(rect.width  * ratio));
      canvas.height = Math.max(220, Math.floor(rect.height * ratio));
      const ctx = canvas.getContext('2d');
      ctx.scale(ratio, ratio);
      ctx.fillStyle = '#f7f0df';
      ctx.fillRect(0, 0, rect.width, rect.height);
    });

    const point = (evt) => {
      const r = canvas.getBoundingClientRect();
      const touch = evt.touches?.[0] || evt.changedTouches?.[0];
      return {
        x: (touch ? touch.clientX : evt.clientX) - r.left,
        y: (touch ? touch.clientY : evt.clientY) - r.top,
      };
    };

    const start = (evt) => {
      evt.preventDefault();
      const p   = point(evt);
      const ctx = canvas.getContext('2d');
      drawStateRef.current = { drawing: true, moved: false, lastX: p.x, lastY: p.y };
      ctx.strokeStyle = drawColorRef.current;
      ctx.lineWidth   = drawLineWidthRef.current;
      ctx.lineCap     = 'round';
      ctx.lineJoin    = 'round';
      ctx.beginPath();
      ctx.moveTo(p.x, p.y);
    };

    const move = (evt) => {
      if (!drawStateRef.current.drawing) return;
      evt.preventDefault();
      const p   = point(evt);
      const ctx = canvas.getContext('2d');
      ctx.strokeStyle = drawColorRef.current;
      ctx.lineWidth   = drawLineWidthRef.current;
      ctx.lineTo(p.x, p.y);
      ctx.stroke();
      drawStateRef.current = { ...drawStateRef.current, lastX: p.x, lastY: p.y, moved: true };
    };

    const end = (evt) => {
      if (!drawStateRef.current.drawing) return;
      evt.preventDefault();
      const p   = point(evt);
      const ctx = canvas.getContext('2d');
      if (!drawStateRef.current.moved) {
        ctx.fillStyle = drawColorRef.current;
        ctx.beginPath();
        ctx.arc(p.x, p.y, Math.max(1, drawLineWidthRef.current), 0, Math.PI * 2);
        ctx.fill();
      }
      drawStateRef.current.drawing = false;
      ctx.closePath();
    };

    canvas.addEventListener('pointerdown', start);
    canvas.addEventListener('pointermove', move);
    window.addEventListener('pointerup', end);
    canvas.addEventListener('touchstart', start, { passive: false });
    canvas.addEventListener('touchmove', move, { passive: false });
    window.addEventListener('touchend', end, { passive: false });

    return () => {
      cancelAnimationFrame(rafId);
      canvas.removeEventListener('pointerdown', start);
      canvas.removeEventListener('pointermove', move);
      window.removeEventListener('pointerup', end);
      canvas.removeEventListener('touchstart', start);
      canvas.removeEventListener('touchmove', move);
      window.removeEventListener('touchend', end);
    };
  // Only re-run when the modal opens/closes. Color & line-width are read via refs
  // so changing them never re-initialises (and wipes) the canvas.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [drawModal]);

  /* ──────────────────────────────────────────────────
     Core save helpers
     ────────────────────────────────────────────────── */
  function getMarkdownSource() {
    const body = bodyRef.current;
    if (!body) return markdownSourceRef.current || '';
    if (sourceViewRef.current) {
      const source = body.textContent || body.innerText || '';
      markdownSourceRef.current = source;
      renderedDomDirtyRef.current = false;
      return source;
    }
    const stable = getStableMarkdownSource();
    markdownSourceRef.current = stable;
    return stable;
  }

  function getCleanHtml() {
    const body = bodyRef.current;
    if (!body) return '';
    if (sourceViewRef.current) return markdownToHtml(body.textContent || body.innerText || '');
    const clone = body.cloneNode(true);
    clone.querySelectorAll('.code-actions,.media-delete-btn,.media-resize-handle,.media-drag-chip,.li-collapse-toggle').forEach(b => b.remove());
    clone.querySelectorAll('ul,ol').forEach(list => {
      if (list.style && list.style.display === 'none') list.style.display = '';
    });
    return clone.innerHTML;
  }

  function getCleanText() {
    const body = bodyRef.current;
    if (!body) return '';
    if (sourceViewRef.current) return body.textContent || body.innerText || '';
    const clone = body.cloneNode(true);
    clone.querySelectorAll('.code-actions,.media-delete-btn,.media-resize-handle,.media-drag-chip,.li-collapse-toggle').forEach(b => b.remove());
    return clone.innerText || '';
  }

  function getEditorSnapshot() {
    return JSON.stringify({
      title: titleRef.current?.value || '',
      content: sourceViewRef.current ? (bodyRef.current?.innerText || '') : getCleanHtml(),
      markdownSource: getMarkdownSource(),
      sourceMode: sourceViewRef.current,
      renderedDirty: renderedDomDirtyRef.current,
    });
  }

  function restoreEditorSnapshot(snapshot) {
    if (!snapshot || !bodyRef.current) return;
    let data;
    try { data = JSON.parse(snapshot); } catch { return; }
    restoringHistoryRef.current = true;
    if (titleRef.current) {
      titleRef.current.value = data.title || '';
      autoResize(titleRef.current);
    }
    if (data.markdownSource !== undefined) markdownSourceRef.current = data.markdownSource || '';
    if (data.sourceMode) {
      bodyRef.current.textContent = data.content || '';
      markdownSourceRef.current = data.content || '';
      sourceViewRef.current = true;
      renderedDomDirtyRef.current = false;
      setMarkdownSourceClass(true);
    } else {
      bodyRef.current.innerHTML = data.content || '';
      sourceViewRef.current = false;
      renderedDomDirtyRef.current = !!data.renderedDirty;
      setMarkdownSourceClass(false);
      enhanceCodeBlocks(bodyRef.current);
      enhanceCheckLists(bodyRef.current);
      enhanceCollapsibleLists(bodyRef.current);
      enhanceMediaItems(bodyRef.current);
      normalizeFigurePositions(bodyRef.current);
      attachCodeBlockHandlers(bodyRef.current, markDirty);
    }
    updateWC();
    updateTbState();
    setDirty(true);
    setSaveState('saving');
    restoringHistoryRef.current = false;
  }

  function resetEditorHistory() {
    clearTimeout(historyTimerRef.current);
    undoStackRef.current = [];
    redoStackRef.current = [];
    lastHistoryRef.current = getEditorSnapshot();
  }

  function pushEditorHistory() {
    if (restoringHistoryRef.current || !bodyRef.current) return;
    const snapshot = getEditorSnapshot();
    const last = lastHistoryRef.current;
    if (!last) {
      lastHistoryRef.current = snapshot;
      return;
    }
    if (snapshot === last) return;
    undoStackRef.current.push(last);
    if (undoStackRef.current.length > 80) undoStackRef.current.shift();
    redoStackRef.current = [];
    lastHistoryRef.current = snapshot;
  }

  function scheduleEditorHistory() {
    clearTimeout(historyTimerRef.current);
    historyTimerRef.current = setTimeout(pushEditorHistory, 120);
  }

  function undoEditor() {
    clearTimeout(historyTimerRef.current);
    pushEditorHistory();
    const previous = undoStackRef.current.pop();
    if (!previous) {
      showToast('Nothing to undo', 'fa-rotate-left');
      return;
    }
    const current = getEditorSnapshot();
    redoStackRef.current.push(current);
    lastHistoryRef.current = previous;
    restoreEditorSnapshot(previous);
    markDirty(false);
    showToast('Undo', 'fa-rotate-left');
  }

  function redoEditor() {
    clearTimeout(historyTimerRef.current);
    const next = redoStackRef.current.pop();
    if (!next) {
      showToast('Nothing to redo', 'fa-rotate-right');
      return;
    }
    const current = getEditorSnapshot();
    undoStackRef.current.push(current);
    lastHistoryRef.current = next;
    restoreEditorSnapshot(next);
    markDirty(false);
    showToast('Redo', 'fa-rotate-right');
  }

  const saveNow = useCallback((silent = false) => {
    if (savingLockRef.current) return;

    const title   = (titleRef.current?.value || '').trim();
    const content = getCleanHtml();
    const text    = getCleanText();

    const base = noteRef.current;
    if (!base) return;

    const updated = {
      ...base,
      title:     title || 'Untitled',
      content,
      markdown:  getMarkdownSource(),
      wordCount: countWords(text),
      updatedAt: new Date().toISOString(),
    };

    const saveHash = JSON.stringify({
      id: updated.id,
      title: updated.title,
      content: updated.content,
      markdown: updated.markdown,
      tags: updated.tags || [],
      notebookId: updated.notebookId || null,
      pinned: !!updated.pinned,
    });

    if (!dirty && lastSavedHashRef.current === saveHash) return;

    savingLockRef.current = true;
    try {
      saveNote(updated);
      dispatch({ type: 'RELOAD' });
      updateNote(updated);
      lastSavedHashRef.current = saveHash;
      renderedDomDirtyRef.current = false;
      if (isMountedRef.current) {
        setDirty(false);
        setSaveState('saved');
      }
      if (!silent) haptic('light');
    } catch (err) {
      console.error('Save failed', err);
      if (isMountedRef.current) {
        setSaveState('saving');
        showToast('Save failed — storage may be full', 'fa-circle-exclamation');
      }
    } finally {
      savingLockRef.current = false;
    }
  }, [dispatch, dirty]);
  saveNowRef.current = saveNow;

  function markDirty(recordHistory = true) {
    if (!noteRef.current) return;
    setDirty(true);
    setSaveState('saving');
    if (recordHistory) scheduleEditorHistory();
    clearTimeout(autoSaveTimer.current);
    if (autoSaveEnabled && !isReadMode) {
      autoSaveTimer.current = setTimeout(() => saveNowRef.current?.(true), 700);
    }
  }

  /* ──────────────────────────────────────────────────
     Word / char count
     ────────────────────────────────────────────────── */
  function updateWC() {
    const text = getCleanText();
    setWc(countWords(text));
    setCc(text.replace(/\s/g, '').length);
  }

  /* ──────────────────────────────────────────────────
     Title helpers
     ────────────────────────────────────────────────── */
  function autoResize(el) {
    el.style.height = 'auto';
    el.style.height = el.scrollHeight + 'px';
  }

  function titleKeydown(e) {
    if (e.key === 'Enter') { e.preventDefault(); bodyRef.current?.focus(); }
  }

  /* ──────────────────────────────────────────────────
     Editor focus / selection restore
     ────────────────────────────────────────────────── */
  function focusEditor() {
    const body = bodyRef.current;
    if (!body) return;
    body.focus({ preventScroll: true });

    const sel = window.getSelection();
    if (!sel) return;

    // If the selection is already inside the editor body (toolbar button tapped
    // with preventDefault keeping the selection alive), keep it as-is.
    const anchor = sel.anchorNode;
    if (anchor && body.contains(anchor)) return;

    // Otherwise restore the last saved range.
    if (savedRangeRef.current) {
      try {
        sel.removeAllRanges();
        sel.addRange(savedRangeRef.current);
        return;
      } catch {}
    }
    // Last resort: place caret at end of body.
    const range = document.createRange();
    range.selectNodeContents(body);
    range.collapse(false);
    sel.removeAllRanges();
    sel.addRange(range);
    savedRangeRef.current = range.cloneRange();
  }


  function restoreSavedSelection() {
    const sel = window.getSelection();
    if (!sel) return;
    bodyRef.current?.focus();
    if (savedRangeRef.current) {
      try {
        sel.removeAllRanges();
        sel.addRange(savedRangeRef.current);
        return;
      } catch {}
    }
    const body = bodyRef.current;
    if (!body) return;
    const range = document.createRange();
    range.selectNodeContents(body);
    range.collapse(false);
    sel.removeAllRanges();
    sel.addRange(range);
    savedRangeRef.current = range.cloneRange();
  }

  /* ──────────────────────────────────────────────────
     Toolbar: prevent blur on button tap
     ────────────────────────────────────────────────── */
  function toolbarMouseDown(e) {
    // Keep the editor selection alive when tapping toolbar buttons on mobile/desktop.
    // Without this, Android can blur the contenteditable before the code button runs.
    if (e.target.closest('button')) e.preventDefault();
  }

  function toolbarPointerDown(e) {
    // Prevent the editor body from losing focus when a toolbar button is tapped.
    // For touch (pointerType === 'touch'), we skip preventDefault so that the
    // browser still synthesizes the click event — otherwise onClick never fires
    // on Android/iOS.
    if (e.target.closest('button') && e.pointerType !== 'touch') e.preventDefault();
  }

  /* ──────────────────────────────────────────────────
     Formatting commands
     ────────────────────────────────────────────────── */
  function fmt(cmd, val) {
    focusEditor();
    // Guard: don't let list commands corrupt a check-list.
    if ((cmd === 'insertUnorderedList' || cmd === 'insertOrderedList') &&
        closestNode('check-list', bodyRef.current)) {
      return;
    }
    document.execCommand(cmd, false, val || null);
    if (['insertUnorderedList', 'insertOrderedList', 'indent', 'outdent'].includes(cmd)) {
      enhanceCollapsibleLists(bodyRef.current);
    }
    updateTbState();
    updateWC();
    markDirty();
  }

  function fmtBlock(tag) {
    focusEditor();
    const body = bodyRef.current;
    // Don't break list structure by converting a list item to a bare block.
    if (tag === 'p' && closestNode('LI', body)) return;
    const active  = currentBlockTag();
    const nextTag = (active === tag && tag !== 'p') ? 'p' : tag;
    document.execCommand('formatBlock', false, '<' + nextTag + '>');
    updateTbState();
    updateWC();
    markDirty();
  }

  function fmtInlineCode() {
    focusEditor();
    const body = bodyRef.current;
    if (!body) return;

    const existing = closestNode('CODE', body);
    if (existing && existing.parentElement?.nodeName !== 'PRE') {
      const text = document.createTextNode(existing.textContent || '');
      existing.replaceWith(text);
      const sel = window.getSelection();
      const r = document.createRange();
      r.setStartAfter(text);
      r.collapse(true);
      sel.removeAllRanges();
      sel.addRange(r);
      savedRangeRef.current = r.cloneRange();
      markDirty(); updateTbState();
      return;
    }

    const sel = window.getSelection();
    let range = sel && sel.rangeCount ? sel.getRangeAt(0) : null;
    if (!range || !body.contains(range.commonAncestorContainer)) {
      body.focus();
      range = document.createRange();
      range.selectNodeContents(body);
      range.collapse(false);
      sel.removeAllRanges();
      sel.addRange(range);
    }

    const selectedText = sel.toString();
    const code = document.createElement('code');
    code.textContent = selectedText || 'code';

    range.deleteContents();
    range.insertNode(code);

    // Add a normal text spacer after inline code so typing can continue outside it.
    const spacer = document.createTextNode(' ');
    code.after(spacer);

    const next = document.createRange();
    if (selectedText) {
      next.setStartAfter(spacer);
      next.collapse(true);
    } else {
      // Select the placeholder word so the user can immediately type over `code`.
      next.selectNodeContents(code);
    }
    sel.removeAllRanges();
    sel.addRange(next);
    savedRangeRef.current = next.cloneRange();

    body.focus();
    markDirty(); updateTbState();
  }

  function insertHR() {
    focusEditor();
    document.execCommand('insertHTML', false, '<hr><p><br></p>');
    updateTbState();
    updateWC();
    markDirty();
  }

  function insertCodeBlock() {
    focusEditor();
    const pre = insideCodeBlock(bodyRef.current);
    if (pre) {
      let after = pre.nextElementSibling;
      if (!after || after.nodeName !== 'P') {
        after = document.createElement('p');
        after.innerHTML = '<br>';
        pre.after(after);
      }
      placeCaretAtEnd(after);
      updateTbState();
      return;
    }
    const sel      = window.getSelection();
    const selected = sel && sel.rangeCount ? sel.toString() : '';
    insertCodeBlockElement(selected || '');
  }

  function insertCodeBlockElement(text = '') {
    const pre  = document.createElement('pre');
    const code = document.createElement('code');
    code.innerHTML = text ? escH(text) : '<br>';
    pre.appendChild(code);

    const after = document.createElement('p');
    after.innerHTML = '<br>';

    focusEditor();
    const sel  = window.getSelection();
    const body = bodyRef.current;
    if (sel && sel.rangeCount && body.contains(sel.anchorNode)) {
      const range = sel.getRangeAt(0);
      range.deleteContents();
      range.insertNode(after);
      range.insertNode(pre);
    } else {
      body.append(pre, after);
    }
    enhanceCodeBlocks(body);
    enhanceCheckLists(body);
    enhanceCollapsibleLists(body);
    enhanceMediaItems(body);
    attachCodeBlockHandlers(body, markDirty);
    placeCaretInCode(code);
    markDirty();
  }

  function insertChecklist() {
    focusEditor();
    const list = closestNode('check-list', bodyRef.current);
    if (list) {
      const items = [...list.querySelectorAll('li')]
        .map(li => li.innerText.trim().replace(/^\s*[☑☐]\s*/, ''))
        .filter(Boolean);
      const html = items.length
        ? items.map(t => '<p>' + escH(t) + '</p>').join('')
        : '<p><br></p>';
      list.insertAdjacentHTML('afterend', html);
      list.remove();
      markDirty(); updateTbState(); updateWC();
      return;
    }

    const ul = document.createElement('ul');
    ul.className = 'check-list';
    ul.setAttribute('data-type', 'taskList');
    ul.appendChild(createChecklistItem());

    const after = document.createElement('p');
    after.innerHTML = '<br>';

    const sel = window.getSelection();
    const body = bodyRef.current;
    if (sel && sel.rangeCount && body.contains(sel.anchorNode)) {
      const range = sel.getRangeAt(0);
      range.deleteContents();
      range.insertNode(after);
      range.insertNode(ul);
    } else {
      body.append(ul, after);
    }

    enhanceCheckLists(body);
    enhanceCollapsibleLists(body);
    const target = ul.querySelector('.check-text') || ul.querySelector('span:not(.check-box)') || ul.querySelector('li');
    placeCaretAtEnd(target);
    markDirty(); updateTbState(); updateWC();
  }

  function toggleHighlight() {
    focusEditor();
    const bg = String(document.queryCommandValue('backColor') || '').toLowerCase();
    if (bg && bg !== 'transparent' && bg !== 'rgba(0, 0, 0, 0)') {
      document.execCommand('backColor', false, 'transparent');
      document.execCommand('removeFormat', false, null);
    } else {
      document.execCommand('backColor', false, '#c9a96e55');
    }
    markDirty(); updateTbState();
  }

  function insertTable() {
    focusEditor();
    document.execCommand('insertHTML', false,
      '<table><thead><tr><th>Column 1</th><th>Column 2</th></tr></thead>' +
      '<tbody><tr><td>Text</td><td>Text</td></tr></tbody></table><p><br></p>');
    updateTbState();
    updateWC();
    markDirty();
  }

  function insertDateTime() {
    focusEditor();
    document.execCommand('insertText', false, new Date().toLocaleString());
    markDirty(); updateTbState();
  }

  function toggleAlign(where) {
    focusEditor();
    const blockEl = currentBlockEl(bodyRef.current);
    let active = blockEl ? (blockEl.style.textAlign || getComputedStyle(blockEl).textAlign) : '';
    // Normalize logical alignment keywords
    if (active === 'start') active = 'left';
    if (active === 'end')   active = 'right';
    const cmdMap = { center: 'justifyCenter', right: 'justifyRight', left: 'justifyLeft' };
    if ((where === 'center' && active === 'center') || (where === 'right' && active === 'right')) {
      document.execCommand('justifyLeft');
    } else {
      document.execCommand(cmdMap[where] || 'justifyLeft');
    }
    updateWC();
    markDirty();
    updateTbState();
  }

  function clearFormatting() {
    focusEditor();
    document.execCommand('removeFormat');
    // Only reset block to <p> when NOT inside a list (would break list structure).
    const block = currentBlockEl(bodyRef.current);
    if (!block?.closest?.('li')) {
      document.execCommand('formatBlock', false, '<p>');
    }
    updateWC();
    markDirty();
    updateTbState();
  }

  /* ──────────────────────────────────────────────────
     Link popover
     ────────────────────────────────────────────────── */
  function normalizeUrl(url = '') {
    const value = url.trim();
    if (!value) return '';
    if (/^(https?:|mailto:|tel:)/i.test(value)) return value;
    return 'https://' + value.replace(/^\/+/, '');
  }

  function toggleLinkPopover() {
    focusEditor();
    const a = closestNode('A', bodyRef.current);
    if (a) {
      const text = document.createTextNode(a.textContent || a.href || 'link');
      a.replaceWith(text);
      markDirty(); updateTbState();
      showToast('Link removed', 'fa-link-slash');
      return;
    }
    const sel = window.getSelection();
    savedRangeRef.current = sel && sel.rangeCount ? sel.getRangeAt(0).cloneRange() : null;
    const selected = sel ? sel.toString().trim() : '';
    setLinkText(selected);
    setLinkUrl('');
    setLinkPopover(true);
  }

  function insertLink() {
    const url = normalizeUrl(linkUrl);
    const label = linkText.trim();
    if (!url) return;
    setLinkPopover(false);
    setLinkUrl('');
    setLinkText('');
    restoreSavedSelection();
    const sel = window.getSelection();
    const selected = sel ? sel.toString().trim() : '';
    if (!selected && label) {
      document.execCommand(
        'insertHTML',
        false,
        `<a href="${escH(url)}" target="_blank" rel="noreferrer">${escH(label)}</a>`
      );
    } else if (!selected) {
      document.execCommand(
        'insertHTML',
        false,
        `<a href="${escH(url)}" target="_blank" rel="noreferrer">${escH(url)}</a>`
      );
    } else {
      document.execCommand('createLink', false, url);
      bodyRef.current?.querySelectorAll('a').forEach(a => {
        a.target = '_blank';
        a.rel = 'noreferrer';
      });
    }
    markDirty();
    updateTbState();
    showToast('Link inserted', 'fa-link');
  }

  function linkKeydown(e) {
    if (e.key === 'Enter') insertLink();
    if (e.key === 'Escape') {
      setLinkPopover(false);
      setLinkUrl('');
      setLinkText('');
    }
  }


  function toggleCollapsedList(li) {
    if (!li || !bodyRef.current?.contains(li)) return;
    normalizeNestedLists(bodyRef.current);
    const lists = nestedListsForLi(li);
    if (!lists.length) return;
    const collapsed = li.dataset.collapsed === 'true';
    li.dataset.collapsed = collapsed ? 'false' : 'true';
    li.classList.toggle('li-collapsed', !collapsed);
    enhanceCollapsibleLists(bodyRef.current);
    markDirty();
    updateWC();
    updateTbState();
  }

  function rangeBelongsToBody(range) {
    const body = bodyRef.current;
    if (!body || !range) return false;
    const node = range.commonAncestorContainer;
    return node === body || body.contains(node.nodeType === 1 ? node : node.parentElement);
  }

  function setMediaSelected(fig) {
    const body = bodyRef.current;
    body?.querySelectorAll('figure.editor-figure.media-selected').forEach(el => {
      if (el !== fig) el.classList.remove('media-selected');
    });
    fig?.classList.add('media-selected');
  }

  function toggleMediaSelected(fig) {
    if (!fig) return;
    const already = fig.classList.contains('media-selected');
    if (already) {
      fig.classList.remove('media-selected');
      return;
    }
    setMediaSelected(fig);
  }

  function removeMediaSelected() {
    bodyRef.current?.querySelectorAll('figure.editor-figure.media-selected').forEach(el => el.classList.remove('media-selected'));
  }

  function ensureMediaControls(fig) {
    fig.setAttribute('contenteditable', 'false');
    fig.dataset.media = fig.dataset.media || 'image';
    fig.style.position = fig.style.position || 'relative';
    // Do NOT set display via inline style — let the CSS class control it
    // (display:inline-block caused drawings to overlap text when placed inside <p>)
    fig.style.touchAction = 'none';

    const img = fig.querySelector('img');
    if (img) {
      img.classList.add('editor-inline-image');
      img.loading = 'lazy';
      img.draggable = false;
      img.style.pointerEvents = 'none';
    }

    if (!fig.querySelector('.media-drag-chip')) {
      const chip = document.createElement('button');
      chip.type = 'button';
      chip.className = 'media-drag-chip';
      chip.title = 'Drag to move';
      chip.innerHTML = '<i class="fa-solid fa-arrows-up-down-left-right"></i>';
      fig.appendChild(chip);
    }

    if (!fig.querySelector('.media-delete-btn')) {
      const btn = document.createElement('button');
      btn.type = 'button';
      btn.className = 'media-delete-btn';
      btn.title = fig.dataset.media === 'drawing' ? 'Delete drawing' : 'Delete photo';
      btn.innerHTML = '<i class="fa-solid fa-trash-can"></i>';
      fig.appendChild(btn);
    }

    if (!fig.querySelector('.media-resize-handle')) {
      const handle = document.createElement('span');
      handle.className = 'media-resize-handle';
      handle.title = 'Drag to resize';
      handle.innerHTML = '<i class="fa-solid fa-up-right-and-down-left-from-center"></i>';
      fig.appendChild(handle);
    }
  }


  function getClientPoint(evt) {
    const touch = evt.touches?.[0] || evt.changedTouches?.[0];
    if (touch) return { x: touch.clientX, y: touch.clientY, isTouch: true };
    return { x: evt.clientX, y: evt.clientY, isTouch: false };
  }

  function startMediaDrag(e, fig) {
    if (!fig || !bodyRef.current?.contains(fig)) return;
    const isMouse = e.pointerType === 'mouse' || (e.type === 'mousedown');
    if (isMouse && e.button !== 0) return;
    e.preventDefault();
    e.stopPropagation();
    setMediaSelected(fig);

    const start = getClientPoint(e);
    const startX = start.x;
    const startY = start.y;
    const baseX = Number(fig.dataset.x || '0');
    const baseY = Number(fig.dataset.y || '0');
    const useTouch = start.isTouch;
    let moved = false;

    try { if (!useTouch) fig.setPointerCapture?.(e.pointerId); } catch {}

    const onMove = (ev) => {
      const p = getClientPoint(ev);
      ev.preventDefault();
      const dx = Math.round(p.x - startX);
      const dy = Math.round(p.y - startY);
      if (Math.abs(dx) + Math.abs(dy) > 3) moved = true;
      const nextX = Math.max(-220, Math.min(220, baseX + dx));
      const nextY = Math.max(-220, Math.min(220, baseY + dy));
      fig.dataset.x = String(nextX);
      fig.dataset.y = String(nextY);
      fig.style.transform = `translate(${nextX}px, ${nextY}px)`;
    };

    const onUp = () => {
      try { if (!useTouch) fig.releasePointerCapture?.(e.pointerId); } catch {}
      window.removeEventListener(useTouch ? 'touchmove' : 'pointermove', onMove, true);
      window.removeEventListener(useTouch ? 'touchend' : 'pointerup', onUp, true);
      if (!useTouch) window.removeEventListener('pointercancel', onUp, true);
      if (moved) {
        markDirty();
        updateTbState();
        showToast('Media moved', 'fa-arrows-up-down-left-right');
      }
    };

    if (useTouch) {
      window.addEventListener('touchmove', onMove, { capture: true, passive: false });
      window.addEventListener('touchend', onUp, { capture: true, passive: false });
    } else {
      window.addEventListener('pointermove', onMove, true);
      window.addEventListener('pointerup', onUp, true);
      window.addEventListener('pointercancel', onUp, true);
    }
  }

  function startMediaResize(e, fig) {
    if (!fig || !bodyRef.current?.contains(fig)) return;
    const isMouse = e.pointerType === 'mouse' || (e.type === 'mousedown');
    if (isMouse && e.button !== 0) return;
    e.preventDefault();
    e.stopPropagation();
    setMediaSelected(fig);

    const start = getClientPoint(e);
    const startX = start.x;
    const startW = fig.getBoundingClientRect().width;
    const maxW = Math.max(160, (bodyRef.current?.clientWidth || 360) - 24);
    const useTouch = start.isTouch;
    let moved = false;

    try { if (!useTouch) fig.setPointerCapture?.(e.pointerId); } catch {}

    const onMove = (ev) => {
      const p = getClientPoint(ev);
      ev.preventDefault();
      const dx = p.x - startX;
      const nextW = Math.round(Math.max(90, Math.min(maxW, startW + dx)));
      if (Math.abs(dx) > 3) moved = true;
      fig.style.width = `${nextW}px`;
      fig.style.maxWidth = 'none';
      const img = fig.querySelector('img');
      if (img) {
        img.style.width = '100%';
        img.style.maxHeight = 'none';
      }
    };

    const onUp = () => {
      try { if (!useTouch) fig.releasePointerCapture?.(e.pointerId); } catch {}
      window.removeEventListener(useTouch ? 'touchmove' : 'pointermove', onMove, true);
      window.removeEventListener(useTouch ? 'touchend' : 'pointerup', onUp, true);
      if (!useTouch) window.removeEventListener('pointercancel', onUp, true);
      if (moved) {
        markDirty();
        updateTbState();
        showToast('Media resized', 'fa-up-right-and-down-left-from-center');
      }
    };

    if (useTouch) {
      window.addEventListener('touchmove', onMove, { capture: true, passive: false });
      window.addEventListener('touchend', onUp, { capture: true, passive: false });
    } else {
      window.addEventListener('pointermove', onMove, true);
      window.addEventListener('pointerup', onUp, true);
      window.addEventListener('pointercancel', onUp, true);
    }
  }

  function deleteMediaFigure(fig) {
    if (!fig || !bodyRef.current?.contains(fig)) return;
    const next = fig.nextElementSibling;
    fig.remove();
    if (next && next.matches('p') && !next.innerText.trim() && !next.querySelector('img,figure,table,pre')) {
      next.remove();
    }
    markDirty();
    updateWC();
    updateTbState();
    showToast('Media deleted', 'fa-trash-can');
  }

  function enhanceMediaItems(root = bodyRef.current) {
    if (!root) return;
    root.querySelectorAll('figure.editor-figure').forEach(fig => ensureMediaControls(fig));
  }

  // Migrate old saved notes: figures stored inside <p> tags (invalid HTML) caused
  // drawings to overlap text. This hoists every figure to be a direct child of
  // the editor body and clears any stale inline display style.
  function normalizeFigurePositions(body) {
    if (!body) return;
    body.querySelectorAll('figure.editor-figure').forEach(fig => {
      // 1. Always clear inline display so the CSS class (display:block) wins
      fig.style.display = '';

      // 2. If the figure is nested inside something other than body, hoist it out
      if (fig.parentNode !== body) {
        const parent = fig.parentNode;
        // Insert figure right after its parent block
        parent.after(fig);
        // If parent became empty, give it a placeholder
        if (!parent.textContent?.trim() && !parent.querySelector(':not(br)')) {
          parent.innerHTML = '<br>';
        }
      }

      // 3. Ensure there is a paragraph right after the figure to type into
      const next = fig.nextSibling;
      if (!next || (next.nodeType === 1 && next.tagName !== 'P')) {
        const after = document.createElement('p');
        after.innerHTML = '<br>';
        fig.after(after);
      }
    });
  }

  function insertImageFromDataUrl(src, alt = 'Inserted image', type = 'image') {
    if (!src || !bodyRef.current) return;
    const body = bodyRef.current;
    body.focus();

    const figure = document.createElement('figure');
    figure.className = `editor-figure ${type === 'drawing' ? 'drawing-figure' : 'photo-figure'}`;
    figure.contentEditable = 'false';
    figure.dataset.media = type;
    figure.style.width = type === 'drawing' ? '190px' : '180px';

    const img = document.createElement('img');
    img.src = src;
    img.alt = alt || (type === 'drawing' ? 'Drawing' : 'Photo');
    img.className = 'editor-inline-image';
    img.loading = 'lazy';
    figure.appendChild(img);

    ensureMediaControls(figure);

    const after = document.createElement('p');
    after.innerHTML = '<br>';

    const sel = window.getSelection();
    let range = savedRangeRef.current && rangeBelongsToBody(savedRangeRef.current)
      ? savedRangeRef.current.cloneRange()
      : null;

    if (!range && sel && sel.rangeCount > 0) {
      range = sel.getRangeAt(0).cloneRange();
    }

    // Delete any selected content first
    if (range && !range.collapsed) {
      range.deleteContents();
    }

    // Walk up from the cursor to find the direct child of body.
    // Inserting <figure> *inside* a <p> creates invalid HTML and causes
    // the drawing to visually overlap text as the user types. We must
    // insert the figure as a sibling of block elements, not inside them.
    let blockRef = null;
    if (range) {
      let node = range.startContainer;
      while (node && node.parentNode !== body) {
        node = node.parentNode;
      }
      if (node && node !== body && node.parentNode === body) {
        blockRef = node;
      }
    }

    if (blockRef) {
      // Insert figure after the current block, then the empty paragraph after it
      blockRef.after(figure);
      figure.after(after);
    } else {
      // Fallback: append to end of body
      body.appendChild(figure);
      body.appendChild(after);
    }

    const placeholder = body.querySelector('.image-placeholder');
    if (placeholder) placeholder.remove();

    enhanceMediaItems(body);
    placeCaretAtEnd(after);
    if (sel) {
      const next = document.createRange();
      next.selectNodeContents(after);
      next.collapse(true);
      sel.removeAllRanges();
      sel.addRange(next);
      savedRangeRef.current = next.cloneRange();
    }

    markDirty();
    updateWC();
    updateTbState();
    showToast(type === 'drawing' ? 'Drawing added' : 'Photo added', type === 'drawing' ? 'fa-pen-ruler' : 'fa-image');
  }

  function fileToOptimizedDataUrl(file) {
    // Use the original data URL on mobile. Canvas compression can turn some
    // Android images black because of decoder/orientation issues. Display size
    // is controlled by CSS, so photos still appear small in the note.
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onerror = () => reject(reader.error || new Error('Could not read image'));
      reader.onload = () => resolve(String(reader.result || ''));
      reader.readAsDataURL(file);
    });
  }

  function openPhotoPicker() {
    const sel = window.getSelection();
    if (sel && sel.rangeCount && rangeBelongsToBody(sel.getRangeAt(0))) {
      savedRangeRef.current = sel.getRangeAt(0).cloneRange();
    }
    // Trigger the file picker FIRST, while still inside the user-gesture call stack.
    // Wrapping in requestAnimationFrame breaks iOS Safari (file open is blocked if
    // it's not called synchronously from the tap handler).
    imageInputRef.current?.click();
    setImageModal(false);
  }

  async function onPickPhoto(e) {
    const file = e.target.files?.[0];
    if (!file) return;
    try {
      const dataUrl = await fileToOptimizedDataUrl(file);

      // Warn if this photo would push localStorage close to quota (~5MB)
      const currentUsage = Object.keys(localStorage)
        .filter(k => k.startsWith('iw_'))
        .reduce((sum, k) => sum + (localStorage.getItem(k) || '').length * 2, 0);
      const photoSize = dataUrl.length * 2;
      const totalAfter = currentUsage + photoSize;
      if (totalAfter > 4 * 1024 * 1024) {
        showToast('⚠️ Storage nearly full — photo may not save', 'fa-triangle-exclamation');
      }

      insertImageFromDataUrl(dataUrl, file.name || 'Photo');
    } catch {
      showToast('Could not add photo', 'fa-circle-exclamation');
    } finally {
      e.target.value = '';
    }
  }

  function openDrawModal() {
    const sel = window.getSelection();
    savedRangeRef.current = sel && sel.rangeCount ? sel.getRangeAt(0).cloneRange() : savedRangeRef.current;
    setDrawModal(true);
  }

  function clearDrawing() {
    const canvas = drawCanvasRef.current;
    if (!canvas) return;
    const ctx   = canvas.getContext('2d');
    const ratio = Math.max(window.devicePixelRatio || 1, 1);
    ctx.fillStyle = '#f7f0df';
    // Canvas pixels = ratio × CSS pixels, so divide back to clear the full surface.
    ctx.fillRect(0, 0, canvas.width / ratio, canvas.height / ratio);
  }

  function insertDrawing() {
    const canvas = drawCanvasRef.current;
    if (!canvas) return;
    insertImageFromDataUrl(canvas.toDataURL('image/png'), 'Drawing', 'drawing');
    setDrawModal(false);
  }

  /* ──────────────────────────────────────────────────
     Markdown shortcuts applied immediately after ONE space
     Works with Android/mobile keyboards by handling:
       1) beforeinput when the space is about to be inserted
       2) input when the space has already been inserted
     ────────────────────────────────────────────────── */
  function applyRichMarkdownShortcut(trigger = 'auto', pendingText = '') {
    if (!markdownEnabled) return false;

    const body = bodyRef.current;
    if (!body) return false;

    const sel = window.getSelection();
    if (!sel || !sel.anchorNode) return false;

    const block = currentBlockEl(body);
    if (!block || block.closest?.('pre')) return false;

    // Read only the active line/block and normalize Android keyboard spaces.
    let raw = (block.innerText || block.textContent || '')
      .replace(/\u00a0/g, ' ')
      .replace(/\u200B/g, '')
      .replace(/\r/g, '');

    // If browser gives the whole editor/body, only use the current last line.
    raw = raw.split('\n').pop() || raw;

    // beforeinput runs BEFORE the typed space exists, so add the pending space.
    const typed = raw + (pendingText || '');

    // Convert only when there is exactly one final space after the marker.
    const shortcut = typed;

    const replaceBlockWith = (el, focusTarget = el) => {
      if (block === body) {
        body.innerHTML = '';
        body.appendChild(el);
      } else {
        block.replaceWith(el);
      }
      placeCaretAtEnd(focusTarget);
      markDirty();
      updateWC();
      updateTbState();
      return true;
    };

    const makeBlock = (tag) => {
      const el = document.createElement(tag);
      el.innerHTML = '<br>';
      return replaceBlockWith(el);
    };

    // Headings: #<space>, ##<space>, ###<space>
    if (shortcut === '# ')   return makeBlock('h1');
    if (shortcut === '## ')  return makeBlock('h2');
    if (shortcut === '### ') return makeBlock('h3');

    // Quote: ><space>
    if (shortcut === '> ') return makeBlock('blockquote');

    // Bullet: -<space>
    if (shortcut === '- ') {
      const ul = document.createElement('ul');
      ul.innerHTML = '<li><br></li>';
      return replaceBlockWith(ul, ul.querySelector('li'));
    }

    // Number list: 1.<space>
    if (shortcut === '1. ') {
      const ol = document.createElement('ol');
      ol.innerHTML = '<li><br></li>';
      return replaceBlockWith(ol, ol.querySelector('li'));
    }

    // Checklist: -[]<space> or - [ ]<space>
    if (shortcut === '-[] ' || shortcut === '- [ ] ') {
      const ul = document.createElement('ul');
      ul.className = 'check-list';
      ul.setAttribute('data-type', 'taskList');
      ul.appendChild(createChecklistItem());
      if (block === body) {
        body.innerHTML = '';
        body.appendChild(ul);
      } else {
        block.replaceWith(ul);
      }
      const target = ul.querySelector('.check-text') || ul.querySelector('span:not(.check-box)') || ul.querySelector('li');
      placeCaretAtEnd(target);
      markDirty();
      updateWC();
      updateTbState();
      return true;
    }

    // Code block: ```<space> or ~~~<space>. Enter is handled separately too.
    if (shortcut === '``` ' || shortcut === '~~~ ' || shortcut === '```' || shortcut === '~~~') {
      if (block === body) body.innerHTML = '';
      else block.remove();
      insertCodeBlockElement('');
      markDirty();
      updateWC();
      updateTbState();
      return true;
    }

    return false;
  }

  /* ──────────────────────────────────────────────────
     Inline backtick → <code>
     ────────────────────────────────────────────────── */
  function convertBacktickInlineCode() {
    const block = currentBlockEl(bodyRef.current);
    if (!block || block === bodyRef.current || block.closest('pre')) return;
    const text = block.innerText;
    if (!/`[^`\n]+`/.test(text)) return;
    const html = escH(text).replace(/`([^`\n]+)`/g, '<code>$1</code>');
    block.innerHTML = html || '<br>';
    placeCaretAtEnd(block);
    markDirty();
  }

  function onBodyBeforeInput(/* e */) {
    // Intentionally empty: markdown shortcuts are applied in onBodyInput after the
    // space is fully inserted, keeping Android keyboards open. No action needed here.
  }

  /* ──────────────────────────────────────────────────
     Editor keydown
     ────────────────────────────────────────────────── */
  function editorKeydown(e) {
    const body  = bodyRef.current;
    const sel   = window.getSelection();
    const node  = sel?.anchorNode ? (sel.anchorNode.nodeType === 1 ? sel.anchorNode : sel.anchorNode.parentElement) : null;
    const pre   = node?.closest?.('pre') || null;

    // Ctrl / Cmd shortcuts
    if ((e.ctrlKey || e.metaKey) && !e.shiftKey) {
      const k = e.key.toLowerCase();
      if (k === 'b') { e.preventDefault(); return fmt('bold'); }
      if (k === 'i') { e.preventDefault(); return fmt('italic'); }
      if (k === 'u') { e.preventDefault(); return fmt('underline'); }
      if (k === 'k') { e.preventDefault(); return toggleLinkPopover(); }
      if (k === 'e') { e.preventDefault(); return fmtInlineCode(); }
      if (k === '`') { e.preventDefault(); return insertCodeBlock(); }
    }

    // Tab → two spaces
    if (e.key === 'Tab') {
      e.preventDefault();
      document.execCommand('insertText', false, '  ');
      markDirty();
      return;
    }

    // Backspace / Delete inside empty code block → remove block
    if ((e.key === 'Backspace' || e.key === 'Delete') && pre) {
      const codeEl = pre.querySelector('code') || pre;
      const text   = (codeEl.innerText || codeEl.textContent || '').replace(/\u200B/g, '').trim();
      if (!text) {
        e.preventDefault();
        const p = document.createElement('p'); p.innerHTML = '<br>';
        pre.replaceWith(p); placeCaretAtEnd(p); markDirty(); updateWC();
        return;
      }
    }

    // Backtick wraps selection in inline code
    if (e.key === '`' && !e.ctrlKey && !e.metaKey) {
      const selected = sel && sel.rangeCount && !sel.isCollapsed ? sel.toString() : '';
      if (selected) {
        e.preventDefault();
        document.execCommand('insertHTML', false, '<code>' + escH(selected) + '</code>');
        markDirty();
      }
      return;
    }


    const checklist = closestNode('check-list', body);
    const checklistItem = closestNode('LI', body);
    if (checklist && checklistItem) {
      if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault();
        const textSpan = checklistItem.querySelector('.check-text') || checklistItem.querySelector('span:not(.check-box)');
        const itemText = (textSpan?.innerText || checklistItem.innerText || '').replace(/ /g, ' ').trim();
        const nextLi = createChecklistItem();
        if (!itemText) {
          const p = document.createElement('p');
          p.innerHTML = '<br>';
          if (checklist.children.length <= 1) {
            checklist.replaceWith(p);
          } else {
            checklistItem.replaceWith(p);
          }
          placeCaretAtEnd(p);
        } else {
          checklistItem.after(nextLi);
          enhanceCheckLists(body);
          enhanceCollapsibleLists(body);
          placeCaretAtEnd(nextLi.querySelector('.check-text') || nextLi.querySelector('span:not(.check-box)')); 
        }
        markDirty();
        updateWC();
        updateTbState();
        return;
      }

      if (e.key === 'Backspace') {
        const textSpan = checklistItem.querySelector('.check-text') || checklistItem.querySelector('span:not(.check-box)');
        const itemText = (textSpan?.innerText || checklistItem.innerText || '').replace(/ /g, ' ').trim();
        if (!itemText) {
          const selNow = window.getSelection();
          const collapsed = !selNow || selNow.isCollapsed;
          if (collapsed) {
            e.preventDefault();
            const prevLi = checklistItem.previousElementSibling;
            const nextLi = checklistItem.nextElementSibling;
            if (checklist.children.length <= 1) {
              const p = document.createElement('p');
              p.innerHTML = '<br>';
              checklist.replaceWith(p);
              placeCaretAtEnd(p);
            } else {
              checklistItem.remove();
              const target = (prevLi || nextLi)?.querySelector('.check-text') || (prevLi || nextLi)?.querySelector('span:not(.check-box)') || (prevLi || nextLi);
              if (target) placeCaretAtEnd(target);
            }
            markDirty();
            updateWC();
            updateTbState();
            return;
          }
        }
      }
    }

    // Enter key
    if (e.key === 'Enter') {
      // Inside code block
      if (pre && !e.shiftKey) {
        const codeEl = pre.querySelector('code') || pre;
        const text   = (codeEl.innerText || '').replace(/\u200B/g, '');
        const lines  = text.split('\n');
        const last   = lines[lines.length - 1].trim();
        if (last === '```') {
          // Close code block
          e.preventDefault();
          lines.pop();
          codeEl.textContent = lines.join('\n').replace(/\n$/, '');
          const p = document.createElement('p'); p.innerHTML = '<br>';
          pre.after(p); placeCaretAtEnd(p); enhanceCodeBlocks(body);
          attachCodeBlockHandlers(body, markDirty);
          markDirty();
          return;
        }
        e.preventDefault();
        const r = sel && sel.rangeCount ? sel.getRangeAt(0) : null;
        if (r) {
          r.deleteContents();
          const br = document.createTextNode('\n');
          r.insertNode(br);
          r.setStartAfter(br); r.collapse(true);
          sel.removeAllRanges(); sel.addRange(r);
        } else {
          codeEl.appendChild(document.createTextNode('\n'));
          placeCaretInCode(codeEl);
        }
        markDirty();
        return;
      }

      // Slash / shorthand commands
      const block = currentBlockEl(body);
      const bText = (block ? block.innerText : '').replace(/\u00a0/g, ' ').trim();
      if (markdownEnabled && applyRichMarkdownShortcut('enter')) { e.preventDefault(); return; }
      if (bText === '```') { e.preventDefault(); if (block) block.remove(); insertCodeBlockElement(''); return; }
      if (/^\/code\b/i.test(bText))  { e.preventDefault(); if (block) block.remove(); insertCodeBlockElement(''); return; }
      if (/^\/table\b/i.test(bText)) { e.preventDefault(); block?.remove(); insertTable(); return; }
      if (/^\/check\b/i.test(bText)) { e.preventDefault(); block?.remove(); insertChecklist(); return; }
    }

    // Markdown shortcuts are applied in onBodyInput after the actual space is inserted.
    // This keeps Android/mobile keyboards open.
  }

  function editorKeyup(e) {
    if (markdownEnabled && e.key === '`') convertBacktickInlineCode();
    updateTbState();
  }

  /* ──────────────────────────────────────────────────
     Body input handler
     ────────────────────────────────────────────────── */
  function onBodyInput() {
    const body = bodyRef.current;
    if (sourceViewRef.current) {
      markdownSourceRef.current = body?.textContent || body?.innerText || '';
      renderedDomDirtyRef.current = false;
      markDirty();
      updateWC();
      updateTbState();
      return;
    }
    renderedDomDirtyRef.current = true;
    enhanceCodeBlocks(body);
    enhanceCheckLists(body);
    // Debounce collapse enhance: normalizeNestedLists moves DOM nodes and
    // can reset the cursor if run on every keystroke.
    clearTimeout(collapseTimerRef.current);
    collapseTimerRef.current = setTimeout(() => enhanceCollapsibleLists(body), 300);
    enhanceMediaItems(body);
    attachCodeBlockHandlers(body, markDirty);
    if (markdownEnabled) {
      // Mobile-safe Markdown conversion: wait until the space is actually inserted,
      // then replace the block and immediately restore focus to the editor root.
      if (applyRichMarkdownShortcut('input')) {
        requestAnimationFrame(() => {
          try { bodyRef.current?.focus({ preventScroll: true }); } catch { bodyRef.current?.focus(); }
          updateTbState();
          updateWC();
        });
        return;
      }
      convertBacktickInlineCode();
    }
    markDirty();
    updateWC();
    updateTbState();
  }


  function toggleChecklistBox(box, keepCaret = true) {
    if (!box || !bodyRef.current?.contains(box)) return;
    const checked = box.dataset.checked === 'true';
    const next = checked ? 'false' : 'true';
    box.dataset.checked = next;
    box.setAttribute('aria-checked', next);
    const li = box.closest('li');
    if (li) li.dataset.checked = next;
    const text = box.parentElement?.querySelector('.check-text') || box.parentElement?.querySelector('span:not(.check-box)');
    if (keepCaret && text) requestAnimationFrame(() => placeCaretAtEnd(text));
    markDirty();
    updateWC();
    updateTbState();
  }

  function editorCheckEvent(e) {
    const collapseBtn = e.target.closest?.('.li-collapse-toggle');
    if (collapseBtn && bodyRef.current?.contains(collapseBtn)) {
      e.preventDefault();
      e.stopPropagation();
      // Android may fire pointerdown + click for one tap. Debounce to avoid double-toggle.
      const now = Date.now();
      const last = Number(collapseBtn.dataset.lastToggle || '0');
      // 500ms debounce: pointerdown + click from the same tap must not double-toggle.
      if (now - last > 500) {
        collapseBtn.dataset.lastToggle = String(now);
        toggleCollapsedList(collapseBtn.closest('li'));
      }
      return;
    }

    if (isReadMode) return;
    const mediaDelete = e.target.closest?.('.media-delete-btn');
    if (mediaDelete && bodyRef.current?.contains(mediaDelete)) {
      e.preventDefault();
      e.stopPropagation();
      deleteMediaFigure(mediaDelete.closest('figure.editor-figure'));
      return;
    }

    const mediaResize = e.target.closest?.('.media-resize-handle');
    if (mediaResize && bodyRef.current?.contains(mediaResize)) {
      e.preventDefault();
      e.stopPropagation();
      setMediaSelected(mediaResize.closest('figure.editor-figure'));
      if (e.type !== 'click') startMediaResize(e, mediaResize.closest('figure.editor-figure'));
      return;
    }

    const mediaDrag = e.target.closest?.('.media-drag-chip');
    if (mediaDrag && bodyRef.current?.contains(mediaDrag)) {
      e.preventDefault();
      e.stopPropagation();
      setMediaSelected(mediaDrag.closest('figure.editor-figure'));
      if (e.type !== 'click') startMediaDrag(e, mediaDrag.closest('figure.editor-figure'));
      return;
    }

    const mediaFig = e.target.closest?.('figure.editor-figure');
    if (mediaFig && bodyRef.current?.contains(mediaFig)) {
      e.preventDefault();
      e.stopPropagation();
      if (e.type === 'click') {
        toggleMediaSelected(mediaFig);
      } else {
        // pointerdown on figure body: select it, then start drag after 180ms
        // (long-press to drag directly without needing the drag chip).
        setMediaSelected(mediaFig);
        const dragTimer = setTimeout(() => {
          startMediaDrag(e, mediaFig);
        }, 180);
        // Cancel the long-press drag if the pointer moves enough (it's a scroll)
        const cancel = () => clearTimeout(dragTimer);
        window.addEventListener('pointerup', cancel, { once: true });
        window.addEventListener('pointermove', cancel, { once: true });
      }
      return;
    }

    removeMediaSelected();

    const placeholder = e.target.closest?.('.image-placeholder');
    if (placeholder && bodyRef.current?.contains(placeholder)) {
      e.preventDefault();
      e.stopPropagation();
      const range = document.createRange();
      range.setStartAfter(placeholder);
      range.collapse(true);
      savedRangeRef.current = range.cloneRange();
      openPhotoPicker();
      return;
    }

    const box = e.target.closest?.('.check-box');
    if (!box || !bodyRef.current?.contains(box)) return;
    e.preventDefault();
    e.stopPropagation();

    // Android Chrome can fire pointer + click for the same tap. Avoid double-toggle.
    const now = Date.now();
    const last = Number(box.dataset.lastToggle || '0');
    if (now - last < 320) return;
    box.dataset.lastToggle = String(now);

    toggleChecklistBox(box, true);
  }


  function onBodyPaste(e) {
    if (isReadMode) return;

    const items = [...(e.clipboardData?.items || [])];
    const imageItem = items.find(item => item.type?.startsWith('image/'));
    if (imageItem) {
      e.preventDefault();
      const file = imageItem.getAsFile();
      if (file) {
        fileToOptimizedDataUrl(file)
          .then(dataUrl => insertImageFromDataUrl(dataUrl, file.name || 'Pasted image'))
          .catch(() => showToast('Could not paste photo', 'fa-circle-exclamation'));
      }
      return;
    }

    const html = e.clipboardData?.getData('text/html') || '';
    const text = e.clipboardData?.getData('text/plain') || '';
    const shouldRender = markdownEnabled && ((text && (looksLikeMarkdown(text) || looksLikeHtmlSource(text))) || (html && looksLikeHtmlSource(html)));
    if (!shouldRender) return;

    e.preventDefault();
    pushEditorHistory();
    const body = bodyRef.current;
    const currentPlain = (body?.innerText || body?.textContent || '').trim();
    const currentHtml = (body?.innerHTML || '').replace(/<p><br><\/p>|<br>|&nbsp;/g, '').trim();
    const pasteSource = text || htmlToMarkdown(sanitizeEditorHtml(html));
    const rendered = text
      ? renderPastedText(text)
      : sanitizeEditorHtml(html);

    // Preserve the exact pasted Markdown for future ON/OFF toggles and saves.
    // When the editor is empty, replace the whole source. Otherwise append.
    if (!currentPlain && !currentHtml) {
      markdownSourceRef.current = pasteSource;
    } else if (sourceViewRef.current) {
      markdownSourceRef.current = body?.textContent || body?.innerText || '';
    } else if (pasteSource && (looksLikeMarkdown(pasteSource) || looksLikeHtmlSource(pasteSource))) {
      const before = markdownSourceRef.current || htmlToMarkdown(getCleanHtml());
      markdownSourceRef.current = `${before}${before.trim() ? '\n\n' : ''}${pasteSource}`;
    }

    document.execCommand('insertHTML', false, rendered);
    enhanceCodeBlocks(body);
    enhanceCheckLists(body);
    enhanceCollapsibleLists(body);
    enhanceMediaItems(body);
    attachCodeBlockHandlers(body, markDirty);
    if (!sourceViewRef.current) renderedDomDirtyRef.current = true;
    markDirty(false);
    updateWC();
    updateTbState();
    showToast(looksLikeHtmlSource(html || text) ? 'Formatted paste rendered' : 'Markdown pasted', 'fa-file-lines');
  }

  /* ──────────────────────────────────────────────────
     Toolbar state updater
     ────────────────────────────────────────────────── */
  function updateTbState() {
    const body     = bodyRef.current;
    const block    = (document.queryCommandValue('formatBlock') || '').toLowerCase().replace(/[<>]/g, '');
    const blockEl  = currentBlockEl(body);
    const align    = blockEl ? (blockEl.style.textAlign || getComputedStyle(blockEl).textAlign) : '';
    const bg       = String(document.queryCommandValue('backColor') || '').toLowerCase();
    const inCB     = !!insideCodeBlock(body);
    const inCode   = !!closestNode('CODE', body) && !inCB;
    const inLink   = !!closestNode('A', body);
    const inCheck  = !!closestNode('check-list', body);
    const inUL     = document.queryCommandState('insertUnorderedList');
    const inOL     = document.queryCommandState('insertOrderedList');

    setTbState({
      markdown: markdownEnabled,
      bold:      document.queryCommandState('bold'),
      italic:    document.queryCommandState('italic'),
      underline: document.queryCommandState('underline'),
      strike:    document.queryCommandState('strikeThrough'),
      sup:       document.queryCommandState('superscript'),
      sub:       document.queryCommandState('subscript'),
      h1: block === 'h1', h2: block === 'h2', h3: block === 'h3',
      p:  block === 'p' || block === 'div' || block === '',
      bq: block === 'blockquote',
      ul: inUL && !inCheck, ol: inOL, check: inCheck,
      left:   !align || align === 'start' || align === 'left',
      center: align === 'center',
      right:  align === 'right' || align === 'end',
      highlight: bg && bg !== 'transparent' && bg !== 'rgba(0, 0, 0, 0)',
      code:      inCode,
      codeblock: inCB,
      link:      inLink,
    });
  }

  function toggleMarkdownMode() {
    setMarkdownEnabled(prev => {
      const next = !prev;
      showToast(next ? 'Markdown render mode on' : 'Markdown text mode on', next ? 'fa-markdown' : 'fa-file-lines');
      return next;
    });
    requestAnimationFrame(updateTbState);
  }

  /* ──────────────────────────────────────────────────
     Toolbar custom handler dispatch
     ────────────────────────────────────────────────── */
  function handleToolClick(key) {
    switch (key) {
      case 'markdown':  return toggleMarkdownMode();
      case 'undo':      return undoEditor();
      case 'redo':      return redoEditor();
      case 'bold':      return fmt('bold');
      case 'italic':    return fmt('italic');
      case 'underline': return fmt('underline');
      case 'strike':    return fmt('strikeThrough');
      case 'sup':       return fmt('superscript');
      case 'sub':       return fmt('subscript');
      case 'h1':        return fmtBlock('h1');
      case 'h2':        return fmtBlock('h2');
      case 'h3':        return fmtBlock('h3');
      case 'p':         return fmtBlock('p');
      case 'bq':        return fmtBlock('blockquote');
      case 'ul':        return fmt('insertUnorderedList');
      case 'ol':        return fmt('insertOrderedList');
      case 'check':     return insertChecklist();
      case 'left':      return toggleAlign('left');
      case 'center':    return toggleAlign('center');
      case 'right':     return toggleAlign('right');
      case 'indent':    return fmt('indent');
      case 'outdent':   return fmt('outdent');
      case 'highlight': return toggleHighlight();
      case 'table':     return insertTable();
      case 'datetime':  return insertDateTime();
      case 'hr':        return insertHR();
      case 'code':      return fmtInlineCode();
      case 'codeblock': return insertCodeBlock();
      case 'photo':     return setImageModal(true);
      case 'draw':      return openDrawModal();
      case 'clear':     return clearFormatting();
      case 'link':      return toggleLinkPopover();
      default: break;
    }
  }

  /* ──────────────────────────────────────────────────
     Tags
     ────────────────────────────────────────────────── */
  function commitTag(rawValue = tagInput) {
    const v = String(rawValue || '').trim().replace(/^#/, '').replace(/,/g, '').toLowerCase();
    if (!v) { setTagInput(''); return; }
    if (!(note.tags || []).includes(v)) {
      const updated = { ...note, tags: [...(note.tags || []), v] };
      updateNote(updated);
      markDirty();
    }
    setTagInput('');
  }

  function tagKeydown(e) {
    if (e.key === 'Enter' || e.key === ',') {
      e.preventDefault();
      commitTag();
    }
    if (e.key === 'Backspace' && !tagInput && note.tags?.length) {
      updateNote(n => ({ ...n, tags: n.tags.slice(0, -1) }));
      markDirty();
    }
  }

  function selectSavedTag(t) {
    if ((note.tags || []).includes(t)) {
      removeTag(t);
      return;
    }
    const updated = { ...note, tags: [...(note.tags || []), t] };
    updateNote(updated);
    markDirty();
  }

  function removeTag(t) {
    updateNote(n => ({ ...n, tags: (n.tags || []).filter(x => x !== t) }));
    markDirty();
  }

  /* ──────────────────────────────────────────────────
     Notebook picker
     ────────────────────────────────────────────────── */
  function pickNb(id) {
    updateNote(n => ({ ...n, notebookId: id }));
    setNbPicker(false);
    markDirty();
  }

  function openCreateNotebookFromPicker() {
    setNbPicker(false);
    setNbForm(EMPTY_NOTEBOOK_FORM);
    setNbCreateModal(true);
  }

  function submitCreateNotebook() {
    const name = nbForm.name.trim();
    if (!name) {
      showToast('Notebook name required', 'fa-circle-exclamation');
      return;
    }
    const newNb = {
      id: genId(),
      createdAt: new Date().toISOString(),
      name,
      color: nbForm.color,
      icon: nbForm.icon,
    };
    saveNotebook(newNb);

    // Assign this new notebook to the current note immediately.
    const updated = updateNote(n => ({ ...n, notebookId: newNb.id, updatedAt: new Date().toISOString() }));
    if (updated) saveNote(updated);
    dispatch({ type: 'RELOAD' });

    setNbCreateModal(false);
    setNbPicker(false);
    setNbForm(EMPTY_NOTEBOOK_FORM);
    setDirty(false);
    setSaveState('saved');
    showToast('Notebook created and selected', 'fa-book-open');
  }

  const nbObj = note?.notebookId ? getNotebook(note.notebookId) : null;

  /* ──────────────────────────────────────────────────
     Pin / Delete / Read mode / Export
     ────────────────────────────────────────────────── */
  function togglePin() {
    updateNote(n => {
      const updated = { ...n, pinned: !n.pinned };
      showToast(updated.pinned ? 'Note pinned' : 'Note unpinned', 'fa-thumbtack');
      haptic('medium');
      return updated;
    });
    markDirty();
  }

  function doDelete() {
    setDelModal(false);
    haptic('heavy');
    const deleted = noteRef.current ? { ...noteRef.current, title: titleRef.current?.value || noteRef.current.title, content: getCleanHtml(), markdown: getMarkdownSource() } : null;
    if (paramId) {
      deleteNote(paramId);
      dispatch({ type: 'RELOAD' });
      showToast('Note deleted', 'fa-trash-can', {
        label: 'Undo',
        onClick: () => {
          if (deleted) saveNote(deleted);
          dispatch({ type: 'RELOAD' });
          showToast('Note restored', 'fa-rotate-left');
        },
      });
    }
    navigate('/');
  }

  function toggleReadMode() {
    setIsReadMode(v => {
      const next = !v;
      if (next) {
        renderForReadingMode();
      } else {
        restoreAfterReadingMode();
      }
      showToast(next ? 'Reading mode on' : 'Editing mode on', 'fa-book-open');
      return next;
    });
  }

  async function downloadNote(format) {
    const current = {
      ...(noteRef.current || note || {}),
      title: (titleRef.current?.value || note?.title || 'Untitled').trim() || 'Untitled',
      content: getCleanHtml(),
      markdown: getMarkdownSource(),
      wordCount: countWords(getCleanText()),
      updatedAt: new Date().toISOString(),
    };
    try {
      const result = await exportNoteFile(current, format);
      setExportModal(false);
      const typeName = format === 'pdf' ? 'PDF' : format === 'md' ? 'Markdown' : 'Excel';
      showToast(result?.native ? `${typeName} saved. Choose Save/Share if shown` : `${typeName} downloaded`, 'fa-download');
    } catch (err) {
      console.error(err);
      showToast('Download failed', 'fa-circle-exclamation');
    }
  }

  /* ──────────────────────────────────────────────────
     Back
     ────────────────────────────────────────────────── */
  function handleBack() {
    if (dirty) saveNowRef.current?.(true);
    navigate(-1);
  }

  /* ──────────────────────────────────────────────────
     Toolbar manager
     ────────────────────────────────────────────────── */
  function toggleToolVisibility(key, show) {
    setHiddenTools(prev => {
      const next = new Set(prev);
      if (show) next.delete(key); else next.add(key);
      saveHiddenTools(next);
      showToast(show ? 'Tool enabled' : 'Tool hidden', show ? 'fa-eye' : 'fa-eye-slash');
      return next;
    });
  }

  function resetToolbarTools() {
    setHiddenTools(new Set());
    saveHiddenTools(new Set());
    showToast('Toolbar reset', 'fa-rotate-left');
  }

  /* ──────────────────────────────────────────────────
     Guard — wait until note is loaded
     ────────────────────────────────────────────────── */
  if (!note) return null;

  /* ──────────────────────────────────────────────────
     Render
     ────────────────────────────────────────────────── */
  const allButtons = TOOLBAR_GROUPS.flatMap(g => g.buttons);

  return (
    <>
      <style>{`
        .editor-shell{display:flex;flex-direction:column;height:100%}
        .editor-content-area{flex:1;display:flex;flex-direction:column;overflow:hidden}
        .editor-title{border-bottom:1px solid var(--border);font-size:21px;padding:16px 20px 14px;flex-shrink:0;width:100%;box-sizing:border-box;background:transparent;color:var(--text-1);resize:none;border-left:none;border-right:none;border-top:none;outline:none;font-family:var(--font-b)}
        .editor-body{flex:1;overflow-y:auto;padding:12px 20px 24px;font-size:var(--editor-fs,15px);line-height:1.42;outline:none;min-height:120px}
        .editor-body.markdown-source-mode{white-space:pre-wrap;word-break:break-word;overflow-wrap:anywhere;font-family:ui-monospace,SFMono-Regular,Menlo,Consolas,monospace!important;line-height:1.42;letter-spacing:0;tab-size:2}
        .editor-body.markdown-source-mode *{white-space:pre-wrap!important}
        .editor-body:empty::before{content:attr(data-placeholder);color:var(--text-3);pointer-events:none}
        .editor-body[contenteditable="true"] > p,.editor-body[contenteditable="true"] > div:not(.md-table-wrap):not(.code-actions):not(.code-scroll):not(.image-placeholder):not(.drawing-placeholder):not(.media-controls),.editor-body[contenteditable="true"] p{margin:0!important;line-height:1.42!important;min-height:1.42em}
        .editor-body-serif{font-family:'Lora',Georgia,serif!important}
        .tb-group{display:flex;align-items:center;gap:1px}
        .tb-group.tool-group-hidden{display:none}
        .tb-btn.tool-hidden{display:none}
        .link-popover{position:absolute;bottom:calc(100%+8px);left:0;right:0;background:var(--bg-elevated);border:1px solid var(--border-hi);border-radius:var(--r-sm);padding:10px 12px;z-index:50;box-shadow:var(--sh)}
        .link-popover input{width:100%;background:var(--bg-input);border:1px solid var(--border-hi);border-radius:8px;padding:8px 12px;font-size:13px;color:var(--text-1);outline:none;font-family:var(--font-b);box-sizing:border-box}
        .link-popover input:focus{border-color:var(--accent)}
        .link-popover .lp-actions{display:flex;gap:8px;margin-top:8px;justify-content:flex-end}
        .nb-picker-bottom{position:absolute;bottom:0;left:0;right:0;background:var(--bg-card);border-top:1px solid var(--border);border-radius:20px 20px 0 0;padding:0 0 20px;transform:translateY(100%);transition:transform .3s cubic-bezier(.16,1,.3,1);z-index:200}
        .nb-picker-bottom.show{transform:translateY(0)}
        .nb-picker-item{display:flex;align-items:center;gap:12px;padding:12px 20px;cursor:pointer;transition:background .2s}
        .nb-picker-item:hover{background:var(--bg-elevated)}
        .nb-picker-item .nb-dot{width:10px;height:10px;border-radius:50%;flex-shrink:0}
        .nb-picker-item span{font-size:14px;color:var(--text-1)}
        .nb-picker-item.selected span{color:var(--accent);font-weight:500}
        .format-wrap{position:relative}
        .read-mode .editor-toolbar,.read-mode .editor-bottom-panel,.read-mode .tags-row,.read-mode .nb-selector,.read-mode .editor-footer,.read-mode .code-actions,.read-mode .media-delete-btn,.read-mode .media-resize-handle,.read-mode .media-drag-chip{display:none!important}
        .read-mode .editor-title{pointer-events:none}.read-mode .editor-body{pointer-events:auto;overflow-y:auto;overflow-x:hidden;-webkit-overflow-scrolling:touch;touch-action:pan-y;padding-bottom:34px!important}
        .read-mode .editor-body .md-table-wrap{max-width:100%;overflow-x:auto;overscroll-behavior-x:contain}
        .read-mode .editor-body .md-table-wrap table{min-width:520px;width:max-content;max-width:none}
        .read-mode .editor-body pre,.read-mode .editor-body .ed-code{max-width:100%;overflow-x:auto!important;white-space:pre!important;word-break:normal!important;overflow-wrap:normal!important}
        .read-mode .editor-body p,.read-mode .editor-body li,.read-mode .editor-body blockquote{overflow-wrap:anywhere;word-break:normal}
        .read-mode .editor-figure{cursor:default;touch-action:auto}.read-mode .editor-figure.media-selected{outline:none}
        .read-btn.on{background:var(--accent-dim);color:var(--accent);border-color:var(--accent)}
        .export-options{display:grid;grid-template-columns:1fr;gap:10px;margin-top:12px}
        .export-option{display:flex;align-items:center;gap:12px;width:100%;padding:13px 14px;border-radius:14px;border:1px solid var(--border-hi);background:var(--bg-input);color:var(--text-1);font-family:var(--font-b);font-size:14px;text-align:left;cursor:pointer}
        .export-option:hover{border-color:var(--accent);background:var(--accent-dim)}
        .export-option i{width:22px;text-align:center;color:var(--accent);font-size:17px}
        .export-option small{display:block;color:var(--text-3);font-size:12px;margin-top:2px}
        .editor-body pre{position:relative;padding-top:40px;white-space:pre;overflow-x:auto;overflow-y:hidden;word-break:normal;overflow-wrap:normal}
        .editor-body li{position:relative}
        .li-collapse-toggle{display:inline-flex;align-items:center;justify-content:center;width:18px;height:18px;margin-right:5px;border:none;background:transparent;color:var(--text-3);cursor:pointer;vertical-align:middle;border-radius:5px;font-size:9px;touch-action:manipulation;user-select:none;-webkit-user-select:none;flex-shrink:0;transition:color .15s,background .15s}
        .li-collapse-toggle:hover{color:var(--accent);background:var(--accent-dim)}
        .li-collapsed > .li-collapse-toggle{color:var(--accent)}
        .li-collapsed > ul,.li-collapsed > ol{display:none!important}
        .editor-body pre code{display:block;min-height:26px;outline:none;white-space:pre;tab-size:2;min-width:max-content;word-break:normal;overflow-wrap:normal}
        .code-actions{position:absolute;top:7px;right:7px;z-index:2;display:flex;align-items:center;gap:5px;user-select:none}
        .code-action-btn{height:25px;min-width:28px;padding:0 8px;border-radius:7px;border:1px solid var(--border-hi);background:var(--bg-input);color:var(--text-2);font-size:11px;display:flex;align-items:center;gap:5px;cursor:pointer;line-height:1}
        .code-action-btn:hover{color:var(--accent);border-color:var(--accent);background:var(--accent-dim)}
        .code-action-btn.danger:hover{color:#ff6b6b;border-color:#ff6b6b;background:rgba(255,107,107,.12)}
        .code-action-btn i{font-size:11px}
        .tool-toggle{display:flex;align-items:center;justify-content:space-between;padding:10px 16px;border-radius:10px;cursor:pointer;gap:10px}
        .tool-toggle:hover{background:var(--bg-elevated)}
        .tool-left{display:flex;align-items:center;gap:10px;font-size:14px;color:var(--text-1)}
        .tool-left i{width:18px;text-align:center;color:var(--text-2);font-size:14px}
        .toolbar-tool-list{max-height:55vh;overflow-y:auto;padding:4px 0}
        .toolbar-modal{max-height:80vh;display:flex;flex-direction:column}
        .toolbar-modal .toolbar-tool-list{flex:1;overflow-y:auto}
        .link-modal .modal-input{margin-bottom:0}
        .link-modal .modal-grid{display:grid;gap:10px}
        .drawing-tools{display:flex;align-items:center;gap:10px;flex-wrap:wrap;margin-top:10px}
        .draw-swatch{width:26px;height:26px;border-radius:999px;border:2px solid var(--border-hi);cursor:pointer}
        .draw-swatch.on{outline:2px solid var(--accent);outline-offset:2px}
        .draw-canvas-wrap{margin-top:12px;border:1px solid var(--border-hi);border-radius:16px;overflow:hidden;background:#f7f0df}
        .draw-canvas{display:block;width:100%;height:240px;touch-action:none;cursor:crosshair;background:#f7f0df}
        .editor-figure{position:relative;display:block;clear:both;margin:10px 0 12px 0;width:180px;max-width:none;touch-action:none;user-select:none;cursor:default}
        .editor-figure:active{cursor:default}
        .editor-figure.media-selected{outline:2px solid var(--accent);outline-offset:4px;border-radius:16px}
        .editor-inline-image{display:block;width:100%;max-width:100%;height:auto;max-height:none;object-fit:contain;border-radius:14px;border:1px solid var(--border-hi);box-shadow:var(--sh);background:var(--bg-elevated);pointer-events:none}
        .drawing-figure{width:190px}.drawing-figure .editor-inline-image{background:#f7f0df}
        .media-delete-btn,.media-resize-handle,.media-drag-chip{position:absolute;z-index:5;display:flex;align-items:center;justify-content:center;border:1px solid var(--border-hi);box-shadow:0 8px 20px rgba(0,0,0,.28);opacity:0;pointer-events:none;transform:scale(.92);transition:opacity .18s ease,transform .18s ease}
        .editor-figure.media-selected .media-delete-btn,.editor-figure.media-selected .media-resize-handle,.editor-figure.media-selected .media-drag-chip{opacity:1;pointer-events:auto;transform:scale(1)}
        .media-delete-btn{top:6px;right:6px;width:30px;height:30px;border-radius:999px;background:rgba(20,20,20,.78);color:#fff;font-size:12px;cursor:pointer}
        .media-delete-btn:hover{background:var(--danger);border-color:var(--danger)}
        .media-resize-handle{right:-10px;bottom:-10px;width:30px;height:30px;border-radius:10px;background:var(--accent);color:#fff;font-size:12px;cursor:nwse-resize;touch-action:none}
        .media-drag-chip{left:6px;top:6px;width:28px;height:28px;border-radius:999px;background:rgba(20,20,20,.64);color:#fff;font-size:11px;cursor:grab;touch-action:none}
        .media-drag-chip:active{cursor:grabbing}
        .image-placeholder{display:flex;align-items:center;justify-content:center;gap:10px;padding:26px 18px;border:1px dashed var(--border-hi);border-radius:16px;color:var(--text-3);background:var(--bg-elevated);margin:14px 0}
        @media(max-width:430px){.editor-figure{width:150px}.drawing-figure{width:160px}.media-delete-btn,.media-resize-handle{width:28px;height:28px;font-size:11px}.media-drag-chip{width:26px;height:26px}}
      `}</style>

      <div className={`app-shell${isReadMode ? ' read-mode' : ''}`}>

        {/* ── Top Bar ── */}
        <header className="top-bar editor-top">
          <div className="top-bar-left">
            <button className="back-btn" onClick={handleBack}>
              <i className="fa-solid fa-chevron-left" />
            </button>
          </div>
          <span className="top-bar-title" title={note.title || 'New Note'}>
            {note.title || 'New Note'}
          </span>
          <div className="top-bar-actions">
            <button
              className={`icon-btn read-btn${isReadMode ? ' on' : ''}`}
              title="Reading mode"
              onClick={toggleReadMode}
            >
              <i className="fa-solid fa-book-open" />
            </button>
            <button
              className="icon-btn"
              title="Download note"
              onClick={() => setExportModal(true)}
            >
              <i className="fa-solid fa-download" />
            </button>
            <button
              className={`icon-btn${note.pinned ? ' act' : ''}`}
              title={note.pinned ? 'Unpin' : 'Pin note'}
              onClick={togglePin}
            >
              <i className="fa-solid fa-thumbtack" />
            </button>
            <button className="icon-btn danger" title="Delete" onClick={() => setDelModal(true)}>
              <i className="fa-regular fa-trash-can" />
            </button>
            <button
              className="save-btn"
              onClick={() => {
                saveNow();
                navigate('/', { replace: true });
              }}
            >
              Save
            </button>
          </div>
        </header>

        <input
          ref={imageInputRef}
          type="file"
          accept="image/*"
          style={{ display: 'none' }}
          onChange={onPickPhoto}
        />

        {/* ── Editor Shell ── */}
        <div className="editor-shell" id="editor-shell">

          {/* ── Toolbar ── */}
          <div
            className="editor-toolbar"
            id="toolbar"
            onMouseDown={toolbarMouseDown}
            onPointerDown={toolbarPointerDown}
          >
            {TOOLBAR_GROUPS.map((group, groupIndex) => {
              const visibleButtons = group.buttons.filter(b => !hiddenTools.has(b.key));
              return (
                <div className="tb-group-wrap" key={group.id}>
                  <div
                    className={`tb-group${visibleButtons.length === 0 ? ' tool-group-hidden' : ''}`}
                  >
                  {group.buttons.map(btn => (
                    <button
                      key={btn.key}
                      type="button"
                      className={`tb-btn${hiddenTools.has(btn.key) ? ' tool-hidden' : ''}${tbState[btn.key] ? ' on' : ''}`}
                      title={btn.key === 'markdown' ? (markdownEnabled ? 'Markdown render mode: On' : 'Markdown text/source mode: On') : btn.title}
                      aria-pressed={!!tbState[btn.key]}
                      onClick={() => handleToolClick(btn.key)}
                      data-tool={btn.key}
                    >
                      {btn.customLabel ?? <i className={btn.icon} />}
                    </button>
                  ))}
                  </div>
                  {groupIndex < TOOLBAR_GROUPS.length - 1 && <div className="tb-sep" aria-hidden="true" />}
                </div>
              );
            })}

            {/* Manage button — always visible */}
            <div className="tb-sep tb-manage-sep" />
            <div className="tb-group tb-manage-group">
              <button
                type="button"
                className="tb-btn tb-manage-btn"
                title="Manage toolbar"
                onClick={() => setTbModal(true)}
              >
                <i className="fa-solid fa-sliders" />
              </button>
            </div>
          </div>

          {/* ── Editor content ── */}
          <div className="editor-content-area">
            <textarea
              ref={titleRef}
              className="editor-title"
              placeholder="Note title…"
              rows={1}
              onInput={e => { autoResize(e.target); markDirty(); }}
              onKeyDown={titleKeydown}
            />
            <div
              ref={bodyRef}
              className={`editor-body${serifBody ? ' editor-body-serif' : ''}`}
              spellCheck={spellcheckEnabled}
              contentEditable={!isReadMode}
              suppressContentEditableWarning
              data-placeholder="Start writing…"
              onBeforeInput={onBodyBeforeInput}
              onInput={onBodyInput}
              onPaste={onBodyPaste}
              onKeyDown={editorKeydown}
              onKeyUp={editorKeyup}
              onMouseUp={updateTbState}
              onPointerDownCapture={editorCheckEvent}
              onTouchStartCapture={editorCheckEvent}
              onClickCapture={editorCheckEvent}
            />
          </div>

          {/* ── Bottom editor panel: tags, notebook, words/chars */}
          <div className="editor-bottom-panel" id="editor-bottom-panel">
          <div className="nb-selector tag-selector-row" id="tags-row" onClick={() => setTagManageModal(true)}>
            <i className="fa-solid fa-tag" />
            {(note.tags || []).length ? (
              <span className="nb-sel-text tag-sel-text">
                In <span className="tag-sel-name">{(note.tags || []).slice(0, 2).join(', ')}</span>
                {(note.tags || []).length > 2 ? ` +${(note.tags || []).length - 2} more` : ''}
              </span>
            ) : (
              <span className="nb-sel-text tag-sel-empty" style={{ color: 'var(--text-3)' }}>
                No tags selected
              </span>
            )}
            <i className="fa-solid fa-chevron-up" style={{ fontSize: 11, color: 'var(--text-3)' }} />
          </div>

          {/* ── Notebook selector ── */}
          <div className="nb-selector" id="nb-selector" onClick={() => setNbPicker(true)}>
            <i className="fa-solid fa-book-open" />
            {nbObj ? (
              <span className="nb-sel-text">
                In <span className="nb-sel-name" style={{ color: nbObj.color }}>{nbObj.name}</span>
              </span>
            ) : (
              <span className="nb-sel-text" style={{ color: 'var(--text-3)' }}>
                No notebook selected
              </span>
            )}
            <i className="fa-solid fa-chevron-up" style={{ fontSize: 11, color: 'var(--text-3)' }} />
          </div>

          {/* ── Editor footer cards ── */}
          <div className="editor-footer">
            <div className="wc-info stat-cards">
              <span className="editor-stat-card"><i className="fa-solid fa-align-left" /> <b>{wc}</b> words</span>
              <span className="editor-stat-card"><i className="fa-solid fa-i-cursor" /> <b>{cc}</b> chars</span>
              <span className="editor-stat-card"><i className="fa-solid fa-text-width" /> <b>{selectedWords}</b> selected</span>
              {streak > 0 && (
                <span className="editor-stat-card" title={`${streak}-day writing streak`} style={{ color: streak >= 7 ? '#f28c40' : 'var(--text-2)' }}>
                  🔥 <b>{streak}</b> day{streak !== 1 ? 's' : ''}
                </span>
              )}
            </div>
            <div className={`autosave-dot${saveState === 'saving' ? ' saving' : ''}`}>
              <i className="fa-solid fa-circle" style={{ fontSize: 7 }} />
              {' '}{saveState === 'saving' ? 'Saving…' : 'Saved'}
            </div>
          </div>

          </div>{/* /editor-bottom-panel */}

        </div>{/* /editor-shell */}

        {linkPopover && (
          <div className="modal-overlay show" onClick={e => { if (e.target === e.currentTarget) { setLinkPopover(false); setLinkUrl(''); setLinkText(''); } }}>
            <div className="modal link-modal">
              <div className="modal-title">Insert link</div>
              <div className="modal-sub">Add a web link to the note. If text is selected, the link will use it.</div>
              <div className="modal-grid">
                <div>
                  <div className="modal-label">Text</div>
                  <input
                    className="modal-input"
                    placeholder="Link text"
                    value={linkText}
                    onChange={e => setLinkText(e.target.value)}
                    onKeyDown={linkKeydown}
                  />
                </div>
                <div>
                  <div className="modal-label">URL</div>
                  <input
                    className="modal-input"
                    type="url"
                    placeholder="https://example.com"
                    value={linkUrl}
                    onChange={e => setLinkUrl(e.target.value)}
                    onKeyDown={linkKeydown}
                    autoFocus
                  />
                </div>
              </div>
              <div className="modal-actions">
                <button className="btn btn-ghost" onClick={() => { setLinkPopover(false); setLinkUrl(''); setLinkText(''); }}>Cancel</button>
                <button className="btn btn-primary" onClick={insertLink}>Insert</button>
              </div>
            </div>
          </div>
        )}

        {imageModal && (
          <div className="modal-overlay show" onClick={e => { if (e.target === e.currentTarget) setImageModal(false); }}>
            <div className="modal">
              <div className="modal-title">Add photo</div>
              <div className="modal-sub">Choose a photo from your device and insert it into the note.</div>
              <div className="modal-actions" style={{ justifyContent: 'space-between' }}>
                <button className="btn btn-ghost" onClick={() => setImageModal(false)}>Cancel</button>
                <button className="btn btn-primary" onClick={openPhotoPicker}>Choose photo</button>
              </div>
            </div>
          </div>
        )}

        {drawModal && (
          <div className="modal-overlay show" onClick={e => { if (e.target === e.currentTarget) setDrawModal(false); }}>
            <div className="modal">
              <div className="modal-title">Draw</div>
              <div className="modal-sub">Sketch something, then insert it into your note.</div>
              <div className="drawing-tools">
                {['#f28c40', '#f8f8f8', '#7dd3fc', '#86efac', '#fda4af'].map(color => (
                  <button
                    key={color}
                    type="button"
                    className={`draw-swatch${drawColor === color ? ' on' : ''}`}
                    style={{ background: color }}
                    onClick={() => setDrawColor(color)}
                    aria-label={`Use ${color} color`}
                  />
                ))}
                <label className="modal-label" style={{ margin: 0 }}>Brush</label>
                <input
                  type="range"
                  min="1"
                  max="10"
                  value={drawLineWidth}
                  onChange={e => setDrawLineWidth(Number(e.target.value))}
                />
              </div>
              <div className="draw-canvas-wrap">
                <canvas ref={drawCanvasRef} className="draw-canvas" />
              </div>
              <div className="modal-actions">
                <button className="btn btn-ghost" onClick={() => setDrawModal(false)}>Cancel</button>
                <button className="btn btn-ghost" onClick={clearDrawing}>Clear</button>
                <button className="btn btn-primary" onClick={insertDrawing}>Insert</button>
              </div>
            </div>
          </div>
        )}

        {/* ── Notebook picker (bottom sheet) ── */}
        {nbPicker && (
          <>
            <div className="sheet-overlay show" onClick={() => setNbPicker(false)} />
            <div className="nb-picker-bottom show">
              <div className="sheet-handle" />
              <div className="sheet-title">Move to Notebook</div>
              <div
                className={`nb-picker-item${!note.notebookId ? ' selected' : ''}`}
                onClick={() => pickNb(null)}
              >
                <span className="nb-dot" style={{ background: 'var(--text-3)' }} />
                <span>None</span>
              </div>
              {getNotebooks().map(nb => (
                <div
                  key={nb.id}
                  className={`nb-picker-item${note.notebookId === nb.id ? ' selected' : ''}`}
                  onClick={() => pickNb(nb.id)}
                >
                  <span className="nb-dot" style={{ background: nb.color }} />
                  <span>{nb.name}</span>
                </div>
              ))}
              <div className="sh-divider" style={{ marginTop: 6 }} />
              <button className="nb-create-btn" onClick={openCreateNotebookFromPicker}>
                <span className="nb-create-btn-icon"><i className="fa-solid fa-plus" /></span>
                <span className="nb-create-btn-copy">Create notebook</span>
              </button>
            </div>
          </>
        )}

        {tagManageModal && (
          <div className="modal-overlay show" onClick={(e) => { if (e.target === e.currentTarget) { setTagManageModal(false); setTagInput(''); } }}>
            <div className="modal tag-manage-modal">
              <div className="modal-title">Manage tags</div>
              <div className="modal-sub">Add, remove, and reuse tags without covering the editor footer.</div>

              <div className="modal-label">Current tags</div>
              {(note.tags || []).length ? (
                <div className="tag-manage-current">
                  {(note.tags || []).map(t => (
                    <button
                      type="button"
                      key={t}
                      className="tag-chip manage"
                      onClick={() => removeTag(t)}
                      title={`Remove ${t}`}
                    >
                      <span>{t}</span>
                      <i className="fa-solid fa-xmark" />
                    </button>
                  ))}
                </div>
              ) : (
                <div className="tag-manage-empty">No tags added yet.</div>
              )}

              <div className="modal-label" style={{ marginTop: '14px' }}>Add tag</div>
              <div className="tag-manage-input-row single">
                <input
                  className="modal-input"
                  placeholder="e.g. code, github…"
                  value={tagInput}
                  onChange={e => setTagInput(e.target.value)}
                  onKeyDown={tagKeydown}
                  maxLength={24}
                  autoFocus
                />
              </div>

              {savedTagOptions.length > 0 && (
                <>
                  <div className="modal-label" style={{ marginTop: '8px' }}>Saved tags</div>
                  <div className="saved-tags-list in-modal">
                    {savedTagOptions.map(t => (
                      <button
                        type="button"
                        key={t}
                        className="saved-tag-option"
                        onClick={() => selectSavedTag(t)}
                      >
                        <i className="fa-solid fa-tag" />
                        <span>{t}</span>
                      </button>
                    ))}
                  </div>
                </>
              )}

              <div className="modal-actions modal-actions-2col">
                <button
                  className="btn btn-ghost"
                  onClick={() => { setTagManageModal(false); setTagInput(''); }}
                >
                  Close
                </button>
                <button
                  className="btn btn-primary"
                  onClick={() => {
                    if (tagInput.trim()) {
                      commitTag();
                    } else {
                      setTagManageModal(false);
                      setTagInput('');
                    }
                  }}
                >
                  Add
                </button>
              </div>
            </div>
          </div>
        )}

        {nbCreateModal && (
          <div className="modal-overlay show notebook-create-overlay">
            <div className="modal">
              <div className="modal-title">Create Notebook</div>
              <div className="modal-sub">Create a notebook and move this note into it.</div>

              <div className="modal-label">Name</div>
              <input
                className="modal-input"
                placeholder="e.g. Work, Ideas…"
                maxLength={32}
                value={nbForm.name}
                onChange={e => setNbForm(f => ({ ...f, name: e.target.value }))}
                autoFocus
              />

              <div className="modal-label">Colour</div>
              <div className="color-row">
                {NB_COLORS.map(c => (
                  <div
                    key={c}
                    className={`c-dot${nbForm.color === c ? ' on' : ''}`}
                    style={{ background: c }}
                    onClick={() => setNbForm(f => ({ ...f, color: c }))}
                  />
                ))}
              </div>

              <div className="modal-label">Icon</div>
              <div className="icon-grid">
                {NB_ICONS.map(ic => (
                  <div
                    key={ic}
                    className={`icon-opt${nbForm.icon === ic ? ' on' : ''}`}
                    onClick={() => setNbForm(f => ({ ...f, icon: ic }))}
                  >
                    <i className={`fa-solid ${ic}`} />
                  </div>
                ))}
              </div>

              <div className="modal-actions">
                <button className="btn btn-ghost" onClick={() => { setNbCreateModal(false); setNbForm(EMPTY_NOTEBOOK_FORM); }}>Cancel</button>
                <button className="btn btn-primary" onClick={submitCreateNotebook}>Save</button>
              </div>
            </div>
          </div>
        )}


        {/* ── Download / export modal ── */}
        {exportModal && (
          <div className="modal-overlay show" onClick={e => { if (e.target === e.currentTarget) setExportModal(false); }}>
            <div className="modal">
              <div className="modal-title">Download note</div>
              <div className="modal-sub">Save this note to your device as PDF, Markdown, or Excel.</div>
              <div className="export-options">
                <button className="export-option" onClick={() => downloadNote('pdf')}>
                  <i className="fa-regular fa-file-pdf" />
                  <span><b>PDF</b><small>Best for reading and sharing</small></span>
                </button>
                <button className="export-option" onClick={() => downloadNote('md')}>
                  <i className="fa-regular fa-file-lines" />
                  <span><b>Markdown</b><small>Plain .md file for editing</small></span>
                </button>
                <button className="export-option" onClick={() => downloadNote('txt')}>
                  <i className="fa-regular fa-file-alt" />
                  <span><b>Plain text</b><small>Simple .txt with no formatting</small></span>
                </button>
                <button className="export-option" onClick={() => downloadNote('xls')}>
                  <i className="fa-regular fa-file-excel" />
                  <span><b>Excel</b><small>.xls file with title and content</small></span>
                </button>
              </div>
              <div className="modal-actions">
                <button className="btn btn-ghost" onClick={() => setExportModal(false)}>Cancel</button>
              </div>
            </div>
          </div>
        )}

        {/* ── Delete confirm modal ── */}
        {delModal && (
          <div className="modal-overlay show">
            <div className="modal">
              <div className="modal-title">Delete note?</div>
              <div className="modal-sub">
                This note will be permanently removed. This action cannot be undone.
              </div>
              <div className="modal-actions">
                <button className="btn btn-ghost" onClick={() => setDelModal(false)}>Cancel</button>
                <button
                  className="btn btn-primary"
                  style={{ background: 'var(--danger)' }}
                  onClick={doDelete}
                >
                  Delete
                </button>
              </div>
            </div>
          </div>
        )}

        {/* ── Toolbar manager modal ── */}
        {tbModal && (
          <div className="modal-overlay show" onClick={e => { if (e.target === e.currentTarget) setTbModal(false); }}>
            <div className="modal toolbar-modal">
              <div className="modal-title">Toolbar tools</div>
              <div className="modal-sub">
                Turn off tools you do not need.
              </div>
              <div className="toolbar-tool-list">
                {allButtons.map(btn => (
                  <label key={btn.key} className="tool-toggle">
                    <span className="tool-left">
                      {btn.icon
                        ? <i className={btn.icon} />
                        : <i className="fa-solid fa-circle-dot" />
                      }
                      <span>{btn.title}</span>
                    </span>
                    <input
                      type="checkbox"
                      checked={!hiddenTools.has(btn.key)}
                      onChange={e => toggleToolVisibility(btn.key, e.target.checked)}
                    />
                  </label>
                ))}
              </div>
              <div className="modal-actions">
                <button className="btn btn-ghost" onClick={resetToolbarTools}>Reset</button>
                <button className="btn btn-primary" onClick={() => setTbModal(false)}>Done</button>
              </div>
            </div>
          </div>
        )}

      </div>{/* /app-shell */}

      <Toast />
    </>
  );
}
