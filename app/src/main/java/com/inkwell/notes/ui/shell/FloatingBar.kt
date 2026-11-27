package com.inkwell.notes.ui.shell

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inkwell.notes.ui.components.InkIcon
import com.inkwell.notes.ui.components.InkIconView
import com.inkwell.notes.ui.theme.ErrorTint
import com.inkwell.notes.ui.theme.Ink
import com.inkwell.notes.ui.theme.InkType
import com.inkwell.notes.ui.theme.Lime
import com.inkwell.notes.ui.theme.Paper

/** Black pill at the bottom. Turns into the selection toolbar while notes are being selected. */
@Composable
fun FloatingBar(
    current: String?,
    selectMode: Boolean,
    selectedCount: Int,
    anySelectedPinned: Boolean,
    onNavigate: (String) -> Unit,
    onNew: () -> Unit,
    onCancel: () -> Unit,
    onPin: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 24.dp, end = 24.dp, bottom = 20.dp)
            .height(68.dp)
            .clip(CircleShape)
            .background(Ink),
    ) {
        AnimatedContent(
            targetState = selectMode,
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) },
            label = "bar",
        ) { selecting ->
            if (selecting) {
                SelectionRow(selectedCount, anySelectedPinned, onCancel, onPin, onDelete)
            } else {
                NavRow(current, onNavigate, onNew)
            }
        }
    }
}

@Composable
private fun NavRow(current: String?, onNavigate: (String) -> Unit, onNew: () -> Unit) {
    Row(
        Modifier.fillMaxSize().padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavItem(InkIcon.Home, "Home", current == "home") { onNavigate("home") }
        NavItem(InkIcon.Tag, "Tags", current == "tags") { onNavigate("tags") }
        Box(
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Lime)
                .clickable(role = Role.Button, onClick = onNew)
                .semantics { contentDescription = "New note" },
            contentAlignment = Alignment.Center,
        ) { InkIconView(InkIcon.Plus, Ink, Modifier.size(26.dp), strokeWidth = 2.8f) }
        NavItem(InkIcon.Book, "Notebooks", current == "notebooks") { onNavigate("notebooks") }
        NavItem(InkIcon.User, "Profile", current == "profile") { onNavigate("profile") }
    }
}

@Composable
private fun NavItem(icon: InkIcon, label: String, selected: Boolean, onClick: () -> Unit) {
    val tint by animateColorAsState(if (selected) Lime else Paper.copy(alpha = 0.75f), label = "navTint")
    Box(
        Modifier
            .size(52.dp)
            .clip(CircleShape)
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        InkIconView(icon, tint, Modifier.size(24.dp))
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 6.dp)
                .size(4.dp)
                .clip(CircleShape)
                .background(if (selected) Lime else Color.Transparent),
        )
    }
}

@Composable
private fun SelectionRow(
    count: Int,
    anyPinned: Boolean,
    onCancel: () -> Unit,
    onPin: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier.fillMaxSize().padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onCancel)
                .semantics { contentDescription = "Cancel selection" },
            contentAlignment = Alignment.Center,
        ) { InkIconView(InkIcon.Close, Paper, Modifier.size(20.dp)) }
        Text(
            if (count == 0) "Select notes" else "$count selected",
            style = InkType.Label.copy(fontSize = 14.sp),
            color = Paper,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        ActionPill(if (anyPinned) "Unpin" else "Pin", InkIcon.Pin, Lime, count > 0, onPin)
        ActionPill("Delete", InkIcon.Trash, ErrorTint, count > 0, onDelete)
    }
}

@Composable
private fun ActionPill(label: String, icon: InkIcon, bg: Color, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .height(44.dp)
            .clip(CircleShape)
            .background(bg.copy(alpha = if (enabled) 1f else 0.35f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InkIconView(icon, Ink, Modifier.size(16.dp), strokeWidth = 2.4f)
        Spacer(Modifier.size(6.dp))
        Text(label, style = InkType.Label.copy(fontSize = 14.sp), color = Ink)
    }
}
