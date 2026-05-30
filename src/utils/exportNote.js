import { escH } from './helpers';

function safeFileName(name = 'note') {
  const cleaned = String(name || 'note')
    .trim()
    .replace(/[\\/:*?"<>|]+/g, '-')
    .replace(/\s+/g, ' ')
    .slice(0, 80);
  return cleaned || 'note';
}

function htmlToText(html = '') {
  const wrap = document.createElement('div');
  wrap.innerHTML = html || '';
  wrap.querySelectorAll('br').forEach(br => br.replaceWith('\n'));
  wrap.querySelectorAll('p,div,h1,h2,h3,h4,h5,h6,li,blockquote,pre,tr').forEach(el => {
    el.appendChild(document.createTextNode('\n'));
  });
  return (wrap.innerText || wrap.textContent || '')
    .replace(/\u00a0/g, ' ')
    .replace(/\n{3,}/g, '\n\n')
    .trim();
}

function htmlToMarkdown(html = '') {
  const wrap = document.createElement('div');
  wrap.innerHTML = html || '';

  wrap.querySelectorAll('pre').forEach(pre => {
    const code = pre.querySelector('code')?.textContent || pre.textContent || '';
    pre.replaceWith(document.createTextNode(`\n\n\`\`\`\n${code.replace(/\n+$/,'')}\n\`\`\`\n\n`));
  });
  wrap.querySelectorAll('h1,h2,h3').forEach(h => {
    const level = h.tagName === 'H1' ? '# ' : h.tagName === 'H2' ? '## ' : '### ';
    h.replaceWith(document.createTextNode(`\n${level}${h.textContent.trim()}\n`));
  });
  wrap.querySelectorAll('blockquote').forEach(bq => {
    const txt = (bq.textContent || '').trim().split('\n').map(line => `> ${line}`).join('\n');
    bq.replaceWith(document.createTextNode(`\n${txt}\n`));
  });
  wrap.querySelectorAll('li').forEach(li => {
    const checked = li.getAttribute('data-checked');
    let prefix = '- ';
    if (checked === 'true') prefix = '- [x] ';
    if (checked === 'false') prefix = '- [ ] ';
    li.prepend(document.createTextNode(prefix));
    li.appendChild(document.createTextNode('\n'));
  });
  wrap.querySelectorAll('strong,b').forEach(el => el.replaceWith(document.createTextNode(`**${el.textContent}**`)));
  wrap.querySelectorAll('em,i').forEach(el => el.replaceWith(document.createTextNode(`*${el.textContent}*`)));
  wrap.querySelectorAll('u').forEach(el => el.replaceWith(document.createTextNode(el.textContent)));
  wrap.querySelectorAll('a').forEach(a => {
    const text = a.textContent || a.href;
    const href = a.getAttribute('href') || '';
    a.replaceWith(document.createTextNode(href ? `[${text}](${href})` : text));
  });
  wrap.querySelectorAll('img').forEach(img => {
    const alt = img.getAttribute('alt') || 'image';
    img.replaceWith(document.createTextNode(`\n![${alt}](${img.getAttribute('src') || ''})\n`));
  });
  wrap.querySelectorAll('br').forEach(br => br.replaceWith('\n'));
  wrap.querySelectorAll('p,div,ul,ol,table,tr').forEach(el => el.appendChild(document.createTextNode('\n')));

  return (wrap.innerText || wrap.textContent || '')
    .replace(/\u00a0/g, ' ')
    .replace(/[ \t]+\n/g, '\n')
    .replace(/\n{3,}/g, '\n\n')
    .trim();
}

function downloadBlob(blob, filename) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  a.rel = 'noopener';
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1200);
}

function bytesToBase64(bytes) {
  let binary = '';
  const chunkSize = 0x8000;
  for (let i = 0; i < bytes.length; i += chunkSize) {
    binary += String.fromCharCode(...bytes.subarray(i, i + chunkSize));
  }
  return btoa(binary);
}

function textToBase64(text) {
  return bytesToBase64(new TextEncoder().encode(text));
}

function binaryStringToBase64(text) {
  const bytes = new Uint8Array(text.length);
  for (let i = 0; i < text.length; i += 1) bytes[i] = text.charCodeAt(i) & 0xff;
  return bytesToBase64(bytes);
}

async function saveWithCapacitor({ data, filename, mimeType, binary = false }) {
  const { Capacitor } = await import('@capacitor/core');
  if (!Capacitor.isNativePlatform?.()) return null;

  const { Filesystem, Directory } = await import('@capacitor/filesystem');
  const base64 = binary ? binaryStringToBase64(data) : textToBase64(data);

  const saved = await Filesystem.writeFile({
    path: filename,
    data: base64,
    directory: Directory.Documents,
    recursive: true,
  });

  // Open Android share sheet after saving so the user can save to Downloads,
  // Drive, WhatsApp, Telegram, etc. This avoids the WebView fake-success issue.
  try {
    const { Share } = await import('@capacitor/share');
    await Share.share({
      title: filename,
      text: `Inkwell note exported: ${filename}`,
      url: saved.uri,
      dialogTitle: 'Save or share note',
    });
  } catch {
    // File is still saved in app Documents even if the share sheet is cancelled.
  }

  return { native: true, filename, uri: saved.uri, mimeType };
}

async function saveFile({ data, filename, mimeType, binary = false }) {
  const nativeResult = await saveWithCapacitor({ data, filename, mimeType, binary }).catch(err => {
    console.warn('Native file save failed, falling back to browser download:', err);
    return null;
  });
  if (nativeResult) return nativeResult;

  const blobData = binary
    ? new Uint8Array([...data].map(ch => ch.charCodeAt(0) & 0xff))
    : data;
  downloadBlob(new Blob([blobData], { type: mimeType }), filename);
  return { native: false, filename };
}

function pdfEscape(text = '') {
  return String(text).replace(/\\/g, '\\\\').replace(/\(/g, '\\(').replace(/\)/g, '\\)');
}

function makeSimplePdf(title, bodyText) {
  const pageWidth = 595;
  const pageHeight = 842;
  const margin = 48;
  const maxChars = 88;
  const lines = [];
  const wrapLine = (line) => {
    const words = String(line || '').split(/\s+/);
    let current = '';
    words.forEach(word => {
      if (!word) return;
      if ((current + ' ' + word).trim().length > maxChars) {
        lines.push(current);
        current = word;
      } else {
        current = (current + ' ' + word).trim();
      }
    });
    lines.push(current);
  };

  lines.push(title || 'Untitled');
  lines.push('');
  String(bodyText || '').split(/\r?\n/).forEach(wrapLine);

  const pages = [];
  let current = [];
  let y = pageHeight - margin;
  lines.forEach((line, index) => {
    const fontSize = index === 0 ? 18 : 11;
    const lineGap = index === 0 ? 24 : 15;
    if (y < margin + lineGap) {
      pages.push(current);
      current = [];
      y = pageHeight - margin;
    }
    current.push({ text: line, y, size: fontSize });
    y -= lineGap;
  });
  if (current.length) pages.push(current);

  const objects = [];
  const addObj = (body) => { objects.push(body); return objects.length; };
  const catalogId = addObj('');
  const pagesId = addObj('');
  const fontId = addObj('<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>');
  const pageIds = [];

  pages.forEach(pageLines => {
    const stream = ['BT'];
    pageLines.forEach(line => {
      stream.push(`/F1 ${line.size} Tf`);
      stream.push(`${margin} ${line.y} Td (${pdfEscape(line.text)}) Tj`);
      stream.push(`${-margin} ${-line.y} Td`);
    });
    stream.push('ET');
    const streamText = stream.join('\n');
    const contentId = addObj(`<< /Length ${streamText.length} >>\nstream\n${streamText}\nendstream`);
    const pageId = addObj(`<< /Type /Page /Parent ${pagesId} 0 R /MediaBox [0 0 ${pageWidth} ${pageHeight}] /Resources << /Font << /F1 ${fontId} 0 R >> >> /Contents ${contentId} 0 R >>`);
    pageIds.push(pageId);
  });

  objects[catalogId - 1] = `<< /Type /Catalog /Pages ${pagesId} 0 R >>`;
  objects[pagesId - 1] = `<< /Type /Pages /Kids [${pageIds.map(id => `${id} 0 R`).join(' ')}] /Count ${pageIds.length} >>`;

  let pdf = '%PDF-1.4\n';
  const offsets = [0];
  objects.forEach((body, i) => {
    offsets.push(pdf.length);
    pdf += `${i + 1} 0 obj\n${body}\nendobj\n`;
  });
  const xref = pdf.length;
  pdf += `xref\n0 ${objects.length + 1}\n0000000000 65535 f \n`;
  offsets.slice(1).forEach(off => { pdf += `${String(off).padStart(10, '0')} 00000 n \n`; });
  pdf += `trailer\n<< /Size ${objects.length + 1} /Root ${catalogId} 0 R >>\nstartxref\n${xref}\n%%EOF`;
  return pdf;
}

export async function exportNoteFile(note, format) {
  const title = note?.title || 'Untitled';
  const base = safeFileName(title);
  const html = note?.content || '';
  const plain = htmlToText(html);

  if (format === 'txt') {
    const txt = `${title}\n${'─'.repeat(Math.min(title.length, 60))}\n\n${plain}\n`;
    return saveFile({ data: txt, filename: `${base}.txt`, mimeType: 'text/plain;charset=utf-8' });
  }

  if (format === 'md') {
    const md = `# ${title}\n\n${htmlToMarkdown(html)}\n`;
    return saveFile({ data: md, filename: `${base}.md`, mimeType: 'text/markdown;charset=utf-8' });
  }

  if (format === 'xls') {
    const table = `<!doctype html><html><head><meta charset="utf-8" /></head><body><table border="1"><tr><th>Title</th><td>${escH(title)}</td></tr><tr><th>Updated</th><td>${escH(note?.updatedAt || '')}</td></tr><tr><th>Words</th><td>${note?.wordCount || 0}</td></tr><tr><th>Content</th><td>${escH(plain).replace(/\n/g, '<br>')}</td></tr></table></body></html>`;
    return saveFile({ data: table, filename: `${base}.xls`, mimeType: 'application/vnd.ms-excel;charset=utf-8' });
  }

  const pdf = makeSimplePdf(title, plain);
  return saveFile({ data: pdf, filename: `${base}.pdf`, mimeType: 'application/pdf', binary: true });
}
