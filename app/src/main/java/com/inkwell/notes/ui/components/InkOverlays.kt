package com.inkwell.notes.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.inkwell.notes.ui.theme.Ink
import com.inkwell.notes.ui.theme.InkDanger
import kotlinx.coroutines.launch

/**
 * Bottom sheet with a real slide-out. Inside [content] call `closeThen { ... }` to slide the
 * sheet away first and then run your action (open dialog, navigate, ...).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InkSheet(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.(closeThen: (() -> Unit) -> Unit) -> Unit,
) {
    val c = Ink.colors
    val state = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    val closeThen: (() -> Unit) -> Unit = { then ->
        scope.launch {
            state.hide()
            then()
            onDismiss()
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = c.surface,
        contentColor = c.text,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = c.border) },
    ) {
        Text(
            title,
            color = c.text,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp),
        )
        content(closeThen)
        Spacer(Modifier.navigationBarsPadding().height(12.dp))
    }
}

@Composable
fun SheetItem(icon: ImageVector, label: String, onClick: () -> Unit, danger: Boolean = false) {
    val c = Ink.colors
    val tint = if (danger) InkDanger else c.text
    Row(
        Modifier.fillMaxWidth().pressScale(onClick).padding(horizontal = 22.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        Text(label, color = tint, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Centered dialog that pops in with a small spring. */
@Composable
fun InkDialog(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val c = Ink.colors
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        var shown by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { shown = true }
        AnimatedVisibility(
            visible = shown,
            enter = fadeIn(tween(160)) + scaleIn(
                animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow),
                initialScale = 0.86f,
            ),
        ) {
            val shape = RoundedCornerShape(28.dp)
            Column(
                Modifier
                    .padding(horizontal = 28.dp)
                    .fillMaxWidth()
                    .clip(shape)
                    .background(c.surface)
                    .border(1.dp, c.border, shape)
                    .heightIn(max = 640.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(22.dp),
                content = content,
            )
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    danger: Boolean = true,
) {
    val c = Ink.colors
    InkDialog(onDismiss) {
        Text(title, color = c.text, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(text, color = c.subText, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            InkButton("Cancel", onDismiss, Modifier.weight(1f), InkButtonStyle.Ghost)
            InkButton(confirmLabel, onConfirm, Modifier.weight(1f), if (danger) InkButtonStyle.Danger else InkButtonStyle.Primary)
        }
    }
}
