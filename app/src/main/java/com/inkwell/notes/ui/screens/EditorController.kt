package com.inkwell.notes.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import com.inkwell.notes.editor.Block
import com.inkwell.notes.editor.BlockType
import com.inkwell.notes.editor.EditResult
import com.inkwell.notes.editor.HtmlCodec
import com.inkwell.notes.editor.LinkTable
import com.inkwell.notes.editor.Mark
import com.inkwell.notes.editor.Para
import com.inkwell.notes.editor.PasteHandler
import com.inkwell.notes.editor.RichDoc
import com.inkwell.notes.ui.Route
import com.inkwell.notes.ui.components.ToastState
import com.inkwell.notes.util.Iso
import com.inkwell.notes.util.countWords
import com.inkwell.notes.util.genId
import com.inkwell.notes.util.normalizeTagInput
import java.text.Collator
import java.text.DateFormat
import java.util.Date

/**
 * State + save logic of the Editor page (the non-visual half of `pages/Editor.jsx`).
 *
 * Created with `remember(route)` by [EditorScreen], so every open of a note is a fresh session.
 * Mirrors the web behaviour:
 *  - `saveNow`      → [save] (skips when nothing changed, catches storage errors → toast)
 *  - flush on pause / leave → [flush]
 *  - 2 s debounced auto-save → driven by [edits] from the screen (not while Reading mode is on)
 *
 * Part 5: inline code, links (popover + edit / remove / open), divider, tables (grid editor sheet), code-block
 * actions (copy / jump to end / delete), date-time insert, and paste handling (markdown / code / plain, sanitised).
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

    /** Typing formatting chosen with a collapsed caret (e.g. Bold toggled, nothing selected yet). */
    private var pending by mutableStateOf<Int?>(null)

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
                block = para.type,
                align = para.align,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty(),
                markdown = markdownShortcuts,
                inLink = doc.linkSpanAt(sel.min, sel.max) != null,
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

    val wordCount: Int get() = countWords(body.text)
    val charCount: Int get() = body.text.length

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

    fun onBodyChange(v: TextFieldValue) {
        if (readMode || !bodyEditable) return
        if (v.text == body.text) {
            // Caret / selection / IME-composition change only. Moving the caret drops a pending format.
            if (v.selection != body.selection) pending = null
            body = v
            return
        }
        val caret = if (v.selection.collapsed) v.selection.start else null
        if (handlePaste(v, caret)) return
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

    fun toggleMarkdown() {
        markdownShortcuts = !markdownShortcuts
        AppPrefs.setPref(context, "markdownMode", markdownShortcuts)
        showToast(
            if (markdownShortcuts) "Markdown shortcuts on" else "Markdown shortcuts off",
            R.drawable.ic_fa_code,
        )
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

    // ─── paste (Part 5) ──────────────────────

    /**
     * A multi-line insertion is a paste: markdown becomes real blocks, code becomes a code block, anything else
     * is cleaned (line endings, control characters, tabs) and typed in. @return true when it handled the change.
     */
    private fun handlePaste(v: TextFieldValue, caret: Int?): Boolean {
        val ch = PasteHandler.diff(doc.text, v.text, caret) ?: return false
        val t = ch.text
        if (t.length < 2 || t == "\n" || t.isBlank() || '\n' !in t && '\r' !in t) return false
        // A table row only takes cell text; RichDoc.edit flattens line breaks there.
        val touchesTable = (doc.paraIndexAt(ch.start)..doc.paraIndexAt(ch.end)).any { doc.paras[it].type == BlockType.TABLE }
        if (touchesTable) return false

        val clean = PasteHandler.sanitize(t)
        var kind = PasteHandler.classify(clean)
        if (kind == PasteHandler.Kind.MARKDOWN && !markdownShortcuts) kind = PasteHandler.Kind.PLAIN
        val result: EditResult = if (kind == PasteHandler.Kind.PLAIN) {
            if (clean == t) return false
            val newText = doc.text.substring(0, ch.start) + clean + doc.text.substring(ch.end)
            doc.edit(newText, pending, markdownShortcuts, ch.start + clean.length)
        } else {
            PasteHandler.splice(doc, ch.start, ch.end, PasteHandler.toBlocks(kind, clean))
        }
        recordBefore(KIND_FORMAT)
        pending = null
        doc = result.doc
        val len = result.doc.text.length
        body = TextFieldValue(result.doc.text, TextRange((result.caret ?: (ch.start + clean.length)).coerceIn(0, len)))
        markDirty()
        return true
    }

    /** Swaps in [newDoc] as its own undo step with the caret at [caret]. */
    private fun apply(newDoc: RichDoc, caret: Int) {
        recordBefore(KIND_FORMAT)
        doc = newDoc
        body = TextFieldValue(newDoc.text, TextRange(caret.coerceIn(0, newDoc.text.length)))
        pending = null
        markDirty()
    }

    // ─── date / time, divider (Part 5) ───────

    /** `insertDateTime()` — the device's locale date + time at the caret. */
    fun insertDateTime() {
        if (!canFormat) return
        val t = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date())
        val sel = body.selection
        val a = sel.min
        val newText = doc.text.substring(0, a) + t + doc.text.substring(sel.max)
        val r = doc.edit(newText, pending, false, a + t.length)
        apply(r.doc, r.caret ?: (a + t.length))
    }

    /** Where new whole blocks go: replace an empty plain line, else below the current paragraph (below a table / code run). */
    private fun insertBlocksAtCaret(blocks: List<Block>): Pair<RichDoc, Int> {
        val i = doc.paraIndexAt(body.selection.max)
        val run = doc.tableRun(i) ?: doc.codeRun(i)
        val emptyPlain = run == null && doc.paras[i].type == BlockType.P && doc.paraEnd(i) == doc.starts[i]
        val at = when {
            emptyPlain -> i
            run != null -> run.last + 1
            else -> i + 1
        }
        return doc.replaceParas(at, if (emptyPlain) at + 1 else at, blocks) to at
    }

    /** Divider (`<hr>`): a rule line, with a fresh paragraph under it for the caret. */
    fun insertDivider() {
        if (!canFormat) return
        val (next, at) = insertBlocksAtCaret(
            listOf(Block("", IntArray(0), Para(BlockType.HR)), Block("", IntArray(0), Para())),
        )
        apply(next, next.starts[(at + 1).coerceAtMost(next.paraCount - 1)])
    }

    // ─── tables (Part 5) ─────────────────────

    var tableEdit by mutableStateOf<TableEdit?>(null)
        private set

    /** Insert a 2 × 2 table (header + one row, like the web) and open the grid editor on it. */
    fun insertTable() {
        if (!canFormat) return
        val rows = listOf(listOf("Column 1", "Column 2"), listOf("Text", "Text"))
        val blocks = rows.map { tableBlock(it) } + Block("", IntArray(0), Para())
        val (next, at) = insertBlocksAtCaret(blocks)
        apply(next, next.starts[(at + rows.size).coerceAtMost(next.paraCount - 1)])
        tableEdit = TableEdit(at, at + rows.size - 1, rows)
    }

    private fun tableBlock(cells: List<String>): Block {
        val t = cells.joinToString("\t") { it.replace('\t', ' ').replace('\n', ' ') }
        return Block(t, IntArray(t.length), Para(BlockType.TABLE))
    }

    /** Tap on a table row (or "Edit table"). */
    fun openTableEditor(para: Int) {
        if (!canFormat) return
        val run = doc.tableRun(para) ?: return
        tableEdit = TableEdit(run.first, run.last, doc.tableRows(run))
    }

    fun closeTableEditor() {
        tableEdit = null
    }

    fun saveTable(rows: List<List<String>>) {
        val st = tableEdit ?: return
        tableEdit = null
        if (rows.isEmpty()) {
            deleteTableAt(st.first)
            return
        }
        val clean = rows.map { r -> r.map { it.replace('\t', ' ').replace('\n', ' ').trim().take(200) } }
        if (clean == st.rows) return
        val next = doc.replaceParas(st.first, st.last + 1, clean.map { tableBlock(it) })
        apply(next, next.starts[(st.first + clean.size).coerceAtMost(next.paraCount - 1)])
    }

    fun deleteTableAt(para: Int) {
        if (!canFormat) return
        val run = doc.tableRun(para) ?: return
        tableEdit = null
        val next = doc.replaceParas(run.first, run.last + 1, emptyList())
        apply(next, next.starts[run.first.coerceAtMost(next.paraCount - 1)])
    }

    fun deleteTableAtCaret() = deleteTableAt(doc.paraIndexAt(body.selection.min))

    fun editTableAtCaret() = openTableEditor(doc.paraIndexAt(body.selection.min))

    // ─── code block actions (Part 5) ─────────

    /** What the context bar above the toolbar offers at the caret. */
    val contextKind: ContextKind
        get() {
            historyVersion.let { }
            if (!canFormat) return ContextKind.None
            val sel = body.selection
            return when {
                doc.paraAt(sel.min).type == BlockType.CODE -> ContextKind.Code
                doc.paraAt(sel.min).type == BlockType.TABLE -> ContextKind.Table
                doc.linkSpanAt(sel.min, sel.max) != null -> ContextKind.Link
                else -> ContextKind.None
            }
        }

    private fun codeRunAtCaret(): IntRange? = doc.codeRun(doc.paraIndexAt(body.selection.min))

    fun copyCodeBlock(setClipboard: (String) -> Unit) {
        val run = codeRunAtCaret() ?: return
        setClipboard(doc.textOf(run))
        showToast("Code copied", R.drawable.ic_fa_check)
    }

    /** "Jump to bottom": caret to the end of the code block's last line. */
    fun jumpToCodeEnd() {
        val run = codeRunAtCaret() ?: return
        pending = null
        body = TextFieldValue(doc.text, TextRange(doc.paraEnd(run.last)))
    }

    fun deleteCodeBlock() {
        if (!canFormat) return
        val run = codeRunAtCaret() ?: return
        val next = doc.replaceParas(run.first, run.last + 1, emptyList())
        apply(next, next.starts[run.first.coerceAtMost(next.paraCount - 1)])
    }

    // ─── links (Part 5) ──────────────────────

    var linkEdit by mutableStateOf<LinkEdit?>(null)
        private set

    /** Toolbar link button: removes the link under the caret, otherwise opens the popover (like the web). */
    fun onLinkButton() {
        if (!canFormat) return
        val sel = body.selection
        if (doc.linkSpanAt(sel.min, sel.max) != null) removeLink() else openLinkDialog()
    }

    fun openLinkDialog() {
        if (!canFormat) return
        val sel = body.selection
        val span = doc.linkSpanAt(sel.min, sel.max)
        linkEdit = if (span != null) {
            LinkEdit(
                start = span.first,
                end = span.last + 1,
                url = LinkTable.urlOf(Mark.linkId(doc.marks[span.first])).orEmpty(),
                text = doc.text.substring(span.first, span.last + 1),
                editing = true,
            )
        } else {
            val picked = doc.text.substring(sel.min, sel.max).trim().takeIf { '\n' !in it }.orEmpty()
            LinkEdit(sel.min, sel.max, "", picked, false)
        }
    }

    fun closeLinkDialog() {
        linkEdit = null
    }

    fun removeLink() {
        if (!canFormat) return
        val sel = body.selection
        val span = doc.linkSpanAt(sel.min, sel.max) ?: return
        apply(doc.setLink(span.first, span.last + 1, null), sel.end)
        showToast("Link removed", R.drawable.ic_fa_circle_check)
    }

    fun openLinkAtCaret() {
        val sel = body.selection
        val span = doc.linkSpanAt(sel.min, sel.max) ?: return
        val url = LinkTable.urlOf(Mark.linkId(doc.marks[span.first])) ?: return
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            showToast("Can't open this link", R.drawable.ic_fa_circle_exclamation)
        }
    }

    fun submitLink(rawUrl: String, rawLabel: String) {
        val st = linkEdit ?: return
        val url = normalizeUrl(rawUrl)
        if (url.isEmpty()) {
            showToast("Enter a URL", R.drawable.ic_fa_circle_exclamation)
            return
        }
        linkEdit = null
        val label = rawLabel.trim()
        when {
            st.editing -> apply(doc.setLink(st.start, st.end, url), body.selection.end)
            st.start != st.end && doc.text.substring(st.start, st.end).isNotBlank() ->
                apply(doc.setLink(st.start, st.end, url), st.end)
            else -> {
                val t = label.ifEmpty { url }.replace('\n', ' ')
                val newText = doc.text.substring(0, st.start) + t + doc.text.substring(st.end)
                val r = doc.edit(newText, null, false, st.start + t.length)
                apply(r.doc.setLink(st.start, st.start + t.length, url), st.start + t.length)
            }
        }
        showToast("Link inserted", R.drawable.ic_fa_globe)
    }

    /** Web `normalizeUrl`: http(s) / mailto / tel stay, anything else gets `https://`. */
    private fun normalizeUrl(raw: String): String {
        val v = raw.trim()
        if (v.isEmpty()) return ""
        if (Regex("^(https?:|mailto:|tel:)", RegexOption.IGNORE_CASE).containsMatchIn(v)) return v
        return "https://" + v.trimStart('/')
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
        if (!readMode) flush() // entering Reading mode saves what you have, like the web
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

        val text = body.text
        val titleText = title.text.trim()
        val rich = bodyEditable

        // Brand-new note that is still completely empty: don't litter the list with "Untitled".
        if (!persisted && titleText.isEmpty() && text.isBlank() && !note.pinned) {
            dirty = false
            saved = true
            return
        }

        val finalTitle = titleText.ifEmpty { "Untitled" }
        val html = if (rich) HtmlCodec.toHtml(doc) else note.content
        val hash = fingerprint(finalTitle, html)
        if (persisted && hash == lastHash) {
            dirty = false
            saved = true
            return
        }

        val updated = note.copy(
            title = finalTitle,
            content = html,
            markdown = if (rich) HtmlCodec.toMarkdown(doc) else note.markdown,
            wordCount = if (rich) countWords(text) else note.wordCount,
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

/** Link popover state: the range being linked ([start, end) — empty = insert), its URL and label. */
class LinkEdit(val start: Int, val end: Int, val url: String, val text: String, val editing: Boolean)

/** Table editor state: paragraph rows [first, last] and their cell texts. */
class TableEdit(val first: Int, val last: Int, val rows: List<List<String>>)

/** What the bar above the toolbar shows. */
enum class ContextKind { None, Code, Table, Link }
