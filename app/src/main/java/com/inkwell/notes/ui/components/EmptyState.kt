package com.inkwell.notes.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inkwell.notes.ui.theme.Ink

/** Port of `components/EmptyState.jsx` (.empty-state). */
@Composable
fun EmptyState(
    @DrawableRes icon: Int,
    title: String,
    sub: String = "",
    action: (@Composable () -> Unit)? = null,
) {
    val c = Ink.colors
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 52.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        FaIcon(icon, tint = c.text3, size = 38.dp)
        Spacer(Modifier.height(16.dp))
        InkText(title, family = Ink.serif, size = 16.sp, color = c.text2, align = TextAlign.Center)
        if (sub.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            InkText(sub, size = 13.sp, color = c.text3, align = TextAlign.Center, lineHeight = 20.sp)
        }
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            action()
        }
    }
}
