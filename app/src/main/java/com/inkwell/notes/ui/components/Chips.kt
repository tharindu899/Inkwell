package com.inkwell.notes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inkwell.notes.ui.theme.Ink
import com.inkwell.notes.ui.theme.InkType
import com.inkwell.notes.ui.theme.Lime

/** Pill chip. Active = filled (default black + lime text, or a custom colour + black text). */
@Composable
fun InkChip(
    label: String,
    on: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: InkIcon? = null,
    count: Int? = null,
    activeBg: Color = Ink,
    activeFg: Color = Lime,
) {
    val fg = if (on) activeFg else Ink
    Row(
        modifier
            .clip(CircleShape)
            .then(if (on) Modifier.background(activeBg) else Modifier.border(2.dp, Ink, CircleShape))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            InkIconView(icon, fg, Modifier.size(14.dp), strokeWidth = 2.6f)
            Spacer(Modifier.size(6.dp))
        }
        Text(label, style = InkType.Label.copy(fontSize = 14.sp), color = fg, maxLines = 1)
        if (count != null) {
            Spacer(Modifier.size(8.dp))
            Text(
                "$count",
                style = InkType.Label.copy(fontSize = 12.sp),
                color = if (on) activeBg else Ink.copy(alpha = 0.75f),
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (on) activeFg else Ink.copy(alpha = 0.1f))
                    .padding(horizontal = 7.dp, vertical = 1.dp),
            )
        }
    }
}
