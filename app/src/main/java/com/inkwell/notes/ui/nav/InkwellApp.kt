package com.inkwell.notes.ui.nav

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.inkwell.notes.data.SampleData
import com.inkwell.notes.model.SortOrder
import com.inkwell.notes.model.ThemeMode
import com.inkwell.notes.ui.components.InkBottomBar
import com.inkwell.notes.ui.components.NavTab
import com.inkwell.notes.ui.screens.HomeScreen
import com.inkwell.notes.ui.screens.PlaceholderScreen
import com.inkwell.notes.ui.theme.Ink
import com.inkwell.notes.ui.theme.InkwellTheme

/** Same routes as the web app's React Router. */
object Routes {
    const val Home = "home"
    const val Tags = "tags"
    const val Notebooks = "notebooks"
    const val Search = "search"
    const val Settings = "settings"
    const val Profile = "profile"
    const val Editor = "editor"
}

@Composable
fun InkwellApp() {
    var theme by rememberSaveable { mutableStateOf(ThemeMode.Dark) }
    InkwellTheme(theme) {
        val nav = rememberNavController()
        val entry by nav.currentBackStackEntryAsState()
        val route = entry?.destination?.route

        // Same 3 tabs as the web BottomNav (Search lives in the top bar).
        val tabs = listOf(
            NavTab(Routes.Home, "Home", Icons.Rounded.Home),
            NavTab(Routes.Tags, "Tags", Icons.Rounded.Sell),
            NavTab(Routes.Notebooks, "Notebook", Icons.Rounded.MenuBook),
        )
        val tabIndex = tabs.indexOfFirst { it.route == route }

        // Demo state. Replace with your real store in the data-layer part.
        var sort by remember { mutableStateOf(SortOrder.Modified) }
        val notes = remember { SampleData.notes() }

        Box(Modifier.fillMaxSize().background(Ink.colors.bg)) {
            NavHost(
                navController = nav,
                startDestination = Routes.Home,
                enterTransition = { fadeIn(tween(280)) + slideInHorizontally(tween(340)) { it / 14 } },
                exitTransition = { fadeOut(tween(160)) },
                popEnterTransition = { fadeIn(tween(280)) },
                popExitTransition = { fadeOut(tween(180)) + slideOutHorizontally(tween(320)) { it / 14 } },
            ) {
                composable(Routes.Home) {
                    HomeScreen(
                        userName = "Rema",
                        notes = notes,
                        notebookCount = SampleData.notebooks.size,
                        sort = sort,
                        onSort = { sort = it },
                        onOpenNote = { nav.navigate("${Routes.Editor}/$it") },
                        onProfile = { nav.navigate(Routes.Profile) },
                        onSearch = { nav.navigate(Routes.Search) },
                        // Temporary: cycles Light -> Dark -> GitHub so you can preview the theme animation.
                        onSettings = { theme = ThemeMode.entries[(theme.ordinal + 1) % ThemeMode.entries.size] },
                        onNewNote = { nav.navigate(Routes.Editor) },
                        onNewNotebook = { nav.navigate(Routes.Notebooks) },
                        onNewTag = { nav.navigate(Routes.Tags) },
                    )
                }
                composable(Routes.Tags) { PlaceholderScreen("Tags") }
                composable(Routes.Notebooks) { PlaceholderScreen("Notebooks") }
                composable(Routes.Search) { PlaceholderScreen("Search") }
                composable(Routes.Settings) { PlaceholderScreen("Settings") }
                composable(Routes.Profile) { PlaceholderScreen("Profile") }
                composable(Routes.Editor) { PlaceholderScreen("Editor") }
                composable("${Routes.Editor}/{id}") { PlaceholderScreen("Editor") }
            }
            AnimatedVisibility(
                visible = tabIndex >= 0,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 18.dp, vertical = 16.dp),
                enter = slideInVertically(tween(300)) { it } + fadeIn(tween(300)),
                exit = slideOutVertically(tween(220)) { it } + fadeOut(tween(220)),
            ) {
                InkBottomBar(
                    tabs = tabs,
                    selected = tabIndex.coerceAtLeast(0),
                    onSelect = { i ->
                        nav.navigate(tabs[i].route) {
                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        }
    }
}
