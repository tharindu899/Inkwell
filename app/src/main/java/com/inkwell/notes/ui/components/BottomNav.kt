package com.inkwell.notes.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inkwell.notes.R
import com.inkwell.notes.ui.theme.Ink
import com.inkwell.notes.ui.theme.bentoPress

enum class NavTab(val label: String, @DrawableRes val icon: Int) {
    Home("Home", R.drawable.ic_fa_house),
    AllNotes("Notes", R.drawable.ic_fa_note_sticky),
    Notebooks("Notebook", R.drawable.ic_fa_book_open),
}

/**
 * Bento nav: a rounded "pill" tile floating on the page background. The selected tab becomes a
 * lime pill (colour fades in); every tab squishes slightly while pressed.
 * Total height is Ink.navHeight (pill + a 21dp gap above the screen edge); screens use the same constant.
 */
@Composable
fun BottomNav(active: NavTab?, onSelect: (NavTab) -> Unit, modifier: Modifier = Modifier) {
    val c = Ink.colors
    val pill = RoundedCornerShape(Ink.radiusPill)
    val onAcc = onAccent()
    Box(
        modifier
            .fillMaxWidth()
            .background(c.bg)
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(Ink.navHeight)
                .padding(start = Ink.screenPad, end = Ink.screenPad, top = 3.dp, bottom = 21.dp)
                .clip(pill)
                .background(c.bgCard)
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavTab.entries.forEach { tab ->
                val on = tab == active
                val bg by animateColorAsState(if (on) c.accent else Color.Transparent, tween(220), label = "navBg")
                val fg by animateColorAsState(if (on) onAcc else c.text2, tween(220), label = "navFg")
                Row(
                    Modifier
                        .weight(1f)
                        .height(44.dp)
                        .bentoPress { onSelect(tab) }
                        .clip(pill)
                        .background(bg),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FaIcon(tab.icon, tint = fg, size = 18.dp)
                    Spacer(Modifier.width(6.dp))
                    InkText(tab.label, size = 13.sp, weight = FontWeight.Bold, color = fg)
                }
            }
        }
    }
}
