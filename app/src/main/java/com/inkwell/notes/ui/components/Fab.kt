package com.inkwell.notes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import com.inkwell.notes.R
import com.inkwell.notes.ui.theme.Ink

/** Port of `Fab.jsx` button (.fab, 48px). The Create-New menu comes in a later step. */
@Composable
fun Fab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Ink.colors
    Box(
        modifier
            .shadow(8.dp, CircleShape, ambientColor = c.accentGlow, spotColor = c.accentGlow)
            .size(48.dp)
            .clip(CircleShape)
            .background(c.accent)
            .tap(onClick),
        contentAlignment = Alignment.Center,
    ) {
        FaIcon(R.drawable.ic_fa_plus, tint = onAccent(), size = 20.dp)
    }
}
