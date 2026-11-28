package com.inkwell.notes.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.inkwell.notes.data.ToastState
import com.inkwell.notes.model.Note
import com.inkwell.notes.ui.theme.Ink
import com.inkwell.notes.ui.theme.InkDanger
import com.inkwell.notes.ui.theme.Pastel
import kotlinx.coroutines.delay
import kotlin.math.abs

/** Stable pastel per note, so colors don't jump around between recompositions. */
@Composable
fun pastelFor(note: Note): Pastel {
    val list = Ink.colors.pastels
    return list[abs(note.id.hashCode()) % list.size]
}

/** Inner-page top bar: optional back button, serif title, action buttons on the right. */
@Composable
fun InkTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val c = Ink.colors
    Row(
        modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (onBack != null) InkIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
        Text(
            title,
            color = c.text,
            style = MaterialTheme.typography.headlineMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = actions)
    }
}

enum class InkButtonStyle { Primary, Ghost, Danger }

@Composable
fun InkButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: InkButtonStyle = InkButtonStyle.Primary,
) {
    val c = Ink.colors
    val (bg, fg) = when (style) {
        InkButtonStyle.Primary -> c.inverse to c.onInverse
        InkButtonStyle.Ghost -> c.bg to c.text
        InkButtonStyle.Danger -> InkDanger to Color.White
    }
    Box(
        modifier
            .pressScale(onClick)
            .height(48.dp)
            .clip(CircleShape)
            .background(bg)
            .then(if (style == InkButtonStyle.Ghost) Modifier.border(1.dp, c.border, CircleShape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = fg, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 18.dp))
    }
}

/** Single-action FAB (the speed-dial FAB on Home is InkFab). */
@Composable
fun InkFabButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Ink.colors
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier
            .pressScale(onClick)
            .shadow(10.dp, shape, ambientColor = c.accent, spotColor = c.accent)
            .size(58.dp)
            .clip(shape)
            .background(c.accent),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = c.onAccent, modifier = Modifier.size(28.dp))
    }
}

/** Small pill that slides up and disappears after ~2 seconds. Trigger with store.toast.show("..."). */
@Composable
fun InkToastHost(toast: ToastState, modifier: Modifier = Modifier) {
    val c = Ink.colors
    LaunchedEffect(toast.token) {
        if (toast.visible) {
            delay(2200)
            toast.visible = false
        }
    }
    AnimatedVisibility(
        visible = toast.visible,
        modifier = modifier,
        enter = slideInVertically(spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium)) { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
    ) {
        Row(
            Modifier.clip(CircleShape).background(c.inverse).padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Rounded.Check, null, tint = c.onInverse, modifier = Modifier.size(16.dp))
            Text(toast.message, color = c.onInverse, style = MaterialTheme.typography.labelLarge)
        }
    }
}
