package com.inkwell.notes.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.inkwell.notes.ui.components.CreateMenu
import com.inkwell.notes.ui.components.Fab
import com.inkwell.notes.ui.components.InkToast
import com.inkwell.notes.ui.components.QuickCreateKind
import com.inkwell.notes.ui.components.QuickCreateModal

/**
 * The FAB + Create-New menu + notebook/tag quick-create modal + toast.
 * On the web these are `<Fab />` and `<Toast />`, dropped into every page — so Home and
 * Tags (and the next screens) share this instead of each copying it. Place it inside the
 * screen's root Box, after the BottomNav, so it draws on top.
 *
 * It uses the same [HomeViewModel] as Home, so the menu / toast state is shared.
 */
@Composable
fun BoxScope.CreateLayer(
    state: HomeState,
    vm: HomeViewModel,
    showFab: Boolean = true,
    /** Tags page: the FAB opens the "Create tag" modal directly instead of the Create-New menu. */
    fabCreatesTag: Boolean = false,
    onNewNote: () -> Unit = {},
    onSearch: () -> Unit = {},
    /** Notes page: adds a "tags" button to the dial that opens the tag manager. */
    onManageTags: (() -> Unit)? = null,
    /** Called after a notebook was created from the quick-create modal (web: `navigate('/notebooks')`). */
    onNotebookCreated: () -> Unit = {},
    /** Called with the cleaned tag after the "Create tag" modal is saved. */
    onTagCreated: (String) -> Unit = {},
) {
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // Back closes the quick-create modal first, then the menu.
    BackHandler(enabled = state.quickCreate != null || state.createMenuOpen) {
        if (state.quickCreate != null) vm.closeQuickCreate() else vm.closeCreateMenu()
    }

    // Create-New menu: backdrop + sheet sit under the FAB
    CreateMenu(
        open = state.createMenuOpen,
        onClose = vm::closeCreateMenu,
        onNewNote = { vm.closeCreateMenu(); onNewNote() },
        onNewNotebook = { vm.openQuickCreate(QuickCreateKind.Notebook) },
        onNewTag = { vm.openQuickCreate(QuickCreateKind.Tag) },
        onSearch = { vm.closeCreateMenu(); onSearch() },
        onManageTags = onManageTags?.let { f -> { vm.closeCreateMenu(); f() } },
    )

    if (showFab) {
        Fab(
            onClick = {
                if (fabCreatesTag) vm.openQuickCreate(QuickCreateKind.Tag) else vm.toggleCreateMenu()
            },
            open = !fabCreatesTag && state.createMenuOpen,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(end = 18.dp, bottom = 62.dp + 12.dp),
        )
    }

    // .toast — sits 12dp above the bottom nav
    InkToast(
        toast = state.toast,
        onAction = vm::onToastAction,
        onDismiss = vm::dismissToast,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(start = 17.dp, end = 17.dp, bottom = 62.dp + navInset + 12.dp),
    )

    state.quickCreate?.let { kind ->
        when (kind) {
            // Same form as the Notebooks page: name, colour, icon.
            QuickCreateKind.Notebook -> NotebookFormModal(
                editing = null,
                onDismiss = vm::closeQuickCreate,
                onSave = { name, color, icon ->
                    if (vm.createNotebook(name, color, icon)) onNotebookCreated()
                },
            )
            QuickCreateKind.Tag -> QuickCreateModal(
                kind = kind,
                onDismiss = vm::closeQuickCreate,
                onSave = { text -> vm.createTag(text)?.let(onTagCreated) },
            )
        }
    }
}
