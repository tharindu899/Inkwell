package com.inkwell.notes.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inkwell.notes.auth.AuthUser
import com.inkwell.notes.ui.components.InkText
import com.inkwell.notes.ui.theme.Ink

/**
 * TEMPORARY screen shown after a successful sign-in, so the login flow can be
 * tested end-to-end. It is replaced by the real Home page in the next step.
 */
@Composable
fun HomePlaceholder(user: AuthUser, onSignOut: () -> Unit) {
    val c = Ink.colors
    val shape = RoundedCornerShape(Ink.radiusSm)
    Column(
        Modifier
            .fillMaxSize()
            .background(c.bg)
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        InkText(
            text = "Signed in ✓",
            family = Ink.serif,
            size = 24.sp,
            weight = FontWeight.Bold,
            align = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        InkText(text = user.name, size = 15.sp, color = c.accent, align = TextAlign.Center)
        InkText(text = user.email, size = 13.sp, color = c.text2, align = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        InkText(
            text = "Home page coming next.",
            size = 13.sp,
            color = c.text3,
            align = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        // .btn .btn-ghost
        InkText(
            text = "Sign out",
            size = 14.sp,
            weight = FontWeight.Medium,
            color = c.text2,
            modifier = Modifier
                .clip(shape)
                .border(1.dp, c.borderHi, shape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onSignOut,
                )
                .padding(horizontal = 20.dp, vertical = 10.dp),
        )
    }
}
