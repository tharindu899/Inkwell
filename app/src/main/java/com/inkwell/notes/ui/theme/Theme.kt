package com.inkwell.notes.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val InkColors = lightColorScheme(
    primary = Ink,
    onPrimary = Lime,
    secondary = Lime,
    onSecondary = Ink,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
)

@Composable
fun InkwellTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = InkColors, content = content)
}
