package com.inkwell.notes.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inkwell.notes.R
import com.inkwell.notes.ui.components.InkText
import com.inkwell.notes.ui.theme.Ink

/**
 * Port of `src/pages/Login.jsx` + the "GOOGLE AUTH — Login Page" block of styles.css.
 * Every size below is the CSS px value (1 CSS px == 1 dp on Android).
 */
@Composable
fun LoginScreen(
    signingIn: Boolean,
    error: String?,
    hasClientId: Boolean,
    onSignIn: () -> Unit,
) {
    val c = Ink.colors
    val density = LocalDensity.current
    val topInset = with(density) { WindowInsets.statusBars.getTop(this).toFloat() }
    val bottomInset = with(density) { WindowInsets.navigationBars.getBottom(this).toFloat() }

    Box(Modifier.fillMaxSize().background(c.bg)) {

        // ── Decorative ink blobs ──
        LoginBlobs(topInset = topInset, bottomInset = bottomInset)

        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars),
        ) {
            val viewportHeight = maxHeight
            // .login-shell: max-width 430, min-height 100vh, padding 24 20 40, content centred
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .widthIn(max = 430.dp)
                    .fillMaxWidth()
                    .heightIn(min = viewportHeight)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                // .login-card: max-width 360
                Column(
                    modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    LoginLogo()
                    Spacer(Modifier.height(20.dp))

                    InkText(
                        text = "Inkwell",
                        family = Ink.serif,
                        size = 32.sp,
                        weight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    InkText(text = "Your notes. Synced. Private.", size = 14.sp, color = c.text2)
                    Spacer(Modifier.height(32.dp))

                    FeatureList()
                    Spacer(Modifier.height(28.dp))

                    if (!hasClientId) {
                        EnvWarning()
                    } else {
                        GoogleButton(signingIn = signingIn, onClick = onSignIn)
                    }
                    Spacer(Modifier.height(16.dp))

                    if (error != null) {
                        ErrorMessage(error)
                        Spacer(Modifier.height(12.dp))
                    }

                    InkText(
                        text = "Your notes are stored locally and optionally backed up to your own " +
                            "Google Drive — never our servers.",
                        modifier = Modifier.widthIn(max = 300.dp),
                        size = 11.5.sp,
                        lineHeight = 18.4.sp,
                        color = c.text3,
                        align = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/* ─────────────────────────── Logo ─────────────────────────── */

@Composable
private fun LoginLogo() {
    // @keyframes loginPop: scale .7→1, opacity 0→1, .5s cubic-bezier(.34,1.56,.64,1)
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(500, easing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)))
    }
    Image(
        painter = painterResource(R.drawable.login_logo),
        contentDescription = "Inkwell app icon",
        modifier = Modifier
            .graphicsLayer {
                val p = progress.value
                val s = 0.7f + 0.3f * p
                scaleX = s
                scaleY = s
                alpha = p.coerceIn(0f, 1f)
            }
            .size(72.dp)
            .clip(RoundedCornerShape(18.dp)),
    )
}

/* ─────────────────────────── Feature list ─────────────────────────── */

private val features = listOf(
    "✦" to "Write beautifully, anywhere",
    "☁" to "Auto-sync via Google Drive",
    "🔒" to "Your data stays yours",
)

@Composable
private fun FeatureList() {
    val c = Ink.colors
    val shape = RoundedCornerShape(Ink.radius)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.bgCard)
            .border(1.dp, c.border, shape)
            .padding(vertical = 4.dp),
    ) {
        features.forEachIndexed { index, (icon, text) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                InkText(
                    text = icon,
                    modifier = Modifier.width(24.dp),
                    size = 15.sp,
                    color = c.accent,
                    align = TextAlign.Center,
                )
                InkText(text = text, size = 13.sp, color = c.text2)
            }
            if (index < features.lastIndex) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
            }
        }
    }
}

/* ─────────────────────────── Google button ─────────────────────────── */

@Composable
private fun GoogleButton(signingIn: Boolean, onClick: () -> Unit) {
    val c = Ink.colors
    val shape = RoundedCornerShape(50)
    // Native build: Google SDK is always ready, so only `signingIn` disables it.
    val enabled = !signingIn

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = if (enabled) 1f else 0.55f }          // :disabled { opacity .55 }
            .shadow(4.dp, shape, ambientColor = c.shadowSm, spotColor = c.shadowSm) // --sh-sm
            .clip(shape)
            .background(c.bgElevated)
            .border(1.5.dp, c.borderHi, shape)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,                                          // no ripple: matches web
                onClick = onClick,
            )
            .padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
    ) {
        if (signingIn) {
            Spinner()
            InkText(text = "Signing in…", size = 15.sp, weight = FontWeight.SemiBold)
        } else {
            Image(
                painter = painterResource(R.drawable.ic_google_g),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            InkText(text = "Sign in with Google", size = 15.sp, weight = FontWeight.SemiBold)
        }
    }
}

/** .google-btn-spinner: 18px ring, 2px border-hi, top border accent, spin .7s linear */
@Composable
private fun Spinner() {
    val c = Ink.colors
    val transition = rememberInfiniteTransition(label = "spin")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Restart),
        label = "spinAngle",
    )
    Canvas(Modifier.size(18.dp).graphicsLayer { rotationZ = angle }) {
        val stroke = 2.dp.toPx()
        val inset = stroke / 2
        val arcSize = Size(size.width - stroke, size.height - stroke)
        val topLeft = Offset(inset, inset)
        drawArc(c.borderHi, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
        // CSS border-top-color covers the top 90° arc (from -135° to -45°)
        drawArc(c.accent, -135f, 90f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Butt))
    }
}

/* ─────────────────────────── Messages ─────────────────────────── */

@Composable
private fun ErrorMessage(message: String) {
    val c = Ink.colors
    val shape = RoundedCornerShape(Ink.radiusSm)
    val red = Color(0xFFE06060)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(red.copy(alpha = 0.12f))
            .border(1.dp, red.copy(alpha = 0.25f), shape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.ic_fa_circle_exclamation),
            contentDescription = null,
            modifier = Modifier.size(13.dp),
            colorFilter = ColorFilter.tint(c.danger),
        )
        InkText(text = message, size = 13.sp, color = c.danger)
    }
}

@Composable
private fun EnvWarning() {
    val c = Ink.colors
    val shape = RoundedCornerShape(Ink.radius)
    val code = SpanStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        color = c.accent,
        background = c.bgElevated,
    )
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color(0xFFC9A96E).copy(alpha = 0.08f))
            .border(1.dp, c.accentDim, shape)
            .padding(16.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.ic_fa_triangle_exclamation),
            contentDescription = null,
            modifier = Modifier.padding(top = 2.dp).size(18.dp),
            colorFilter = ColorFilter.tint(c.accent),
        )
        Column {
            InkText(
                text = "Setup required",
                size = 13.sp,
                weight = FontWeight.Bold,
                color = c.text1,
                lineHeight = 20.8.sp,
            )
            Spacer(Modifier.height(4.dp))
            InkText(
                text = buildAnnotatedString {
                    append("Add ")
                    withStyle(code) { append("VITE_GOOGLE_CLIENT_ID") }
                    append(" to your ")
                    withStyle(code) { append("local.properties") }
                    append(" file.\nSee ")
                    withStyle(code) { append("local.properties.example") }
                    append(" for details.")
                },
                size = 13.sp,
                color = c.text2,
                lineHeight = 20.8.sp,
            )
        }
    }
}

/* ─────────────────────────── Blobs ─────────────────────────── */

// Radial profile of a CSS `filter: blur(60px)` disc (r=130 / r=100), sampled every 1/12 of
// the outer radius (r + 3σ). Android < 12 has no Modifier.blur, so a gradient keeps every
// device looking the same.
private val BLOB_PROFILE_130 = floatArrayOf(
    0.904f, 0.883f, 0.818f, 0.709f, 0.570f, 0.411f, 0.263f, 0.147f, 0.071f, 0.030f, 0.011f, 0.003f, 0.001f,
)
private val BLOB_PROFILE_100 = floatArrayOf(
    0.751f, 0.726f, 0.650f, 0.540f, 0.412f, 0.281f, 0.176f, 0.098f, 0.047f, 0.021f, 0.008f, 0.003f, 0.001f,
)

@Composable
private fun LoginBlobs(topInset: Float, bottomInset: Float) {
    val c = Ink.colors
    Canvas(Modifier.fillMaxSize()) {
        // .b1: 260px, top -80, right -60, accent, opacity .12  → centre (W-70, top+50)
        drawBlob(
            center = Offset(size.width - 70.dp.toPx(), topInset + 50.dp.toPx()),
            radius = 310.dp.toPx(),
            profile = BLOB_PROFILE_130,
            color = c.accent,
            opacity = 0.12f,
        )
        // .b2: 200px, bottom -60, left -60, info, opacity .10 → centre (40, bottom-40)
        drawBlob(
            center = Offset(40.dp.toPx(), size.height - bottomInset - 40.dp.toPx()),
            radius = 280.dp.toPx(),
            profile = BLOB_PROFILE_100,
            color = c.info,
            opacity = 0.10f,
        )
    }
}

private fun DrawScope.drawBlob(
    center: Offset,
    radius: Float,
    profile: FloatArray,
    color: Color,
    opacity: Float,
) {
    val last = profile.lastIndex
    val stops = Array(profile.size) { i ->
        (i.toFloat() / last) to color.copy(alpha = profile[i] * opacity)
    }
    drawCircle(
        brush = Brush.radialGradient(*stops, center = center, radius = radius),
        radius = radius,
        center = center,
    )
}
