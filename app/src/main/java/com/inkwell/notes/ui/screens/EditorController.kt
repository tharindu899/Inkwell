package com.inkwell.notes.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.inkwell.notes.R
import com.inkwell.notes.data.AppPrefs
import com.inkwell.notes.data.NotesStore
import com.inkwell.notes.data.model.Note
import com.inkwell.notes.data.model.Notebook
import com.inkwell.notes.editor.Align
import com.inkwell.notes.editor.BlockType
import com.inkwell.notes.editor.HtmlCodec
import com.inkwell.notes.editor.ImageData
import com.inkwell.notes.editor.LinkTable
import com.inkwell.notes.editor.Mark
import com.inkwell.notes.editor.MarkdownCodec
import com.inkwell.notes.editor.NoteExport
import com.inkwell.notes.editor.OBJ
import com.inkwell.notes.editor.Para
import com.inkwell.notes.editor.RichDoc
import com.inkwell.notes.editor.TableData
import com.inkwell.notes.ui.Route
import com.inkwell.notes.ui.components.ToastState
import com.inkwell.notes.util.Iso
import com.inkwell.notes.util.PdfExport
import com.inkwell.notes.util.countWords
import com.inkwell.notes.util.genId
import com.inkwell.notes.util.normalizeTagInput
import java.text.Collator
import java.text.DateFormat
import java.util.Date

/** The table cell the "Edit cell" sheet is open on (paragraph index of the table + row / column). */
class TableEdit(val para: Int, val row: Int, val col: Int)

/** The link dialog: [start]..[end] is the text it applies to (empty range = insert at the caret). */
class LinkDraft(
    val text: String,
    val url: String,
    val start: Int,
    val end: Int,
    /** An existing link is being edited (shows "Remove"). */
    val editing: Boolean,
)

/**
 * State + save logic of the Editor page (the non-visual half of `pages/Editor.jsx`).
 *
 * Created with `remember(route)` by [EditorScreen], so every open of a note is a fresh session.
 * Mirrors the web behaviour:
 *  - `saveNow`      → [save] (skips when nothing changed, catches storage errors → toast)
 *  - flush on pause / leave → [flush]
 *  - 2 s debounced auto-save → driven by [edits] from the screen (not while Reading mode is on)
 *
 * Part 4: tags (manage modal: add with Enter / comma, Backspace on an empty input removes the last, tap a chip to
 * remove, saved-tag quick chips) + notebook (picker sheet, inline "Create notebook" that moves this note into it).
 * Tag / notebook changes ride the normal dirty → 2 s auto-save path; they are locked while Reading mode is on.
 *
 * Part 3: checklists (tap a box), nested + collapsible lists, markdown shortcuts (toggle persisted as the
 * `markdownMode` pref, same key as Settings and the web app), Enter / Backspace list behaviour (in [RichDoc.edit]).
 *
 * Part 2: the body is a [RichDoc] (text + inline marks + per-line block attributes). The text field
 * edits `body.text`; every change is fed through [RichDoc.edit] so formatting follows the text.
 * A note whose stored HTML uses something [HtmlCodec] can't represent yet (tables, inline code,
 * images, links …) is shown read-only ([bodyEditable] = false) and its `content` /
 * `markdown` are never rewritten, so opening + pinning + retitling such a note can't flatten it.
 */
@Stable
class EditorController(private val context: Context, route: Route.Editor) {
    private val store = NotesStore(context)

    /** Note id was not found (deleted / bad link) → the screen leaves. */
    var missing by mutableStateOf(false)
        private set

    /** The note as last saved / created — carries pinned, tags, notebook, createdAt … */
    var note by mutableStateOf(Note(id = ""))
        private set

    var notebook by mutableStateOf<Notebook?>(null)
        private set

    var title by mutableStateOf(TextFieldValue(""))
        private set

    var body by mutableStateOf(TextFieldValue(""))
        private set

    /** The formatted body; `doc.text == body.text` at all times while [bodyEditable]. */
    var doc by mutableStateOf(RichDoc.EMPTY)
        private set

    /** False for notes using markup this editor version can't write back yet — see class doc. */
    var bodyEditable by mutableStateOf(true)
        private set

    /** Markdown shortcuts (`# `, `- `, `1. `, `[] `, `> `, ``` ```) on / off — `markdownMode` pref, default on. */
    var markdownShortcuts by mutableStateOf(AppPrefs.getPref(context, "markdownMode", true))
        private set

    /** Part 7: Markdown text mode — the body is shown / edited as markdown source ([source]) instead of rendered. */
    var sourceMode by mutableStateOf(false)
        private set

    var source by mutableStateOf(TextFieldValue(""))
        private set

    /** Part 7: export sheet + pending SAF save (format, file name, bytes). */
    var exportOpen by mutableStateOf(false)
        private set

    var pendingExport by mutableStateOf<PendingExport?>(null)
        private set

    class PendingExport(val fileName: String, val mime: String, val bytes: ByteArray)

    /** Typing formatting chosen with a collapsed caret (e.g. Bold toggled, nothing selected yet). */
    private var pending by mutableStateOf<Int?>(null)

    /** Part 5: the open table-cell sheet / link dialog (null = closed). Both are cleared by undo / redo. */
    var tableEdit by mutableStateOf<TableEdit?>(null)
        private set

    var linkDraft by mutableStateOf<LinkDraft?>(null)
        private set

    /** Part 6: the image paragraph whose handles are showing, and the drawing dialog. */
    var selectedImage by mutableStateOf<Int?>(null)
        private set

    var drawOpen by mutableStateOf(false)
        private set

    private class Snap(val doc: RichDoc, val selection: TextRange)

    private val undoStack = ArrayList<Snap>()
    private val redoStack = ArrayList<Snap>()
    private var lastEditAt = 0L
    private var lastKind = 0 // 0 none, 1 typing, 2 formatting

    /** Bumped whenever the history changes so [toolbarState] recomputes. */
    private var historyVersion by mutableIntStateOf(0)

    /** What the toolbar highlights for the current caret / selection. */
    val toolbarState: ToolbarState
        get() {
            historyVersion.let { } // read so the toolbar recomposes when the history changes
            val sel = body.selection
            val para = doc.paraAt(sel.min)
            return ToolbarState(
                marks = pending ?: doc.marksAt(sel.min, sel.max),
                link = doc.linkIdAt(sel.min) != 0,
                block = para.type,
                align = para.align,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty(),
                markdown = sourceMode,
            )
        }

    // ─── tags + notebook (Part 4) ────────────

    var tagManageOpen by mutableStateOf(false)
        private set

    /** The text field of the tag modal. */
    var tagInput by mutableStateOf("")
        private set

    /** Every tag used by any saved note, snapshotted when the tag modal opens (web `savedTagOptions` source). */
    private var knownTags by mutableStateOf<List<String>>(emptyList())

    /** "Saved tags" quick chips: tags used elsewhere that this note doesn't have yet — max 12. */
    val savedTagOptions: List<String>
        get() {
            val current = note.tags.map { it.lowercase() }.toSet()
            return knownTags.filter { it !in current }.take(12)
        }

    var nbPickerOpen by mutableStateOf(false)
        private set

    var nbCreateOpen by mutableStateOf(false)
        private set

    /** Notebooks listed by the picker, loaded when it opens. */
    var pickerNotebooks by mutableStateOf<List<Notebook>>(emptyList())
        private set

    /** Reading mode: fields are locked, auto-save is off. */
    var readMode by mutableStateOf(false)
        private set

    /** true = "Saved", false = "Saving…" (the `.autosave-dot`). */
    var saved by mutableStateOf(true)
        private set

    var deleteConfirmOpen by mutableStateOf(false)
        private set

    var toast by mutableStateOf<ToastState?>(null)
        private set

    /** Bumped on every text edit; the screen restarts its 2 s auto-save timer when it changes. */
    var edits by mutableIntStateOf(0)
        private set

    val isNew: Boolean = route.id == null
    val autosaveEnabled: Boolean = AppPrefs.getPref(context, "autosave", true)

    val wordCount: Int get() = when {
        sourceMode -> countWords(source.text)
        bodyEditable -> countWords(doc.countText)
        else -> countWords(body.text)
    }
    val charCount: Int get() = when {
        sourceMode -> source.text.length
        bodyEditable -> doc.charCount
        else -> body.text.length
    }

    /** The document as it would be saved right now (source text is parsed back when in Markdown text mode). */
    private fun currentDoc(): RichDoc = if (sourceMode) MarkdownCodec.parse(source.text) else doc

    private var dirty = false
    private var persisted = false
    private var discarded = false
    private var lastHash = ""

    init {
        if (route.id != null) {
            val existing = store.getNote(route.id)
            if (existing == null) {
                missing = true
            } else {
                persisted = true
                open(existing)
            }
        } else {
            val nb = route.notebookId?.let(store::getNotebook)
            val tag = normalizeTagInput(route.tag.orEmpty())
            val now = Iso.now()
            open(
                Note(
                    id = genId(),
                    notebookId = nb?.id,
                    notebookName = nb?.name.orEmpty(),
                    notebookColor = nb?.color.orEmpty(),
                    tags = if (tag.isEmpty()) emptyList() else listOf(tag),
                    content = starterHtml(route.type),
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
    }

    private fun open(n: Note) {
        note = n
        notebook = n.notebookId?.let(store::getNotebook)
        title = TextFieldValue(n.title)

        // No HTML but a markdown source (imported / odd notes) → keep it read-only rather than guess.
        val parsed = if (n.content.isBlank() && n.markdown.isNotBlank()) null else HtmlCodec.parse(n.content)
        if (parsed != null) {
            bodyEditable = true
            doc = parsed
            body = TextFieldValue(parsed.text)
            lastHash = fingerprint(n.title, HtmlCodec.toHtml(parsed))
        } else {
            // Read-only preview: the markdown source when there is one, else the HTML as text.
            bodyEditable = false
            body = TextFieldValue(if (n.markdown.isNotBlank()) n.markdown else HtmlCodec.previewText(n.content))
            lastHash = fingerprint(n.title, n.content)
        }
    }

    // ─── edits ───────────────────────────────

    fun onTitleChange(v: TextFieldValue) {
        if (readMode) return
        // Enter in the title is handled by the screen (moves to the body); never keep the newline.
        val clean = if ('\n' in v.text) v.copy(text = v.text.replace("\n", "")) else v
        val changed = clean.text != title.text
        title = clean
        if (changed) markDirty()
    }

    fun onBodyChange(v0: TextFieldValue) {
        if (readMode || !bodyEditable) return
        selectedImage = null
        // Windows line endings from the clipboard never enter the document.
        val v = if ('\r' in v0.text) stripCr(v0) else v0
        if (v.text == body.text) {
            // Caret / selection / IME-composition change only. Moving the caret drops a pending format.
            if (v.selection != body.selection) pending = null
            // A caret can't rest on a divider / table line: move it to the nearest real paragraph.
            val sel = v.selection
            if (sel.collapsed && doc.objectParaAt(sel.start) >= 0) {
                val forward = sel.start >= body.selection.start
                val to = doc.escapeObject(sel.start, forward)
                body = v.copy(selection = TextRange(to))
            } else {
                body = v
            }
            return
        }
        val caret = if (v.selection.collapsed) v.selection.start else null
        val d = doc.diffFor(v.text, caret)
        val inserted = v.text.substring(d.start, d.insEnd)

        // The object placeholder can't be typed or pasted.
        if (OBJ in inserted) {
            val cleanIns = inserted.replace(OBJ.toString(), "")
            val removed = inserted.length - cleanIns.length
            val t = v.text.substring(0, d.start) + cleanIns + v.text.substring(d.insEnd)
            val fix = { o: Int -> if (o >= d.insEnd) o - removed else minOf(o, d.start + cleanIns.length) }
            onBodyChange(TextFieldValue(t, TextRange(fix(v.selection.start), fix(v.selection.end))))
            return
        }

        // Paste: markdown / HTML source / code get turned into real blocks (web `handlePaste`).
        if (markdownShortcuts && inserted.length >= 2 && ('\n' in inserted || inserted.length >= 6)) {
            val frag = when {
                MarkdownCodec.looksLikeHtmlSource(inserted) -> HtmlCodec.parseLenient(inserted)
                MarkdownCodec.looksLikeCode(inserted) -> MarkdownCodec.codeFragment(inserted)
                MarkdownCodec.looksLikeMarkdown(inserted) -> MarkdownCodec.parse(inserted)
                else -> null
            }
            if (frag != null) {
                val (nd, at) = doc.replaceWithFragment(d.start, d.removedEnd, frag)
                if (nd !== doc) {
                    recordBefore(KIND_FORMAT)
                    pending = null
                    doc = nd
                    body = TextFieldValue(nd.text, TextRange(at.coerceIn(0, nd.text.length)))
                    markDirty()
                    return
                }
            }
        }

        val result = doc.edit(v.text, pending, markdownShortcuts, caret)
        // A rewritten edit (shortcut conversion, leaving a list …) is its own undo step, so Undo takes just that back.
        recordBefore(if (result.caret != null) KIND_FORMAT else KIND_TYPING)
        if (result.caret != null) pending = null
        doc = result.doc
        body = if (result.doc.text == v.text) {
            v
        } else {
            // The edit was rewritten (e.g. Enter on an empty bullet) — put the text and caret back.
            TextFieldValue(result.doc.text, TextRange((result.caret ?: v.selection.start).coerceIn(0, result.doc.text.length)))
        }
        markDirty()
    }

    private fun stripCr(v: TextFieldValue): TextFieldValue {
        val sb = StringBuilder(v.text.length)
        var s = v.selection.start
        var e = v.selection.end
        for (i in v.text.indices) {
            if (v.text[i] == '\r') {
                if (i < v.selection.start) s--
                if (i < v.selection.end) e--
            } else sb.append(v.text[i])
        }
        return TextFieldValue(sb.toString(), TextRange(s.coerceAtLeast(0), e.coerceAtLeast(0)))
    }

    // ─── formatting (toolbar) ────────────────

    private val canFormat: Boolean get() = bodyEditable && !readMode

    /** Bold / italic / underline / strike / highlight / sup / sub. */
    fun toggleMark(mark: Int) {
        if (!canFormat) return
        val sel = body.selection
        if (sel.collapsed) {
            // Nothing selected: the format applies to what you type next.
            val current = pending ?: doc.marksAt(sel.start, sel.end)
            val exclusive = when (mark) {
                Mark.SUP -> Mark.SUB
                Mark.SUB -> Mark.SUP
                else -> 0
            }
            pending = if (current and mark != 0) current and mark.inv() else (current or mark) and exclusive.inv()
            return
        }
        recordBefore(KIND_FORMAT)
        doc = doc.toggleMark(mark, sel.min, sel.max)
        markDirty()
    }

    fun setBlock(type: BlockType) = format { it.setBlock(type, body.selection.min, body.selection.max) }

    fun setAlign(align: Align) = format { it.setAlign(align, body.selection.min, body.selection.max) }

    fun changeIndent(delta: Int) = format { it.changeIndent(delta, body.selection.min, body.selection.max) }

    // ─── code, links, inserts (Part 5) ───────

    /** Selects / moves the caret after a structural edit. */
    private fun place(newDoc: RichDoc, selection: TextRange) {
        doc = newDoc
        val len = newDoc.text.length
        body = TextFieldValue(newDoc.text, TextRange(selection.start.coerceIn(0, len), selection.end.coerceIn(0, len)))
        pending = null
        markDirty()
    }

    /** Toolbar "Code": wraps the selection in inline code, or unwraps when the caret is already inside code. */
    fun toggleInlineCode() {
        if (!canFormat) return
        val sel = body.selection
        if (doc.paraAt(sel.min).type == BlockType.CODE) return
        if (sel.collapsed) {
            val run = doc.markRun(Mark.CODE, sel.start)
            recordBefore(KIND_FORMAT)
            if (run != null) {
                doc = doc.clearMark(Mark.CODE, run.first, run.second)
                pending = null
                markDirty()
            } else {
                // Nothing selected: drop in a selected "code" placeholder to type over.
                place(doc.insertInline(sel.start, sel.start, "code", Mark.CODE), TextRange(sel.start, sel.start + 4))
            }
            return
        }
        recordBefore(KIND_FORMAT)
        val all = doc.marksAt(sel.min, sel.max) and Mark.CODE != 0
        doc = if (all) doc.clearMark(Mark.CODE, sel.min, sel.max) else doc.toggleMark(Mark.CODE, sel.min, sel.max)
        markDirty()
    }

    /** Toolbar "Code block": turns the selected lines into a code block (again = back to paragraphs). */
    fun toggleCodeBlock() {
        if (!canFormat) return
        recordBefore(KIND_FORMAT)
        doc = doc.setBlock(BlockType.CODE, body.selection.min, body.selection.max).withParagraphAfterCode()
        markDirty()
    }

    /** Copy chip of a code block (works in Reading mode too). */
    fun copyCode(para: Int) {
        val text = doc.codeBlockText(para)
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        runCatching { cm?.setPrimaryClip(ClipData.newPlainText("code", text)) }
        showToast("Code copied", R.drawable.ic_fa_check)
    }

    /** "Bottom" chip: caret to the paragraph under the block. */
    fun jumpBelowCode(para: Int) {
        if (!canFormat) return
        val (nd, at) = doc.exitCodeBlock(para)
        if (nd !== doc) {
            recordBefore(KIND_FORMAT)
            place(nd, TextRange(at))
        } else {
            body = body.copy(selection = TextRange(at))
        }
    }

    /** "Delete" chip: removes the whole block. */
    fun deleteCode(para: Int) {
        if (!canFormat) return
        val (nd, at) = doc.deleteCodeBlock(para)
        recordBefore(KIND_FORMAT)
        place(nd, TextRange(at))
    }

    fun insertHr() {
        if (!canFormat) return
        recordBefore(KIND_FORMAT)
        val (nd, at) = doc.insertObject(body.selection.max, Para(type = BlockType.HR))
        place(nd, TextRange(at))
    }

    fun insertTable() {
        if (!canFormat) return
        recordBefore(KIND_FORMAT)
        val (nd, at) = doc.insertObject(body.selection.max, Para(type = BlockType.TABLE, table = TableData.blank()))
        place(nd, TextRange(at))
    }

    /** Date + time at the caret, in the phone's own format. */
    fun insertDateTime() {
        if (!canFormat) return
        val s = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date())
        val sel = body.selection
        recordBefore(KIND_FORMAT)
        place(doc.insertInline(sel.min, sel.max, s), TextRange(sel.min + s.length))
    }

    // ── link dialog ──

    fun openLink() {
        if (!canFormat) return
        val sel = body.selection
        if (doc.paraAt(sel.min).type == BlockType.CODE) return
        if (sel.collapsed) {
            val span = doc.linkSpan(sel.start)
            linkDraft = if (span != null) {
                LinkDraft(
                    doc.text.substring(span.first, span.second),
                    LinkTable.urlOf(doc.linkIdAt(sel.start)).orEmpty(),
                    span.first,
                    span.second,
                    editing = true,
                )
            } else {
                LinkDraft("", "", sel.start, sel.start, editing = false)
            }
        } else {
            val txt = doc.text.substring(sel.min, sel.max).replace("\n", " ").replace(OBJ.toString(), "")
            val existing = LinkTable.urlOf(doc.linkIdAt((sel.min + 1).coerceAtMost(doc.text.length)))
            linkDraft = LinkDraft(txt, existing.orEmpty(), sel.min, sel.max, editing = existing != null)
        }
    }

    fun closeLink() {
        linkDraft = null
    }

    /** Dialog "Insert" / "Save": [rawText] is what the link reads as, [rawUrl] where it goes. */
    fun submitLink(rawText: String, rawUrl: String) {
        val draft = linkDraft ?: return
        val url = normalizeUrl(rawUrl)
        if (url.isEmpty()) {
            showToast("Enter a link address", R.drawable.ic_fa_circle_exclamation)
            return
        }
        val label = rawText.trim().replace("\n", " ").replace(OBJ.toString(), "").ifEmpty { url }
        recordBefore(KIND_FORMAT)
        val unchanged = draft.end > draft.start && label == doc.text.substring(draft.start, draft.end)
        if (unchanged) {
            doc = doc.setLink(draft.start, draft.end, url)
            markDirty()
        } else {
            val nd = doc.insertInline(draft.start, draft.end, label, 0, LinkTable.idOf(url))
            place(nd, TextRange(draft.start + label.length))
        }
        linkDraft = null
    }

    fun removeLink() {
        val draft = linkDraft ?: return
        recordBefore(KIND_FORMAT)
        doc = doc.setLink(draft.start, draft.end, null)
        markDirty()
        linkDraft = null
    }

    private fun normalizeUrl(raw: String): String {
        val u = raw.trim()
        if (u.isEmpty() || ' ' in u) return ""
        if (Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:").containsMatchIn(u) || u.startsWith("#")) return u
        if ('@' in u && '/' !in u) return "mailto:$u"
        return "https://$u"
    }

    // ── images + drawing (Part 6) ──

    fun selectImage(para: Int) {
        if (doc.paras.getOrNull(para)?.image == null) return
        selectedImage = if (selectedImage == para) null else para
    }

    /** A picked photo (data URL) or a finished sketch lands below the caret's line, like the web figure. */
    fun insertImage(src: String, alt: String, drawing: Boolean) {
        if (!canFormat) return
        recordBefore(KIND_FORMAT)
        val (nd, at) = doc.insertObject(body.selection.max, Para(type = BlockType.IMAGE, image = ImageData(src, alt.ifBlank { if (drawing) "Drawing" else "Photo" }, drawing)))
        place(nd, TextRange(at))
        selectedImage = null
        showToast(if (drawing) "Drawing added" else "Photo added", if (drawing) R.drawable.ic_fa_check else R.drawable.ic_fa_image)
    }

    fun photoFailed() {
        showToast("Could not add photo", R.drawable.ic_fa_circle_exclamation)
    }

    /** Size / position changed by the handles (committed on release). */
    fun commitImage(para: Int, image: ImageData) {
        if (!canFormat || doc.paras.getOrNull(para)?.image == image) return
        recordBefore(KIND_FORMAT)
        doc = doc.setImage(para, image)
        markDirty()
    }

    fun deleteImage(para: Int) {
        if (!canFormat || doc.paras.getOrNull(para)?.image == null) return
        recordBefore(KIND_FORMAT)
        val nd = doc.removeParaAt(para)
        val at = nd.escapeObject(nd.starts[para.coerceIn(0, nd.paraCount - 1)], forward = true)
        selectedImage = null
        place(nd, TextRange(at))
        showToast("Media deleted", R.drawable.ic_fa_trash_can)
    }

    fun openDraw() {
        if (!canFormat) return
        drawOpen = true
    }

    fun closeDraw() {
        drawOpen = false
    }

    /** Web `?type=checklist|image|voice` starters. */
    private fun starterHtml(type: String?): String = when (type) {
        "checklist" -> "<ul class=\"check-list\" data-type=\"taskList\"><li data-checked=\"false\"><label><span class=\"check-box\" role=\"checkbox\" tabindex=\"-1\" contenteditable=\"false\" data-checked=\"false\" aria-checked=\"false\"></span> <span class=\"check-text\">New task</span></label></li></ul>"
        "image" -> "<p>Image note</p><p><br></p>"
        "voice" -> "<p>Voice note</p><blockquote>Record or paste your voice note text here.</blockquote>"
        else -> ""
    }

    // ── table cells ──

    fun openCell(para: Int, row: Int, col: Int) {
        if (!canFormat) return
        if (doc.paras.getOrNull(para)?.table == null) return
        tableEdit = TableEdit(para, row, col)
    }

    fun closeCell() {
        tableEdit = null
    }

    /** The text of the cell being edited. */
    fun cellText(): String {
        val e = tableEdit ?: return ""
        return doc.paras.getOrNull(e.para)?.table?.cell(e.row, e.col)?.text.orEmpty()
    }

    fun onCellText(raw: String) {
        val e = tableEdit ?: return
        val t = doc.paras.getOrNull(e.para)?.table ?: return
        val clean = raw.replace("\n", " ").replace(OBJ.toString(), "")
        val nt = t.withCell(e.row, e.col, clean)
        if (nt === t) return
        recordBefore(KIND_TYPING)
        doc = doc.setTable(e.para, nt)
        markDirty()
    }

    val tableForEdit: TableData? get() = tableEdit?.let { doc.paras.getOrNull(it.para)?.table }

    /** Row / column buttons of the cell sheet. [op] returns the new table and the cell to keep editing. */
    private fun tableOp(op: (TableData, TableEdit) -> Pair<TableData, TableEdit>) {
        val e = tableEdit ?: return
        val t = doc.paras.getOrNull(e.para)?.table ?: return
        val (nt, ne) = op(t, e)
        if (nt === t) return
        recordBefore(KIND_FORMAT)
        doc = doc.setTable(e.para, nt)
        tableEdit = TableEdit(
            e.para,
            ne.row.coerceIn(0, nt.rowCount - 1),
            ne.col.coerceIn(0, nt.colCount - 1),
        )
        markDirty()
    }

    fun rowAbove() = tableOp { t, e ->
        val at = if (t.header) maxOf(1, e.row) else e.row
        t.addRow(at) to TableEdit(e.para, at, e.col)
    }

    fun rowBelow() = tableOp { t, e -> t.addRow(e.row + 1) to TableEdit(e.para, e.row + 1, e.col) }

    fun deleteRow() = tableOp { t, e -> t.removeRow(e.row) to e }

    fun colLeft() = tableOp { t, e -> t.addCol(e.col) to TableEdit(e.para, e.row, e.col) }

    fun colRight() = tableOp { t, e -> t.addCol(e.col + 1) to TableEdit(e.para, e.row, e.col + 1) }

    fun deleteCol() = tableOp { t, e -> t.removeCol(e.col) to e }

    fun toggleHeader() = tableOp { t, e -> t.withHeader(!t.header) to e }

    fun deleteTable() {
        val e = tableEdit ?: return
        if (doc.paras.getOrNull(e.para)?.table == null) return
        recordBefore(KIND_FORMAT)
        val nd = doc.removeParaAt(e.para)
        val at = nd.escapeObject(nd.starts[e.para.coerceIn(0, nd.paraCount - 1)], forward = true)
        tableEdit = null
        place(nd, TextRange(at))
    }

    // ─── lists (Part 3) ──────────────────────

    /** Tap on a checklist box. */
    fun toggleChecked(para: Int) {
        if (!canFormat) return
        recordBefore(KIND_FORMAT)
        doc = doc.toggleChecked(para)
        markDirty()
    }

    /**
     * Tap on a collapse chevron. Works in Reading mode too (it only changes what is shown); the open / closed
     * state is saved with the note like the web `data-collapsed`.
     */
    fun toggleCollapse(para: Int) {
        if (!bodyEditable || !doc.hasChildren(para)) return
        val collapsing = !doc.paras[para].collapsed
        doc = doc.toggleCollapse(para)
        if (collapsing) {
            // keep the caret / selection out of the items that just disappeared
            val lo = doc.starts[para + 1]
            val hi = doc.paraEnd(doc.subtreeEnd(para))
            val anchor = doc.paraEnd(para)
            fun fix(o: Int) = if (o in lo..hi) anchor else o
            val sel = body.selection
            body = body.copy(selection = TextRange(fix(sel.start), fix(sel.end)))
        }
        historyVersion++
        if (!readMode) markDirty()
    }

    /** Toolbar "M": switches THIS note between rendered view and Markdown text (the pref only sets the default). */
    fun toggleMarkdown() {
        if (!bodyEditable || readMode) return
        if (sourceMode) leaveSource() else enterSource()
    }

    private fun enterSource() {
        pending = null
        tableEdit = null
        linkDraft = null
        selectedImage = null
        source = TextFieldValue(HtmlCodec.toMarkdown(doc))
        sourceMode = true
        showToast("Markdown text mode", R.drawable.ic_fa_code)
    }

    private fun leaveSource() {
        val parsed = MarkdownCodec.parse(source.text)
        if (parsed.text != doc.text || HtmlCodec.toHtml(parsed) != HtmlCodec.toHtml(doc)) {
            recordBefore(KIND_FORMAT)
            doc = parsed
            markDirty()
        }
        body = TextFieldValue(doc.text, TextRange(0))
        sourceMode = false
        historyVersion++
        showToast("Rendered view", R.drawable.ic_fa_code)
    }

    fun onSourceChange(v: TextFieldValue) {
        if (!sourceMode || readMode) return
        val changed = v.text != source.text
        source = v
        if (changed) markDirty()
    }

    // ─── export ──────────────────────────────

    fun openExport() {
        exportOpen = true
    }

    fun closeExport() {
        exportOpen = false
    }

    fun consumeExport() {
        pendingExport = null
    }

    fun exportSaved(ok: Boolean) {
        pendingExport = null
        showToast(if (ok) "Note exported" else "Export failed", if (ok) R.drawable.ic_fa_book_open else R.drawable.ic_fa_circle_exclamation)
    }

    /** Builds the file for [format] ("pdf" | "md" | "html" | "txt" | "xls"); the screen then asks where to save it. */
    fun export(format: String) {
        exportOpen = false
        val d = currentDoc()
        val t = title.text.trim().ifEmpty { "Untitled" }
        val plain = if (bodyEditable) NoteExport.plainText(d) else body.text
        val stem = NoteExport.safeFileName(t)
        try {
            pendingExport = when (format) {
                "pdf" -> PendingExport("$stem.pdf", "application/pdf", PdfExport.render(t, plain))
                "md" -> PendingExport(
                    "$stem.md", "text/markdown",
                    NoteExport.markdown(t, if (bodyEditable) HtmlCodec.toMarkdown(d) else note.markdown).toByteArray(),
                )
                "html" -> PendingExport(
                    "$stem.html", "text/html",
                    NoteExport.html(t, if (bodyEditable) HtmlCodec.toHtml(d) else note.content).toByteArray(),
                )
                "xls" -> PendingExport(
                    "$stem.xls", "application/vnd.ms-excel",
                    NoteExport.xls(t, note.updatedAt, countWords(plain), plain).toByteArray(),
                )
                else -> PendingExport("$stem.txt", "text/plain", NoteExport.text(t, plain).toByteArray())
            }
        } catch (_: Exception) {
            showToast("Export failed", R.drawable.ic_fa_circle_exclamation)
        }
    }

    fun clearFormat() {
        if (!canFormat) return
        pending = null
        format { it.clearFormat(body.selection.min, body.selection.max) }
    }

    private inline fun format(op: (RichDoc) -> RichDoc) {
        if (!canFormat) return
        recordBefore(KIND_FORMAT)
        doc = op(doc)
        markDirty()
    }

    // ─── undo / redo ─────────────────────────

    /** Call BEFORE changing [doc]. Typing bursts (< 0.7 s apart) share one undo step; formatting always gets its own. */
    private fun recordBefore(kind: Int) {
        val now = SystemClock.uptimeMillis()
        if (kind == KIND_FORMAT || lastKind != KIND_TYPING || now - lastEditAt > TYPING_GAP_MS) {
            undoStack.add(Snap(doc, body.selection))
            if (undoStack.size > MAX_HISTORY) undoStack.removeAt(0)
            redoStack.clear()
            historyVersion++
        }
        lastKind = kind
        lastEditAt = now
    }

    fun undo() {
        if (!canFormat || undoStack.isEmpty()) return
        redoStack.add(Snap(doc, body.selection))
        restore(undoStack.removeAt(undoStack.lastIndex))
    }

    fun redo() {
        if (!canFormat || redoStack.isEmpty()) return
        undoStack.add(Snap(doc, body.selection))
        restore(redoStack.removeAt(redoStack.lastIndex))
    }

    private fun restore(s: Snap) {
        doc = s.doc
        val len = s.doc.text.length
        body = TextFieldValue(s.doc.text, TextRange(s.selection.start.coerceIn(0, len), s.selection.end.coerceIn(0, len)))
        pending = null
        tableEdit = null
        linkDraft = null
        selectedImage = null
        lastKind = 0
        historyVersion++
        markDirty()
    }

    private fun markDirty() {
        dirty = true
        saved = false
        edits++
    }

    // ─── tags (Part 4) ───────────────────────

    fun openTagManager() {
        if (locked()) return
        tagInput = ""
        knownTags = store.getNotes()
            .flatMap { it.tags }
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sortedWith(Collator.getInstance())
        tagManageOpen = true
    }

    fun closeTagManager() {
        tagManageOpen = false
        tagInput = ""
    }

    /** Typing in the tag field. A comma commits what came before it (also for pasted "a,b,c"). */
    fun onTagInputChange(raw: String) {
        if (',' !in raw) {
            tagInput = raw.take(TAG_MAX)
            return
        }
        val parts = raw.split(',')
        parts.dropLast(1).forEach { addTag(it) }
        tagInput = parts.last().take(TAG_MAX)
    }

    /** Enter / IME Done in the tag field. */
    fun commitTag() {
        if (locked()) return
        addTag(tagInput)
        tagInput = ""
    }

    /** The modal's "Add" button: adds a typed tag, or closes when the field is empty. */
    fun addOrCloseTags() {
        if (tagInput.isNotBlank()) commitTag() else closeTagManager()
    }

    /** Backspace on an empty tag field removes the last tag. */
    fun removeLastTag() {
        if (tagInput.isNotEmpty() || note.tags.isEmpty() || locked()) return
        note = note.copy(tags = note.tags.dropLast(1))
        markDirty()
    }

    fun removeTag(tag: String) {
        if (locked()) return
        if (tag !in note.tags) return
        note = note.copy(tags = note.tags.filter { it != tag })
        markDirty()
    }

    /** Tap on a saved-tag chip: adds it (or removes it if the note already has it). */
    fun toggleSavedTag(tag: String) {
        if (tag in note.tags) removeTag(tag) else {
            if (locked()) return
            addTag(tag)
        }
    }

    private fun addTag(raw: String) {
        val v = normalizeTagInput(raw)
        if (v.isEmpty() || v in note.tags) return
        note = note.copy(tags = note.tags + v)
        markDirty()
    }

    // ─── notebook (Part 4) ───────────────────

    fun openNotebookPicker() {
        if (locked()) return
        pickerNotebooks = store.getNotebooks()
        nbPickerOpen = true
    }

    fun closeNotebookPicker() {
        nbPickerOpen = false
    }

    /** `pickNb(id)` — null = "None". */
    fun pickNotebook(id: String?) {
        if (locked()) return
        val nb = id?.let(store::getNotebook)
        note = note.copy(
            notebookId = nb?.id,
            notebookName = nb?.name.orEmpty(),
            notebookColor = nb?.color.orEmpty(),
        )
        notebook = nb
        nbPickerOpen = false
        markDirty()
    }

    /** Picker → "Create notebook": swaps the sheet for the create form. */
    fun openCreateNotebook() {
        if (locked()) return
        nbPickerOpen = false
        nbCreateOpen = true
    }

    fun closeCreateNotebook() {
        nbCreateOpen = false
    }

    /** `submitCreateNotebook()` — makes the notebook, moves this note into it and saves straight away. */
    fun createNotebookAndMove(rawName: String, color: String, icon: String) {
        if (locked()) return
        val name = rawName.trim()
        if (name.isEmpty()) {
            showToast("Notebook name required", R.drawable.ic_fa_circle_exclamation)
            return
        }
        val now = Iso.now()
        val nb = Notebook(id = genId(), name = name.take(32), color = color, icon = icon, createdAt = now, updatedAt = now)
        store.saveNotebook(nb)
        note = note.copy(notebookId = nb.id, notebookName = nb.name, notebookColor = nb.color)
        notebook = nb
        nbCreateOpen = false
        nbPickerOpen = false
        markDirty()
        save()
        showToast("Notebook created and selected", R.drawable.ic_fa_book_open)
    }

    /** Web `isEditorReadLocked(true)`: edits are refused (with a toast) while Reading mode is on. */
    private fun locked(): Boolean {
        if (!readMode) return false
        showToast("Reading mode is locked — turn it off to edit", R.drawable.ic_fa_circle_exclamation)
        return true
    }

    // ─── toolbar actions ─────────────────────

    fun toggleReadMode() {
        if (!readMode) {
            if (sourceMode) leaveSource() // Reading mode always shows the rendered note
            flush() // entering Reading mode saves what you have, like the web
        }
        readMode = !readMode
        showToast(
            if (readMode) "Reading mode on" else "Editing mode on",
            R.drawable.ic_fa_book_open,
        )
    }

    fun togglePin() {
        if (readMode) return
        val nowPinned = !note.pinned
        note = note.copy(pinned = nowPinned, pinOrder = if (nowPinned) note.pinOrder else null)
        dirty = true
        save()
        // A fresh pin goes to the end of the pinned list (same rule as Home's bulk pin).
        if (persisted) {
            store.setPinned(setOf(note.id), nowPinned)
            store.getNote(note.id)?.let { note = it }
        }
        showToast(if (nowPinned) "Note pinned" else "Note unpinned", R.drawable.ic_fa_thumbtack)
    }

    fun requestDelete() {
        if (readMode) return
        deleteConfirmOpen = true
    }

    fun dismissDelete() {
        deleteConfirmOpen = false
    }

    /** Deletes the note (if it was ever saved). The caller then leaves the screen. */
    fun confirmDelete() {
        deleteConfirmOpen = false
        discarded = true // flush on leave must not re-create it
        if (persisted) store.deleteNote(note.id)
    }

    // ─── saving ──────────────────────────────

    /** Leave / background / pause: save pending edits, never throws. */
    fun flush() {
        if (!discarded && dirty) save()
    }

    /** `saveNow()` */
    fun save() {
        if (discarded || missing || !dirty) return

        val d = currentDoc()
        val text = if (sourceMode) d.text else body.text
        val titleText = title.text.trim()
        val rich = bodyEditable

        // Brand-new note that is still completely empty: don't litter the list with "Untitled".
        if (!persisted && titleText.isEmpty() && text.isBlank() && !note.pinned) {
            dirty = false
            saved = true
            return
        }

        val finalTitle = titleText.ifEmpty { "Untitled" }
        val html = if (rich) HtmlCodec.toHtml(d) else note.content
        val hash = fingerprint(finalTitle, html)
        if (persisted && hash == lastHash) {
            dirty = false
            saved = true
            return
        }

        val updated = note.copy(
            title = finalTitle,
            content = html,
            markdown = if (rich) HtmlCodec.toMarkdown(d) else note.markdown,
            wordCount = if (rich) countWords(d.countText) else note.wordCount,
            updatedAt = Iso.now(),
        )
        try {
            store.saveNote(updated)
            note = updated
            persisted = true
            lastHash = hash
            dirty = false
            saved = true
        } catch (_: Exception) {
            saved = false
            showToast("Save failed — storage may be full", R.drawable.ic_fa_circle_exclamation)
        }
    }

    // ─── toast ───────────────────────────────

    fun dismissToast() {
        toast = null
    }

    private fun showToast(message: String, icon: Int) {
        toast = ToastState(System.nanoTime(), message, icon)
    }

    private companion object {
        const val KIND_TYPING = 1
        const val KIND_FORMAT = 2
        const val TYPING_GAP_MS = 700L
        const val MAX_HISTORY = 100
        const val TAG_MAX = 24
    }

    /** Everything a save would write; equal fingerprint = nothing to save (the web `saveHash`). */
    private fun fingerprint(title: String, content: String): String =
        "$title\u0000$content\u0000${note.pinned}\u0000${note.notebookId}\u0000${note.tags}"
}
