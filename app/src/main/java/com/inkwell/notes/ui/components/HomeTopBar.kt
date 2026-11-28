package com.inkwell.notes.ui.components

import com.inkwell.notes.ui.theme.bentoPress
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.inkwell.notes.R
import com.inkwell.notes.ui.theme.Ink

/** Port of `TopBar.jsx` → HomeTopBar (.top-bar.app-nav.home-nav). Sync dot comes with Drive sync. */
@Composable
fun HomeTopBar(
    greeting: String,
    firstName: String,
    picture: String,
    onAvatarClick: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
) {
    val c = Ink.colors
    Row(
        Modifier
            .fillMaxWidth()
            .background(c.bg)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = Ink.screenPad, end = Ink.screenPad, top = 10.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // .profile-avatar — 42px circle
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(c.accent, lerp(c.accent, Color.White, 0.25f))))
                .tap(onAvatarClick),
            contentAlignment = Alignment.Center,
        ) {
            if (picture.isNotBlank()) {
                AsyncImage(
                    model = picture,
                    contentDescription = firstName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(48.dp),
                )
            } else {
                InkText(
                    text = firstName.trim().take(1).uppercase().ifEmpty { "U" },
                    family = Ink.serif,
                    size = 18.sp,
                    weight = FontWeight.Bold,
                    color = onAccent(),
                )
            }
        }

        Spacer(Modifier.size(10.dp))

        // .app-nav-title-block
        Column(Modifier.weight(1f)) {
            InkText(
                text = buildAnnotatedString {
                    append("$greeting, ")
                    withStyle(SpanStyle(color = c.accentInk, fontWeight = FontWeight.Bold)) { append(firstName) }
                },
                size = 14.sp,
                weight = FontWeight.Medium,
                color = c.text2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            InkText(
                text = "My Notes",
                family = Ink.sans,
                size = 32.sp,
                weight = FontWeight.Bold,
                lineHeight = 36.sp,
                letterSpacing = (-0.5).sp,
            )
        }

        // .top-bar-actions
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBtn(R.drawable.ic_fa_magnifying_glass, onSearch)
            Spacer(Modifier.size(8.dp))
            IconBtn(R.drawable.ic_fa_gear, onSettings)
        }
    }
}

/** Rounded-square tile button (44dp) with a spring press. */
@Composable
internal fun IconBtn(icon: Int, onClick: () -> Unit) {
    val c = Ink.colors
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(Ink.radiusSm)
    Box(
        Modifier.size(44.dp).bentoPress(onClick).clip(shape).background(c.bgCard),
        contentAlignment = Alignment.Center,
    ) {
        FaIcon(icon, tint = c.text1, size = 17.dp)
    }
}
