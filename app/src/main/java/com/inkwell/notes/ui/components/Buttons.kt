package com.inkwell.notes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inkwell.notes.ui.theme.ErrorTint
import com.inkwell.notes.ui.theme.Ink
import com.inkwell.notes.ui.theme.InkType
import com.inkwell.notes.ui.theme.Lime

@Composable
fun CircleIconButton(
    icon: InkIcon,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
) {
    Box(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .then(if (filled) Modifier.background(Ink) else Modifier.border(2.dp, Ink, CircleShape))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        InkIconView(icon, if (filled) Lime else Ink, Modifier.size(20.dp), strokeWidth = 2.4f)
    }
}

@Composable
fun PillButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
    danger: Boolean = false,
    enabled: Boolean = true,
) {
    val fg = if (!filled) Ink else if (danger) ErrorTint else Lime
    Box(
        modifier
            .height(52.dp)
            .clip(CircleShape)
            .then(
                if (filled) Modifier.background(if (enabled) Ink else Ink.copy(alpha = 0.35f))
                else Modifier.border(2.dp, Ink, CircleShape),
            )
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = InkType.Button.copy(fontSize = 16.sp), color = fg)
    }
}
