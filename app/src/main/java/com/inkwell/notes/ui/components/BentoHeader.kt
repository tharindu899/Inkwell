package com.inkwell.notes.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inkwell.notes.ui.theme.Ink
import com.inkwell.notes.ui.theme.bentoPress

/** Big left-aligned screen title (32sp) with a small subtitle above it and tile buttons on the right. */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val c = Ink.colors
    Row(
        modifier
            .fillMaxWidth()
            .background(c.bg)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = Ink.screenPad, end = Ink.screenPad, top = 10.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            if (subtitle != null) {
                InkText(subtitle, size = 14.sp, weight = FontWeight.Medium, color = c.text2, maxLines = 1)
                Spacer(Modifier.height(2.dp))
            }
            InkText(
                title,
                size = 32.sp,
                weight = FontWeight.Bold,
                lineHeight = 36.sp,
                letterSpacing = (-0.5).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = actions,
        )
    }
}

/** Pill chip used for filters/sort/tags. The selected chip fades to lime; presses spring. */
@Composable
fun BentoChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    count: Int? = null,
    height: Dp = 36.dp,
    maxLabelWidth: Dp = 180.dp,
) {
    val c = Ink.colors
    val pill = RoundedCornerShape(Ink.radiusPill)
    val onAcc = onAccent()
    val bg by animateColorAsState(if (selected) c.accent else c.bgCard, tween(220), label = "chipBg")
    val fg by animateColorAsState(if (selected) onAcc else c.text2, tween(220), label = "chipFg")
    Row(
        modifier
            .height(height)
            .bentoPress(onClick)
            .clip(pill)
            .background(bg)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            FaIcon(icon, tint = fg, size = 12.dp)
            Spacer(Modifier.size(6.dp))
        }
        InkText(
            label,
            size = 13.sp,
            weight = FontWeight.Bold,
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = maxLabelWidth),
        )
        if (count != null) {
            Spacer(Modifier.size(8.dp))
            Box(
                Modifier
                    .clip(pill)
                    .background(if (selected) onAcc.copy(alpha = 0.15f) else c.bgElevated)
                    .padding(horizontal = 7.dp, vertical = 1.dp),
            ) {
                InkText(count.toString(), size = 11.sp, weight = FontWeight.Bold, color = fg)
            }
        }
    }
}
