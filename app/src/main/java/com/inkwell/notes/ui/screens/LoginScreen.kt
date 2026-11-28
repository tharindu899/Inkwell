package com.inkwell.notes.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.inkwell.notes.ui.components.GoogleLogo
import com.inkwell.notes.ui.components.InkDropLogo
import com.inkwell.notes.ui.components.pressScale
import com.inkwell.notes.ui.components.staggeredEntrance
import com.inkwell.notes.ui.theme.DisplayFont
import com.inkwell.notes.ui.theme.Ink
import com.inkwell.notes.ui.theme.InkDanger

@Composable
fun LoginScreen(signingIn: Boolean, error: String?, setupRequired: Boolean = false, onSignIn: () -> Unit) {
    val c = Ink.colors
    val drift = rememberInfiniteTransition(label = "blobs")
    val a by drift.animateFloat(
        0f, 1f, infiniteRepeatable(tween(7000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "blobA",
    )
    val b by drift.animateFloat(
        0f, 1f, infiniteRepeatable(tween(9000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "blobB",
    )

    Box(Modifier.fillMaxSize().background(c.bg)) {
        // Two slow-floating soft blobs (like the web login background).
        Box(
            Modifier
                .size(340.dp)
                .graphicsLayer { translationX = -120.dp.toPx() + a * 70.dp.toPx(); translationY = -110.dp.toPx() + a * 50.dp.toPx() }
                .background(Brush.radialGradient(listOf(c.accent.copy(alpha = 0.28f), Color.Transparent)), CircleShape),
        )
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .size(380.dp)
                .graphicsLayer { translationX = 140.dp.toPx() - b * 80.dp.toPx(); translationY = 150.dp.toPx() - b * 60.dp.toPx() }
                .background(Brush.radialGradient(listOf(c.pastels[2].container.copy(alpha = 0.9f), Color.Transparent)), CircleShape),
        )

        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier.staggeredEntrance(0).size(84.dp).clip(RoundedCornerShape(28.dp)).background(c.pastels[0].container),
                contentAlignment = Alignment.Center,
            ) { InkDropLogo(Modifier.size(46.dp), c.pastels[0].title) }

            Spacer(Modifier.height(18.dp))
            Text(
                "Inkwell",
                color = c.text,
                style = MaterialTheme.typography.displayLarge,
                modifier = Modifier.staggeredEntrance(1),
            )
            Text(
                "Your notes. Synced. Private.",
                color = c.subText,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.staggeredEntrance(2),
            )

            Spacer(Modifier.height(28.dp))
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Feature(Icons.Rounded.AutoAwesome, "Write beautifully, anywhere", Modifier.staggeredEntrance(3))
                Feature(Icons.Rounded.CloudSync, "Auto-sync via Google Drive", Modifier.staggeredEntrance(4))
                Feature(Icons.Rounded.Lock, "Your data stays yours", Modifier.staggeredEntrance(5))
            }

            Spacer(Modifier.height(32.dp))
            if (setupRequired) {
                SetupWarning(Modifier.staggeredEntrance(6))
            } else {
                GoogleButton(signingIn, onSignIn, Modifier.staggeredEntrance(6))
            }

            if (error != null) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Rounded.ErrorOutline, null, tint = InkDanger, modifier = Modifier.size(16.dp))
                    Text(error, color = InkDanger, style = MaterialTheme.typography.bodyMedium)
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(
                "Your notes are stored locally and optionally backed up to your own Google Drive — never our servers.",
                color = c.subText,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.staggeredEntrance(7),
            )
        }
    }
}

@Composable
private fun Feature(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    val c = Ink.colors
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(13.dp)).background(c.surface).border(1.dp, c.border, RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = c.text, modifier = Modifier.size(19.dp))
        }
        Text(text, color = c.text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun GoogleButton(signingIn: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Ink.colors
    Box(
        modifier
            .fillMaxWidth()
            .then(if (signingIn) Modifier else Modifier.pressScale(onClick))
            .height(56.dp)
            .clip(CircleShape)
            .background(c.surface)
            .border(1.dp, c.border, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(signingIn, animationSpec = tween(200), label = "googleBtn") { busy ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = c.text, strokeWidth = 2.dp)
                    Text("Signing in…", color = c.text, style = MaterialTheme.typography.labelLarge)
                } else {
                    GoogleLogo(20.dp)
                    Text("Sign in with Google", color = c.text, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun SetupWarning(modifier: Modifier = Modifier) {
    val c = Ink.colors
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier.fillMaxWidth().clip(shape).background(c.surface).border(1.dp, InkDanger, shape).padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Rounded.ErrorOutline, null, tint = InkDanger, modifier = Modifier.size(20.dp))
        Column {
            Text("Setup required", color = c.text, style = MaterialTheme.typography.titleMedium)
            Text(
                "Add your Google web client ID as the GitHub secret VITE_GOOGLE_CLIENT_ID and rebuild.",
                color = c.subText, style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
