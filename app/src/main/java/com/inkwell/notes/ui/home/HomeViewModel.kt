package com.inkwell.notes.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.inkwell.notes.InkwellApplication
import com.inkwell.notes.data.Note
import com.inkwell.notes.data.Notebook
import com.inkwell.notes.data.SortOrder
import com.inkwell.notes.ui.components.ToastData
import com.inkwell.notes.util.nowIso
import com.inkwell.notes.util.sortNotes
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HomeStats(val notes: Int = 0, val books: Int = 0, val words: Int = 0)

data class HomeUiState(
    val pinned: List<Note> = emptyList(),
    val recent: List<Note> = emptyList(),
    val notebooks: Map<String, Notebook> = emptyMap(),
    val stats: HomeStats = HomeStats(),
    val sort: SortOrder = SortOrder.Modified,
    val selectMode: Boolean = false,
    val selected: Set<String> = emptySet(),
    val anySelectedPinned: Boolean = false,
    val confirmDelete: Boolean = false,
)

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as InkwellApplication).repo

    /** null = normal mode, non-null = multi-select mode with these ids */
    private val selection = MutableStateFlow<Set<String>?>(null)
    private val confirm = MutableStateFlow(false)

    private val _events = MutableSharedFlow<ToastData>(extraBufferCapacity = 4)
    val events: SharedFlow<ToastData> = _events.asSharedFlow()

    val ui: StateFlow<HomeUiState> = combine(
        repo.notes, repo.notebooks, repo.sortOrder, selection, confirm,
    ) { notes, books, sort, sel, conf ->
        val sorted = sortNotes(notes, sort)
        HomeUiState(
            pinned = sorted.filter { it.pinned },
            recent = sorted.filter { !it.pinned },
            notebooks = books.associateBy { it.id },
            stats = HomeStats(notes.size, books.size, notes.sumOf { it.wordCount }),
            sort = sort,
            selectMode = sel != null,
            selected = sel.orEmpty(),
            anySelectedPinned = sel != null && notes.any { it.id in sel && it.pinned },
            confirmDelete = conf,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HomeUiState())

    fun setSort(order: SortOrder) = repo.setSort(order)

    // ── selection ──
    fun startSelecting() { selection.value = emptySet() }
    fun enterSelectMode(firstId: String) { selection.value = setOf(firstId) }
    fun toggleSelect(id: String) {
        val cur = selection.value ?: return
        selection.value = if (id in cur) cur - id else cur + id
    }
    fun cancelSelect() {
        confirm.value = false
        selection.value = null
    }

    // ── bulk actions ──
    fun pinSelected() {
        val sel = selection.value ?: return
        if (sel.isEmpty()) return
        val notes = repo.notes.value
        val anyPinned = notes.any { it.id in sel && it.pinned }
        var next = (notes.filter { it.pinned }.mapNotNull { it.pinOrder }.maxOrNull() ?: -1) + 1
        val now = nowIso()
        repo.update(sel) { n ->
            if (anyPinned) n.copy(pinned = false, pinOrder = null, updatedAt = now)
            else n.copy(pinned = true, pinOrder = n.pinOrder ?: next++, updatedAt = now)
        }
        val c = sel.size
        _events.tryEmit(ToastData(if (anyPinned) "$c note${if (c > 1) "s" else ""} unpinned" else "$c note${if (c > 1) "s" else ""} pinned"))
        cancelSelect()
    }

    fun requestDelete() { if (!selection.value.isNullOrEmpty()) confirm.value = true }
    fun dismissDelete() { confirm.value = false }

    fun confirmDelete() {
        val sel = selection.value.orEmpty()
        val deleted = repo.notes.value.filter { it.id in sel }
        if (deleted.isEmpty()) { cancelSelect(); return }
        repo.delete(sel)
        cancelSelect()
        val c = deleted.size
        _events.tryEmit(
            ToastData(
                message = "$c note${if (c > 1) "s" else ""} deleted",
                actionLabel = "Undo",
                onAction = {
                    repo.restore(deleted)
                    _events.tryEmit(ToastData("$c note${if (c > 1) "s" else ""} restored"))
                },
            ),
        )
    }
}
