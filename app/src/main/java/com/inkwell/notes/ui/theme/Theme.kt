package com.inkwell.notes.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/**
 * Ported 1:1 from `src/styles/styles.css` (:root / [data-theme="light"] / [data-theme="github"]).
 * Same CSS-variable names, camel-cased: --bg-card → bgCard, --text-1 → text1 …
 */
enum class ThemeMode(val key: String) {
    Dark("dark"),
    Light("light"),
    Github("github");

    companion object {
        /** `iw_theme` value → mode. Unknown / missing → dark (same as the web app). */
        fun fromKey(key: String?): ThemeMode = entries.firstOrNull { it.key == key } ?: Dark
    }
}

@Immutable
data class InkwellColors(
    val bg: Color,
    val bgCard: Color,
    val bgElevated: Color,
    val bgInput: Color,
    val text1: Color,
    val text2: Color,
    val text3: Color,
    val accent: Color,
    val accentDim: Color,
    val accentGlow: Color,
    val border: Color,
    val borderHi: Color,
    val danger: Color,
    val success: Color,
    val info: Color,
    /** --sh-sm shadow colour */
    val shadowSm: Color,
    val isDark: Boolean,
)

private val DarkColors = InkwellColors(
    bg = Color(0xFF0B0B12),
    bgCard = Color(0xFF13131C),
    bgElevated = Color(0xFF1A1A26),
    bgInput = Color(0xFF171722),
    text1 = Color(0xFFEAE6DD),
    text2 = Color(0xFF8A8699),
    text3 = Color(0xFF4C4A60),
    accent = Color(0xFFC9A96E),
    accentDim = Color(0xFFC9A96E).copy(alpha = 0.14f),
    accentGlow = Color(0xFFC9A96E).copy(alpha = 0.30f),
    border = Color.White.copy(alpha = 0.06f),
    borderHi = Color.White.copy(alpha = 0.11f),
    danger = Color(0xFFE06060),
    success = Color(0xFF60AD82),
    info = Color(0xFF6090E0),
    shadowSm = Color.Black.copy(alpha = 0.38f),
    isDark = true,
)

private val LightColors = InkwellColors(
    bg = Color(0xFFF7F5F0),
    bgCard = Color(0xFFFFFFFF),
    bgElevated = Color(0xFFEDE9E0),
    bgInput = Color(0xFFF0EDE6),
    text1 = Color(0xFF1C1828),
    text2 = Color(0xFF6A677A),
    text3 = Color(0xFFB0ACBF),
    accent = Color(0xFF7A5228),
    accentDim = Color(0xFF7A5228).copy(alpha = 0.10f),
    accentGlow = Color(0xFF7A5228).copy(alpha = 0.22f),
    border = Color.Black.copy(alpha = 0.07f),
    borderHi = Color.Black.copy(alpha = 0.13f),
    danger = Color(0xFFC0392B),
    success = Color(0xFF27AE60),
    info = Color(0xFF2980B9),
    shadowSm = Color.Black.copy(alpha = 0.07f),
    isDark = false,
)

private val GithubColors = InkwellColors(
    bg = Color(0xFF0D1117),
    bgCard = Color(0xFF161B22),
    bgElevated = Color(0xFF21262D),
    bgInput = Color(0xFF161B22),
    text1 = Color(0xFFE6EDF3),
    text2 = Color(0xFF8B949E),
    text3 = Color(0xFF484F58),
    accent = Color(0xFF2F81F7),
    accentDim = Color(0xFF2F81F7).copy(alpha = 0.14f),
    accentGlow = Color(0xFF2F81F7).copy(alpha = 0.30f),
    border = Color(0xFF30363D),
    borderHi = Color(0xFF30363D),
    danger = Color(0xFFF85149),
    success = Color(0xFF3FB950),
    info = Color(0xFF2F81F7),
    shadowSm = Color(0xFF010409).copy(alpha = 0.35f),
    isDark = true,
)

private val LocalInkwellColors = staticCompositionLocalOf { DarkColors }

/** Design tokens that never change between themes (--r, --r-sm, fonts). */
object Ink {
    val colors: InkwellColors
        @Composable
        @ReadOnlyComposable
        get() = LocalInkwellColors.current

    val radius = 16.dp      // --r
    val radiusSm = 10.dp    // --r-sm

    /** --font-s: Georgia, 'Times New Roman', serif */
    val serif: FontFamily = FontFamily.Serif

    /** --font-b: system sans-serif */
    val sans: FontFamily = FontFamily.Default
}

@Composable
fun InkwellTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val colors = when (mode) {
        ThemeMode.Dark -> DarkColors
        ThemeMode.Light -> LightColors
        ThemeMode.Github -> GithubColors
    }
    CompositionLocalProvider(LocalInkwellColors provides colors, content = content)
}
