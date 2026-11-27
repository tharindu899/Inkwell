package com.inkwell.notes.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inkwell.notes.auth.UserSession
import com.inkwell.notes.ui.theme.Ink
import com.inkwell.notes.ui.theme.InkMuted
import com.inkwell.notes.ui.theme.InkType
import com.inkwell.notes.ui.theme.Lime
import com.inkwell.notes.ui.theme.Paper
import com.inkwell.notes.util.SystemBars

/** Temporary screen so the login flow can be tested. Replaced by the real Home in Part 2. */
@Composable
fun HomePlaceholder(user: UserSession, onSignOut: () -> Unit) {
    SystemBars(lightNavIcons = false)

    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        p.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow))
    }

    Column(Modifier.fillMaxSize().background(Paper)) {
        Column(
            Modifier
                .fillMaxWidth()
                .graphicsLayer { translationY = -(1f - p.value) * 80.dp.toPx() }
                .clip(RoundedCornerShape(bottomStart = 40.dp, bottomEnd = 40.dp))
                .background(Lime)
                .statusBarsPadding()
                .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 32.dp),
        ) {
            Text("inkwell", style = InkType.Title, color = Ink)
            Spacer(Modifier.height(28.dp))
            Text("Hi,", style = InkType.Headline, color = Ink)
            Text(
                text = user.name.substringBefore(' '),
                style = InkType.Display.copy(fontSize = 56.sp, lineHeight = 56.sp, letterSpacing = (-2).sp),
                color = Ink,
            )
            Spacer(Modifier.height(6.dp))
            Text(user.email, style = InkType.Body, color = Ink)
        }

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(Ink)
                    .padding(22.dp),
            ) {
                Text("Signed in ✓", style = InkType.Title, color = Lime)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Login works. The real Home screen with your notes comes in Part 2.",
                    style = InkType.Body,
                    color = InkMuted,
                )
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(CircleShape)
                    .border(2.dp, Ink, CircleShape)
                    .clickable(role = Role.Button, onClick = onSignOut),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Sign out", style = InkType.Button, color = Ink)
            }
        }
    }
}
