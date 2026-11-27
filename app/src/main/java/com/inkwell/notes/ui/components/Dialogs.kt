package com.inkwell.notes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.inkwell.notes.ui.theme.DangerText
import com.inkwell.notes.ui.theme.ErrorTint
import com.inkwell.notes.ui.theme.Ink
import com.inkwell.notes.ui.theme.InkType
import com.inkwell.notes.ui.theme.Paper
import kotlinx.coroutines.launch

/** Rounded paper card in a full-width dialog. */
@Composable
fun InkDialog(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .imePadding()
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(32.dp))
                .background(Paper)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            content = content,
        )
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    InkDialog(onDismiss = onCancel) {
        Box(Modifier.size(52.dp).clip(CircleShape).background(ErrorTint), contentAlignment = Alignment.Center) {
            InkIconView(InkIcon.Trash, Ink, Modifier.size(24.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = InkType.Headline.copy(fontSize = 22.sp), color = Ink)
        Spacer(Modifier.height(8.dp))
        Text(message, style = InkType.Body, color = Ink)
        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton("Cancel", onCancel, Modifier.weight(1f), filled = false)
            PillButton(confirmLabel, onConfirm, Modifier.weight(1f), danger = true)
        }
    }
}

data class SheetItem(val label: String, val icon: InkIcon, val danger: Boolean = false, val onClick: () -> Unit)

/** Bottom sheet with a title and a list of actions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionSheet(title: String, items: List<SheetItem>, onDismiss: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = Paper,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
    ) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Text(title, style = InkType.Headline.copy(fontSize = 22.sp), color = Ink, maxLines = 1)
            Spacer(Modifier.height(8.dp))
            items.forEachIndexed { i, item ->
                if (item.danger && items.getOrNull(i - 1)?.danger != true) {
                    Box(Modifier.padding(vertical = 6.dp).fillMaxWidth().height(2.dp).background(Ink.copy(alpha = 0.12f)))
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(role = Role.Button) {
                            scope.launch { state.hide() }.invokeOnCompletion {
                                onDismiss()
                                item.onClick()
                            }
                        }
                        .padding(vertical = 14.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    InkIconView(item.icon, if (item.danger) DangerText else Ink, Modifier.size(22.dp))
                    Spacer(Modifier.size(16.dp))
                    Text(item.label, style = InkType.Body.copy(fontSize = 17.sp), color = if (item.danger) DangerText else Ink)
                }
            }
        }
    }
}
