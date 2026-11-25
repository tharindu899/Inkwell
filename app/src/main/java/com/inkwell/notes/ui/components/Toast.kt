package com.inkwell.notes.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inkwell.notes.ui.theme.Ink
import kotlinx.coroutines.delay

@Immutable
data class ToastState(
    /** Changes for every new toast so the auto-dismiss timer restarts. */
    val key: Long,
    val message: String,
    @DrawableRes val icon: Int,
    val actionLabel: String? = null,
)

/**
 * Port of `Toast.jsx` + `.toast` CSS.
 * Auto-dismisses after 2.6 s, or 5 s when it carries an action (Undo).
 * Position it with [modifier] (the caller aligns it above the bottom nav).
 */
@Composable
fun InkToast(
    toast: ToastState?,
    onAction: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Ink.colors

    // Keep the last toast around so its content stays during the fade-out.
    var shown by remember { mutableStateOf<ToastState?>(null) }
    if (toast != null) shown = toast

    LaunchedEffect(toast?.key) {
        if (toast != null) {
            delay(if (toast.actionLabel != null) 5_000L else 2_600L)
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = toast != null,
        modifier = modifier,
        enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 2 },
        exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { it / 2 },
    ) {
        shown?.let { t ->
            val shape = RoundedCornerShape(24.dp)
            Row(
                Modifier
                    .clip(shape)
                    .background(c.bgElevated)
                    .border(1.dp, c.borderHi, shape)
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FaIcon(t.icon, tint = c.accent, size = 13.dp)
                InkText(
                    t.message,
                    size = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (t.actionLabel != null) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(c.accent)
                            .tap(onAction)
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    ) {
                        InkText(t.actionLabel, size = 12.sp, weight = FontWeight.ExtraBold, color = onAccent())
                    }
                }
            }
        }
    }
}
