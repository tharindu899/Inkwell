package com.inkwell.notes.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inkwell.notes.R
import com.inkwell.notes.ui.theme.Ink

/** `.create-menu-icon` colours (web `--accent2`, `--green`, purple). */
private val MenuBlue = Color(0xFF5B6AF0)
internal val MenuGreen = Color(0xFF2E9E6B)
private val MenuPurple = Color(0xFF7C4FD4)

/**
 * Port of the Create menu in `Fab.jsx` (.create-menu-backdrop / .create-menu).
 * A dim backdrop plus a rounded sheet that floats 78dp above the bottom nav, right over the FAB.
 * Draw the FAB *after* this so it stays on top (web z-index 200 vs 190).
 */
@Composable
fun CreateMenu(
    open: Boolean,
    onClose: () -> Unit,
    onNewNote: () -> Unit,
    onNewNotebook: () -> Unit,
    onNewTag: () -> Unit,
    onSearch: () -> Unit,
    onImage: () -> Unit,
    onVoice: () -> Unit,
    onChecklist: () -> Unit,
) {
    val c = Ink.colors
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val shape = RoundedCornerShape(24.dp)

    Box(Modifier.fillMaxSize()) {
        // .create-menu-backdrop
        AnimatedVisibility(open, enter = fadeIn(tween(180)), exit = fadeOut(tween(120))) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f)).tap(onClose))
        }

        // .create-menu  (@keyframes sheetUp: fade + 12dp rise + scale .98)
        AnimatedVisibility(
            visible = open,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 12.dp, end = 12.dp, bottom = 62.dp + navInset + 78.dp),
            enter = fadeIn(tween(180)) + slideInVertically(tween(180)) { 12 } + scaleIn(tween(180), initialScale = 0.98f),
            exit = fadeOut(tween(120)),
        ) {
            Column(
                Modifier
                    .shadow(18.dp, shape)
                    .clip(shape)
                    .background(c.bg) // web: var(--surface)
                    .border(1.dp, c.border, shape)
                    .tap { /* swallow taps */ }
                    .verticalScroll(rememberScrollState()) // @media (max-height:620px) scrolls
                    .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 14.dp),
            ) {
                // .create-menu-handle
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(38.dp).height(4.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(c.border),
                )
                Spacer(Modifier.height(10.dp))
                // .create-menu-title
                InkText(
                    "Create New",
                    size = 16.sp,
                    weight = FontWeight.Black,
                    family = Ink.serif,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
                Spacer(Modifier.height(10.dp))

                MenuItem(R.drawable.ic_fa_file_circle_plus, c.accent, onAccent(), "Create new note", "Start a blank note", onNewNote)
                MenuItem(R.drawable.ic_fa_folder_plus, MenuBlue, Color.White, "Create notebook", "Make a new notebook/category", onNewNotebook)
                MenuItem(R.drawable.ic_fa_tag, MenuGreen, Color.White, "Create tag", "Open a new note with this tag", onNewTag)
                MenuItem(R.drawable.ic_fa_magnifying_glass, MenuPurple, Color.White, "Search notes", "Find notes, notebooks, and tags", onSearch)

                // .create-menu-row  (3 equal columns, gap 8, margin 8 4 0)
                Row(
                    Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MiniButton(R.drawable.ic_fa_image, "Image", Modifier.weight(1f), onImage)
                    MiniButton(R.drawable.ic_fa_microphone, "Voice", Modifier.weight(1f), onVoice)
                    MiniButton(R.drawable.ic_fa_square_check, "Checklist", Modifier.weight(1f), onChecklist)
                }
            }
        }
    }
}

/** .create-menu-item — pressed state fills with surface2. */
@Composable
private fun MenuItem(
    @DrawableRes icon: Int,
    iconBg: Color,
    iconTint: Color,
    title: String,
    sub: String,
    onClick: () -> Unit,
) {
    val c = Ink.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (pressed) c.bgCard else Color.Transparent)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // .create-menu-icon — 40dp, radius 13
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(iconBg),
            contentAlignment = Alignment.Center,
        ) {
            FaIcon(icon, tint = iconTint, size = 17.dp)
        }
        Column {
            InkText(title, size = 14.sp, weight = FontWeight.Black, lineHeight = 16.1.sp)
            Spacer(Modifier.height(2.dp))
            InkText(sub, size = 11.sp, weight = FontWeight.Bold, color = c.text3, lineHeight = 13.75.sp)
        }
    }
}

/** .create-mini — pressed: surface3 fill + stronger text. */
@Composable
private fun MiniButton(@DrawableRes icon: Int, label: String, modifier: Modifier, onClick: () -> Unit) {
    val c = Ink.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(14.dp)
    val fg = if (pressed) c.text1 else c.text2
    Row(
        modifier
            .clip(shape)
            .background(if (pressed) c.bgElevated else c.bgCard)
            .border(1.dp, c.border, shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FaIcon(icon, tint = fg, size = 12.dp)
        InkText(label, size = 12.sp, weight = FontWeight.ExtraBold, color = fg, maxLines = 1)
    }
}
