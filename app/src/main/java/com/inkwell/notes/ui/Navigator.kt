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

    /** All Notes page. [manageTags] opens the tag manager (edit / delete tags) straight away. */
    data class AllNotes(val manageTags: Boolean = false) : Route

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

    /**
     * `/editor/:id` (existing note) or `/editor?new=1&notebook=..&tag=..` ([id] = null).
     * Data class on purpose: every push is a fresh editor session.
     */
    data class Editor(
        val id: String? = null,
        val notebookId: String? = null,
        val tag: String? = null,
    ) : Route
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

    /** Swap the top screen for [route] (no extra back-stack entry). */
    fun replaceTop(route: Route) {
        if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
        stack.add(route)
    }

    /** True when [route]'s type is already somewhere below the top of the stack. */
    fun hasBelow(route: Route): Boolean =
        stack.dropLast(1).any { it::class == route::class }

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
