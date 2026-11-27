package com.inkwell.notes.ui.login

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.inkwell.notes.auth.AuthUiState
import com.inkwell.notes.ui.theme.ErrorTint
import com.inkwell.notes.ui.theme.Ink
import com.inkwell.notes.ui.theme.InkLine
import com.inkwell.notes.ui.theme.InkMuted
import com.inkwell.notes.ui.theme.InkType
import com.inkwell.notes.ui.theme.Lime
import com.inkwell.notes.ui.theme.Paper
import com.inkwell.notes.util.SystemBars
import kotlinx.coroutines.delay

/**
 * Login — "Bold Sheet" design.
 * Lime hero with a big springing "Ink" wordmark + dark sheet that slides up.
 * Only Google sign-in (same as the old app).
 */
@Composable
fun LoginScreen(
    ui: AuthUiState,
    hasClientId: Boolean,
    onSignIn: () -> Unit,
) {
    SystemBars(lightNavIcons = true)
    Box(Modifier.fillMaxSize().background(Lime)) {
        Hero()
        SignInSheet(
            ui = ui,
            hasClientId = hasClientId,
            onSignIn = onSignIn,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

// ───────────────────────── Hero ─────────────────────────

@Composable
private fun Hero() {
    Column(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("inkwell", style = InkType.Title, color = Ink)
            Box(
                Modifier
                    .border(2.dp, Ink, CircleShape)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Text("synced · private", style = InkType.Label, color = Ink)
            }
        }
        Spacer(Modifier.height(36.dp))
        InkWordmark()
    }
}

@Composable
private fun InkWordmark() {
    val letters = listOf("I", "n", "k")
    Row(verticalAlignment = Alignment.Bottom) {
        letters.forEachIndexed { i, ch ->
            val p = remember { Animatable(0f) }
            LaunchedEffect(Unit) {
                delay(150L + i * 90L)
                p.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow))
            }
            Text(
                text = ch,
                style = InkType.Display,
                color = Ink,
                modifier = Modifier.graphicsLayer {
                    translationY = (1f - p.value) * 140.dp.toPx()
                    alpha = p.value.coerceIn(0f, 1f)
                },
            )
        }
        val pulse = rememberInfiniteTransition(label = "dot")
        val s by pulse.animateFloat(
            initialValue = 0.82f,
            targetValue = 1.15f,
            animationSpec = infiniteRepeatable(
                tween(900, easing = FastOutSlowInEasing),
                RepeatMode.Reverse,
            ),
            label = "dotScale",
        )
        Box(
            Modifier
                .padding(start = 8.dp, bottom = 22.dp)
                .size(28.dp)
                .graphicsLayer { scaleX = s; scaleY = s }
                .clip(CircleShape)
                .background(Ink),
        )
    }
}

// ───────────────────────── Sheet ─────────────────────────

@Composable
private fun SignInSheet(
    ui: AuthUiState,
    hasClientId: Boolean,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetP = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(250)
        sheetP.animateTo(1f, spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessLow))
    }

    // little shake whenever a new error arrives
    val shake = remember { Animatable(0f) }
    LaunchedEffect(ui.error) {
        if (ui.error != null) {
            for (v in listOf(-12f, 12f, -8f, 8f, 0f)) shake.animateTo(v, tween(55))
        }
    }
    var lastError by remember { mutableStateOf("") }
    val err = ui.error
    if (err != null) lastError = err
    val density = LocalDensity.current

    Column(
        modifier
            .fillMaxWidth()
            .graphicsLayer { translationY = (1f - sheetP.value) * size.height }
            .clip(RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp))
            .background(Ink)
            .navigationBarsPadding()
            .padding(start = 28.dp, end = 28.dp, top = 34.dp, bottom = 22.dp),
    ) {
        Reveal(500) {
            Text("Your notes.\nSynced. Private.", style = InkType.Headline, color = Paper)
        }
        Spacer(Modifier.height(22.dp))
        Reveal(640) { FeatureRow(FeatureKind.Write, "Write beautifully, anywhere") }
        Spacer(Modifier.height(14.dp))
        Reveal(750) { FeatureRow(FeatureKind.Sync, "Auto-sync via Google Drive") }
        Spacer(Modifier.height(14.dp))
        Reveal(860) { FeatureRow(FeatureKind.Lock, "Your data stays yours") }
        Spacer(Modifier.height(28.dp))

        Reveal(980) {
            Column(Modifier.graphicsLayer { translationX = shake.value * density.density }) {
                if (hasClientId) {
                    GoogleButton(loading = ui.signingIn, onClick = onSignIn)
                } else {
                    SetupCard()
                }
                AnimatedVisibility(
                    visible = ui.error != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    Text(
                        text = lastError,
                        style = InkType.Label,
                        color = ErrorTint,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        Reveal(1100) {
            Text(
                "Your notes are stored locally and optionally backed up to your own Google Drive — never our servers.",
                style = InkType.Label,
                color = InkMuted,
            )
        }
    }
}

/** Fade + rise in after [delayMs]. */
@Composable
private fun Reveal(delayMs: Int, content: @Composable () -> Unit) {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMs.toLong())
        p.animateTo(1f, tween(450, easing = FastOutSlowInEasing))
    }
    Box(
        Modifier.graphicsLayer {
            alpha = p.value
            translationY = (1f - p.value) * 24.dp.toPx()
        },
    ) { content() }
}

// ───────────────────────── Button ─────────────────────────

@Composable
private fun GoogleButton(loading: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    Box(
        Modifier
            .fillMaxWidth()
            .height(58.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(Lime)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = !loading,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = loading,
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
            label = "btnContent",
        ) { isLoading ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Ink,
                        strokeWidth = 2.5.dp,
                    )
                    Text("Signing in…", style = InkType.Button, color = Ink)
                } else {
                    Box(
                        Modifier.size(30.dp).clip(CircleShape).background(Color.White),
                        contentAlignment = Alignment.Center,
                    ) { GoogleG(Modifier.size(18.dp)) }
                    Text("Continue with Google", style = InkType.Button, color = Ink)
                }
            }
        }
    }
}

@Composable
private fun SetupCard() {
    Column(
        Modifier
            .fillMaxWidth()
            .border(2.dp, InkLine, RoundedCornerShape(20.dp))
            .padding(16.dp),
    ) {
        Text("Setup required", style = InkType.Title, color = Lime)
        Spacer(Modifier.height(4.dp))
        Text(
            "Add the GOOGLE_CLIENT_ID secret (same value as VITE_GOOGLE_CLIENT_ID) and rebuild.",
            style = InkType.Label,
            color = InkMuted,
        )
    }
}

/** Multi-colour "G" drawn with Canvas — no image files. */
@Composable
private fun GoogleG(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = size.minDimension * 0.22f
        val half = stroke / 2f
        val topLeft = Offset(half, half)
        val arcSize = Size(size.width - stroke, size.height - stroke)
        fun arc(color: Color, start: Float, sweep: Float) = drawArc(
            color = color,
            startAngle = start,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke),
        )
        arc(Color(0xFFEA4335), 205f, 110f)
        arc(Color(0xFFFBBC05), 135f, 70f)
        arc(Color(0xFF34A853), 50f, 85f)
        arc(Color(0xFF4285F4), 0f, 50f)
        drawLine(
            color = Color(0xFF4285F4),
            start = Offset(size.width / 2f, size.height / 2f),
            end = Offset(size.width - half * 0.2f, size.height / 2f),
            strokeWidth = stroke,
        )
    }
}

// ───────────────────────── Features ─────────────────────────

private enum class FeatureKind { Write, Sync, Lock }

@Composable
private fun FeatureRow(kind: FeatureKind, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(38.dp).clip(CircleShape).background(Lime),
            contentAlignment = Alignment.Center,
        ) { FeatureGlyph(kind, Modifier.size(20.dp)) }
        Text(text, style = InkType.Body, color = Paper)
    }
}

@Composable
private fun FeatureGlyph(kind: FeatureKind, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val sw = 2.6.dp.toPx()
        when (kind) {
            FeatureKind.Write -> {
                drawLine(Ink, Offset(w * .22f, h * .72f), Offset(w * .8f, h * .14f), sw, StrokeCap.Round)
                drawLine(Ink, Offset(w * .12f, h * .92f), Offset(w * .58f, h * .92f), sw, StrokeCap.Round)
            }
            FeatureKind.Sync -> {
                drawArc(
                    color = Ink, startAngle = 40f, sweepAngle = 280f, useCenter = false,
                    topLeft = Offset(w * .12f, h * .12f), size = Size(w * .76f, h * .76f),
                    style = Stroke(width = sw, cap = StrokeCap.Round),
                )
            }
            FeatureKind.Lock -> {
                drawRoundRect(
                    color = Ink, topLeft = Offset(w * .15f, h * .45f),
                    size = Size(w * .7f, h * .47f), cornerRadius = CornerRadius(3.dp.toPx()),
                )
                drawArc(
                    color = Ink, startAngle = 180f, sweepAngle = 180f, useCenter = false,
                    topLeft = Offset(w * .29f, h * .08f), size = Size(w * .42f, h * .74f),
                    style = Stroke(width = sw, cap = StrokeCap.Round),
                )
            }
        }
    }
}
