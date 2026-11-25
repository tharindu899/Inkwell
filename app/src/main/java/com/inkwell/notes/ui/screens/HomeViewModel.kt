package com.inkwell.notes.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.inkwell.notes.R
import com.inkwell.notes.data.NotesStore
import com.inkwell.notes.data.model.Note
import com.inkwell.notes.data.model.Notebook
import com.inkwell.notes.data.model.SortOrder
import com.inkwell.notes.data.model.Stats
import com.inkwell.notes.ui.components.QuickCreateKind
import com.inkwell.notes.ui.components.ToastState
import com.inkwell.notes.util.Iso
import com.inkwell.notes.util.genId
import com.inkwell.notes.util.normalizeTagInput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class HomeState(
    /** Already sorted (pinned first). */
    val notes: List<Note> = emptyList(),
    val notebooks: List<Notebook> = emptyList(),
    val sortOrder: SortOrder = SortOrder.Modified,
    val stats: Stats = Stats(0, 0, 0),
    // ── multi-select (Home.jsx: selectMode / selected / deleteConfirmOpen) ──
    val selectMode: Boolean = false,
    val selected: Set<String> = emptySet(),
    val deleteConfirmOpen: Boolean = false,
    /** Current toast (Toast.jsx). */
    val toast: ToastState? = null,
    // ── Create-New menu (Fab.jsx: open / quickModal) ──
    val createMenuOpen: Boolean = false,
    val quickCreate: QuickCreateKind? = null,
) {
    val pinned: List<Note> get() = notes.filter { it.pinned }
    val recent: List<Note> get() = notes.filter { !it.pinned }

    /** `anyPinned` — decides whether the bulk button says Pin or Unpin. */
    val anySelectedPinned: Boolean get() = notes.any { it.id in selected && it.pinned }
}

/** Kotlin side of `AppContext.jsx` + the handlers inside `Home.jsx`. */
class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val store = NotesStore(application)

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    /** Notes removed by the last bulk delete, kept for the Undo action. */
    private var lastDeleted: List<Note> = emptyList()

    init {
        store.seedIfEmpty()
        reload()
    }

    private fun HomeState.withFreshData(): HomeState {
        val order = store.getSortOrder()
        return copy(
            notes = NotesStore.sortNotes(store.getNotes(), order),
            notebooks = store.getNotebooks(),
            sortOrder = order,
            stats = store.getStats(),
        )
    }

    /** `dispatch({ type: 'RELOAD' })` — keeps selection / toast state. */
    fun reload() {
        _state.update { it.withFreshData() }
    }

    /** `dispatch({ type: 'SET_SORT' })` */
    fun setSortOrder(order: SortOrder) {
        store.setSortOrder(order)
        reload()
    }

    // ─── multi-select ────────────────────────────

    /** The "Select" chip: enter select mode with nothing selected. */
    fun startSelectMode() {
        _state.update { it.copy(selectMode = true, selected = emptySet()) }
    }

    /** Long-press on a card while not in select mode: enter and select that card. */
    fun enterSelectMode(firstId: String) {
        _state.update { it.copy(selectMode = true, selected = setOf(firstId)) }
    }

    fun toggleSelect(id: String) {
        _state.update { s ->
            s.copy(selected = if (id in s.selected) s.selected - id else s.selected + id)
        }
    }

    fun cancelSelectMode() {
        _state.update { it.copy(selectMode = false, selected = emptySet(), deleteConfirmOpen = false) }
    }

    /** `handleBulkPin` — if any selected note is pinned, unpin all; otherwise pin all. */
    fun bulkPin() {
        val s = _state.value
        if (s.selected.isEmpty()) return
        val count = s.selected.size
        val unpin = s.anySelectedPinned
        store.setPinned(s.selected, pinned = !unpin)
        reload()
        cancelSelectMode()
        showToast("$count ${noteWord(count)} ${if (unpin) "unpinned" else "pinned"}", R.drawable.ic_fa_thumbtack)
    }

    /** `handleBulkDelete` — opens the confirm modal. */
    fun requestDelete() {
        if (_state.value.selected.isEmpty()) return
        _state.update { it.copy(deleteConfirmOpen = true) }
    }

    fun dismissDelete() {
        _state.update { it.copy(deleteConfirmOpen = false) }
    }

    /** `confirmBulkDelete` */
    fun confirmDelete() {
        val removed = store.deleteNotes(_state.value.selected)
        if (removed.isEmpty()) {
            dismissDelete()
            return
        }
        lastDeleted = removed
        reload()
        _state.update { it.copy(selectMode = false, selected = emptySet(), deleteConfirmOpen = false) }
        showToast(
            "${removed.size} ${noteWord(removed.size)} deleted",
            R.drawable.ic_fa_trash_can,
            actionLabel = "Undo",
        )
    }

    // ─── Create-New menu (Fab.jsx) ───────────────

    fun toggleCreateMenu() {
        _state.update { it.copy(createMenuOpen = !it.createMenuOpen) }
    }

    fun closeCreateMenu() {
        _state.update { it.copy(createMenuOpen = false) }
    }

    /** "Create notebook" / "Create tag" rows: close the menu, open the small input modal. */
    fun openQuickCreate(kind: QuickCreateKind) {
        _state.update { it.copy(createMenuOpen = false, quickCreate = kind) }
    }

    fun closeQuickCreate() {
        _state.update { it.copy(quickCreate = null) }
    }

    /** Notebook branch of `submitQuickModal` (blank → just closes). */
    fun createNotebook(rawName: String) {
        val name = rawName.trim()
        closeQuickCreate()
        if (name.isEmpty()) return
        val now = Iso.now()
        store.saveNotebook(
            Notebook(
                id = genId(),
                name = name.take(32),
                color = NOTEBOOK_COLORS.random(),
                icon = "fa-book-open",
                createdAt = now,
                updatedAt = now,
            ),
        )
        reload()
        // Web navigates to /notebooks here; until that screen exists, confirm with a toast.
        showToast("Notebook \"${name.take(32)}\" created", R.drawable.ic_fa_book_open)
    }

    /**
     * Tag branch of `submitQuickModal`: closes the modal and returns the cleaned tag
     * (null when blank). The caller opens the Editor with it once that screen exists.
     */
    fun createTag(rawTag: String): String? {
        closeQuickCreate()
        return normalizeTagInput(rawTag).ifEmpty { null }
    }

    // ─── pinned drag-reorder ─────────────────────

    /** Live swap while dragging; returns true if the order actually changed. */
    fun movePinned(activeId: String, overId: String): Boolean {
        if (!store.movePinned(activeId, overId)) return false
        reload()
        return true
    }

    // ─── toast ───────────────────────────────────

    /** The toast's action button (only "Undo" exists for now). */
    fun onToastAction() {
        val restored = lastDeleted
        if (restored.isEmpty()) return
        lastDeleted = emptyList()
        store.restoreNotes(restored)
        reload()
        showToast("${restored.size} ${noteWord(restored.size)} restored", R.drawable.ic_fa_rotate_left)
    }

    fun dismissToast() {
        // Once the Undo toast is gone there is nothing left to undo.
        if (_state.value.toast?.actionLabel != null) lastDeleted = emptyList()
        _state.update { it.copy(toast = null) }
    }

    private fun showToast(message: String, icon: Int, actionLabel: String? = null) {
        _state.update { it.copy(toast = ToastState(System.nanoTime(), message, icon, actionLabel)) }
    }

    private companion object {
        val NOTEBOOK_COLORS = listOf("#E07B39", "#5B6AF0", "#2E9E6B", "#7C4FD4", "#D64040", "#E8A820")
    }

    private fun noteWord(n: Int) = if (n == 1) "note" else "notes"
}
