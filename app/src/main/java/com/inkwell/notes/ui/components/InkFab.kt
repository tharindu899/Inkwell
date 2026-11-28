package com.inkwell.notes.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.inkwell.notes.ui.theme.Ink

/** Same actions as the web FAB: search, create tag, new notebook, new note. */
@Composable
fun InkFab(
    onNewNote: () -> Unit,
    onNewNotebook: () -> Unit,
    onCreateTag: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Ink.colors
    var open by remember { mutableStateOf(false) }
    var tagDialog by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (open) 135f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "fabRotation",
    )
    val actions: List<Triple<String, ImageVector, () -> Unit>> = listOf(
        Triple("Search notes", Icons.Rounded.Search, onSearch),
        Triple("Create tag", Icons.Rounded.Sell, { tagDialog = true }),
        Triple("New notebook", Icons.Rounded.CreateNewFolder, onNewNotebook),
        Triple("New note", Icons.Rounded.Edit, onNewNote),
    )
    Column(modifier, horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        actions.forEachIndexed { i, (label, icon, action) ->
            AnimatedVisibility(
                visible = open,
                enter = fadeIn(tween(180, delayMillis = i * 40)) +
                    scaleIn(spring(dampingRatio = 0.7f), transformOrigin = TransformOrigin(1f, 1f)) +
                    slideInVertically(tween(260, delayMillis = i * 40)) { it / 2 },
                exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.85f, transformOrigin = TransformOrigin(1f, 1f)),
            ) {
                Row(
                    Modifier.pressScale { open = false; action() },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        label,
                        color = c.text,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(c.surface)
                            .border(1.dp, c.border, CircleShape)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                    Box(
                        Modifier.size(46.dp).clip(CircleShape).background(c.inverse),
                        contentAlignment = Alignment.Center,
                    ) { Icon(icon, label, tint = c.onInverse, modifier = Modifier.size(20.dp)) }
                }
            }
        }
        val fabShape = RoundedCornerShape(22.dp)
        Box(
            Modifier
                .pressScale { open = !open }
                .shadow(10.dp, fabShape, ambientColor = c.accent, spotColor = c.accent)
                .size(58.dp)
                .clip(fabShape)
                .background(c.accent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Add, "Create", tint = c.onAccent, modifier = Modifier.size(28.dp).rotate(rotation))
        }
    }
    if (tagDialog) {
        QuickNameDialog(
            title = "Create tag",
            subtitle = "Open a new note with this tag.",
            placeholder = "Tag name",
            confirmLabel = "Create",
            onConfirm = { tagDialog = false; onCreateTag(it) },
            onDismiss = { tagDialog = false },
        )
    }
}
