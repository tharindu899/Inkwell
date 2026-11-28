package com.inkwell.notes.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inkwell.notes.ui.theme.Ink

/**
 * Port of `.sheet-overlay.show` + `.sheet.show` (final overrides): dim scrim, rounded card
 * floating 14dp above the bottom with a handle and a serif title.
 * Tapping the scrim calls [onDismiss].
 */
@Composable
fun ContextSheet(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Ink.colors
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val shape = RoundedCornerShape(24.dp)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.46f))
            .tap(onDismiss)
            .padding(start = 14.dp, end = 14.dp, bottom = 14.dp + navInset),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .clip(shape)
                .background(c.bg) // web: var(--surface)
                .border(1.dp, c.border, shape)
                .tap { /* swallow taps */ }
                .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 12.dp),
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 4.dp, bottom = 10.dp)
                    .width(38.dp).height(4.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(c.border),
            )
            InkText(
                title,
                family = Ink.serif,
                size = 17.sp,
                weight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
            )
            content()
        }
    }
}

/** `.sh-item` — 48dp row with a 38dp rounded icon tile. */
@Composable
fun SheetItem(@DrawableRes icon: Int, label: String, danger: Boolean = false, onClick: () -> Unit) {
    val c = Ink.colors
    val tint = if (danger) DeleteRed else c.accent
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(16.dp))
            .tap(onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(if (danger) DeleteRed.copy(alpha = 0.12f) else c.bgCard),
            contentAlignment = Alignment.Center,
        ) {
            FaIcon(icon, tint = tint, size = 16.dp)
        }
        Spacer(Modifier.size(12.dp))
        InkText(
            label,
            size = 14.sp,
            weight = FontWeight.ExtraBold,
            color = if (danger) DeleteRed else c.text1,
        )
    }
}

/** `.sh-divider` */
@Composable
fun SheetDivider() {
    Box(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp).height(1.dp).background(Ink.colors.border))
}
