package com.inkwell.notes.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.inkwell.notes.auth.AuthViewModel
import com.inkwell.notes.ui.home.HomePlaceholder
import com.inkwell.notes.ui.login.LoginScreen
import com.inkwell.notes.util.findActivity

/**
 * Root of the app. Part 1: Login <-> Home placeholder.
 * Part 2 will replace the placeholder with the real Home + navigation.
 */
@Composable
fun InkwellApp(vm: AuthViewModel = viewModel()) {
    val ui by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    AnimatedContent(
        targetState = ui.user,
        contentKey = { it != null },
        transitionSpec = {
            (fadeIn(tween(420)) + scaleIn(initialScale = 0.96f, animationSpec = tween(420))) togetherWith
                fadeOut(tween(200))
        },
        label = "root",
    ) { user ->
        if (user != null) {
            HomePlaceholder(user = user, onSignOut = { vm.signOut() })
        } else {
            LoginScreen(
                ui = ui,
                hasClientId = vm.hasClientId,
                onSignIn = { context.findActivity()?.let { vm.signIn(it) } },
            )
        }
    }
}
