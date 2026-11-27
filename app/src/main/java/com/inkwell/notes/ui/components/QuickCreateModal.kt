package com.inkwell.notes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inkwell.notes.R
import com.inkwell.notes.ui.theme.Ink

enum class QuickCreateKind { Notebook, Tag }

/**
 * Port of the `quickModal` in `Fab.jsx` (.quick-create-modal): "Create notebook" / "Create tag"
 * with one text field. Rides above the keyboard. Enter (IME Done) = Save.
 * Empty input just closes — [onSave] receives the raw text and the caller trims / ignores blanks.
 */
@Composable
fun QuickCreateModal(kind: QuickCreateKind, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    val c = Ink.colors
    val isNotebook = kind == QuickCreateKind.Notebook
    val maxLen = if (isNotebook) 32 else 24
    val tint = if (isNotebook) c.accent else MenuGreen
    val onTint = if (isNotebook) onAccent() else Color.White

    var value by remember { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() } // autoFocus

    val cardShape = RoundedCornerShape(22.dp)
    val inputShape = RoundedCornerShape(Ink.radiusSm)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.44f))
            .tap(onDismiss)
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
            .padding(start = 12.dp, end = 12.dp, bottom = 14.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .clip(cardShape)
                .background(c.bg) // web: var(--surface)
                .border(1.dp, c.border, cardShape)
                .tap { /* swallow taps so the scrim doesn't dismiss */ }
                .padding(18.dp),
        ) {
            // .quick-create-head
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
                Box(
                    Modifier.size(52.dp).clip(RoundedCornerShape(18.dp)).background(tint),
                    contentAlignment = Alignment.Center,
                ) {
                    FaIcon(if (isNotebook) R.drawable.ic_fa_book_open else R.drawable.ic_fa_tag, tint = onTint, size = 20.dp)
                }
                Column(Modifier.weight(1f)) {
                    InkText(
                        if (isNotebook) "Create notebook" else "Create tag",
                        size = 21.sp,
                        weight = FontWeight.Bold,
                        family = Ink.serif,
                        lineHeight = 24.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    InkText(
                        if (isNotebook) "Add a new notebook that matches the Inkwell UI."
                        else "Create a new note and save it with this tag.",
                        size = 14.sp,
                        color = c.text2,
                        lineHeight = 20.sp,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))

            // .modal-label
            InkText(
                (if (isNotebook) "Notebook name" else "Tag name").uppercase(),
                size = 12.sp,
                weight = FontWeight.ExtraBold,
                color = c.text3,
                letterSpacing = 0.96.sp,
            )
            Spacer(Modifier.height(8.dp))

            // .modal-input
            BasicTextField(
                value = value,
                onValueChange = { value = it.take(maxLen) },
                singleLine = true,
                textStyle = TextStyle(color = c.text1, fontSize = 14.sp, fontFamily = Ink.sans),
                cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSave(value) }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
                    .onFocusChanged { focused = it.isFocused },
                decorationBox = { inner ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(inputShape)
                            .background(c.bgCard)
                            .border(1.5.dp, if (focused) c.accent else c.border, inputShape)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        if (value.isEmpty()) {
                            InkText(
                                if (isNotebook) "e.g. Work, Ideas…" else "e.g. code, github…",
                                size = 14.sp,
                                color = c.text3,
                            )
                        }
                        inner()
                    }
                },
            )
            Spacer(Modifier.height(22.dp)) // 10 (input margin) + 12 (actions margin)

            // .modal-actions — 2 columns, gap 10
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickButton("Cancel", c.bgCard, c.text2, c.border, Modifier.weight(1f), onDismiss)
                QuickButton("Save", c.accent, onAccent(), c.accent, Modifier.weight(1f)) { onSave(value) }
            }
        }
    }
}

@Composable
private fun QuickButton(
    label: String,
    background: Color,
    textColor: Color,
    border: Color,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .heightIn(min = 52.dp)
            .clip(shape)
            .background(background)
            .border(1.dp, border, shape)
            .tap(onClick),
        contentAlignment = Alignment.Center,
    ) {
        InkText(label, size = 15.sp, weight = FontWeight.Black, color = textColor)
    }
}
