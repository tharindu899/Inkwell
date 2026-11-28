package com.inkwell.notes.ui.components

import com.inkwell.notes.ui.theme.bentoPress
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.IntOffset
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedVisibility
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
    val shape = RoundedCornerShape(Ink.radiusLg)
    // the sheet springs up from the bottom while the scrim fades in
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val scrim by animateFloatAsState(if (shown) 0.55f else 0f, tween(200), label = "scrim")
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = scrim))
            .tap(onDismiss)
            .padding(start = 10.dp, end = 10.dp, bottom = 10.dp + navInset),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visible = shown,
            enter = fadeIn(tween(150)) + slideInVertically(spring<IntOffset>(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) { it / 2 },
        ) {
        Column(
            Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .clip(shape)
                .background(c.bg) // web: var(--surface)
                .border(1.dp, c.borderHi, shape)
                .tap { /* swallow taps */ }
                .padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 14.dp),
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 4.dp, bottom = 10.dp)
                    .width(38.dp).height(4.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(c.borderHi),
            )
            InkText(
                title,
                family = Ink.sans,
                size = 20.sp,
                weight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
            )
            content()
        }
        }
    }
}

/** `.sh-item` — 48dp row with a 38dp rounded icon tile. */
@Composable
fun SheetItem(@DrawableRes icon: Int, label: String, danger: Boolean = false, onClick: () -> Unit) {
    val c = Ink.colors
    val tint = if (danger) DeleteRed else c.accentInk
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .heightIn(min = 48.dp)
            .bentoPress(onClick)
            .clip(RoundedCornerShape(Ink.radiusSm))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (danger) DeleteRed.copy(alpha = 0.12f) else c.bgCard),
            contentAlignment = Alignment.Center,
        ) {
            FaIcon(icon, tint = tint, size = 16.dp)
        }
        Spacer(Modifier.size(12.dp))
        InkText(
            label,
            size = 15.sp,
            weight = FontWeight.Bold,
            color = if (danger) DeleteRed else c.text1,
        )
    }
}

/** `.sh-divider` */
@Composable
fun SheetDivider() {
    Box(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp).height(1.dp).background(Ink.colors.border))
}
