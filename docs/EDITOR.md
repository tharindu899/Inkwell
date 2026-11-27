# Editor — how it works + stability checklist

## Architecture (one paragraph)
One `BasicTextField` shows the whole note. The note is an immutable flat `RichDoc` (text + per-char marks + per-paragraph `Para`).
`RichTransformation` paints it; tables / images / dividers are one-char object paragraphs (`OBJ`) drawn as Compose overlays.
Undo = whole-doc snapshots. Storage = web-compatible HTML (`HtmlCodec`) + markdown (`HtmlCodec.toMarkdown`).
Markdown text mode swaps the field for a plain monospace one and parses back with `MarkdownCodec` when you leave it.

## Files
| Area | Files |
|---|---|
| Model / codecs | `editor/RichDoc.kt`, `HtmlCodec.kt`, `MarkdownCodec.kt`, `TableData.kt`, `ImageData.kt`, `RichLayout.kt`, `RichTransformation.kt` |
| Export / stats / toolbar list | `editor/NoteExport.kt`, `editor/EditorStats.kt`, `util/PdfExport.kt` |
| State | `ui/screens/EditorController.kt` |
| UI | `EditorScreen.kt`, `EditorToolbar.kt`, `EditorSheets.kt`, `EditorTables.kt`, `EditorImages.kt`, `EditorDraw.kt` |
| Tests (pure Kotlin) | `scripts/model-tests/*.kt` — see its README |

## Back button order (top wins)
Tag manager / notebook picker / create-notebook → delete confirm → cell sheet → link dialog → draw → image selection (handles only) → export → toolbar customiser → leave note (saves first).

## Manual checklist (run on a device after each CI build)
**Typing**
- [ ] Type Sinhala + English + emoji; caret never jumps; word / char counts change.
- [ ] Undo / redo after typing, after bold, after paste.
- [ ] Spell check off in Settings → keyboard stops suggesting in the body.
**Formatting**
- [ ] Bold / italic / underline / strike / highlight / sup / sub on a selection and with a collapsed caret.
- [ ] H1–H3, bullets, numbers, checklist (tap box), nesting + collapse, quote, alignment, indent.
- [ ] Markdown shortcuts: `# `, `- `, `1. `, `[] `, `> `, triple backtick.
**Objects**
- [ ] Table: add / delete row + column, header toggle, edit cell, scroll sideways.
- [ ] Divider; inline code; code block (Copy / Bottom / Delete); link insert / edit / remove.
- [ ] Photo: insert, select, drag, resize, delete. Sketch: draw, undo, clear, insert.
- [ ] Caret can't land on a table / image / divider line.
**Modes**
- [ ] Markdown text mode: toggle on, edit, toggle off → formatting preserved; save while in text mode.
- [ ] Reading mode: full-screen: no top bar, no status bar, no tags / toolbar; counts stay; floating book button + Back exit; keyboard hides, edits refused with toast, links open, toggling back restores editing.
- [ ] Toolbar customiser (⚙): hide a tool, hide a whole group (its separator vanishes), Reset, restart app → choice kept.
**Markdown text view (default on; Settings → Markdown mode)**
- [ ] Toolbar works on the text: B / I / U / S / H1–3 / lists / quote / code / link / table / divider / photo / undo / redo.
- [ ] Typing at the bottom or with the keyboard up keeps the caret visible (auto-scroll).
- [ ] Rendered view: copy / cut a selection → paste elsewhere gives Markdown (`**bold**`, `- item`).
**Backup / restore (Drive)**
- [ ] Backup → toast; Restore on a second install brings the notes back (photos included).
**Export**
- [ ] PDF with Sinhala text opens correctly; md / html / txt / xls open in their apps.
- [ ] Cancel in the save dialog leaves no toast / crash.
**Save / lifecycle**
- [ ] Auto-save after 2 s; leave via back; background app mid-edit → reopen, text is there.
- [ ] Empty new note leaves no "Untitled".
- [ ] Rotate / dark-mode switch mid-edit keeps text + selection.
**Footer**
- [ ] "N selected" follows the selection; streak chip shows after editing notes on consecutive days (orange from 7).
**Haptics**
- [ ] Save (light), Pin (medium), Delete confirm (medium) vibrate once.

## Known limits
- Compose code is compiled only by CI; pure model logic is tested locally.
- PDF export is text only. Images / tables in a PDF become placeholders / `a | b` rows.
- Words selected inside a table cell sheet are not counted.
