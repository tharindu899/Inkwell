package com.inkwell.notes.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.inkwell.notes.ui.theme.Ink

/** Tag pill with a count badge. Turns accent-coloured when selected. */
@Composable
fun TagChip(name: String, count: Int, active: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Ink.colors
    val bg by animateColorAsState(if (active) c.accent else c.surface, tween(220), label = "tagBg")
    val fg by animateColorAsState(if (active) c.onAccent else c.text, tween(220), label = "tagFg")
    val badge by animateColorAsState(if (active) c.onAccent.copy(alpha = 0.15f) else c.bg, tween(220), label = "tagBadge")
    Row(
        modifier
            .pressScale(onClick)
            .clip(CircleShape)
            .background(bg)
            .border(1.dp, if (active) bg else c.border, CircleShape)
            .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.Rounded.Sell, null, tint = fg, modifier = Modifier.size(14.dp))
        Text(name, color = fg, style = MaterialTheme.typography.labelLarge)
        Text(
            count.toString(),
            color = fg,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.clip(CircleShape).background(badge).padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
fun CountBadge(count: Int, modifier: Modifier = Modifier) {
    val c = Ink.colors
    Text(
        count.toString(),
        color = c.subText,
        style = MaterialTheme.typography.labelMedium,
        modifier = modifier.clip(CircleShape).background(c.surface).border(1.dp, c.border, CircleShape)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

/** "Title [3]" row used for section headings on Tags. */
@Composable
fun SectionHeader(title: String, count: Int, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = Ink.colors.text, style = MaterialTheme.typography.titleMedium)
        CountBadge(count)
    }
}

private fun buildHighlighted(text: String, query: String, span: SpanStyle): AnnotatedString = buildAnnotatedString {
    val q = query.trim()
    if (q.isEmpty()) {
        append(text)
        return@buildAnnotatedString
    }
    var i = 0
    while (i < text.length) {
        if (i <= text.length - q.length && text.regionMatches(i, q, 0, q.length, ignoreCase = true)) {
            withStyle(span) { append(text.substring(i, i + q.length)) }
            i += q.length
        } else {
            append(text[i])
            i++
        }
    }
}

/** Text with every match of [query] highlighted (same idea as the web highlight()). */
@Composable
fun HighlightedText(
    text: String,
    query: String,
    color: Color,
    style: TextStyle,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
) {
    val accent = Ink.colors.accent
    val annotated = remember(text, query, accent) {
        buildHighlighted(text, query, SpanStyle(background = accent.copy(alpha = 0.45f), fontWeight = FontWeight.Bold))
    }
    Text(annotated, color = color, style = style, modifier = modifier, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}
