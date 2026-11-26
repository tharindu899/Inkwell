package com.inkwell.notes.ui.screens

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
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inkwell.notes.R
import com.inkwell.notes.ui.components.DeleteRed
import com.inkwell.notes.ui.components.FaIcon
import com.inkwell.notes.ui.components.InkText
import com.inkwell.notes.ui.components.onAccent
import com.inkwell.notes.ui.components.tap
import com.inkwell.notes.ui.theme.Ink

/*
 * Editor Part 5 — the UI that sits around the text field for the "insert" features:
 *   EditorContextBar  ← the strip above the toolbar (code block: Copy / Jump to bottom / Delete,
 *                        table: Edit / Delete, link: Edit / Open / Remove)
 *   LinkDialog        ← `.link-popover`
 *   TableEditorSheet  ← the table grid (scrolls sideways; add / remove rows and columns)
 * Pure UI: the rules live in EditorController.
 */

// ─────────────────────────────────────────────
// Context bar
// ─────────────────────────────────────────────

@Composable
fun EditorContextBar(
    kind: ContextKind,
    onCopyCode: () -> Unit,
    onJumpCode: () -> Unit,
    onDeleteCode: () -> Unit,
    onEditTable: () -> Unit,
    onDeleteTable: () -> Unit,
    onEditLink: () -> Unit,
    onOpenLink: () -> Unit,
    onRemoveLink: () -> Unit,
) {
    if (kind == ContextKind.None) return
    val c = Ink.colors
    Column(Modifier.fillMaxWidth().background(c.bg)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            InkText(
                when (kind) {
                    ContextKind.Code -> "CODE"
                    ContextKind.Table -> "TABLE"
                    else -> "LINK"
                },
                size = 11.sp,
                weight = FontWeight.ExtraBold,
                color = c.text3,
                letterSpacing = 0.88.sp,
            )
            when (kind) {
                ContextKind.Code -> {
                    BarButton("Copy", onClick = onCopyCode)
                    BarButton("Jump to bottom", onClick = onJumpCode)
                    BarButton("Delete", danger = true, onClick = onDeleteCode)
                }
                ContextKind.Table -> {
                    BarButton("Edit table", onClick = onEditTable)
                    BarButton("Delete table", danger = true, onClick = onDeleteTable)
                }
                else -> {
                    BarButton("Edit link", onClick = onEditLink)
                    BarButton("Open", onClick = onOpenLink)
                    BarButton("Remove link", danger = true, onClick = onRemoveLink)
                }
            }
        }
    }
}

@Composable
private fun BarButton(label: String, danger: Boolean = false, onClick: () -> Unit) {
    val c = Ink.colors
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier
            .height(30.dp)
            .clip(shape)
            .background(c.bgCard)
            .border(1.dp, c.border, shape)
            .tap(onClick)
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (danger) FaIcon(R.drawable.ic_fa_trash_can, tint = DeleteRed, size = 11.dp)
        InkText(label, size = 12.sp, weight = FontWeight.Bold, color = if (danger) DeleteRed else c.text2)
    }
}

// ─────────────────────────────────────────────
// Link popover
// ─────────────────────────────────────────────

/** "Insert link" / "Edit link": optional text (when nothing is selected) + URL. Enter submits. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LinkDialog(
    state: LinkEdit,
    onSubmit: (url: String, text: String) -> Unit,
    onClose: () -> Unit,
) {
    val c = Ink.colors
    var url by remember { mutableStateOf(state.url) }
    var label by remember { mutableStateOf(state.text) }
    // text is only asked for when there is no selection to link
    val askText = !state.editing && state.start == state.end
    val urlFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { urlFocus.requestFocus() } }
    val keyboard = LocalSoftwareKeyboardController.current
    DisposableEffect(Unit) { onDispose { keyboard?.hide() } }

    ModalFrame(onDismiss = onClose) {
        InkText(
            if (state.editing) "Edit link" else "Insert link",
            family = Ink.serif,
            size = 20.sp,
            weight = FontWeight.Bold,
            lineHeight = 24.sp,
        )
        Spacer(Modifier.height(4.dp))
        InkText(
            if (askText) "Add the address and the text to show." else "The selected text becomes a link.",
            size = 12.sp,
            color = c.text2,
            lineHeight = 17.sp,
        )
        Spacer(Modifier.height(12.dp))
        if (askText) {
            FieldLabel("Text")
            Spacer(Modifier.height(7.dp))
            InputBox(value = label, onChange = { label = it }, placeholder = "Link text (optional)", ime = ImeAction.Next)
            Spacer(Modifier.height(12.dp))
        }
        FieldLabel("URL")
        Spacer(Modifier.height(7.dp))
        InputBox(
            value = url,
            onChange = { url = it },
            placeholder = "example.com",
            keyboard = KeyboardType.Uri,
            ime = ImeAction.Done,
            onDone = { onSubmit(url, label) },
            focus = urlFocus,
        )
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SheetButton("Cancel", c.bgCard, c.text2, c.border, Modifier.weight(1f), onClose)
            SheetButton(if (state.editing) "Update" else "Insert", c.accent, onAccent(), c.accent, Modifier.weight(1f)) {
                onSubmit(url, label)
            }
        }
    }
}

// ─────────────────────────────────────────────
// Table editor
// ─────────────────────────────────────────────

/**
 * Grid editor for a table. Cells are plain text; the first row is the header. "+ Row" / "+ Col" add after the
 * focused cell, "− Row" / "− Col" remove the focused one (a table keeps at least one row and one column).
 * The grid scrolls sideways, so wide tables stay readable. Nothing is applied until Save.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TableEditorSheet(
    state: TableEdit,
    onSave: (List<List<String>>) -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
) {
    val c = Ink.colors
    var rows by remember { mutableStateOf(state.rows.map { it.toList() }) }
    var fr by remember { mutableIntStateOf(0) }
    var fc by remember { mutableIntStateOf(0) }
    val cols = rows.firstOrNull()?.size ?: 1

    val keyboard = LocalSoftwareKeyboardController.current
    DisposableEffect(Unit) { onDispose { keyboard?.hide() } }

    fun setCell(r: Int, col: Int, v: String) {
        val clean = v.replace('\n', ' ').replace('\t', ' ').take(200)
        rows = rows.mapIndexed { ri, row ->
            if (ri != r) row else row.mapIndexed { ci, cell -> if (ci == col) clean else cell }
        }
    }

    // Swallows scrim taps: unsaved cell edits are only dropped by Cancel / Back.
    ModalFrame(onDismiss = null) {
        InkText("Edit table", family = Ink.serif, size = 20.sp, weight = FontWeight.Bold, lineHeight = 24.sp)
        Spacer(Modifier.height(4.dp))
        InkText(
            "Row ${fr + 1} · Column ${fc + 1}  —  the first row is the header.",
            size = 12.sp,
            color = c.text2,
            lineHeight = 17.sp,
        )
        Spacer(Modifier.height(12.dp))

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SheetChip("+ Row") {
                val at = (fr + 1).coerceAtMost(rows.size)
                rows = rows.toMutableList().apply { add(at, List(cols) { "" }) }
                fr = at
            }
            SheetChip("− Row", enabled = rows.size > 1) {
                rows = rows.filterIndexed { i, _ -> i != fr }
                fr = fr.coerceAtMost(rows.size - 1)
            }
            SheetChip("+ Column") {
                val at = (fc + 1).coerceAtMost(cols)
                rows = rows.map { r -> r.toMutableList().apply { add(at, "") } }
                fc = at
            }
            SheetChip("− Column", enabled = cols > 1) {
                rows = rows.map { r -> r.filterIndexed { i, _ -> i != fc } }
                fc = fc.coerceAtMost(cols - 2).coerceAtLeast(0)
            }
        }
        Spacer(Modifier.height(10.dp))

        val cellShape = RoundedCornerShape(10.dp)
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 280.dp)
                .verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            rows.forEachIndexed { r, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEachIndexed { col, cell ->
                        val focused = r == fr && col == fc
                        BasicTextField(
                            value = cell,
                            onValueChange = { setCell(r, col, it) },
                            singleLine = true,
                            textStyle = TextStyle(
                                color = c.text1,
                                fontSize = 13.sp,
                                fontFamily = Ink.sans,
                                fontWeight = if (r == 0) FontWeight.Bold else FontWeight.Normal,
                            ),
                            cursorBrush = SolidColor(c.accent),
                            modifier = Modifier
                                .width(CELL_WIDTH)
                                .onFocusChanged { if (it.isFocused) { fr = r; fc = col } },
                            decorationBox = { inner ->
                                Box(
                                    Modifier
                                        .width(CELL_WIDTH)
                                        .heightIn(min = 40.dp)
                                        .clip(cellShape)
                                        .background(if (r == 0) c.bgCard else c.bg)
                                        .border(if (focused) 1.5.dp else 1.dp, if (focused) c.accent else c.border, cellShape)
                                        .padding(horizontal = 10.dp),
                                    contentAlignment = Alignment.CenterStart,
                                ) { inner() }
                            },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SheetButton("Delete", c.bgCard, DeleteRed, c.border, Modifier.weight(1f), onDelete)
            SheetButton("Cancel", c.bgCard, c.text2, c.border, Modifier.weight(1f), onClose)
            SheetButton("Save", c.accent, onAccent(), c.accent, Modifier.weight(1f)) { onSave(rows) }
        }
    }
}

private val CELL_WIDTH = 124.dp

// ─────────────────────────────────────────────
// Shared bits
// ─────────────────────────────────────────────

/** Bottom card on a scrim, riding above the keyboard (same frame as the tag modal). [onDismiss] null = scrim doesn't close it. */
@Composable
private fun ModalFrame(onDismiss: (() -> Unit)?, content: @Composable () -> Unit) {
    val c = Ink.colors
    val cardShape = RoundedCornerShape(22.dp)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.44f))
            .tap { onDismiss?.invoke() }
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
                .tap { /* swallow taps so the scrim doesn't dismiss */ }
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
        ) { content() }
    }
}

@Composable
private fun FieldLabel(text: String) {
    InkText(text.uppercase(), size = 11.sp, weight = FontWeight.ExtraBold, color = Ink.colors.text3, letterSpacing = 0.88.sp)
}

@Composable
private fun InputBox(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    keyboard: KeyboardType = KeyboardType.Text,
    ime: ImeAction = ImeAction.Done,
    onDone: () -> Unit = {},
    focus: FocusRequester? = null,
) {
    val c = Ink.colors
    val shape = RoundedCornerShape(12.dp)
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = TextStyle(color = c.text1, fontSize = 13.sp, fontFamily = Ink.sans),
        cursorBrush = SolidColor(c.accent),
        keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = ime),
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
                if (value.isEmpty()) InkText(placeholder, size = 13.sp, color = c.text3)
                inner()
            }
        },
    )
}

@Composable
private fun SheetChip(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    val c = Ink.colors
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier
            .height(32.dp)
            .clip(shape)
            .background(c.bgCard)
            .border(1.dp, c.border, shape)
            .tap { if (enabled) onClick() }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        InkText(label, size = 12.sp, weight = FontWeight.Bold, color = if (enabled) c.text2 else c.text3)
    }
}

@Composable
private fun SheetButton(label: String, bg: Color, textColor: Color, border: Color, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier.heightIn(min = 42.dp).clip(shape).background(bg).border(1.dp, border, shape).tap(onClick),
        contentAlignment = Alignment.Center,
    ) {
        InkText(label, size = 14.sp, weight = FontWeight.Black, color = textColor)
    }
}
