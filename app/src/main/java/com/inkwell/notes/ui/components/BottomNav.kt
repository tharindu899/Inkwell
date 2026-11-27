package com.inkwell.notes.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inkwell.notes.R
import com.inkwell.notes.ui.theme.Ink

enum class NavTab(val label: String, @DrawableRes val icon: Int) {
    Home("Home", R.drawable.ic_fa_house),
    AllNotes("Notes", R.drawable.ic_fa_note_sticky),
    Notebooks("Notebook", R.drawable.ic_fa_book_open),
}

/** Port of `BottomNav.jsx` (.bottom-nav / .nav-item, final overrides: 62px + nav-bar inset). */
@Composable
fun BottomNav(active: NavTab?, onSelect: (NavTab) -> Unit, modifier: Modifier = Modifier) {
    val c = Ink.colors
    if (c.isLime) {
        LimeBottomNav(active, onSelect, modifier)
        return
    }
    Box(
        modifier
            .fillMaxWidth()
            .background(c.bg)
            .drawBehind { drawRect(c.border, size = androidx.compose.ui.geometry.Size(size.width, 1.dp.toPx())) }
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        Row(
            Modifier.fillMaxWidth().height(62.dp).padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavTab.entries.forEach { tab ->
                val on = tab == active
                val color = if (on) c.accent else c.text3
                Column(
                    Modifier.weight(1f).height(62.dp).tap { onSelect(tab) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
                ) {
                    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                        FaIcon(tab.icon, tint = color, size = 20.dp)
                    }
                    InkText(
                        text = tab.label.uppercase(),
                        size = 10.sp,
                        weight = FontWeight.ExtraBold,
                        letterSpacing = 0.7.sp,
                        color = color,
                    )
                    Spacer(
                        Modifier.size(4.dp).alpha(if (on) 1f else 0f).clip(CircleShape).background(c.accent),
                    )
                }
            }
        }
    }
}

/** Lime theme: ink-black pill floating inside the same 62dp strip, lime active icon + dot. */
@Composable
private fun LimeBottomNav(active: NavTab?, onSelect: (NavTab) -> Unit, modifier: Modifier) {
    val c = Ink.colors
    Box(
        modifier
            .fillMaxWidth()
            .background(c.bg)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 20.dp, vertical = 4.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(54.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(27.dp))
                .background(c.text1),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavTab.entries.forEach { tab ->
                val on = tab == active
                Column(
                    Modifier.weight(1f).height(54.dp).tap { onSelect(tab) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                ) {
                    FaIcon(tab.icon, tint = if (on) c.hero else androidx.compose.ui.graphics.Color(0xFFC9C9C0), size = 20.dp)
                    Spacer(Modifier.size(5.dp).alpha(if (on) 1f else 0f).clip(CircleShape).background(c.hero))
                }
            }
        }
    }
}
