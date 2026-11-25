package com.inkwell.notes.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inkwell.notes.R
import com.inkwell.notes.ui.components.DeleteRed
import com.inkwell.notes.ui.components.FaIcon
import com.inkwell.notes.ui.components.InkText
import com.inkwell.notes.ui.components.onAccent
import com.inkwell.notes.ui.components.tap
import com.inkwell.notes.ui.theme.Ink

/** Rename-tag box (same look as the Create tag modal): one field, Cancel / Save. */
@Composable
fun TagEditModal(tag: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    val c = Ink.colors
    var value by remember { mutableStateOf(tag) }
    var focused by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

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
                .background(c.bg)
                .border(1.dp, c.border, cardShape)
                .tap { /* swallow taps */ }
                .padding(18.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
                Box(
                    Modifier.size(52.dp).clip(RoundedCornerShape(18.dp)).background(c.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    FaIcon(R.drawable.ic_fa_r_pen_to_square, tint = onAccent(), size = 20.dp)
                }
                Column(Modifier.weight(1f)) {
                    InkText("Rename tag", size = 21.sp, weight = FontWeight.Bold, family = Ink.serif, lineHeight = 24.sp)
                    Spacer(Modifier.height(4.dp))
                    InkText(
                        "Every note using this tag will be updated.",
                        size = 14.sp,
                        color = c.text2,
                        lineHeight = 20.sp,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            InkText("TAG NAME", size = 12.sp, weight = FontWeight.ExtraBold, color = c.text3, letterSpacing = 0.96.sp)
            Spacer(Modifier.height(8.dp))
            BasicTextField(
                value = value,
                onValueChange = { value = it.take(24) },
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
                        if (value.isEmpty()) InkText("e.g. code, github…", size = 14.sp, color = c.text3)
                        inner()
                    }
                },
            )
            Spacer(Modifier.height(22.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ModalBtn("Cancel", c.bgCard, c.text2, c.border, Modifier.weight(1f), onDismiss)
                ModalBtn("Save", c.accent, onAccent(), c.accent, Modifier.weight(1f)) { onSave(value) }
            }
        }
    }
}

/** Confirm removing a tag from all notes (the notes are kept). */
@Composable
fun TagDeleteModal(tag: String, count: Int, onCancel: () -> Unit, onConfirm: () -> Unit) {
    val c = Ink.colors
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val cardShape = RoundedCornerShape(26.dp)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.44f))
            .tap(onCancel)
            .padding(start = 12.dp, end = 12.dp, bottom = 14.dp + navInset),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .widthIn(max = 390.dp)
                .fillMaxWidth()
                .clip(cardShape)
                .background(c.bg)
                .border(1.dp, c.border, cardShape)
                .tap { /* swallow taps */ }
                .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(58.dp).clip(RoundedCornerShape(20.dp)).background(DeleteRed.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                FaIcon(R.drawable.ic_fa_trash_can, tint = DeleteRed, size = 24.dp)
            }
            Spacer(Modifier.height(12.dp))
            InkText(
                "Delete tag \"$tag\"?",
                size = 22.sp,
                weight = FontWeight.Bold,
                family = Ink.serif,
                lineHeight = 26.sp,
                align = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            InkText(
                "It will be removed from $count note${if (count != 1) "s" else ""}. The notes themselves are not deleted.",
                size = 13.sp,
                color = c.text2,
                lineHeight = 19.5.sp,
                align = TextAlign.Center,
                modifier = Modifier.widthIn(max = 300.dp),
            )
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ModalBtn("Cancel", c.bgCard, c.text2, c.border, Modifier.weight(1f), onCancel)
                ModalBtn("Delete", DeleteRed, Color.White, DeleteRed, Modifier.weight(1f), onConfirm)
            }
        }
    }
}

@Composable
private fun ModalBtn(
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
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(background)
            .border(1.dp, border, shape)
            .tap(onClick),
        contentAlignment = Alignment.Center,
    ) {
        InkText(label, size = 15.sp, weight = FontWeight.Black, color = textColor)
    }
}
