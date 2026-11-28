package com.inkwell.notes.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.inkwell.notes.ui.theme.Ink

/** 14 weeks x 7 days heat-map drawn with Canvas. Cells pop in one after another. */
@Composable
fun ActivityGrid(levels: List<Int>, modifier: Modifier = Modifier) {
    val c = Ink.colors
    val progress = remember { Animatable(0f) }
    LaunchedEffect(levels) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(1200, easing = LinearEasing))
    }
    val shades = (0..4).map { lerp(c.border, c.accent, it / 4f) }
    Canvas(modifier.fillMaxWidth().aspectRatio(2f)) {
        val gap = 4.dp.toPx()
        val cell = (size.width - 13 * gap) / 14f
        val total = levels.size
        levels.forEachIndexed { i, level ->
            val col = i / 7
            val row = i % 7
            val t = ((progress.value * (total + 14)) - i).div(14f).coerceIn(0f, 1f)
            val s = cell * t
            val x = col * (cell + gap) + (cell - s) / 2
            val y = row * (cell + gap) + (cell - s) / 2
            drawRoundRect(
                color = shades[level.coerceIn(0, 4)].copy(alpha = t),
                topLeft = Offset(x, y),
                size = Size(s, s),
                cornerRadius = CornerRadius(cell * 0.25f),
            )
        }
    }
}
