package com.inkwell.notes.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inkwell.notes.R
import com.inkwell.notes.data.model.Notebook
import com.inkwell.notes.editor.TableData
import com.inkwell.notes.ui.components.DeleteRed
import com.inkwell.notes.ui.components.onAccent
import com.inkwell.notes.ui.components.ContextSheet
import com.inkwell.notes.ui.components.FaIcon
import com.inkwell.notes.ui.components.InkText
import com.inkwell.notes.ui.components.SheetDivider
import com.inkwell.notes.ui.components.notebookColor
import com.inkwell.notes.ui.components.tap
import com.inkwell.notes.ui.theme.Ink

/*
 * Editor Part 4 — the two pickers behind the tags / notebook rows of the bottom panel.
 *   NotebookPickerSheet  ← `.nb-picker-bottom` ("Move to Notebook")
 *   TagManageModal       ← `.tag-manage-modal` ("Manage tags")
 * Both are pure UI: state and rules live in EditorController.
 */

// ─────────────────────────────────────────────
// Notebook picker
// ─────────────────────────────────────────────

/** Bottom sheet: "None" + every notebook (colour dot, selected one in accent) + "Create notebook". */
@Composable
fun NotebookPickerSheet(
    notebooks: List<Notebook>,
    selectedId: String?,
    onPick: (String?) -> Unit,
    onCreate: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = Ink.colors
    ContextSheet(title = "Move to Notebook", onDismiss = onDismiss) {
        // Long notebook lists scroll inside the sheet instead of pushing it off screen.
        Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
            PickerRow(name = "None", dot = c.text3, selected = selectedId == null) { onPick(null) }
            notebooks.forEach { nb ->
                PickerRow(
                    name = nb.name,
                    dot = notebookColor(nb.color, c.accent),
                    selected = nb.id == selectedId,
                ) { onPick(nb.id) }
            }
        }
        SheetDivider()
        CreateNotebookButton(onCreate)
    }
}

/** `.nb-picker-item` */
@Composable
private fun PickerRow(name: String, dot: Color, selected: Boolean, onClick: () -> Unit) {
    val c = Ink.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .tap(onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(dot))
        InkText(
            name,
            modifier = Modifier.weight(1f),
            size = 14.sp,
            weight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) c.accent else c.text1,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** `.nb-create-btn` — 34dp accent tile with a plus + "Create notebook". */
@Composable
private fun CreateNotebookButton(onClick: () -> Unit) {
    val c = Ink.colors
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp)
            .clip(shape)
            .background(c.bgCard)
            .border(1.dp, c.border, shape)
            .tap(onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(12.dp)).background(c.accent),
            contentAlignment = Alignment.Center,
        ) {
            FaIcon(R.drawable.ic_fa_plus, tint = Color.White, size = 14.dp)
        }
        InkText("Create notebook", size = 14.sp, weight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
    }
}

// ─────────────────────────────────────────────
// Tag manager
// ─────────────────────────────────────────────

/**
 * "Manage tags": current tags (tap a chip to remove), an input (Enter / comma adds, Backspace on an empty
 * input removes the last tag) and "Saved tags" quick chips. Both chip rows scroll sideways like the web's
 * v45 compact layout. Rides above the keyboard.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TagManageModal(
    tags: List<String>,
    saved: List<String>,
    input: String,
    onInput: (String) -> Unit,
    onCommit: () -> Unit,
    onBackspaceEmpty: () -> Unit,
    onRemove: (String) -> Unit,
    onPickSaved: (String) -> Unit,
    onAddOrClose: () -> Unit,
    onClose: () -> Unit,
) {
    val c = Ink.colors
    val cardShape = RoundedCornerShape(22.dp)
    val inputShape = RoundedCornerShape(12.dp)
    var focused by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } } // autoFocus

    // Closing the modal must also drop the keyboard it opened.
    val keyboard = LocalSoftwareKeyboardController.current
    DisposableEffect(Unit) { onDispose { keyboard?.hide() } }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.44f))
            .tap(onClose)
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
            .padding(start = 13.dp, end = 13.dp, bottom = 14.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .widthIn(max = 360.dp)
                .fillMaxWidth()
                .clip(cardShape)
                .background(c.bg) // web: var(--surface)
                .border(1.dp, c.border, cardShape)
                .tap { /* swallow taps so the scrim doesn't dismiss */ }
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
        ) {
            InkText("Manage tags", family = Ink.serif, size = 20.sp, weight = FontWeight.Bold, lineHeight = 24.sp)
            Spacer(Modifier.height(4.dp))
            InkText(
                "Add, remove, and reuse tags without covering the editor footer.",
                size = 12.sp,
                color = c.text2,
                lineHeight = 17.sp,
            )
            Spacer(Modifier.height(12.dp))

            TagLabel("Current tags")
            Spacer(Modifier.height(7.dp))
            if (tags.isEmpty()) {
                val emptyShape = RoundedCornerShape(14.dp)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(emptyShape)
                        .background(c.bgCard)
                        .border(1.dp, c.border, emptyShape)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    InkText("No tags added yet.", size = 12.sp, color = c.text3)
                }
            } else {
                ChipRow {
                    tags.forEach { t ->
                        TagChip(
                            text = t,
                            color = c.accent,
                            background = c.accentDim,
                            border = null,
                            trailing = R.drawable.ic_fa_xmark,
                        ) { onRemove(t) }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            TagLabel("Add tag")
            Spacer(Modifier.height(7.dp))
            BasicTextField(
                value = input,
                onValueChange = onInput,
                singleLine = true,
                textStyle = TextStyle(color = c.text1, fontSize = 13.sp, fontFamily = Ink.sans),
                cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onCommit() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
                    .onFocusChanged { focused = it.isFocused }
                    // Backspace on an empty field removes the last tag (web `tagKeydown`).
                    .onPreviewKeyEvent { e ->
                        if (e.type == KeyEventType.KeyDown && e.key == Key.Backspace && input.isEmpty()) {
                            onBackspaceEmpty()
                            true
                        } else false
                    },
                decorationBox = { inner ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 40.dp)
                            .clip(inputShape)
                            .background(c.bgCard)
                            .border(1.5.dp, if (focused) c.accent else c.border, inputShape)
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (input.isEmpty()) InkText("e.g. code, github…", size = 13.sp, color = c.text3)
                        inner()
                    }
                },
            )

            if (saved.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                TagLabel("Saved tags")
                Spacer(Modifier.height(7.dp))
                ChipRow {
                    saved.forEach { t ->
                        TagChip(
                            text = t,
                            color = c.text2,
                            background = c.bgCard,
                            border = c.border,
                            leading = R.drawable.ic_fa_tag,
                            leadingTint = c.accent,
                        ) { onPickSaved(t) }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TagButton("Close", c.bgCard, c.text2, c.border, Modifier.weight(1f), onClose)
                TagButton("Add", c.accent, com.inkwell.notes.ui.components.onAccent(), c.accent, Modifier.weight(1f), onAddOrClose)
            }
        }
    }
}

/** `.modal-label` (11px variant used inside the tag modal). */
@Composable
private fun TagLabel(text: String) {
    InkText(text.uppercase(), size = 11.sp, weight = FontWeight.ExtraBold, color = Ink.colors.text3, letterSpacing = 0.88.sp)
}

/** `.tag-manage-current` / `.saved-tags-list.in-modal` — one line, scrolls sideways, no scrollbar. */
@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 2.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

/** Round-square compact chip: 28dp high, radius 10, max 118dp wide, 11sp / 800. */
@Composable
private fun TagChip(
    text: String,
    color: Color,
    background: Color,
    border: Color?,
    leading: Int? = null,
    leadingTint: Color = color,
    trailing: Int? = null,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier
            .widthIn(max = 118.dp)
            .height(28.dp)
            .clip(shape)
            .background(background)
            .then(if (border != null) Modifier.border(1.dp, border, shape) else Modifier)
            .tap(onClick)
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (leading != null) FaIcon(leading, tint = leadingTint, size = 9.dp)
        InkText(
            text,
            modifier = Modifier.weight(1f, fill = false),
            size = 11.sp,
            weight = FontWeight.ExtraBold,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (trailing != null) FaIcon(trailing, tint = color, size = 9.dp)
    }
}

/** `.modal-actions-2col .btn` — 42dp, radius 14. */
@Composable
private fun TagButton(label: String, bg: Color, textColor: Color, border: Color, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier.heightIn(min = 42.dp).clip(shape).background(bg).border(1.dp, border, shape).tap(onClick),
        contentAlignment = Alignment.Center,
    ) {
        InkText(label, size = 14.sp, weight = FontWeight.Black, color = textColor)
    }
}

// ─────────────────────────────────────────────
// Part 5 — link popover + table sheet
// ─────────────────────────────────────────────

/** `.link-popover`: text + URL; Enter on the URL inserts. Rides above the keyboard. */
@Composable
fun LinkModal(
    text: String,
    url: String,
    onText: (String) -> Unit,
    onUrl: (String) -> Unit,
    onSubmit: () -> Unit,
    onClose: () -> Unit,
) {
    val c = Ink.colors
    val cardShape = RoundedCornerShape(22.dp)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val keyboard = LocalSoftwareKeyboardController.current
    DisposableEffect(Unit) { onDispose { keyboard?.hide() } }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.44f))
            .tap(onClose)
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
            .padding(start = 13.dp, end = 13.dp, bottom = 14.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .widthIn(max = 360.dp)
                .fillMaxWidth()
                .clip(cardShape)
                .background(c.bg)
                .border(1.dp, c.border, cardShape)
                .tap { }
                .padding(18.dp),
        ) {
            InkText("Insert link", family = Ink.serif, size = 20.sp, weight = FontWeight.Bold, lineHeight = 24.sp)
            Spacer(Modifier.height(12.dp))
            SheetLabel("Text")
            Spacer(Modifier.height(7.dp))
            SheetField(text, onText, "Link text (optional)", ImeAction.Next, KeyboardType.Text, null, onSubmit)
            Spacer(Modifier.height(12.dp))
            SheetLabel("URL")
            Spacer(Modifier.height(7.dp))
            SheetField(url, onUrl, "example.com", ImeAction.Done, KeyboardType.Uri, focus, onSubmit)
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TagButton("Cancel", c.bgCard, c.text2, c.border, Modifier.weight(1f), onClose)
                TagButton("Insert", c.accent, onAccent(), c.accent, Modifier.weight(1f), onSubmit)
            }
        }
    }
}

@Composable
private fun SheetLabel(text: String) {
    InkText(text.uppercase(), size = 11.sp, weight = FontWeight.ExtraBold, color = Ink.colors.text3, letterSpacing = 0.88.sp)
}

@Composable
private fun SheetField(
    value: String,
    onValue: (String) -> Unit,
    hint: String,
    ime: ImeAction,
    keyboardType: KeyboardType,
    focus: FocusRequester?,
    onDone: () -> Unit,
) {
    val c = Ink.colors
    val shape = RoundedCornerShape(12.dp)
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value = value,
        onValueChange = onValue,
        singleLine = true,
        textStyle = TextStyle(color = c.text1, fontSize = 13.sp, fontFamily = Ink.sans),
        cursorBrush = SolidColor(c.accent),
        keyboardOptions = KeyboardOptions(imeAction = ime, keyboardType = keyboardType),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focus != null) Modifier.focusRequester(focus) else Modifier)
            .onFocusChanged { focused = it.isFocused },
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 40.dp)
                    .clip(shape)
                    .background(c.bgCard)
                    .border(1.5.dp, if (focused) c.accent else c.border, shape)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) InkText(hint, size = 13.sp, color = c.text3)
                inner()
            }
        },
    )
}

/**
 * Table sheet: every cell is a text field in a grid that scrolls sideways (and up / down). Row / column buttons act on
 * the cell you last touched. Closing the sheet (Done, scrim, back) hands the edited table back; "Delete table" removes it.
 */
@Composable
fun TableEditModal(
    initial: TableData,
    onClose: (TableData) -> Unit,
    onDelete: () -> Unit,
) {
    val c = Ink.colors
    val cardShape = RoundedCornerShape(22.dp)
    var draft by remember { mutableStateOf(initial.normalized()) }
    var row by remember { mutableIntStateOf(0) }
    var col by remember { mutableIntStateOf(0) }
    val keyboard = LocalSoftwareKeyboardController.current
    DisposableEffect(Unit) { onDispose { keyboard?.hide() } }
    BackHandler { onClose(draft) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.44f))
            .tap { onClose(draft) }
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
            .padding(start = 13.dp, end = 13.dp, bottom = 14.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .clip(cardShape)
                .background(c.bg)
                .border(1.dp, c.border, cardShape)
                .tap { }
                .padding(18.dp),
        ) {
            InkText("Edit table", family = Ink.serif, size = 20.sp, weight = FontWeight.Bold, lineHeight = 24.sp)
            Spacer(Modifier.height(4.dp))
            InkText(
                "Tap a cell to edit it. Row / column buttons use the cell you touched last.",
                size = 12.sp,
                color = c.text2,
                lineHeight = 17.sp,
            )
            Spacer(Modifier.height(12.dp))

            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 260.dp)
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState()),
            ) {
                for (r in 0 until draft.rowCount) {
                    Row {
                        for (cc in 0 until draft.colCount) {
                            TableCell(
                                value = draft.cell(r, cc),
                                header = draft.header && r == 0,
                                active = r == row && cc == col,
                                onFocus = { row = r; col = cc },
                                onValue = { draft = draft.withCell(r, cc, it) },
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                TableAction("+ Row", c.accent) { draft = draft.addRow(row); row = (row + 1).coerceAtMost(draft.rowCount - 1) }
                TableAction("+ Column", c.accent) { draft = draft.addColumn(col); col = (col + 1).coerceAtMost(draft.colCount - 1) }
                TableAction("− Row", c.text2) { draft = draft.removeRow(row); row = row.coerceAtMost(draft.rowCount - 1) }
                TableAction("− Column", c.text2) { draft = draft.removeColumn(col); col = col.coerceAtMost(draft.colCount - 1) }
                TableAction(if (draft.header) "Header: on" else "Header: off", c.text2) { draft = draft.copy(header = !draft.header) }
            }

            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TagButton("Delete table", DeleteRed.copy(alpha = 0.12f), DeleteRed, DeleteRed.copy(alpha = 0.4f), Modifier.weight(1f), onDelete)
                TagButton("Done", c.accent, onAccent(), c.accent, Modifier.weight(1f)) { onClose(draft) }
            }
        }
    }
}

@Composable
private fun TableCell(value: String, header: Boolean, active: Boolean, onFocus: () -> Unit, onValue: (String) -> Unit) {
    val c = Ink.colors
    BasicTextField(
        value = value,
        onValueChange = onValue,
        singleLine = true,
        textStyle = TextStyle(
            color = c.text1,
            fontSize = 13.sp,
            fontFamily = Ink.sans,
            fontWeight = if (header) FontWeight.Bold else FontWeight.Normal,
        ),
        cursorBrush = SolidColor(c.accent),
        modifier = Modifier.onFocusChanged { if (it.isFocused) onFocus() },
        decorationBox = { inner ->
            Box(
                Modifier
                    .width(124.dp)
                    .height(40.dp)
                    .background(if (header) c.accentDim else c.bgCard)
                    .border(if (active) 1.5.dp else 1.dp, if (active) c.accent else c.border)
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart,
            ) { inner() }
        },
    )
}

@Composable
private fun TableAction(label: String, color: Color, onClick: () -> Unit) {
    val c = Ink.colors
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier.height(32.dp).clip(shape).background(c.bgCard).border(1.dp, c.border, shape).tap(onClick).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) { InkText(label, size = 12.sp, weight = FontWeight.ExtraBold, color = color, maxLines = 1) }
}


// ─────────────────────────────────────────────
// Part 5 — code block editor
// ─────────────────────────────────────────────

/** Characters that are slow to find on a phone keyboard; one tap puts them at the caret. */
private val CODE_KEYS = listOf("{", "}", "(", ")", "[", "]", "<", ">", ";", "=", "\"", "'", "/", "\\", "|", "_", "#", "$", "`")

/**
 * The code editor sheet: monospace, no wrapping (long lines scroll sideways), GitHub syntax colours while you type.
 * Done (or Back / tapping outside) stores the text; an empty text removes the block.
 */
@Composable
fun CodeEditModal(
    initial: String,
    onClose: (String) -> Unit,
    onDelete: () -> Unit,
    onCopy: (String) -> Unit,
) {
    val c = Ink.colors
    val pal = remember(c.isDark) { com.inkwell.notes.editor.CodePalette.of(c.isDark) }
    val cardShape = RoundedCornerShape(22.dp)
    val boxShape = RoundedCornerShape(12.dp)
    var value by remember {
        mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(initial, androidx.compose.ui.text.TextRange(initial.length)))
    }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    DisposableEffect(Unit) { onDispose { keyboard?.hide() } }
    BackHandler { onClose(value.text) }

    val highlight = remember(pal) {
        androidx.compose.ui.text.input.VisualTransformation { t ->
            androidx.compose.ui.text.input.TransformedText(
                com.inkwell.notes.editor.CodeHighlighter.highlight(t.text, pal),
                androidx.compose.ui.text.input.OffsetMapping.Identity,
            )
        }
    }

    fun insert(s: String) {
        val sel = value.selection
        val t = value.text.replaceRange(sel.min, sel.max, s)
        value = androidx.compose.ui.text.input.TextFieldValue(t, androidx.compose.ui.text.TextRange(sel.min + s.length))
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.44f))
            .tap { onClose(value.text) }
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
            .padding(start = 13.dp, end = 13.dp, bottom = 14.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .clip(cardShape)
                .background(c.bg)
                .border(1.dp, c.border, cardShape)
                .tap { }
                .padding(18.dp),
        ) {
            InkText("Edit code", family = Ink.serif, size = 20.sp, weight = FontWeight.Bold, lineHeight = 24.sp)
            Spacer(Modifier.height(4.dp))
            InkText(
                "Long lines stay on one line — scroll sideways to read them.",
                size = 12.sp,
                color = c.text2,
                lineHeight = 17.sp,
            )
            Spacer(Modifier.height(12.dp))

            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 140.dp, max = 300.dp)
                    .clip(boxShape)
                    .background(pal.bg)
                    .border(1.dp, pal.border, boxShape)
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState()),
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier
                        .focusRequester(focus)
                        .widthIn(min = 300.dp)
                        .heightIn(min = 140.dp)
                        .padding(12.dp),
                    textStyle = TextStyle(
                        color = pal.text,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    ),
                    cursorBrush = SolidColor(c.accent),
                    visualTransformation = highlight,
                    keyboardOptions = KeyboardOptions(
                        capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                    ),
                )
            }

            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                TableAction("Tab", c.accent) { insert("  ") }
                TableAction("Copy", c.text2) { onCopy(value.text) }
                for (k in CODE_KEYS) TableAction(k, c.text1) { insert(k) }
            }

            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TagButton("Delete block", DeleteRed.copy(alpha = 0.12f), DeleteRed, DeleteRed.copy(alpha = 0.4f), Modifier.weight(1f), onDelete)
                TagButton("Done", c.accent, onAccent(), c.accent, Modifier.weight(1f)) { onClose(value.text) }
            }
        }
    }
}
