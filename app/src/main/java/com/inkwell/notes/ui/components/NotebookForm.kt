package com.inkwell.notes.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.inkwell.notes.model.Notebook
import com.inkwell.notes.ui.theme.Ink
import com.inkwell.notes.util.parseHex
import kotlinx.coroutines.delay

/** Create / edit notebook dialog: name, colour, icon (same fields as the web modal). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NotebookFormDialog(
    initial: Notebook?,
    onSave: (name: String, color: String, icon: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = Ink.colors
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var color by remember { mutableStateOf(initial?.color ?: NotebookColors.first()) }
    var icon by remember { mutableStateOf(initial?.icon ?: NotebookIconKeys.first()) }
    val focus = remember { FocusRequester() }
    val fieldShape = RoundedCornerShape(16.dp)

    LaunchedEffect(Unit) {
        if (initial == null) {
            delay(200)
            try { focus.requestFocus() } catch (_: IllegalStateException) { }
        }
    }

    InkDialog(onDismiss) {
        Text(
            if (initial != null) "Edit notebook" else "New notebook",
            color = c.text,
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (initial != null) "Update name, colour, or icon." else "Name it, pick a colour and an icon.",
            color = c.subText,
            style = MaterialTheme.typography.bodyMedium,
        )

        Spacer(Modifier.height(18.dp))
        SectionLabel("Name")
        Spacer(Modifier.height(8.dp))
        BasicTextField(
            value = name,
            onValueChange = { if (it.length <= 40) name = it },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.text),
            cursorBrush = SolidColor(c.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (name.isNotBlank()) onSave(name, color, icon) }),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
            decorationBox = { inner ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(fieldShape)
                        .background(c.bg)
                        .border(1.dp, c.border, fieldShape)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    if (name.isEmpty()) {
                        Text("Notebook name", color = c.subText, style = MaterialTheme.typography.bodyLarge)
                    }
                    inner()
                }
            },
        )

        Spacer(Modifier.height(18.dp))
        SectionLabel("Colour")
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            NotebookColors.forEach { hex -> ColorDot(hex, selected = hex == color) { color = hex } }
        }

        Spacer(Modifier.height(18.dp))
        SectionLabel("Icon")
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            NotebookIconKeys.forEach { key -> IconCell(key, parseHex(color), selected = key == icon) { icon = key } }
        }

        Spacer(Modifier.height(22.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            InkButton("Cancel", onDismiss, Modifier.weight(1f), InkButtonStyle.Ghost)
            InkButton("Save", { if (name.isNotBlank()) onSave(name, color, icon) }, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ColorDot(hex: String, selected: Boolean, onClick: () -> Unit) {
    val c = Ink.colors
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.15f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "dotScale",
    )
    Box(
        Modifier
            .pressScale(onClick)
            .scale(scale)
            .size(34.dp)
            .clip(CircleShape)
            .background(parseHex(hex))
            .border(2.dp, if (selected) c.text else Color.Transparent, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Rounded.Check, null, tint = Color(0xFF1F1B16), modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun IconCell(key: String, tint: Color, selected: Boolean, onClick: () -> Unit) {
    val c = Ink.colors
    val bg by animateColorAsState(if (selected) tint.copy(alpha = 0.25f) else c.bg, label = "iconBg")
    val fg by animateColorAsState(if (selected) tint else c.subText, label = "iconFg")
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier
            .pressScale(onClick)
            .size(40.dp)
            .clip(shape)
            .background(bg)
            .border(1.dp, if (selected) tint else c.border, shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(notebookIcon(key), key, tint = fg, modifier = Modifier.size(20.dp))
    }
}
