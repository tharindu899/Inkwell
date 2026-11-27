package com.inkwell.notes.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.inkwell.notes.R
import com.inkwell.notes.ui.theme.Ink

/** Dial button colours. */
private val MenuBlue = Color(0xFF5B6AF0)
internal val MenuGreen = Color(0xFF2E9E6B)
private val MenuPurple = Color(0xFF7C4FD4)

private class DialItem(
    @DrawableRes val icon: Int,
    val bg: Color,
    val tint: Color,
    val label: String,
    val onClick: () -> Unit,
)

/**
 * Telegram-style speed dial: a dim backdrop plus small round icon-only buttons that pop up in a
 * column right above the FAB (the one nearest the FAB appears first). Draw the FAB *after* this
 * so it stays on top.
 *
 * [onManageTags] is optional; when given (Notes page) a extra "tags" button is added to the dial.
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

    // index 0 = closest to the FAB
    val items = buildList {
        add(DialItem(R.drawable.ic_fa_file_circle_plus, c.fab, c.onFab, "New note", onNewNote))
        add(DialItem(R.drawable.ic_fa_folder_plus, MenuBlue, Color.White, "New notebook", onNewNotebook))
        add(DialItem(R.drawable.ic_fa_tag, MenuGreen, Color.White, "New tag", onNewTag))
        add(DialItem(R.drawable.ic_fa_magnifying_glass, MenuPurple, Color.White, "Search", onSearch))
        if (onManageTags != null) {
            add(DialItem(R.drawable.ic_fa_tags, c.bgElevated, c.text1, "Manage tags", onManageTags))
        }
    }

    Box(Modifier.fillMaxSize()) {
        // backdrop
        AnimatedVisibility(open, enter = fadeIn(tween(180)), exit = fadeOut(tween(120))) {
            Box(Modifier.fillMaxSize().background(if (c.isLime) c.bg.copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.32f)).tap(onClose))
        }

        // FAB: 48dp, end 18dp, bottom = navInset + 62 + 12. Dial buttons: 40dp, centred over it.
        Column(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 18.dp + (fabSize() - 40.dp) / 2, bottom = navInset + 62.dp + 12.dp + fabSize() + 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.End,
        ) {
            // top → bottom, so reverse to put index 0 next to the FAB
            items.asReversed().forEachIndexed { rev, item ->
                val idx = items.size - 1 - rev
                val delay = idx * 40
                AnimatedVisibility(
                    visible = open,
                    enter = fadeIn(tween(150, delay)) +
                        slideInVertically(tween(180, delay)) { it / 2 } +
                        scaleIn(tween(180, delay), initialScale = 0.6f),
                    exit = fadeOut(tween(100)) + scaleOut(tween(100), targetScale = 0.6f),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // label pill
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (c.isLime) c.text1 else c.bgElevated)
                                .border(1.dp, c.borderHi, RoundedCornerShape(50))
                                .tap(item.onClick)
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        ) {
                            InkText(item.label, color = if (c.isLime) c.bg else c.text1, size = 14.sp, weight = FontWeight.Medium)
                        }
                        Spacer(Modifier.width(10.dp))
                        Box(
                            Modifier
                                .shadow(6.dp, CircleShape)
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(item.bg)
                                .tap(item.onClick),
                            contentAlignment = Alignment.Center,
                        ) {
                            FaIcon(item.icon, tint = item.tint, size = 17.dp)
                        }
                    }
                }
            }
        }
    }
}
