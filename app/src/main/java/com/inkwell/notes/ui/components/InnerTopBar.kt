package com.inkwell.notes.ui.components

import com.inkwell.notes.ui.theme.bentoPress
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inkwell.notes.R
import com.inkwell.notes.ui.theme.Ink

/**
 * Port of `TopBar.jsx` → InnerTopBar (.top-bar.app-nav.inner-nav):
 * round back button on the left, serif title centred, [actions] on the right.
 * Used by every screen except Home.
 */
@Composable
fun InnerTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val c = Ink.colors
    Box(
        modifier
            .fillMaxWidth()
            .background(c.bg)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = Ink.screenPad, vertical = 10.dp),
    ) {
        // back button: 44dp rounded-square tile
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .size(44.dp)
                .bentoPress(onBack)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(Ink.radiusSm))
                .background(c.bgCard),
            contentAlignment = Alignment.Center,
        ) {
            FaIcon(R.drawable.ic_fa_arrow_left, tint = c.text1, size = 16.dp)
        }

        // .top-bar-title — centred, max 45% of the width
        InkText(
            text = title,
            family = Ink.sans,
            size = 20.sp,
            weight = FontWeight.Bold,
            align = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.Center).fillMaxWidth(0.45f),
        )

        // .top-bar-actions
        Row(
            Modifier.align(Alignment.CenterEnd),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = actions,
        )
    }
}
