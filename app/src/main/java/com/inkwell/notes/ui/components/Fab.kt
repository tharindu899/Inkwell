package com.inkwell.notes.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inkwell.notes.R
import com.inkwell.notes.ui.theme.Ink
import com.inkwell.notes.ui.theme.bentoPress

/**
 * Bento FAB: a lime pill with "+ New". When [open] (Create menu showing) the label collapses,
 * the pill shrinks into a circle and the plus turns 45° into a ✕ — all with springs.
 */
@Composable
fun Fab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    open: Boolean = false,
    @DrawableRes icon: Int = R.drawable.ic_fa_plus,
    label: String = "New",
) {
    val c = Ink.colors
    val shape = RoundedCornerShape(Ink.radiusPill)
    val onAcc = onAccent()
    val rotation by animateFloatAsState(
        if (open) 45f else 0f,
        spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "fabRotation",
    )
    Row(
        modifier
            .bentoPress(onClick)
            .shadow(10.dp, shape, ambientColor = c.accentGlow, spotColor = c.accentGlow)
            .height(56.dp)
            .widthIn(min = 56.dp)
            .clip(shape)
            .background(c.accent)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FaIcon(icon, tint = onAcc, size = 20.dp, modifier = Modifier.rotate(rotation))
        AnimatedVisibility(
            visible = !open,
            enter = fadeIn() + expandHorizontally(),
            exit = fadeOut() + shrinkHorizontally(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(8.dp))
                InkText(label, size = 16.sp, weight = FontWeight.Bold, color = onAcc)
            }
        }
    }
}
