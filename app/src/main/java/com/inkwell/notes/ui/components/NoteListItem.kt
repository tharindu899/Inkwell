package com.inkwell.notes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.inkwell.notes.model.Note
import com.inkwell.notes.ui.theme.Ink
import com.inkwell.notes.util.formatRelative
import com.inkwell.notes.util.stripHtml

/** Full-width note row used in Notebook detail (and later Tags / Search results). */
@Composable
fun NoteListItem(note: Note, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Ink.colors
    val p = pastelFor(note)
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier
            .fillMaxWidth()
            .pressScale(onClick)
            .clip(shape)
            .background(c.surface)
            .border(1.dp, c.border, shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(p.body))
            Text(
                note.title.ifBlank { "Untitled" },
                color = c.text,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (note.pinned) Icon(Icons.Rounded.PushPin, "Pinned", tint = c.subText, modifier = Modifier.size(14.dp))
        }
        Text(
            note.content.stripHtml(),
            color = c.subText,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            note.tags.take(2).forEach { tag ->
                Text(
                    "#$tag",
                    color = p.title,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.clip(CircleShape).background(p.container).padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Text(formatRelative(note.updatedAt), color = c.subText, style = MaterialTheme.typography.labelMedium)
        }
    }
}
