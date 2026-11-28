package com.inkwell.notes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.inkwell.notes.model.UserProfile
import com.inkwell.notes.ui.theme.Ink

/** Google profile photo (Coil) on top of an initial-letter circle, so it never looks empty while loading. */
@Composable
fun Avatar(user: UserProfile?, size: Dp, modifier: Modifier = Modifier) {
    val c = Ink.colors
    val initial = (user?.name?.firstOrNull() ?: 'U').uppercase()
    val photo = user?.picture
    Box(modifier.size(size).clip(CircleShape).background(c.inverse), contentAlignment = Alignment.Center) {
        Text(
            initial,
            color = c.onInverse,
            style = MaterialTheme.typography.headlineMedium.copy(fontSize = (size.value * 0.42f).sp),
        )
        if (!photo.isNullOrBlank()) {
            AsyncImage(
                model = photo,
                contentDescription = user?.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
