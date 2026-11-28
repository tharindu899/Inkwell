package com.inkwell.notes.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.inkwell.notes.model.ThemeMode

val LocalInk = staticCompositionLocalOf { LightPalette }

/** Use `Ink.colors.bg`, `Ink.colors.pastels[0]` etc. anywhere inside InkwellTheme. */
object Ink {
    val colors: InkPalette
        @Composable @ReadOnlyComposable get() = LocalInk.current
}

@Composable
private fun anim(c: Color): Color =
    animateColorAsState(c, tween(380), label = "themeColor").value

/** Every color fades to the new theme, so switching Light / Dark / GitHub feels smooth. */
@Composable
private fun InkPalette.animated(): InkPalette = InkPalette(
    isDark = isDark,
    bg = anim(bg), surface = anim(surface), border = anim(border),
    text = anim(text), subText = anim(subText),
    inverse = anim(inverse), onInverse = anim(onInverse),
    accent = anim(accent), onAccent = anim(onAccent),
    pastels = pastels.map { Pastel(anim(it.container), anim(it.title), anim(it.body)) },
)

fun paletteFor(mode: ThemeMode): InkPalette = when (mode) {
    ThemeMode.Light -> LightPalette
    ThemeMode.Dark -> DarkPalette
    ThemeMode.GitHub -> GitHubPalette
}

@Composable
fun InkwellTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val target = paletteFor(mode)
    val p = target.animated()
    val scheme = if (p.isDark) {
        darkColorScheme(primary = p.accent, onPrimary = p.onAccent, background = p.bg, onBackground = p.text, surface = p.surface, onSurface = p.text)
    } else {
        lightColorScheme(primary = p.accent, onPrimary = p.onAccent, background = p.bg, onBackground = p.text, surface = p.surface, onSurface = p.text)
    }
    CompositionLocalProvider(LocalInk provides p) {
        MaterialTheme(colorScheme = scheme, typography = InkTypography, content = content)
    }
}
