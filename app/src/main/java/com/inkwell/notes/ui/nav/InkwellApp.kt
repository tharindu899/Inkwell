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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.inkwell.notes.data.InkStore
import com.inkwell.notes.model.ThemeMode
import com.inkwell.notes.ui.components.InkBottomBar
import com.inkwell.notes.ui.components.InkToastHost
import com.inkwell.notes.ui.components.NavTab
import com.inkwell.notes.ui.screens.HomeScreen
import com.inkwell.notes.ui.screens.NotebookDetailScreen
import com.inkwell.notes.ui.screens.NotebooksScreen
import com.inkwell.notes.ui.screens.PlaceholderScreen
import com.inkwell.notes.ui.screens.SearchScreen
import com.inkwell.notes.ui.screens.TagsScreen
import com.inkwell.notes.ui.theme.Ink
import com.inkwell.notes.ui.theme.InkwellTheme

/** Same routes as the web app's React Router. */
object Routes {
    const val Home = "home"
    const val Tags = "tags"
    const val Notebooks = "notebooks"
    const val NotebookDetail = "notebooks/{id}"
    const val Search = "search"
    const val Settings = "settings"
    const val Profile = "profile"
    const val Editor = "editor"
    fun notebook(id: String) = "notebooks/$id"
}

/** Tab switch without stacking tabs on top of each other (like the web bottom nav). */
private fun NavHostController.goTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id)
        launchSingleTop = true
    }
}

@Composable
fun InkwellApp() {
    var theme by rememberSaveable { mutableStateOf(ThemeMode.Dark) }
    InkwellTheme(theme) {
        val nav = rememberNavController()
        val entry by nav.currentBackStackEntryAsState()
        val route = entry?.destination?.route
        val store = remember { InkStore() }

        // Same 3 tabs as the web BottomNav (Search lives in the top bar).
        val tabs = listOf(
            NavTab(Routes.Home, "Home", Icons.Rounded.Home),
            NavTab(Routes.Tags, "Tags", Icons.Rounded.Sell),
            NavTab(Routes.Notebooks, "Notebook", Icons.Rounded.MenuBook),
        )
        // "notebooks/{id}" keeps the Notebook tab highlighted, like the web app.
        val tabIndex = tabs.indexOfFirst { route == it.route || route?.startsWith(it.route + "/") == true }
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

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
                        notes = store.sortedNotes(),
                        notebookCount = store.notebooks.size,
                        sort = store.sort,
                        onSort = { store.sort = it },
                        onOpenNote = { nav.navigate("${Routes.Editor}/$it") },
                        onProfile = { nav.navigate(Routes.Profile) },
                        onSearch = { nav.navigate(Routes.Search) },
                        // Temporary: cycles Light -> Dark -> GitHub so you can preview the theme animation.
                        onSettings = { theme = ThemeMode.entries[(theme.ordinal + 1) % ThemeMode.entries.size] },
                        onNewNote = { nav.navigate(Routes.Editor) },
                        onNewNotebook = {
                            store.newNotebookRequested = true
                            nav.goTab(Routes.Notebooks)
                        },
                        onCreateTag = { tag ->
                            store.pendingNewTag = tag
                            nav.navigate(Routes.Editor)
                        },
                    )
                }
                composable(Routes.Tags) {
                    TagsScreen(
                        allNotes = store.sortedNotes(),
                        tags = store.tagCounts(),
                        onOpenNote = { nav.navigate("${Routes.Editor}/$it") },
                        onSearch = { nav.navigate(Routes.Search) },
                        onNewNote = { nav.navigate(Routes.Editor) },
                        onNewNotebook = {
                            store.newNotebookRequested = true
                            nav.goTab(Routes.Notebooks)
                        },
                        onCreateTag = { tag ->
                            store.pendingNewTag = tag
                            nav.navigate(Routes.Editor)
                        },
                    )
                }
                composable(Routes.Notebooks) {
                    NotebooksScreen(
                        notebooks = store.sortedNotebooks(),
                        totalCount = store.notebooks.size,
                        noteCount = { store.noteCount(it) },
                        sort = store.notebookSort,
                        onSort = { store.notebookSort = it },
                        onOpen = { nav.navigate(Routes.notebook(it)) },
                        onSave = { id, name, color, icon -> store.saveNotebook(id, name, color, icon) },
                        onDelete = { store.deleteNotebook(it) },
                        openFormRequest = store.newNotebookRequested,
                        onFormRequestHandled = { store.newNotebookRequested = false },
                    )
                }
                composable(
                    Routes.NotebookDetail,
                    arguments = listOf(navArgument("id") { type = NavType.StringType }),
                ) { backEntry ->
                    val id = backEntry.arguments?.getString("id")
                    val live = store.notebook(id)
                    // Keep showing the last notebook while the screen slides out after a delete.
                    var last by remember { mutableStateOf(live) }
                    LaunchedEffect(live) { if (live != null) last = live }
                    val nb = live ?: last
                    if (nb == null) {
                        LaunchedEffect(Unit) { nav.popBackStack() }
                    } else {
                        NotebookDetailScreen(
                            notebook = nb,
                            notes = store.sortedNotes(store.notes.filter { it.notebookId == nb.id }),
                            sort = store.sort,
                            onSort = { store.sort = it },
                            onBack = { nav.popBackStack() },
                            onOpenNote = { nav.navigate("${Routes.Editor}/$it") },
                            onNewNote = { nav.navigate(Routes.Editor) },
                            onAllNotebooks = { nav.popBackStack(Routes.Notebooks, inclusive = false) },
                            onSearch = { nav.navigate(Routes.Search) },
                            onDelete = {
                                store.deleteNotebook(nb.id)
                                nav.popBackStack()
                            },
                        )
                    }
                }
                composable(Routes.Search) {
                    SearchScreen(
                        notebooks = store.notebooks,
                        recents = store.recentSearches,
                        search = { q, mode, nbId -> store.search(q, mode, nbId) },
                        notebookOf = { store.notebook(it) },
                        onSubmit = { store.addSearch(it) },
                        onClearRecents = { store.clearSearches() },
                        onOpenNote = { nav.navigate("${Routes.Editor}/$it") },
                        onBack = { nav.popBackStack() },
                    )
                }
                composable(Routes.Settings) { PlaceholderScreen("Settings") }
                composable(Routes.Profile) { PlaceholderScreen("Profile") }
                composable(Routes.Editor) { PlaceholderScreen("Editor") }
                composable("${Routes.Editor}/{id}") { PlaceholderScreen("Editor") }
            }

            InkToastHost(
                store.toast,
                Modifier.align(Alignment.BottomCenter).padding(bottom = navBottom + 100.dp),
            )

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
                        val target = tabs[i].route
                        if (tabIndex == i) {
                            // Tapping the active tab on a sub-page (notebook detail) goes back to the tab root.
                            nav.popBackStack(target, inclusive = false)
                        } else {
                            nav.goTab(target)
                        }
                    },
                )
            }
        }
    }
}
