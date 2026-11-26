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
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inkwell.notes.R
import com.inkwell.notes.data.model.Notebook
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
