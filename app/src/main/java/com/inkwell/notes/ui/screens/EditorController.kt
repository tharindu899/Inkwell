package com.inkwell.notes.ui.screens

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
import com.inkwell.notes.editor.Mark
import com.inkwell.notes.editor.RichDoc
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
 * Part 2: the body is a [RichDoc] (text + inline marks + per-line block attributes). The text field
 * edits `body.text`; every change is fed through [RichDoc.edit] so formatting follows the text.
 * A note whose stored HTML uses something [HtmlCodec] can't represent yet (tables, code blocks,
 * checklists, images, links …) is shown read-only ([bodyEditable] = false) and its `content` /
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
            )
        }

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
        recordBefore(KIND_TYPING)
        val result = doc.edit(v.text, pending)
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
        lastKind = 0
        historyVersion++
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
    }

    /** Everything a save would write; equal fingerprint = nothing to save (the web `saveHash`). */
    private fun fingerprint(title: String, content: String): String =
        "$title\u0000$content\u0000${note.pinned}\u0000${note.notebookId}\u0000${note.tags}"
}
