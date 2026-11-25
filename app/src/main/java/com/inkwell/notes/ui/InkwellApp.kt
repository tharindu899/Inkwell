package com.inkwell.notes.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.inkwell.notes.auth.AuthViewModel
import com.inkwell.notes.data.AppPrefs
import com.inkwell.notes.ui.screens.AllNotesScreen
import com.inkwell.notes.ui.screens.EditorScreen
import com.inkwell.notes.ui.screens.HomeScreen
import com.inkwell.notes.ui.components.NavTab
import com.inkwell.notes.ui.screens.LoginScreen
import com.inkwell.notes.ui.screens.NotebookDetailScreen
import com.inkwell.notes.ui.screens.NotebooksScreen
import com.inkwell.notes.ui.screens.ProfileScreen
import com.inkwell.notes.ui.screens.SearchScreen
import com.inkwell.notes.ui.screens.SettingsScreen
import com.inkwell.notes.ui.screens.UpdateHost
import com.inkwell.notes.ui.theme.Ink
import com.inkwell.notes.ui.theme.InkwellTheme
import com.inkwell.notes.ui.theme.ThemeMode

/**
 * Root of the app. Mirrors App.jsx routing:
 *   not logged in → Login,  logged in → the screen on top of the [Navigator] stack.
 * The system Back button pops the stack (and leaves the app from Home).
 */
@Composable
fun InkwellApp(vm: AuthViewModel = viewModel()) {
    val context = LocalContext.current
    // Saved theme is applied before the first frame (like main.jsx does for the web build).
    var themeMode by remember { mutableStateOf(ThemeMode.fromKey(AppPrefs.getTheme(context))) }
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

        Box(Modifier.fillMaxSize()) {
            val user = state.user
            if (user == null) {
                LoginScreen(
                    signingIn = state.signingIn,
                    error = state.error,
                    hasClientId = vm.hasClientId,
                    onSignIn = { vm.startSignIn { intent -> signInLauncher.launch(intent) } },
                )
            } else {
                // New navigator per sign-in, so a fresh login always starts on Home.
                val nav = remember { Navigator() }
                BackHandler(enabled = nav.canGoBack) { nav.back() }

                val onNavTab: (NavTab) -> Unit = { tab ->
                    when (tab) {
                        NavTab.Home -> nav.switchTab(Route.Home)
                        NavTab.AllNotes -> nav.switchTab(Route.AllNotes())
                        NavTab.Notebooks -> nav.switchTab(Route.Notebooks)
                    }
                }

                when (val route = nav.current) {
                    Route.Home -> HomeScreen(
                        user = user,
                        onOpenProfile = { nav.push(Route.Profile) },
                        onNavTab = onNavTab,
                        onSearch = { nav.push(Route.Search) },
                        onSettings = { nav.push(Route.Settings) },
                        onOpenNote = { id -> nav.push(Route.Editor(id = id)) },
                        onNewNote = { tag -> nav.push(Route.Editor(tag = tag)) },
                    )
                    is Route.AllNotes -> AllNotesScreen(
                        openManageTags = route.manageTags,
                        onBack = { nav.back() },
                        onNavTab = onNavTab,
                        onSearch = { nav.push(Route.Search) },
                        onOpenNote = { id -> nav.push(Route.Editor(id = id)) },
                        onNewNote = { tag -> nav.push(Route.Editor(tag = tag)) },
                    )
                    Route.Notebooks -> NotebooksScreen(
                        onBack = { nav.back() },
                        onNavTab = onNavTab,
                        onOpenNotebook = { id -> nav.push(Route.NotebookDetail(id)) },
                        onSearch = { nav.push(Route.Search) },
                    )
                    is Route.NotebookDetail -> NotebookDetailScreen(
                        notebookId = route.id,
                        onBack = { nav.back() },
                        onNavTab = onNavTab,
                        onAllNotebooks = { nav.switchTab(Route.Notebooks) },
                        onSearch = { nav.push(Route.Search) },
                        onMissing = { nav.switchTab(Route.Notebooks) },
                        onOpenNote = { id -> nav.push(Route.Editor(id = id)) },
                        onNewNote = { nav.push(Route.Editor(notebookId = route.id)) },
                    )
                    Route.Profile -> ProfileScreen(
                        user = user,
                        onBack = { nav.back() },
                        onNavTab = onNavTab,
                        onSignOut = vm::signOut,
                        onSettings = { nav.push(Route.Settings) },
                    )
                    Route.Search -> SearchScreen(
                        onBack = { nav.back() },
                        onNavTab = onNavTab,
                        onOpenNote = { note -> nav.push(Route.Editor(id = note.id)) },
                    )
                    is Route.Editor -> EditorScreen(
                        route = route,
                        onBack = { nav.back() },
                    )
                    Route.Settings -> SettingsScreen(
                        user = user,
                        themeMode = themeMode,
                        onThemeChange = { mode ->
                            themeMode = mode
                            AppPrefs.setTheme(context, mode.key)
                        },
                        onBack = { nav.back() },
                        onNavTab = onNavTab,
                        onManageTags = { nav.push(Route.AllNotes(manageTags = true)) },
                        // Settings can be opened from Home or Profile: go back to Profile if it is
                        // already below us, otherwise swap Settings for Profile.
                        onOpenProfile = {
                            if (nav.hasBelow(Route.Profile)) nav.back() else nav.replaceTop(Route.Profile)
                        },
                        onResetDone = {
                            themeMode = ThemeMode.Dark
                            vm.signOut()
                        },
                    )
                }
            }
            UpdateHost() // GitHub update sheet + toast, over every screen
        }
    }
}
