package com.inkwell.notes.ui.theme

import androidx.compose.ui.graphics.Color

/** One soft pastel card style: container + title text + body text. */
data class Pastel(val container: Color, val title: Color, val body: Color)

/** Soft Bento palette. Every theme has exactly 4 pastels so colors can animate by index. */
data class InkPalette(
    val isDark: Boolean,
    val bg: Color,
    val surface: Color,
    val border: Color,
    val text: Color,
    val subText: Color,
    val inverse: Color,
    val onInverse: Color,
    val accent: Color,
    val onAccent: Color,
    val pastels: List<Pastel>,
)

val LightPalette = InkPalette(
    isDark = false,
    bg = Color(0xFFF6F3EE), surface = Color(0xFFFFFFFF), border = Color(0xFFE6DFD3),
    text = Color(0xFF1F1B16), subText = Color(0xFF6B6358),
    inverse = Color(0xFF1F1B16), onInverse = Color(0xFFF6F3EE),
    accent = Color(0xFFFFB45E), onAccent = Color(0xFF1F1B16),
    pastels = listOf(
        Pastel(Color(0xFFFFD9A8), Color(0xFF3A2A12), Color(0xFF5A4426)),
        Pastel(Color(0xFFCFE8D5), Color(0xFF1D3A26), Color(0xFF38573F)),
        Pastel(Color(0xFFD9D3F5), Color(0xFF2B2552), Color(0xFF4A4377)),
        Pastel(Color(0xFFFFC9C9), Color(0xFF4A1F1F), Color(0xFF6B3535)),
    ),
)

val DarkPalette = InkPalette(
    isDark = true,
    bg = Color(0xFF141218), surface = Color(0xFF221E27), border = Color(0xFF332D3A),
    text = Color(0xFFF1ECE4), subText = Color(0xFFB8AFA3),
    inverse = Color(0xFFF1ECE4), onInverse = Color(0xFF141218),
    accent = Color(0xFFFFB45E), onAccent = Color(0xFF1F1B16),
    pastels = listOf(
        Pastel(Color(0xFF3D2E14), Color(0xFFFFE9CC), Color(0xFFFFD9A8)),
        Pastel(Color(0xFF1F3326), Color(0xFFCFE8D5), Color(0xFFA9CDB4)),
        Pastel(Color(0xFF2A2547), Color(0xFFD9D3F5), Color(0xFFBDB5E6)),
        Pastel(Color(0xFF3F1F25), Color(0xFFFFC9C9), Color(0xFFEFA9A9)),
    ),
)

/** Uses the same GitHub dark values as the web app (docs/THEME.md). */
val GitHubPalette = InkPalette(
    isDark = true,
    bg = Color(0xFF0D1117), surface = Color(0xFF161B22), border = Color(0xFF30363D),
    text = Color(0xFFE6EDF3), subText = Color(0xFF8B949E),
    inverse = Color(0xFFE6EDF3), onInverse = Color(0xFF0D1117),
    accent = Color(0xFF2F81F7), onAccent = Color(0xFFFFFFFF),
    pastels = listOf(
        Pastel(Color(0xFF12233F), Color(0xFFCDE1FF), Color(0xFF9CC4FF)),
        Pastel(Color(0xFF10261A), Color(0xFFC6F0D2), Color(0xFF8FD1A5)),
        Pastel(Color(0xFF241B3F), Color(0xFFE2D6FF), Color(0xFFBBA6F0)),
        Pastel(Color(0xFF3A2412), Color(0xFFFFDDB8), Color(0xFFF0B27A)),
    ),
)
