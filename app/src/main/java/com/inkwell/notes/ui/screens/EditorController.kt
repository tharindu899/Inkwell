package com.inkwell.notes.ui.screens

import android.content.Context
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
import com.inkwell.notes.editor.Doc
import com.inkwell.notes.editor.DocOps
import com.inkwell.notes.editor.EditorHistory
import com.inkwell.notes.editor.HtmlCodec
import com.inkwell.notes.editor.NoteText
import com.inkwell.notes.editor.SpanKind
import com.inkwell.notes.ui.Route
import com.inkwell.notes.ui.components.ToastState
import com.inkwell.notes.util.Iso
import com.inkwell.notes.util.countWords
import com.inkwell.notes.util.genId
import com.inkwell.notes.util.normalizeTagInput

/**
 * State + save logic of the Editor page (the non-visual half of `pages/Editor.jsx`).
 *
 * Created with `remember(route)` by [EditorScreen], so every open of a note is a fresh session.
 * Mirrors the web behaviour:
 *  - `saveNow`      → [save] (skips when nothing changed, catches storage errors → toast)
 *  - flush on pause / leave → [flush]
 *  - 2 s debounced auto-save → driven by [edits] from the screen (not while Reading mode is on)
 *
 * Part 2: the body is a [Doc] (one string + block / span tables) edited through ONE text field.
 * [body] always holds `doc.text` plus the caret; every text edit goes through [DocOps.applyTextEdit]
 * so formatting follows the typing. A note whose stored HTML holds something Part 2 can't write back
 * (tables, images, code, links, checklists …) stays read-only ([bodyEditable] = false) and its
 * `content` / `markdown` are never rewritten. Notes the editor opened but the user never touched keep
 * their original HTML byte for byte ([contentTouched]).
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

    /** False for notes with rich formatting — see class doc. */
    var bodyEditable by mutableStateOf(true)
        private set

    /** The formatted document behind [body] (text + blocks + spans). */
    var doc by mutableStateOf(Doc.EMPTY)
        private set

    /** Toolbar toggles set with a collapsed caret ("bold on" before typing). Cleared when the caret moves. */
    var pending by mutableStateOf<Map<SpanKind, Boolean>>(emptyMap())
        private set

    private val history = EditorHistory()
    private var historyRev by mutableIntStateOf(0)
    private var contentTouched = false

    val canUndo: Boolean get() { historyRev; return history.canUndo }
    val canRedo: Boolean get() { historyRev; return history.canRedo }

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

        val parsed = HtmlCodec.parse(n.content)
        // Editable when Part 2 understands the HTML. (Empty content + non-empty markdown = a source-only
        // note → keep it read-only rather than overwrite the markdown.)
        bodyEditable = parsed != null && (n.content.isNotBlank() || n.markdown.isBlank())
        if (bodyEditable && parsed != null) {
            doc = parsed
            body = TextFieldValue(parsed.text)
        } else {
            // Rich notes: read-only preview = the markdown source when there is one, else the HTML as text.
            val preview = if (n.markdown.isNotBlank()) n.markdown else NoteText.htmlToPlain(n.content)
            doc = Doc.plain(preview)
            body = TextFieldValue(preview)
        }
        pending = emptyMap()
        contentTouched = false
        lastHash = fingerprint(n.title, n.content)
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
            // Caret / selection / IME-composition only.
            if (v.selection != body.selection && pending.isNotEmpty()) pending = emptyMap()
            body = v
            return
        }
        val old = doc
        val next = DocOps.applyTextEdit(
            old,
            v.text,
            if (v.selection.collapsed) v.selection.start else -1,
            pending,
        )
        history.beforeTyping(System.currentTimeMillis(), snap(old))
        doc = next
        body = v
        pending = emptyMap()
        contentTouched = true
        historyRev++
        markDirty()
    }

    // ─── formatting (Part 2 toolbar) ─────────

    private val selStart: Int get() = minOf(body.selection.start, body.selection.end)
    private val selEnd: Int get() = maxOf(body.selection.start, body.selection.end)

    private fun snap(d: Doc = doc) = EditorHistory.Snap(d, body.selection.start, body.selection.end)

    val canFormat: Boolean get() = bodyEditable && !readMode

    /** Is [kind] on for the selection (or, with a bare caret, for what you'd type next)? */
    fun isInlineActive(kind: SpanKind): Boolean {
        if (selStart == selEnd) return pending[kind] ?: DocOps.coversBefore(doc, kind, selStart)
        return DocOps.covers(doc, kind, selStart, selEnd)
    }

    fun toggleInline(kind: SpanKind) {
        if (!canFormat) return
        if (selStart == selEnd) {
            val now = isInlineActive(kind)
            var p = pending + (kind to !now)
            if (!now && kind == SpanKind.SUP) p = p + (SpanKind.SUB to false)
            if (!now && kind == SpanKind.SUB) p = p + (SpanKind.SUP to false)
            pending = p
            return
        }
        applyDocOp { DocOps.toggleSpan(it, kind, selStart, selEnd) }
    }

    private fun linesInSelection() = doc.lineRange(selStart, selEnd)

    fun isBlockActive(type: BlockType): Boolean =
        linesInSelection().all { doc.blocks[it].type == type }

    fun isAlignActive(align: Align): Boolean =
        linesInSelection().all { doc.blocks[it].align == align }

    fun setBlock(type: BlockType) {
        if (!canFormat) return
        applyDocOp { DocOps.setBlockType(it, selStart, selEnd, type) }
    }

    fun setAlign(align: Align) {
        if (!canFormat) return
        applyDocOp { DocOps.setAlign(it, selStart, selEnd, align) }
    }

    fun indent(delta: Int) {
        if (!canFormat) return
        applyDocOp { DocOps.indent(it, selStart, selEnd, delta) }
    }

    fun clearFormat() {
        if (!canFormat) return
        pending = emptyMap()
        applyDocOp { DocOps.clearFormat(it, selStart, selEnd) }
    }

    /** Runs a formatting op; one undo step per action, nothing happens when the op changes nothing. */
    private fun applyDocOp(op: (Doc) -> Doc) {
        val old = doc
        val next = op(old)
        if (next == old) return
        history.beforeFormat(snap(old))
        doc = next
        contentTouched = true
        historyRev++
        markDirty()
    }

    fun undo() {
        if (!canFormat) return
        history.undo(snap())?.let { restore(it) }
    }

    fun redo() {
        if (!canFormat) return
        history.redo(snap())?.let { restore(it) }
    }

    private fun restore(s: EditorHistory.Snap) {
        doc = s.doc
        val len = s.doc.text.length
        body = TextFieldValue(s.doc.text, TextRange(s.selStart.coerceIn(0, len), s.selEnd.coerceIn(0, len)))
        pending = emptyMap()
        contentTouched = true
        historyRev++
        markDirty()
    }

    private fun markDirty() {
        dirty = true
        saved = false
        edits++
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

        // Brand-new note that is still completely empty: don't litter the list with "Untitled".
        if (!persisted && titleText.isEmpty() && text.isBlank() && !note.pinned) {
            dirty = false
            saved = true
            return
        }

        val finalTitle = titleText.ifEmpty { "Untitled" }
        // Untouched notes keep their stored HTML exactly (a web note opened + pinned stays byte-identical).
        val rewrite = bodyEditable && contentTouched
        val html = if (rewrite) HtmlCodec.toHtml(doc) else note.content
        val hash = fingerprint(finalTitle, html)
        if (persisted && hash == lastHash) {
            dirty = false
            saved = true
            return
        }

        val updated = note.copy(
            title = finalTitle,
            content = html,
            markdown = if (rewrite) HtmlCodec.toMarkdown(doc) else note.markdown,
            wordCount = if (rewrite) countWords(text) else note.wordCount,
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

    /** Everything a save would write; equal fingerprint = nothing to save (the web `saveHash`). */
    private fun fingerprint(title: String, content: String): String =
        "$title\u0000$content\u0000${note.pinned}\u0000${note.notebookId}\u0000${note.tags}"
}
