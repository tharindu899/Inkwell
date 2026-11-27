package com.inkwell.notes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inkwell.notes.ui.theme.Ink
import com.inkwell.notes.ui.theme.InkType
import com.inkwell.notes.ui.theme.Lime

/** Rounded lime (or custom colour) header used by Tags, Notebooks, Notebook detail and Search. */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    background: Color = Lime,
    big: Boolean = true,
    onBack: (() -> Unit)? = null,
    iconSlot: (@Composable () -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
    below: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 40.dp, bottomEnd = 40.dp))
            .background(background)
            .statusBarsPadding()
            .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 26.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                CircleIconButton(InkIcon.Back, "Back", onBack)
                Spacer(Modifier.size(12.dp))
            }
            Text("inkwell", style = InkType.Title, color = Ink)
            Spacer(Modifier.weight(1f))
            trailing()
        }
        Spacer(Modifier.height(if (big) 20.dp else 14.dp))
        if (iconSlot != null) {
            iconSlot()
            Spacer(Modifier.height(12.dp))
        }
        Text(
            title,
            style = if (big) InkType.Display.copy(fontSize = 52.sp, lineHeight = 54.sp, letterSpacing = (-2).sp) else InkType.Headline,
            color = Ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (subtitle != null) {
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = InkType.Body, color = Ink)
        }
        below()
    }
}
