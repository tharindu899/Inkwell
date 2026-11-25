# Editor Part 2 — document model + core formatting

## Model (`editor/Doc.kt`)
- `Doc.text`: one string, lines separated by `\n`.
- `Doc.blocks`: one `BlockFmt(type, align, indent)` per line (P, H1-H3, UL, OL, QUOTE).
- `Doc.spans`: `Span(kind, start, end)` over the whole string (bold, italic, underline, strike, sup, sub, highlight).
- `DocOps.applyTextEdit` diffs old/new text and carries blocks + spans across (typing inherits the
  previous character's style; toolbar toggles with a bare caret are `pending`).

## Files
| File | Role |
|---|---|
| `editor/Doc.kt` | model + all editing ops |
| `editor/HtmlCodec.kt` | stored HTML ⇄ Doc, Doc → markdown |
| `editor/EditorHistory.kt` | undo/redo snapshots (typing coalesced per 900 ms) |
| `editor/RichTransformation.kt` | VisualTransformation: draws the Doc in the single field |
| `ui/screens/FormatToolbar.kt` | scrolling toolbar (tool keys match web `TOOLBAR_GROUPS`) |
| `ui/screens/EditorController.kt` | holds `doc`, history, formatting actions, save |

## Rules
- Notes whose HTML uses tables / images / code / links / checklists / h4-h6 stay read-only
  (`HtmlCodec.parse` returns null) until their part lands.
- A note the user never edited keeps its stored HTML byte-for-byte on save.
- The web's Chrome indent form (`<blockquote style="margin: 0 0 0 40px; border: none">`) is read as indent.

## Needs a device check (can't be compiled in CI-less sandbox)
Paragraph-style rendering inside BasicTextField (caret on empty centered lines, list hanging indent),
toolbar taps keeping the keyboard open, quote bar glyph (U+2503) on your device font.
