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
import com.inkwell.notes.editor.EditResult
import com.inkwell.notes.editor.HtmlCodec
import com.inkwell.notes.editor.Mark
import com.inkwell.notes.editor.Para
import com.inkwell.notes.editor.PasteCodec
import com.inkwell.notes.editor.RichDoc
import com.inkwell.notes.editor.TableData
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
 * Part 5: inline code, code-block actions (Copy / Bottom / Delete), tables (edited in a sheet), link popover,
 * divider, date/time insert and paste handling (markdown / HTML / code detection, sanitising).
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
 * A note whose stored HTML uses something [HtmlCodec] can't represent yet (images, formatted table cells …)
 * is shown read-only ([bodyEditable] = false) and its `content` /
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
                inLink = doc.linkAt(sel.min, sel.max) != null,
                inCodeBlock = bodyEditable && doc.codeBlockAt(sel.min) != null,
            )
        }

    // ─── Part 5 state ────────────────────────

    /** Link popover (web `linkPopover`). */
    var linkOpen by mutableStateOf(false)
        private set
    var linkText by mutableStateOf("")
        private set
    var linkUrl by mutableStateOf("")
        private set
    private var linkSel = TextRange.Zero

    /** Paragraph index of the table open in the table sheet, or null. */
    var tableEditing by mutableStateOf<Int?>(null)
        private set

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

        // Paste: a big chunk of text that looks like markdown / HTML / code is turned into real blocks.
        if (markdownShortcuts && caret != null) {
            val paste = doc.pasteOf(v.text, caret)
            if (paste != null && paste.text.length >= PASTE_MIN && doc.paraAt(paste.start).type != BlockType.CODE) {
                val res = PasteCodec.analyze(paste.text)
                if (res != null) {
                    recordBefore(KIND_FORMAT)
                    val r = doc.splice(paste.start, paste.end, res.doc)
                    applyResult(r)
                    if (res.title != null && PasteCodec.isDefaultTitle(title.text)) title = TextFieldValue(res.title)
                    showToast(
                        when (res.kind) {
                            PasteCodec.Kind.HTML -> "Formatted paste rendered"
                            PasteCodec.Kind.MARKDOWN -> "Markdown pasted"
                            PasteCodec.Kind.CODE -> "Pasted as code block"
                        },
                        if (res.kind == PasteCodec.Kind.CODE) R.drawable.ic_fa_code else R.drawable.ic_fa_note_sticky,
                    )
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

    // ─── inline code · code blocks (Part 5) ──

    /** Code-block button: turns the selected lines into a code block (or back to paragraphs). */
    fun toggleCodeBlock() = setBlock(BlockType.CODE)

    /** Copy action of the code bar: the whole block under the caret goes to the clipboard. */
    fun copyCodeBlock() {
        val r = doc.codeBlockAt(body.selection.min) ?: return
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        runCatching { cm.setPrimaryClip(ClipData.newPlainText("code", doc.codeBlockText(r))) }
        showToast("Code copied", R.drawable.ic_fa_check)
    }

    /** "Bottom": caret to the end of the code block (the screen scrolls it into view). */
    fun codeBlockToBottom() {
        val r = doc.codeBlockAt(body.selection.min) ?: return
        val end = doc.codeBlockEnd(r)
        body = body.copy(selection = TextRange(end))
        pending = null
    }

    fun deleteCodeBlock() {
        if (!canFormat) return
        val r = doc.codeBlockAt(body.selection.min) ?: return
        recordBefore(KIND_FORMAT)
        applyResult(doc.deleteCodeBlock(r))
    }

    // ─── divider · date/time (Part 5) ────────

    fun insertDivider() {
        if (!canFormat) return
        recordBefore(KIND_FORMAT)
        applyResult(doc.insertObject(body.selection.min, Para(type = BlockType.HR)))
    }

    fun insertDateTime() {
        if (!canFormat) return
        val sel = body.selection
        if (doc.paraAt(sel.min).type.isObject) return
        val text = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date())
        val marks = (pending ?: 0) and Mark.LINK_MASK.inv()
        recordBefore(KIND_FORMAT)
        applyResult(doc.replace(sel.min, sel.max, text, marks))
    }

    // ─── tables (Part 5) ─────────────────────

    fun insertTable() {
        if (!canFormat) return
        recordBefore(KIND_FORMAT)
        applyResult(doc.insertObject(body.selection.min, Para(type = BlockType.TABLE, table = TableData.starter())))
    }

    /** Tap on a table in the body → table sheet. */
    fun openTable(para: Int) {
        if (!canFormat || doc.paras.getOrNull(para)?.type != BlockType.TABLE) return
        tableEditing = para
    }

    /** Sheet closed: the edited grid replaces the table (one undo step, only when something changed). */
    fun closeTable(data: TableData) {
        val i = tableEditing ?: return
        tableEditing = null
        val n = data.normalized()
        if (doc.paras.getOrNull(i)?.table == n) return
        recordBefore(KIND_FORMAT)
        doc = doc.setTable(i, n)
        markDirty()
    }

    fun deleteTable() {
        val i = tableEditing ?: return
        tableEditing = null
        recordBefore(KIND_FORMAT)
        applyResult(doc.deleteObject(i))
    }

    // ─── links (Part 5) ──────────────────────

    /** Link button: on a link it removes the link (like the web), otherwise opens the popover. */
    fun onLinkButton() {
        if (!canFormat) return
        val sel = body.selection
        val link = doc.linkAt(sel.min, sel.max)
        if (link != null) {
            recordBefore(KIND_FORMAT)
            doc = doc.removeLink(link.start, link.end)
            markDirty()
            showToast("Link removed", R.drawable.ic_fa_xmark)
            return
        }
        if (doc.paraAt(sel.min).type.isObject) return
        linkSel = sel
        val picked = if (sel.collapsed) "" else doc.text.substring(sel.min, sel.max)
        linkText = if ('\n' in picked) "" else picked.trim()
        linkUrl = ""
        linkOpen = true
    }

    fun onLinkTextChange(v: String) { linkText = v }

    fun onLinkUrlChange(v: String) { linkUrl = v }

    fun closeLink() {
        linkOpen = false
        linkText = ""
        linkUrl = ""
    }

    /** Insert: a selection becomes the link; with no selection the label (or the URL) is inserted as a link. */
    fun submitLink() {
        val url = normalizeUrl(linkUrl)
        if (url.isEmpty()) return
        val label = linkText.trim()
        val len = doc.text.length
        val a = linkSel.min.coerceIn(0, len)
        val b = linkSel.max.coerceIn(0, len)
        closeLink()
        if (!canFormat) return
        recordBefore(KIND_FORMAT)
        if (a != b) {
            doc = doc.setLink(a, b, url)
            body = body.copy(selection = TextRange(b))
            markDirty()
        } else {
            val text = label.ifEmpty { url }
            val r = doc.replace(a, a, text, (pending ?: 0) and Mark.LINK_MASK.inv())
            applyResult(EditResult(r.doc.setLink(a, a + text.replace('\n', ' ').length, url), r.caret))
        }
        showToast("Link inserted", R.drawable.ic_fa_check)
    }

    /** Reading mode: URL of the link whose character index is [offset], when it is safe to open. */
    fun linkUrlAt(offset: Int): String? {
        val o = offset.coerceIn(0, doc.text.length)
        val url = (doc.linkAt(o + 1, o + 1) ?: doc.linkAt(o, o))?.url ?: return null
        return if (url.startsWith("http://", true) || url.startsWith("https://", true) ||
            url.startsWith("mailto:", true) || url.startsWith("tel:", true)
        ) url else null
    }

    private fun normalizeUrl(raw: String): String {
        val v = raw.trim()
        if (v.isEmpty()) return ""
        if (Regex("^(https?:|mailto:|tel:)", RegexOption.IGNORE_CASE).containsMatchIn(v)) return v
        return "https://" + v.trimStart('/')
    }

    /** Puts an edit result into the document and moves the caret where it says. */
    private fun applyResult(r: EditResult) {
        doc = r.doc
        val c = (r.caret ?: body.selection.start).coerceIn(0, doc.text.length)
        body = TextFieldValue(doc.text, TextRange(c))
        pending = null
        markDirty()
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
        const val PASTE_MIN = 8
    }

    /** Everything a save would write; equal fingerprint = nothing to save (the web `saveHash`). */
    private fun fingerprint(title: String, content: String): String =
        "$title\u0000$content\u0000${note.pinned}\u0000${note.notebookId}\u0000${note.tags}"
}
