package com.inkwell.notes.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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

data class NavTab(val route: String, val label: String, val icon: ImageVector)

/** Floating pill bar. The dark pill slides (with a spring) to the selected tab. */
@Composable
fun InkBottomBar(tabs: List<NavTab>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = Ink.colors
    val shape = RoundedCornerShape(33.dp)
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(66.dp)
            .clip(shape)
            .background(c.surface)
            .border(1.dp, c.border, shape)
            .padding(6.dp),
    ) {
        val tabWidth = maxWidth / tabs.size
        val x by animateDpAsState(
            targetValue = tabWidth * selected,
            animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
            label = "tabX",
        )
        Box(Modifier.offset(x = x).width(tabWidth).fillMaxHeight().clip(CircleShape).background(c.inverse))
        Row(Modifier.fillMaxSize()) {
            tabs.forEachIndexed { i, tab ->
                val on = i == selected
                val fg by animateColorAsState(if (on) c.onInverse else c.subText, label = "tabFg")
                Column(
                    Modifier.weight(1f).fillMaxHeight().pressScale { onSelect(i) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(tab.icon, tab.label, tint = fg, modifier = Modifier.size(22.dp))
                    Text(tab.label, color = fg, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
