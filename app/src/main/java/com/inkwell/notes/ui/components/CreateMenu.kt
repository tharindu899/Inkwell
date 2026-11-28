package com.inkwell.notes.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inkwell.notes.R
import com.inkwell.notes.ui.theme.Ink
import com.inkwell.notes.ui.theme.InkMotion
import com.inkwell.notes.ui.theme.bentoPress
import kotlinx.coroutines.delay

internal val MenuGreen = Color(0xFF2E9E6B)

/** Set to false for icon-only buttons (no label pills). */
private const val SHOW_LABELS = true

private class DialItem(
    @DrawableRes val icon: Int,
    val bg: Color,
    val tint: Color,
    val label: String,
    val onClick: () -> Unit,
)

/**
 * Speed dial in Bento style: dim backdrop + tiles that spring up one after another right above
 * the FAB (the one nearest the FAB appears first). "New note" is the lime tile, the rest are
 * neutral tiles. Draw the FAB *after* this so it stays on top.
 *
 * [onManageTags] is optional; when given (Notes page) an extra "tags" tile is added.
 */
@Composable
fun CreateMenu(
    open: Boolean,
    onClose: () -> Unit,
    onNewNote: () -> Unit,
    onNewNotebook: () -> Unit,
    onNewTag: () -> Unit,
    onSearch: () -> Unit,
    onManageTags: (() -> Unit)? = null,
) {
    val c = Ink.colors
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val onAcc = onAccent()

    // index 0 = closest to the FAB
    val items = buildList {
        add(DialItem(R.drawable.ic_fa_file_circle_plus, c.accent, onAcc, "New note", onNewNote))
        add(DialItem(R.drawable.ic_fa_folder_plus, c.bgElevated, c.text1, "New notebook", onNewNotebook))
        add(DialItem(R.drawable.ic_fa_tag, c.bgElevated, c.text1, "New tag", onNewTag))
        add(DialItem(R.drawable.ic_fa_magnifying_glass, c.bgElevated, c.text1, "Search", onSearch))
        if (onManageTags != null) {
            add(DialItem(R.drawable.ic_fa_tags, c.bgElevated, c.text1, "Manage tags", onManageTags))
        }
    }

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(open, enter = fadeIn(tween(200)), exit = fadeOut(tween(140))) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = if (c.isDark) 0.60f else 0.40f))
                    .tap(onClose),
            )
        }

        // FAB: 56dp, end 18dp, bottom = navInset + 62 + 12. Tiles sit 12dp above it.
        Column(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = Ink.screenPad, bottom = navInset + 62.dp + 12.dp + 56.dp + 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.End,
        ) {
            items.asReversed().forEachIndexed { rev, item ->
                DialRow(item, index = items.size - 1 - rev, open = open)
            }
        }
    }
}

@Composable
private fun DialRow(item: DialItem, index: Int, open: Boolean) {
    val c = Ink.colors
    val tile = RoundedCornerShape(18.dp)

    // Stagger: each tile flips to visible a little after the previous one.
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(open) {
        if (open) {
            delay(index * InkMotion.STAGGER_MS)
            shown = true
        } else {
            shown = false
        }
    }

    val springOffset = spring<IntOffset>(dampingRatio = 0.65f, stiffness = Spring.StiffnessMediumLow)
    val springScale = spring<Float>(dampingRatio = 0.65f, stiffness = Spring.StiffnessMediumLow)
    val origin = TransformOrigin(1f, 1f)

    AnimatedVisibility(
        visible = shown,
        enter = fadeIn(tween(120)) +
            slideInVertically(springOffset) { it } +
            scaleIn(springScale, initialScale = 0.6f, transformOrigin = origin),
        exit = fadeOut(tween(90)) + scaleOut(tween(90), targetScale = 0.6f, transformOrigin = origin),
    ) {
        Row(
            Modifier.bentoPress(item.onClick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (SHOW_LABELS) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(Ink.radiusPill))
                        .background(c.bgElevated)
                        .border(1.dp, c.borderHi, RoundedCornerShape(Ink.radiusPill))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    InkText(item.label, color = c.text1, size = 14.sp, weight = FontWeight.Bold)
                }
                Spacer(Modifier.width(10.dp))
            }
            Box(
                Modifier
                    .size(48.dp)
                    .clip(tile)
                    .background(item.bg)
                    .border(1.dp, c.borderHi, tile),
                contentAlignment = Alignment.Center,
            ) {
                FaIcon(item.icon, tint = item.tint, size = 18.dp)
            }
        }
    }
}
