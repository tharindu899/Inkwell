package com.inkwell.notes.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import com.inkwell.notes.R
import com.inkwell.notes.ui.theme.Ink

/**
 * Port of the `Fab.jsx` button (.fab, 48px). When [open] (Create menu showing) it turns
 * 90°, swaps + for ✕ and uses the ink colours (.fab.open).
 */
@Composable
fun Fab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    open: Boolean = false,
    @androidx.annotation.DrawableRes icon: Int = R.drawable.ic_fa_plus,
) {
    val c = Ink.colors
    val rotation by animateFloatAsState(if (open) 90f else 0f, tween(200), label = "fabRotation")
    Box(
        modifier
            .shadow(8.dp, CircleShape, ambientColor = c.accentGlow, spotColor = c.accentGlow)
            .size(48.dp)
            .clip(CircleShape)
            .background(if (open) c.text1 else c.accent)
            .tap(onClick),
        contentAlignment = Alignment.Center,
    ) {
        FaIcon(
            if (open) R.drawable.ic_fa_xmark else icon,
            tint = if (open) c.bg else onAccent(),
            size = 20.dp,
            modifier = Modifier.rotate(rotation),
        )
    }
}
