package com.inkwell.notes.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf

/**
 * Every screen the app can show. Screens that need data carry it in the route
 * (like `/tags?tag=x` or `/notebooks/:id` on the web).
 * New screens get added here as they are ported.
 */
sealed interface Route {
    data object Home : Route

    /** `/tags` — [tag] is the optional `?tag=` filter. */
    data class Tags(val tag: String? = null) : Route

    /** `/notebooks` */
    data object Notebooks : Route

    /** `/notebooks/:id` */
    data class NotebookDetail(val id: String) : Route

    /** `/search` */
    data object Search : Route

    /** `/profile` */
    data object Profile : Route

    /** `/settings` */
    data object Settings : Route
}

/**
 * Tiny back-stack, no library needed. The web app uses react-router + `navigate(-1)`;
 * this is the same idea: [push] to open a screen, [back] to leave it.
 */
@Stable
class Navigator(start: Route = Route.Home) {
    private val stack = mutableStateListOf(start)

    val current: Route get() = stack.last()
    val canGoBack: Boolean get() = stack.size > 1

    /** Open [route] on top of the current screen. */
    fun push(route: Route) {
        stack.add(route)
    }

    /** Pops the top screen. Returns false when already at the root (Home). */
    fun back(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    /**
     * Bottom-nav tab press. Tabs are top-level, so they don't pile up on the stack:
     * Home pops back to the root, any other tab becomes [Home, tab].
     * Pressing the tab you are already on does nothing.
     */
    fun switchTab(route: Route) {
        if (route::class == current::class) return
        stack.clear()
        stack.add(Route.Home)
        if (route != Route.Home) stack.add(route)
    }
}
