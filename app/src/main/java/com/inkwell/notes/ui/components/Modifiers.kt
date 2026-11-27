package com.inkwell.notes.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.inkwell.notes.ui.theme.Ink

/** Tap with no ripple — the web UI uses colour/scale feedback, not Material ripples. */
fun Modifier.tap(onClick: () -> Unit): Modifier = composed {
    clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick,
    )
}

/** Text/icon colour that stays readable on the theme accent (white on brown/blue, dark on gold). */
@Composable
fun onAccent(): Color =
    if (Ink.colors.accent.luminance() > 0.35f) Color(0xFF1A1408) else Color.White
