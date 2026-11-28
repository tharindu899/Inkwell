package com.inkwell.notes.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inkwell.notes.data.InkStore
import com.inkwell.notes.model.Note
import com.inkwell.notes.ui.components.ConfirmDialog
import com.inkwell.notes.ui.components.InkButton
import com.inkwell.notes.ui.components.InkIconButton
import com.inkwell.notes.ui.components.InkSheet
import com.inkwell.notes.ui.components.InkTextField
import com.inkwell.notes.ui.components.SheetItem
import com.inkwell.notes.ui.components.notebookIcon
import com.inkwell.notes.ui.components.pressScale
import com.inkwell.notes.ui.theme.BodyFont
import com.inkwell.notes.ui.theme.DisplayFont
import com.inkwell.notes.ui.theme.Ink
import com.inkwell.notes.util.countWords
import com.inkwell.notes.util.formatRelative
import com.inkwell.notes.util.stripHtml

/**
 * TEMPORARY plain-text editor so you can create and edit notes with the real data layer.
 * Your rich Kotlin editor (parts 1-8) replaces the body field later; everything else here
 * (title, tags, notebook, pin, delete, autosave) already talks to InkStore the right way.
 *
 * Notes that contain web formatting (headings, lists, bold ...) show their body read-only,
 * so formatting is never destroyed by this plain editor.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(store: InkStore, noteId: String, notebookId: String?, onBack: () -> Unit) {
    val c = Ink.colors
    var note by remember(noteId) {
        mutableStateOf(store.noteById(noteId) ?: store.newNote(notebookId, store.pendingNewTag).also { store.pendingNewTag = null })
    }
    var body by remember(noteId) { mutableStateOf(htmlToPlain(note.content)) }
    val rich = remember(noteId) { isRichHtml(note.content) }
    var dirty by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var notebookSheet by remember { mutableStateOf(false) }
    var tagInput by remember { mutableStateOf("") }

    fun commit() {
        val blank = note.title.isBlank() && note.content.stripHtml().isBlank()
        if (blank && store.noteById(note.id) == null) return
        store.saveNote(note)
        dirty = false
    }

    fun change(transform: (Note) -> Note) {
        note = transform(note).copy(updatedAt = System.currentTimeMillis())
        dirty = true
        if (store.autosave) commit()
    }

    fun addTag() {
        val t = tagInput.trim().trimStart('#')
        if (t.isNotEmpty() && t !in note.tags) change { it.copy(tags = it.tags + t) }
        tagInput = ""
    }

    val goBack: () -> Unit = {
        if (dirty && !store.autosave) {
            confirmDiscard = true
        } else {
            onBack()
        }
    }
    BackHandler(enabled = dirty && !store.autosave) { confirmDiscard = true }

    val bodyStyle = TextStyle(
        fontFamily = if (store.serifBody) DisplayFont else BodyFont,
        fontSize = store.fontSize.sp.sp,
        lineHeight = (store.fontSize.sp * 1.55f).sp,
        color = c.text,
    )
    val notebook = store.notebook(note.notebookId)

    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().imePadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            InkIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", goBack)
            Box(Modifier.weight(1f))
            if (!store.autosave) InkButton("Save", { commit() })
            PinButton(note.pinned) {
                val next = (store.notes.filter { it.pinned && it.pinOrder != null }.maxOfOrNull { it.pinOrder ?: 0 } ?: -1) + 1
                change { it.copy(pinned = !it.pinned, pinOrder = if (!it.pinned) (it.pinOrder ?: next) else null) }
            }
            InkIconButton(Icons.Rounded.Delete, "Delete note", { confirmDelete = true })
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    Modifier.pressScale { notebookSheet = true }.clip(CircleShape).background(c.surface)
                        .border(1.dp, c.border, CircleShape).padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(notebookIcon(notebook?.icon ?: "book"), null, tint = c.subText, modifier = Modifier.size(15.dp))
                    Text(notebook?.name ?: "No notebook", color = c.text, style = MaterialTheme.typography.labelLarge)
                }
                Text(formatRelative(note.updatedAt), color = c.subText, style = MaterialTheme.typography.labelMedium)
            }

            BasicTextField(
                value = note.title,
                onValueChange = { v -> change { it.copy(title = v) } },
                textStyle = MaterialTheme.typography.headlineMedium.copy(color = c.text),
                cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    Box {
                        if (note.title.isEmpty()) {
                            Text("Title", color = c.subText, style = MaterialTheme.typography.headlineMedium)
                        }
                        inner()
                    }
                },
            )

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                note.tags.forEach { tag ->
                    val p = c.pastels[0]
                    Row(
                        Modifier.pressScale { change { it.copy(tags = it.tags - tag) } }.clip(CircleShape)
                            .background(p.container).padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text("#$tag", color = p.title, style = MaterialTheme.typography.labelLarge)
                        Icon(Icons.Rounded.Close, "Remove tag $tag", tint = p.body, modifier = Modifier.size(14.dp))
                    }
                }
            }
            InkTextField(tagInput, { if (it.length <= 30) tagInput = it }, "Add a tag and press done", onDone = { addTag() })

            if (rich) {
                val shape = RoundedCornerShape(18.dp)
                Row(
                    Modifier.fillMaxWidth().clip(shape).background(c.surface).border(1.dp, c.border, shape).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Rounded.Info, null, tint = c.subText, modifier = Modifier.size(18.dp))
                    Text(
                        "This note has rich formatting. The body is read-only here so nothing gets flattened.",
                        color = c.subText, style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Text(body, style = bodyStyle)
            } else {
                BasicTextField(
                    value = body,
                    onValueChange = { v ->
                        body = v
                        change { it.copy(content = plainToHtml(v)) }
                    },
                    textStyle = bodyStyle,
                    cursorBrush = SolidColor(c.accent),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        autoCorrectEnabled = store.spellcheck,
                    ),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 280.dp),
                    decorationBox = { inner ->
                        Box {
                            if (body.isEmpty()) Text("Start writing…", color = c.subText, style = bodyStyle)
                            inner()
                        }
                    },
                )
            }
        }

        Text(
            "${countWords(note.content)} words · ${if (dirty) "Unsaved changes" else "Saved"}",
            color = c.subText,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 10.dp),
        )
    }

    if (notebookSheet) {
        InkSheet("Notebook", onDismiss = { notebookSheet = false }) { closeThen ->
            SheetItem(Icons.Rounded.Close, "No notebook", { closeThen { change { it.copy(notebookId = null) } } })
            store.notebooks.forEach { nb ->
                SheetItem(notebookIcon(nb.icon), nb.name, { closeThen { change { it.copy(notebookId = nb.id) } } })
            }
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete this note?",
            text = "This note will be permanently removed.",
            confirmLabel = "Delete",
            onConfirm = {
                confirmDelete = false
                if (store.noteById(note.id) != null) store.deleteNote(note.id)
                onBack()
            },
            onDismiss = { confirmDelete = false },
        )
    }
    if (confirmDiscard) {
        ConfirmDialog(
            title = "Discard changes?",
            text = "Your unsaved changes will be lost.",
            confirmLabel = "Discard",
            onConfirm = { confirmDiscard = false; onBack() },
            onDismiss = { confirmDiscard = false },
        )
    }
}

@Composable
private fun PinButton(pinned: Boolean, onClick: () -> Unit) {
    val c = Ink.colors
    Box(
        Modifier.pressScale(onClick).size(44.dp).clip(CircleShape)
            .background(if (pinned) c.accent else c.surface)
            .border(1.dp, if (pinned) c.accent else c.border, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.PushPin, if (pinned) "Unpin" else "Pin", tint = if (pinned) c.onAccent else c.text, modifier = Modifier.size(20.dp))
    }
}

private fun htmlToPlain(html: String): String = html
    .replace(Regex("<p>\\s*<br\\s*/?>\\s*</p>", RegexOption.IGNORE_CASE), "<p></p>")
    .replace(Regex("</p>\\s*", RegexOption.IGNORE_CASE), "\n")
    .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
    .replace(Regex("<[^>]*>"), "")
    .replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">")
    .replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&")
    .trimEnd('\n')

private fun plainToHtml(text: String): String =
    text.split("\n").joinToString("") { line ->
        "<p>" + line.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;") + "</p>"
    }

/** True when the HTML has anything besides paragraphs and line breaks. */
private fun isRichHtml(html: String): Boolean =
    Regex("<(?!/?p>|br\\s*/?>)[a-zA-Z]", RegexOption.IGNORE_CASE).containsMatchIn(html)
