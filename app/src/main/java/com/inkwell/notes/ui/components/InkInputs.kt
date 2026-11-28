package com.inkwell.notes.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.inkwell.notes.ui.theme.Ink
import kotlinx.coroutines.delay

/** Single-line rounded text field used by small dialogs. */
@Composable
fun InkTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    onDone: () -> Unit = {},
) {
    val c = Ink.colors
    val shape = RoundedCornerShape(16.dp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.text),
        cursorBrush = SolidColor(c.accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Box(
                Modifier.fillMaxWidth().clip(shape).background(c.bg).border(1.dp, c.border, shape)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                if (value.isEmpty()) Text(placeholder, color = c.subText, style = MaterialTheme.typography.bodyLarge)
                inner()
            }
        },
    )
}

/** Small "type a name" dialog (used by the FAB "Create tag"). */
@Composable
fun QuickNameDialog(
    title: String,
    subtitle: String,
    placeholder: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = Ink.colors
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(200)
        try { focus.requestFocus() } catch (_: IllegalStateException) { }
    }
    InkDialog(onDismiss) {
        Text(title, color = c.text, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, color = c.subText, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        InkTextField(
            value = text,
            onValueChange = { if (it.length <= 40) text = it },
            placeholder = placeholder,
            modifier = Modifier.focusRequester(focus),
            onDone = { if (text.isNotBlank()) onConfirm(text.trim()) },
        )
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            InkButton("Cancel", onDismiss, Modifier.weight(1f), InkButtonStyle.Ghost)
            InkButton(confirmLabel, { if (text.isNotBlank()) onConfirm(text.trim()) }, Modifier.weight(1f))
        }
    }
}

/** Big rounded search bar with a clear button that pops in when there is text. */
@Composable
fun InkSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    val c = Ink.colors
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier.fillMaxWidth().height(52.dp).clip(shape).background(c.surface).border(1.dp, c.border, shape)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Rounded.Search, null, tint = c.subText, modifier = Modifier.size(20.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text(placeholder, color = c.subText, style = MaterialTheme.typography.bodyLarge)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.text),
                cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier.fillMaxWidth().then(
                    if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier,
                ),
            )
        }
        AnimatedVisibility(
            visible = value.isNotEmpty(),
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut(),
        ) {
            Box(
                Modifier.size(28.dp).clip(CircleShape).background(c.border).pressScale(onClear),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Close, "Clear", tint = c.text, modifier = Modifier.size(16.dp))
            }
        }
    }
}
