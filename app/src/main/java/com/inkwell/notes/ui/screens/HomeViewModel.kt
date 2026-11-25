package com.inkwell.notes.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.inkwell.notes.data.NotesStore
import com.inkwell.notes.data.model.Note
import com.inkwell.notes.data.model.Notebook
import com.inkwell.notes.data.model.SortOrder
import com.inkwell.notes.data.model.Stats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class HomeState(
    /** Already sorted (pinned first). */
    val notes: List<Note> = emptyList(),
    val notebooks: List<Notebook> = emptyList(),
    val sortOrder: SortOrder = SortOrder.Modified,
    val stats: Stats = Stats(0, 0, 0),
) {
    val pinned: List<Note> get() = notes.filter { it.pinned }
    val recent: List<Note> get() = notes.filter { !it.pinned }
}

/** Kotlin side of `AppContext.jsx` for the Home page. */
class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val store = NotesStore(application)

    private val _state = MutableStateFlow(load())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    private fun load(): HomeState {
        store.seedIfEmpty()
        val order = store.getSortOrder()
        return HomeState(
            notes = NotesStore.sortNotes(store.getNotes(), order),
            notebooks = store.getNotebooks(),
            sortOrder = order,
            stats = store.getStats(),
        )
    }

    /** `dispatch({ type: 'RELOAD' })` */
    fun reload() {
        _state.value = load()
    }

    /** `dispatch({ type: 'SET_SORT' })` */
    fun setSortOrder(order: SortOrder) {
        store.setSortOrder(order)
        reload()
    }
}
