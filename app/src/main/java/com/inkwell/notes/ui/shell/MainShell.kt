package com.inkwell.notes.ui.shell

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.inkwell.notes.InkwellApplication
import com.inkwell.notes.auth.UserSession
import com.inkwell.notes.ui.components.InkToast
import com.inkwell.notes.ui.components.ToastData
import com.inkwell.notes.ui.home.HomeScreen
import com.inkwell.notes.ui.home.HomeViewModel
import com.inkwell.notes.ui.screens.EditorPlaceholder
import com.inkwell.notes.ui.screens.ProfilePlaceholder
import com.inkwell.notes.ui.screens.notebooks.NotebookDetailScreen
import com.inkwell.notes.ui.screens.notebooks.NotebookDetailViewModel
import com.inkwell.notes.ui.screens.notebooks.NotebooksScreen
import com.inkwell.notes.ui.screens.notebooks.NotebooksViewModel
import com.inkwell.notes.ui.screens.search.SearchScreen
import com.inkwell.notes.ui.screens.search.SearchViewModel
import com.inkwell.notes.ui.screens.tags.TagsScreen
import com.inkwell.notes.ui.screens.tags.TagsViewModel
import com.inkwell.notes.ui.theme.Paper
import kotlinx.coroutines.delay

private const val NOTEBOOK_DETAIL = "notebook/{id}"
private val BAR_ROUTES = setOf("home", "tags", "notebooks", "profile", NOTEBOOK_DETAIL)
private val BAR_SPACE = 120.dp

/**
 * App shell: NavHost + the floating bottom bar + toasts.
 * Later parts only need to swap the placeholder composables for real screens.
 */
@Composable
fun MainShell(user: UserSession, onSignOut: () -> Unit) {
    val nav = rememberNavController()
    val app = LocalContext.current.applicationContext as InkwellApplication
    val homeVm: HomeViewModel = viewModel()
    val homeUi by homeVm.ui.collectAsStateWithLifecycle()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showBar = route in BAR_ROUTES

    var toast by remember { mutableStateOf<ToastData?>(null) }
    LaunchedEffect(Unit) { app.toasts.collect { toast = it } }
    LaunchedEffect(toast) {
        if (toast != null) {
            delay(5000)
            toast = null
        }
    }

    BackHandler(enabled = homeUi.selectMode) { homeVm.cancelSelect() }

    fun go(dest: String) {
        nav.navigate(dest) {
            popUpTo("home") { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun newNote() {
        val nbId = if (route == NOTEBOOK_DETAIL) entry?.arguments?.getString("id") else null
        nav.navigate(if (nbId != null) "editor/new?notebook=$nbId" else "editor/new")
    }

    Box(Modifier.fillMaxSize().background(Paper)) {
        NavHost(
            navController = nav,
            startDestination = "home",
            modifier = Modifier.fillMaxSize(),
            enterTransition = { fadeIn(tween(260)) },
            exitTransition = { fadeOut(tween(160)) },
            popEnterTransition = { fadeIn(tween(260)) },
            popExitTransition = { fadeOut(tween(160)) },
        ) {
            composable("home") {
                HomeScreen(
                    userName = user.name.substringBefore(' '),
                    vm = homeVm,
                    onOpenNote = { nav.navigate("editor/$it") },
                    onSearch = { nav.navigate("search") },
                )
            }
            composable("tags") {
                val vm: TagsViewModel = viewModel()
                TagsScreen(vm, onOpenNote = { nav.navigate("editor/$it") }, onSearch = { nav.navigate("search") })
            }
            composable("notebooks") {
                val vm: NotebooksViewModel = viewModel()
                NotebooksScreen(vm, onOpenNotebook = { nav.navigate("notebook/$it") })
            }
            composable(
                NOTEBOOK_DETAIL,
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) {
                val vm: NotebookDetailViewModel = viewModel()
                NotebookDetailScreen(
                    vm = vm,
                    onBack = { nav.popBackStack() },
                    onOpenNote = { nav.navigate("editor/$it") },
                    onSearch = { nav.navigate("search") },
                )
            }
            composable("search") {
                val vm: SearchViewModel = viewModel()
                SearchScreen(vm, onBack = { nav.popBackStack() }, onOpenNote = { nav.navigate("editor/$it") })
            }
            composable("profile") { ProfilePlaceholder(user, onSignOut, BAR_SPACE) }
            composable(
                "editor/{id}?notebook={notebook}",
                arguments = listOf(
                    navArgument("id") { type = NavType.StringType },
                    navArgument("notebook") { type = NavType.StringType; nullable = true; defaultValue = null },
                ),
            ) { e ->
                EditorPlaceholder(
                    noteId = e.arguments?.getString("id").orEmpty(),
                    notebookId = e.arguments?.getString("notebook"),
                    onBack = { nav.popBackStack() },
                )
            }
        }

        AnimatedVisibility(
            visible = showBar,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            FloatingBar(
                current = if (route == NOTEBOOK_DETAIL) "notebooks" else route,
                selectMode = homeUi.selectMode && route == "home",
                selectedCount = homeUi.selected.size,
                anySelectedPinned = homeUi.anySelectedPinned,
                onNavigate = ::go,
                onNew = ::newNote,
                onCancel = homeVm::cancelSelect,
                onPin = homeVm::pinSelected,
                onDelete = homeVm::requestDelete,
            )
        }

        InkToast(
            toast = toast,
            onDismiss = { toast = null },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 104.dp),
        )
    }
}
