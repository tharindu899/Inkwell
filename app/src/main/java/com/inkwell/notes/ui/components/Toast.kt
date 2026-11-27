package com.inkwell.notes.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.inkwell.notes.ui.theme.Ink
import com.inkwell.notes.ui.theme.InkType
import com.inkwell.notes.ui.theme.Lime
import com.inkwell.notes.ui.theme.Paper

data class ToastData(
    val message: String,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
)

@Composable
fun InkToast(toast: ToastData?, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    var last by remember { mutableStateOf(toast) }
    if (toast != null) last = toast
    AnimatedVisibility(
        visible = toast != null,
        modifier = modifier,
        enter = slideInVertically { it / 2 } + fadeIn(),
        exit = slideOutVertically { it / 2 } + fadeOut(),
    ) {
        val t = last
        if (t != null) {
            Row(
                Modifier
                    .clip(CircleShape)
                    .background(Ink)
                    .padding(start = 20.dp, end = if (t.actionLabel != null) 6.dp else 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(t.message, style = InkType.Body, color = Paper, modifier = Modifier.padding(vertical = 14.dp))
                if (t.actionLabel != null) {
                    Text(
                        t.actionLabel,
                        style = InkType.Button,
                        color = Lime,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(role = Role.Button) { t.onAction?.invoke(); onDismiss() }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                    )
                }
            }
        }
    }
}

