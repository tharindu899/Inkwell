package com.inkwell.notes.ui.theme

import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberSaveable
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Shared motion specs so every screen animates the same way. */
object InkMotion {
    const val STAGGER_MS = 45L
    const val COUNT_MS = 700
    val press = spring<Float>(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium)
    val enter = spring<Float>(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow)
}

/** Tile that squishes slightly while pressed, then springs back. Replaces plain `tap` on tiles/buttons. */
fun Modifier.bentoPress(onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, InkMotion.press, label = "bentoPress")
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = source, indication = null, onClick = onClick)
}

/** Tiles fade, rise and scale in one after another; [index] sets the order. Plays only once per item. */
fun Modifier.bentoEnter(index: Int): Modifier = composed {
    var played by rememberSaveable { mutableStateOf(false) }
    val progress = remember { Animatable(if (played) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!played) {
            delay(index.coerceIn(0, 8) * InkMotion.STAGGER_MS)
            progress.animateTo(1f, InkMotion.enter)
            played = true
        }
    }
    this.graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        translationY = (1f - p) * 36.dp.toPx()
        val s = 0.94f + 0.06f * p
        scaleX = s
        scaleY = s
    }
}

/** Number that counts up from 0 to [target] when first shown. */
@Composable
fun animateCount(target: Int): Int {
    val value by animateIntAsState(target, tween(InkMotion.COUNT_MS), label = "count")
    return value
}
