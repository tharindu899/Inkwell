package com.inkwell.notes.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.inkwell.notes.ui.theme.Ink
import com.inkwell.notes.ui.theme.Pastel
import com.inkwell.notes.util.fmtNum

@Composable
fun InkIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Ink.colors
    Box(
        modifier
            .pressScale(onClick)
            .size(44.dp)
            .clip(CircleShape)
            .background(c.surface)
            .border(1.dp, c.border, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = c.text, modifier = Modifier.size(20.dp))
    }
}

/** Filter / sort pill. The colors fade when it becomes selected. */
@Composable
fun InkChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Ink.colors
    val bg by animateColorAsState(if (selected) c.inverse else c.surface, tween(220), label = "chipBg")
    val fg by animateColorAsState(if (selected) c.onInverse else c.text, tween(220), label = "chipFg")
    Text(
        text,
        color = fg,
        style = MaterialTheme.typography.labelLarge,
        modifier = modifier
            .pressScale(onClick)
            .clip(CircleShape)
            .background(bg)
            .border(1.dp, c.border, CircleShape)
            .padding(horizontal = 15.dp, vertical = 9.dp),
    )
}

@Composable
fun PastelCard(
    pastel: Pastel,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val base = if (onClick != null) modifier.pressScale(onClick) else modifier
    Column(
        base.clip(RoundedCornerShape(26.dp)).background(pastel.container).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

/** Small stat tile. The number counts up when it first shows. */
@Composable
fun StatCard(value: Int, label: String, modifier: Modifier = Modifier) {
    val c = Ink.colors
    val shown by animateIntAsState(value, tween(900), label = "stat")
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier.clip(shape).background(c.surface).border(1.dp, c.border, shape).padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(fmtNum(shown), color = c.text, style = MaterialTheme.typography.titleLarge)
        Text(label, color = c.subText, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        color = Ink.colors.subText,
        style = MaterialTheme.typography.labelSmall,
        modifier = modifier,
    )
}

@Composable
fun EmptyState(title: String, subtitle: String, modifier: Modifier = Modifier) {
    val c = Ink.colors
    Column(modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, color = c.text, style = MaterialTheme.typography.headlineSmall)
        Text(subtitle, color = c.subText, style = MaterialTheme.typography.bodyMedium)
    }
}
