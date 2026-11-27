package com.inkwell.notes.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inkwell.notes.InkwellApplication
import com.inkwell.notes.auth.UserSession
import com.inkwell.notes.ui.components.InkIcon
import com.inkwell.notes.ui.components.InkIconView
import com.inkwell.notes.ui.theme.Ink
import com.inkwell.notes.ui.theme.InkMuted
import com.inkwell.notes.ui.theme.InkType
import com.inkwell.notes.ui.theme.Lime
import com.inkwell.notes.ui.theme.Paper
import com.inkwell.notes.util.SystemBars

/** Shared "this screen arrives in a later part" layout — lime header + dark card. */
@Composable
fun ComingSoonScreen(
    title: String,
    message: String,
    part: String,
    bottomSpace: Dp = 0.dp,
    onBack: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    SystemBars(lightNavIcons = false)
    Column(Modifier.fillMaxSize().background(Paper)) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 40.dp, bottomEnd = 40.dp))
                .background(Lime)
                .statusBarsPadding()
                .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 28.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) {
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .border(2.dp, Ink, CircleShape)
                            .clickable(role = Role.Button, onClick = onBack)
                            .semantics { contentDescription = "Back" },
                        contentAlignment = Alignment.Center,
                    ) { InkIconView(InkIcon.Close, Ink, Modifier.size(18.dp), strokeWidth = 2.6f) }
                    Spacer(Modifier.size(12.dp))
                }
                Text("inkwell", style = InkType.Title, color = Ink)
            }
            Spacer(Modifier.height(24.dp))
            Text(
                title,
                style = InkType.Display.copy(fontSize = 52.sp, lineHeight = 52.sp, letterSpacing = (-2).sp),
                color = Ink,
                maxLines = 2,
            )
        }
        Column(Modifier.weight(1f).padding(24.dp).padding(bottom = bottomSpace)) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(Ink)
                    .padding(22.dp),
            ) {
                Text(part, style = InkType.Title, color = Lime)
                Spacer(Modifier.height(6.dp))
                Text(message, style = InkType.Body, color = InkMuted)
            }
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
fun ProfilePlaceholder(user: UserSession, onSignOut: () -> Unit, bottomSpace: Dp) {
    ComingSoonScreen(
        title = user.name,
        message = "${user.email}\nProfile, settings and Drive sync arrive in Part 6.",
        part = "Profile",
        bottomSpace = bottomSpace,
    ) {
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(CircleShape)
                .border(2.dp, Ink, CircleShape)
                .clickable(role = Role.Button, onClick = onSignOut),
            contentAlignment = Alignment.Center,
        ) { Text("Sign out", style = InkType.Button, color = Ink) }
    }
}

@Composable
fun EditorPlaceholder(noteId: String, notebookId: String?, onBack: () -> Unit) {
    val repo = (LocalContext.current.applicationContext as InkwellApplication).repo
    val note = if (noteId == "new") null else repo.getNote(noteId)
    val notebook = notebookId?.let { id -> repo.notebooks.value.firstOrNull { it.id == id } }
    ComingSoonScreen(
        title = note?.title?.ifBlank { "Untitled" } ?: "New note",
        message = when {
            note != null -> "${note.wordCount} words · the full editor (formatting, checklists, code blocks, images) arrives in the last part."
            notebook != null -> "New note in \"${notebook.name}\". The note editor arrives in the last part."
            else -> "The note editor arrives in the last part."
        },
        part = "Editor",
        onBack = onBack,
    )
}
