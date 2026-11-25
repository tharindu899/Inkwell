package com.inkwell.notes.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.inkwell.notes.auth.AuthViewModel
import com.inkwell.notes.data.AppPrefs
import com.inkwell.notes.ui.screens.HomePlaceholder
import com.inkwell.notes.ui.screens.LoginScreen
import com.inkwell.notes.ui.theme.Ink
import com.inkwell.notes.ui.theme.InkwellTheme
import com.inkwell.notes.ui.theme.ThemeMode

/**
 * Root of the app. Mirrors App.jsx routing for now:
 *   not logged in → Login,  logged in → (placeholder until Home is ported).
 */
@Composable
fun InkwellApp(vm: AuthViewModel = viewModel()) {
    val context = LocalContext.current
    // Saved theme is applied before the first frame (like main.jsx does for the web build).
    val themeMode = remember { ThemeMode.fromKey(AppPrefs.getTheme(context)) }
    val state by vm.state.collectAsStateWithLifecycle()

    val signInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result -> vm.onSignInResult(result.data) }

    InkwellTheme(themeMode) {
        val colors = Ink.colors
        val view = LocalView.current
        SideEffect {
            val window = (context as ComponentActivity).window
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !colors.isDark
            controller.isAppearanceLightNavigationBars = !colors.isDark
        }

        val user = state.user
        if (user == null) {
            LoginScreen(
                signingIn = state.signingIn,
                error = state.error,
                hasClientId = vm.hasClientId,
                onSignIn = { vm.startSignIn { intent -> signInLauncher.launch(intent) } },
            )
        } else {
            HomePlaceholder(user = user, onSignOut = vm::signOut)
        }
    }
}
