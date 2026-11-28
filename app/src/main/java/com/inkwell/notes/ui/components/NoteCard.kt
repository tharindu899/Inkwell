package com.inkwell.notes.ui.components

import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
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
 * Pinned cards ([draggablePinned]) show a grip handle; dragging it reports to [onDragStart] /
 * [onDrag] / [onDragEnd] and the screen reorders (web `startPinnedDrag`).
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
    draggablePinned: Boolean = false,
    dragging: Boolean = false,
    onDragStart: (Offset) -> Unit = {},
    onDrag: (Float) -> Unit = {},
    onDragEnd: () -> Unit = {},
    /** Search page: matches of this text are marked in the title, preview and tags (`.highlight-match`). */
    highlight: String = "",
) {
    val c = Ink.colors
    val shape = RoundedCornerShape(Ink.radius)
    val preview = remember(note.content, note.markdown) { notePreview(note.content, note.markdown) }

    // press feedback: transform: scale(.985) while the finger is down
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (dragging) 1.02f else if (pressed) 0.97f else 1f, spring<Float>(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium), label = "cardScale")

    // selected: color-mix(accent 7% into bg-card) fill, accent 42% into border
    val fill = if (selected) lerp(c.bgCard, c.accent, 0.12f) else if (note.pinned) lerp(c.bgCard, c.accent, 0.06f) else c.bgCard
    val stroke = if (selected || dragging) lerp(c.border, c.accent, 0.42f) else c.border

    Box(
        modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
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
            .padding(horizontal = 16.dp, vertical = 14.dp),
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

        // .pin-drag-handle — 28dp circle, top 9 / right 10
        if (draggablePinned && !selectMode) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 1.dp, y = (-1).dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(if (dragging) c.accent else lerp(c.bgCard, c.accent, 0.14f))
                    .pointerInput(note.id) {
                        detectDragGestures(
                            onDragStart = { onDragStart(it) },
                            onDrag = { change, amount ->
                                change.consume()
                                onDrag(amount.y)
                            },
                            onDragEnd = { onDragEnd() },
                            onDragCancel = { onDragEnd() },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                FaIcon(R.drawable.ic_fa_grip_lines, tint = if (dragging) onAccent() else c.accentInk, size = 12.dp)
            }
        }

        // .pin-icon (hidden in select mode and when the drag handle is shown)
        if (note.pinned && !selectMode && !draggablePinned) {
            FaIcon(
                R.drawable.ic_fa_thumbtack,
                tint = c.accentInk,
                size = 14.dp,
                modifier = Modifier.align(Alignment.TopEnd).offset(x = 1.dp, y = (-1).dp),
            )
        }

        androidx.compose.foundation.layout.Column {
            // .n-title
            InkText(
                text = highlighted(note.title.ifEmpty { "Untitled" }, highlight),
                size = 17.sp,
                weight = FontWeight.Bold,
                lineHeight = 22.sp,
                modifier = Modifier.padding(end = if (selectMode) 36.dp else if (draggablePinned) 42.dp else 24.dp),
            )
            Spacer(Modifier.height(4.dp))
            // .n-preview — 2-line clamp
            InkText(
                text = highlighted(if (highlight.isBlank()) preview else preview.take(120), highlight),
                size = 14.sp,
                color = c.text2,
                lineHeight = 21.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(14.dp)) // 6 (margin-bottom) + 6 (.n-meta margin-top)

            // .n-meta
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Row(Modifier.height(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    FaIcon(R.drawable.ic_fa_clock, tint = c.text2, size = 12.dp)
                    Spacer(Modifier.size(4.dp))
                    InkText(
                        text = formatDate(note.updatedAt),
                        size = 12.sp,
                        weight = FontWeight.Medium,
                        color = c.text2,
                    )
                }
                if (notebook != null) {
                    val nbColor = parseColor(notebook.color, c.accent)
                    Tag(
                        text = AnnotatedString(notebook.name),
                        color = nbColor,
                        background = nbColor.copy(alpha = 0.133f),
                        icon = R.drawable.ic_fa_book_open,
                    )
                }
                note.tags.take(2).forEach { t ->
                    Tag(text = highlighted(t, highlight), color = c.accentInk, background = c.accentDim)
                }
            }
        }
    }
}

/** .n-tag */
@Composable
private fun Tag(text: AnnotatedString, color: Color, background: Color, icon: Int? = null) {
    Row(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(background)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            FaIcon(icon, tint = color, size = 11.dp)
            Spacer(Modifier.size(4.dp))
        }
        InkText(text = text, size = 11.sp, weight = FontWeight.Bold, color = color, maxLines = 1)
    }
}

internal fun parseColor(hex: String, fallback: Color): Color = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (_: Exception) {
    fallback
}

/**
 * Web `highlight(str, q)` — every case-insensitive occurrence of [query] gets the
 * `.highlight-match` look (#FFE49A background, dark text in every theme).
 */
internal fun highlighted(text: String, query: String): AnnotatedString {
    val q = query.trim()
    if (q.isEmpty() || text.isEmpty()) return AnnotatedString(text)
    val style = SpanStyle(background = Color(0xFFFFE49A), color = Color(0xFF1C1A17))
    return buildAnnotatedString {
        var last = 0
        for (m in Regex(Regex.escape(q), RegexOption.IGNORE_CASE).findAll(text)) {
            append(text.substring(last, m.range.first))
            withStyle(style) { append(m.value) }
            last = m.range.last + 1
        }
        append(text.substring(last))
    }
}
