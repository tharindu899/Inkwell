package com.inkwell.notes.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inkwell.notes.R
import com.inkwell.notes.data.model.Note
import com.inkwell.notes.data.model.Notebook
import com.inkwell.notes.ui.theme.Ink
import com.inkwell.notes.util.formatDate
import com.inkwell.notes.util.notePreview

/**
 * Port of `NoteCard.jsx` with the Home-page sizes (.home-page .note-card etc.).
 *
 * Multi-select: long-press calls [onLongClick] (the screen decides whether that enters
 * select mode or toggles this card); while [selectMode] is on, a round check chip is shown
 * and the card gets the soft accent tint when [selected].
 * Still to come: pinned drag handle.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun NoteCard(
    note: Note,
    notebook: Notebook?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selectMode: Boolean = false,
    selected: Boolean = false,
    onLongClick: () -> Unit = {},
) {
    val c = Ink.colors
    val shape = RoundedCornerShape(15.dp)
    val preview = remember(note.content, note.markdown) { notePreview(note.content, note.markdown) }

    // press feedback: transform: scale(.985) while the finger is down
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.985f else 1f, tween(150), label = "cardScale")

    // selected: color-mix(accent 7% into bg-card) fill, accent 42% into border
    val fill = if (selected) lerp(c.bgCard, c.accent, 0.07f) else c.bgCard
    val stroke = if (selected) lerp(c.border, c.accent, 0.42f) else c.border

    Box(
        modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(fill)
            .border(1.dp, stroke, shape)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(horizontal = 11.dp, vertical = 10.dp),
    ) {
        // selection check chip (24dp circle, top 10 / right 10)
        if (selectMode) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 1.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (selected) c.accent else c.bgElevated)
                    .border(1.5.dp, if (selected) c.accent else c.border, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) FaIcon(R.drawable.ic_fa_check, tint = onAccent(), size = 11.dp)
            }
        }

        // .pin-icon (hidden in select mode)
        if (note.pinned && !selectMode) {
            FaIcon(
                R.drawable.ic_fa_thumbtack,
                tint = c.accent,
                size = 10.dp,
                modifier = Modifier.align(Alignment.TopEnd).offset(x = 1.dp, y = (-1).dp),
            )
        }

        androidx.compose.foundation.layout.Column {
            // .n-title
            InkText(
                text = note.title.ifEmpty { "Untitled" },
                size = 13.5.sp,
                weight = FontWeight.ExtraBold,
                lineHeight = 17.5.sp,
                modifier = Modifier.padding(end = if (selectMode) 30.dp else 18.dp),
            )
            Spacer(Modifier.height(3.dp))
            // .n-preview — 2-line clamp
            InkText(
                text = preview,
                size = 11.5.sp,
                color = c.text2,
                lineHeight = 16.3.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(12.dp)) // 6 (margin-bottom) + 6 (.n-meta margin-top)

            // .n-meta
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Row(Modifier.height(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    FaIcon(R.drawable.ic_fa_clock, tint = c.text3, size = 10.dp)
                    Spacer(Modifier.size(4.dp))
                    InkText(
                        text = formatDate(note.updatedAt),
                        size = 10.5.sp,
                        weight = FontWeight.SemiBold,
                        color = c.text3,
                    )
                }
                if (notebook != null) {
                    val nbColor = parseColor(notebook.color, c.accent)
                    Tag(
                        text = notebook.name,
                        color = nbColor,
                        background = nbColor.copy(alpha = 0.133f),
                        icon = R.drawable.ic_fa_book_open,
                    )
                }
                note.tags.take(2).forEach { t ->
                    Tag(text = t, color = c.accent, background = c.accentDim)
                }
            }
        }
    }
}

/** .n-tag */
@Composable
private fun Tag(text: String, color: Color, background: Color, icon: Int? = null) {
    Row(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(background)
            .padding(horizontal = 7.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            FaIcon(icon, tint = color, size = 9.dp)
            Spacer(Modifier.size(4.dp))
        }
        InkText(text = text, size = 9.5.sp, weight = FontWeight.ExtraBold, color = color, maxLines = 1)
    }
}

private fun parseColor(hex: String, fallback: Color): Color = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (_: Exception) {
    fallback
}
