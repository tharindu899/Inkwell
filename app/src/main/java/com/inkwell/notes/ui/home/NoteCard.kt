package com.inkwell.notes.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.inkwell.notes.data.Note
import com.inkwell.notes.data.Notebook
import com.inkwell.notes.ui.components.InkIcon
import com.inkwell.notes.ui.components.InkIconView
import com.inkwell.notes.ui.theme.Ink
import com.inkwell.notes.ui.theme.InkType
import com.inkwell.notes.ui.theme.Lilac
import com.inkwell.notes.ui.theme.Lime
import com.inkwell.notes.ui.theme.Peach
import com.inkwell.notes.util.formatDate
import com.inkwell.notes.util.notePreview
import kotlinx.coroutines.delay

private class CardPalette(val bg: Color, val fg: Color, val sub: Color, val outlined: Boolean = false)

private val palettes = listOf(
    CardPalette(Ink, Lime, Color(0xFFC4C4BC)),
    CardPalette(Lilac, Ink, Color(0xFF3A3A36)),
    CardPalette(Color.White, Ink, Color(0xFF4A4A45), outlined = true),
    CardPalette(Peach, Ink, Color(0xFF3A3A36)),
    CardPalette(Lime, Ink, Color(0xFF3A3A36)),
)

// colour is derived from the note id, so it never changes when notes are re-sorted
private fun paletteFor(id: String) = palettes[(id.hashCode() and 0x7fffffff) % palettes.size]

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteCard(
    note: Note,
    notebook: Notebook?,
    index: Int,
    wide: Boolean,
    selectMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = paletteFor(note.id)
    val shape = RoundedCornerShape(28.dp)
    val preview = remember(note.id, note.updatedAt) { notePreview(note) }

    // entrance animation — plays once per card
    var played by rememberSaveable(note.id) { mutableStateOf(false) }
    val appear = remember { Animatable(if (played) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!played) {
            delay(index.coerceAtMost(8) * 60L)
            appear.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow))
            played = true
        }
    }

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else if (selected) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "cardScale",
    )
    val haptics = LocalHapticFeedback.current
    val selBorder = if (c.bg == Ink) Lime else Ink

    Box(
        modifier
            .graphicsLayer {
                alpha = appear.value.coerceIn(0f, 1f)
                translationY = (1f - appear.value) * 40.dp.toPx()
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(c.bg)
            .then(if (c.outlined) Modifier.border(2.dp, Ink, shape) else Modifier)
            .then(if (selected) Modifier.border(4.dp, selBorder, shape) else Modifier)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
            ),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                text = note.title.ifBlank { "Untitled" },
                style = InkType.Title,
                color = c.fg,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = if (note.pinned || selectMode) 26.dp else 0.dp),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = preview,
                style = InkType.Label,
                color = c.sub,
                maxLines = if (wide) 3 else 4,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(14.dp))
            Text(formatDate(note.updatedAt), style = InkType.Label, color = c.sub)
            val chip = notebook?.name ?: note.tags.firstOrNull()
            if (chip != null) {
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier
                        .clip(CircleShape)
                        .border(1.5.dp, c.fg.copy(alpha = 0.5f), CircleShape)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (notebook != null) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(parseColor(notebook.color, c.fg)))
                        Spacer(Modifier.size(6.dp))
                    }
                    Text(chip, style = InkType.Label, color = c.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        // top-right badge: check circle in select mode, pin otherwise
        Box(Modifier.align(Alignment.TopEnd).padding(12.dp)) {
            if (selectMode) {
                Box(
                    Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(if (selected) selBorder else Color.Transparent)
                        .border(2.dp, if (selected) selBorder else c.fg, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) InkIconView(InkIcon.Check, c.bg, Modifier.size(14.dp), strokeWidth = 3f)
                }
            } else if (note.pinned) {
                InkIconView(InkIcon.Pin, c.fg, Modifier.size(18.dp), strokeWidth = 2f)
            }
        }
    }
}

private fun parseColor(hex: String, fallback: Color): Color =
    try { Color(android.graphics.Color.parseColor(hex)) } catch (_: Exception) { fallback }
