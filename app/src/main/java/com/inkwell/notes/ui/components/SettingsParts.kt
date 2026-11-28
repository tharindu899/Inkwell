package com.inkwell.notes.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.inkwell.notes.model.ThemeMode
import com.inkwell.notes.ui.theme.Ink
import com.inkwell.notes.ui.theme.InkDanger
import com.inkwell.notes.ui.theme.paletteFor

/** Rounded card with a small caps label above it. Put SettingsRow items inside. */
@Composable
fun SettingsGroup(
    label: String,
    modifier: Modifier = Modifier,
    labelColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Ink.colors
    val shape = RoundedCornerShape(24.dp)
    Column(modifier.fillMaxWidth()) {
        Text(
            label.uppercase(),
            color = labelColor ?: c.subText,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
        )
        Column(Modifier.fillMaxWidth().clip(shape).background(c.surface).border(1.dp, c.border, shape), content = content)
    }
}

@Composable
fun SettingsDivider() {
    HorizontalDivider(Modifier.padding(start = 72.dp), color = Ink.colors.border)
}

/** One settings line: icon bubble, label, optional sub text, optional trailing widget. kind = pastel index (-1 = neutral). */
@Composable
fun SettingsRow(
    icon: ImageVector,
    label: String,
    sub: String? = null,
    kind: Int = -1,
    danger: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = Ink.colors
    val bubbleBg = when {
        danger -> InkDanger.copy(alpha = 0.15f)
        kind >= 0 -> c.pastels[kind].container
        else -> c.bg
    }
    val bubbleFg = when {
        danger -> InkDanger
        kind >= 0 -> c.pastels[kind].title
        else -> c.text
    }
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(bubbleBg), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = bubbleFg, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(label, color = if (danger) InkDanger else c.text, style = MaterialTheme.typography.titleMedium)
            if (sub != null) Text(sub, color = c.subText, style = MaterialTheme.typography.bodyMedium)
        }
        trailing?.invoke()
    }
}

/** On/off switch with a springy thumb. */
@Composable
fun InkSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val c = Ink.colors
    val track by animateColorAsState(
        if (checked) c.accent else lerp(c.border, c.subText, 0.35f), tween(200), label = "switchTrack",
    )
    val x by animateDpAsState(
        targetValue = if (checked) 22.dp else 0.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "switchThumb",
    )
    Box(
        Modifier
            .toggleable(
                value = checked,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .size(width = 52.dp, height = 30.dp)
            .clip(CircleShape)
            .background(track)
            .padding(3.dp),
    ) {
        Box(Modifier.offset(x = x).size(24.dp).clip(CircleShape).background(Color.White))
    }
}

/** Pill selector with a sliding dark indicator (used for the font size). */
@Composable
fun InkSegmented(items: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = Ink.colors
    val shape = RoundedCornerShape(16.dp)
    BoxWithConstraints(
        modifier.fillMaxWidth().height(46.dp).clip(shape).background(c.bg).border(1.dp, c.border, shape).padding(4.dp),
    ) {
        val segment = maxWidth / items.size
        val x by animateDpAsState(
            targetValue = segment * selected,
            animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow),
            label = "segX",
        )
        Box(Modifier.offset(x = x).width(segment).fillMaxHeight().clip(RoundedCornerShape(12.dp)).background(c.inverse))
        Row(Modifier.fillMaxSize()) {
            items.forEachIndexed { i, label ->
                val on = i == selected
                val fg by animateColorAsState(if (on) c.onInverse else c.subText, label = "segFg")
                Box(Modifier.weight(1f).fillMaxHeight().pressScale { onSelect(i) }, contentAlignment = Alignment.Center) {
                    Text(label, color = fg, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/** Mini preview of a theme. Selected tile gets an accent border and a check. */
@Composable
fun ThemeTile(mode: ThemeMode, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Ink.colors
    val p = paletteFor(mode)
    val line by animateColorAsState(if (selected) c.accent else c.border, tween(220), label = "tileLine")
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.95f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "tileScale",
    )
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier.pressScale(onClick).graphicsLayer { scaleX = scale; scaleY = scale },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(70.dp).clip(shape).background(p.bg).border(2.dp, line, shape).padding(10.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(Modifier.fillMaxWidth(0.7f).height(10.dp).clip(CircleShape).background(p.pastels[0].container))
                Box(Modifier.fillMaxWidth(0.45f).height(10.dp).clip(CircleShape).background(p.pastels[1].container))
            }
            Box(Modifier.align(Alignment.BottomEnd).size(12.dp).clip(CircleShape).background(p.accent))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(mode.label, color = c.text, style = MaterialTheme.typography.labelLarge)
            AnimatedVisibility(selected, enter = scaleIn(), exit = scaleOut()) {
                Icon(Icons.Rounded.Check, null, tint = c.text, modifier = Modifier.size(14.dp))
            }
        }
    }
}
