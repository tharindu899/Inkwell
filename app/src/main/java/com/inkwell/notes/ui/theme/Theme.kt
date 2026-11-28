package com.inkwell.notes.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * "Bento" theme: charcoal / soft-white surfaces + a lime accent.
 * Token names are unchanged from the old theme so every existing screen still compiles:
 * bgCard = tile surface, bgElevated = secondary tile surface.
 */
enum class ThemeMode(val key: String) {
    Dark("dark"),
    Light("light"),

    /** Legacy value kept so saved prefs still load; renders the dark Bento palette. */
    Github("github");

    companion object {
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
    /** Lime brand colour. Use as a FILL (buttons, active pills); put onAccent() on top of it. */
    val accent: Color,
    /** Accent that is readable as TEXT/ICON on [bg] and [bgCard] (olive on light, lime on dark). */
    val accentInk: Color,
    val accentDim: Color,
    val accentGlow: Color,
    val border: Color,
    val borderHi: Color,
    val danger: Color,
    val success: Color,
    val info: Color,
    val shadowSm: Color,
    val isDark: Boolean,
)

private val Lime = Color(0xFFC6F432)

private val DarkColors = InkwellColors(
    bg = Color(0xFF121413),
    bgCard = Color(0xFF1E2220),
    bgElevated = Color(0xFF2A2F2C),
    bgInput = Color(0xFF1E2220),
    text1 = Color(0xFFEDEFEA),
    text2 = Color(0xFFA3A8A0),
    text3 = Color(0xFF6F756D),
    accent = Lime,
    accentInk = Lime,
    accentDim = Lime.copy(alpha = 0.16f),
    accentGlow = Lime.copy(alpha = 0.30f),
    border = Color.White.copy(alpha = 0.06f),
    borderHi = Color.White.copy(alpha = 0.12f),
    danger = Color(0xFFFF6B63),
    success = Color(0xFF5BD08A),
    info = Color(0xFF7AA8FF),
    shadowSm = Color.Black.copy(alpha = 0.40f),
    isDark = true,
)

private val LightColors = InkwellColors(
    bg = Color(0xFFF2F4EE),
    bgCard = Color(0xFFFFFFFF),
    bgElevated = Color(0xFFE3E8DA),
    bgInput = Color(0xFFE9EDE2),
    text1 = Color(0xFF151713),
    text2 = Color(0xFF5A6054),
    text3 = Color(0xFF8A9082),
    accent = Lime,
    accentInk = Color(0xFF4F6A00),
    accentDim = Color(0xFF4F6A00).copy(alpha = 0.12f),
    accentGlow = Lime.copy(alpha = 0.50f),
    border = Color.Black.copy(alpha = 0.07f),
    borderHi = Color.Black.copy(alpha = 0.13f),
    danger = Color(0xFFC0392B),
    success = Color(0xFF2E8B57),
    info = Color(0xFF2F6FD0),
    shadowSm = Color.Black.copy(alpha = 0.08f),
    isDark = false,
)

private val LocalInkwellColors = staticCompositionLocalOf { DarkColors }

/** Typography scale from the Bento mockups (DM Sans can replace [Ink.sans] later). */
object InkType {
    val display = TextStyle(fontFamily = Ink.sans, fontSize = 32.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
    val title = TextStyle(fontFamily = Ink.sans, fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold)
    val body = TextStyle(fontFamily = Ink.sans, fontSize = 16.sp, lineHeight = 25.sp, fontWeight = FontWeight.Normal)
    val label = TextStyle(fontFamily = Ink.sans, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold)
    val caption = TextStyle(fontFamily = Ink.sans, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)
}

/** Design tokens shared by both themes. */
object Ink {
    val colors: InkwellColors
        @Composable
        @ReadOnlyComposable
        get() = LocalInkwellColors.current

    // Shapes (dp)
    val radius = 24.dp       // tiles / cards
    val radiusSm = 16.dp     // inputs, small tiles
    val radiusLg = 32.dp     // bottom sheets
    val radiusPill = 999.dp  // chips, FAB, nav bar

    // Spacing
    val gap = 10.dp          // gap between bento tiles
    val screenPad = 18.dp    // horizontal screen padding
    val tilePad = 16.dp

    /** One family for everything; [serif] is an alias so old headings pick up the new look. */
    val sans: FontFamily = FontFamily.Default
    val serif: FontFamily = sans
}

@Composable
fun InkwellTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val colors = when (mode) {
        ThemeMode.Light -> LightColors
        ThemeMode.Dark, ThemeMode.Github -> DarkColors
    }
    CompositionLocalProvider(LocalInkwellColors provides colors, content = content)
}
