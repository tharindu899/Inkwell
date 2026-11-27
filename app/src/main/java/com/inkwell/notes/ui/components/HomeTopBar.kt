package com.inkwell.notes.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
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
    notes: Int = 0,
    books: Int = 0,
    words: String = "0",
) {
    val c = Ink.colors
    if (c.isLime) {
        LimeHero(greeting, firstName, picture, onAvatarClick, onSearch, onSettings, notes, books, words)
        return
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(c.bg)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // .profile-avatar — 42px circle
        Box(
            Modifier
                .size(42.dp)
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
                    modifier = Modifier.size(42.dp),
                )
            } else {
                InkText(
                    text = firstName.trim().take(1).uppercase().ifEmpty { "U" },
                    family = Ink.serif,
                    size = 16.sp,
                    weight = FontWeight.ExtraBold,
                    color = Color.White,
                )
            }
        }

        Spacer(Modifier.size(10.dp))

        // .app-nav-title-block
        Column(Modifier.weight(1f)) {
            InkText(
                text = buildAnnotatedString {
                    append("$greeting, ")
                    withStyle(SpanStyle(color = c.accent, fontWeight = FontWeight.ExtraBold)) { append(firstName) }
                },
                size = 13.sp,
                weight = FontWeight.SemiBold,
                color = c.text2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            InkText(
                text = "My Notes",
                family = Ink.serif,
                size = 22.sp,
                weight = FontWeight.Bold,
                lineHeight = 23.sp,
            )
        }

        // .top-bar-actions
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBtn(R.drawable.ic_fa_magnifying_glass, onSearch)
            Spacer(Modifier.size(7.dp))
            IconBtn(R.drawable.ic_fa_gear, onSettings)
        }
    }
}

/** .icon-btn — 38px circle, 15px glyph */
@Composable
internal fun IconBtn(icon: Int, onClick: () -> Unit) {
    val c = Ink.colors
    Box(
        Modifier
            .size(if (c.isLime) 42.dp else 38.dp)
            .clip(CircleShape)
            .background(if (c.isLime) androidx.compose.ui.graphics.Color.Transparent else c.bgCard)
            .then(if (c.isLime) Modifier.border(2.dp, c.text1, CircleShape) else Modifier)
            .tap(onClick),
        contentAlignment = Alignment.Center,
    ) {
        FaIcon(icon, tint = if (c.isLime) c.text1 else c.text2, size = if (c.isLime) 17.dp else 15.dp)
    }
}

/** Lime theme: big lime block with title, greeting, name and the notes counter. */
@Composable
private fun LimeHero(
    greeting: String, firstName: String, picture: String,
    onAvatarClick: () -> Unit, onSearch: () -> Unit, onSettings: () -> Unit,
    notes: Int, books: Int, words: String,
) {
    val c = Ink.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp))
            .background(c.hero)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 22.dp, end = 22.dp, top = 12.dp, bottom = 22.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            InkText("inkwell", Modifier.weight(1f), color = c.onHero, size = 19.sp, weight = FontWeight.Bold)
            IconBtn(R.drawable.ic_fa_magnifying_glass, onSearch)
            Spacer(Modifier.size(8.dp))
            IconBtn(R.drawable.ic_fa_gear, onSettings)
            Spacer(Modifier.size(8.dp))
            Box(
                Modifier.size(42.dp).clip(CircleShape).background(c.onHero).tap(onAvatarClick),
                contentAlignment = Alignment.Center,
            ) {
                if (picture.isNotBlank()) {
                    AsyncImage(model = picture, contentDescription = firstName, contentScale = ContentScale.Crop, modifier = Modifier.size(42.dp))
                } else {
                    InkText(firstName.trim().take(1).uppercase().ifEmpty { "U" }, color = c.hero, size = 17.sp, weight = FontWeight.ExtraBold)
                }
            }
        }
        Spacer(Modifier.height(22.dp))
        InkText("$greeting,", color = c.onHero, size = 17.sp)
        InkText(firstName, color = c.onHero, size = 34.sp, weight = FontWeight.Bold, letterSpacing = (-1).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            InkText(notes.toString(), color = c.onHero, size = 96.sp, weight = FontWeight.ExtraBold, letterSpacing = (-4).sp, lineHeight = 92.sp)
            Spacer(Modifier.size(12.dp))
            Column(Modifier.padding(bottom = 8.dp)) {
                InkText(if (notes == 1) "note" else "notes", color = c.onHero, size = 21.sp, weight = FontWeight.Bold)
                InkText("$books books · $words words", color = c.onHero, size = 14.sp)
            }
        }
    }
}
