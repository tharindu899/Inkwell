package com.inkwell.notes.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inkwell.notes.ui.components.DeleteRed
import com.inkwell.notes.ui.components.FaIcon
import com.inkwell.notes.ui.components.InkText
import com.inkwell.notes.ui.components.NOTEBOOK_COLOR_CHOICES
import com.inkwell.notes.ui.components.NOTEBOOK_ICON_CHOICES
import com.inkwell.notes.ui.components.notebookColor
import com.inkwell.notes.ui.components.notebookIconRes
import com.inkwell.notes.ui.components.onAccent
import com.inkwell.notes.ui.components.tap
import com.inkwell.notes.data.model.Notebook
import com.inkwell.notes.ui.theme.Ink

/** Bottom-anchored card over a dim scrim — `.modal-overlay.show` + `.modal`. */
@Composable
internal fun ModalCard(
    onDismiss: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val c = Ink.colors
    val shape = RoundedCornerShape(28.dp)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.44f))
            .tap(onDismiss)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
            .padding(start = 12.dp, end = 12.dp, top = 56.dp, bottom = 14.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .clip(shape)
                .background(c.bg) // web: var(--surface)
                .border(1.dp, c.border, shape)
                .tap { /* swallow taps so the scrim doesn't dismiss */ }
                .verticalScroll(rememberScrollState())
                .padding(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 20.dp),
            content = content,
        )
    }
}

@Composable
internal fun ModalHead(title: String, sub: String) {
    InkText(title, family = Ink.serif, size = 21.sp, weight = FontWeight.Bold, lineHeight = 24.sp)
    Spacer(Modifier.height(8.dp))
    InkText(sub, size = 14.sp, color = Ink.colors.text2, lineHeight = 20.sp)
}

@Composable
internal fun ModalLabel(text: String) {
    InkText(text.uppercase(), size = 12.sp, weight = FontWeight.ExtraBold, color = Ink.colors.text3, letterSpacing = 0.96.sp)
}

/** `.modal-actions .btn` — 52dp, radius 16. */
@Composable
internal fun ModalBtn(label: String, bg: Color, textColor: Color, border: Color, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier.heightIn(min = 52.dp).clip(shape).background(bg).border(1.dp, border, shape).tap(onClick),
        contentAlignment = Alignment.Center,
    ) {
        InkText(label, size = 15.sp, weight = FontWeight.Black, color = textColor)
    }
}

/** Create / Edit notebook: name, colour dots, icon grid. [editing] = null → "New Notebook". */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NotebookFormModal(
    editing: Notebook?,
    onDismiss: () -> Unit,
    onSave: (name: String, color: String, icon: String) -> Unit,
    /** Overrides for the heading copy (the Editor's "Create Notebook" reuses this form). */
    title: String? = null,
    subtitle: String? = null,
) {
    val c = Ink.colors
    var name by remember { mutableStateOf(editing?.name.orEmpty()) }
    var color by remember { mutableStateOf(editing?.color ?: NOTEBOOK_COLOR_CHOICES.first()) }
    var icon by remember { mutableStateOf(editing?.icon ?: NOTEBOOK_ICON_CHOICES.first()) }
    var focused by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() } // autoFocus
    val inputShape = RoundedCornerShape(Ink.radiusSm)

    ModalCard(onDismiss) {
        ModalHead(
            title ?: if (editing != null) "Edit Notebook" else "New Notebook",
            subtitle ?: if (editing != null) "Update name, colour, or icon." else "Name it, pick a colour and an icon.",
        )
        Spacer(Modifier.height(18.dp))

        ModalLabel("Name")
        Spacer(Modifier.height(8.dp))
        BasicTextField(
            value = name,
            onValueChange = { name = it.take(32) },
            singleLine = true,
            textStyle = TextStyle(color = c.text1, fontSize = 14.sp, fontFamily = Ink.sans),
            cursorBrush = SolidColor(c.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSave(name, color, icon) }),
            modifier = Modifier.fillMaxWidth().focusRequester(focus).onFocusChanged { focused = it.isFocused },
            decorationBox = { inner ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(inputShape)
                        .background(c.bgCard)
                        .border(1.5.dp, if (focused) c.accent else c.border, inputShape)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    if (name.isEmpty()) InkText("e.g. Work, Ideas…", size = 14.sp, color = c.text3)
                    inner()
                }
            },
        )
        Spacer(Modifier.height(16.dp))

        ModalLabel("Colour")
        FlowRow(
            Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            NOTEBOOK_COLOR_CHOICES.forEach { hex ->
                val on = hex.equals(color, ignoreCase = true)
                Box(
                    Modifier
                        .size(28.dp)
                        .scale(if (on) 1.12f else 1f)
                        .clip(CircleShape)
                        .background(notebookColor(hex, c.accent))
                        .border(2.dp, if (on) c.text1 else Color.Transparent, CircleShape)
                        .tap { color = hex },
                )
            }
        }

        ModalLabel("Icon")
        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            NOTEBOOK_ICON_CHOICES.chunked(6).forEach { rowIcons ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    rowIcons.forEach { ic ->
                        val on = ic == icon
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (on) c.accent else c.bgCard)
                                .border(1.dp, if (on) c.accent else c.border, RoundedCornerShape(14.dp))
                                .tap { icon = ic },
                            contentAlignment = Alignment.Center,
                        ) {
                            FaIcon(notebookIconRes(ic), tint = if (on) onAccent() else c.text2, size = 18.dp)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ModalBtn("Cancel", c.bgCard, c.text2, c.border, Modifier.weight(1f), onDismiss)
            ModalBtn("Save", c.accent, onAccent(), c.accent, Modifier.weight(1f)) { onSave(name, color, icon) }
        }
    }
}

/** "Delete notebook?" confirm. [sub] differs slightly between the list and the detail page. */
@Composable
fun DeleteNotebookModal(sub: String, onCancel: () -> Unit, onConfirm: () -> Unit) {
    val c = Ink.colors
    ModalCard(onCancel) {
        ModalHead("Delete notebook?", sub)
        Spacer(Modifier.height(30.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ModalBtn("Cancel", c.bgCard, c.text2, c.border, Modifier.weight(1f), onCancel)
            ModalBtn("Delete", DeleteRed, Color.White, DeleteRed, Modifier.weight(1f), onConfirm)
        }
    }
}
