package com.inkwell.notes.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.inkwell.notes.R
import com.inkwell.notes.data.AppPrefs
import com.inkwell.notes.data.NotesStore
import com.inkwell.notes.data.model.Note
import com.inkwell.notes.data.model.Notebook
import com.inkwell.notes.data.model.SortOrder
import com.inkwell.notes.ui.components.ToastState
import com.inkwell.notes.util.Iso
import com.inkwell.notes.util.genId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.Collator

/** Notebooks page sort — the dropdown on the right of the toolbar. */
enum class NbSort(val key: String, val label: String) {
    Newest("newest", "Newest"),
    Oldest("oldest", "Oldest"),
    Name("name", "Name A–Z"),
    Count("count", "Most notes");

    companion object {
        fun fromKey(key: String?): NbSort = entries.firstOrNull { it.key == key } ?: Newest
    }
}

/** Notebooks page layout — the three-way toggle on the left of the toolbar. */
enum class NbView(val key: String) {
    Cards("cards"), Compact("list"), Grid("grid");

    companion object {
        fun fromKey(key: String?): NbView = entries.firstOrNull { it.key == key } ?: Cards
    }
}

data class NotebooksState(
    /** Already ordered by [sort]. */
    val notebooks: List<Notebook> = emptyList(),
    val counts: Map<String, Int> = emptyMap(),
    val sort: NbSort = NbSort.Newest,
    val view: NbView = NbView.Cards,
    /** All notes, already `sortNotes`-ed (NotebookDetail filters this by notebook). */
    val notes: List<Note> = emptyList(),
    val sortOrder: SortOrder = SortOrder.Modified,
    // ── Notebooks page ──
    val formOpen: Boolean = false,
    /** Notebook being edited; null = "New Notebook". */
    val editing: Notebook? = null,
    val ctxId: String? = null,
    val deleteId: String? = null,
    val toast: ToastState? = null,
    // ── NotebookDetail page ──
    val detailMenuOpen: Boolean = false,
    val detailDeleteOpen: Boolean = false,
)

/** Kotlin side of the Notebooks.jsx / NotebookDetail.jsx handlers. */
class NotebooksViewModel(application: Application) : AndroidViewModel(application) {
    private val store = NotesStore(application)

    private val prefs = AppPrefs.of(application)

    private val _state = MutableStateFlow(
        NotebooksState(
            sort = NbSort.fromKey(prefs.getString(KEY_NB_SORT, null)),
            view = NbView.fromKey(prefs.getString(KEY_NB_VIEW, null)),
        ),
    )
    val state: StateFlow<NotebooksState> = _state.asStateFlow()

    init {
        reload()
    }

    private fun NotebooksState.withFreshData(): NotebooksState {
        val order = store.getSortOrder()
        val notes = store.getNotes()
        val counts = notes.groupingBy { it.notebookId }.eachCount()
            .mapNotNull { (id, n) -> id?.let { it to n } }.toMap()
        return copy(
            notebooks = sortNotebooks(store.getNotebooks(), counts, sort),
            counts = counts,
            notes = NotesStore.sortNotes(notes, order),
            sortOrder = order,
        )
    }

    /** `dispatch({ type: 'RELOAD' })` */
    fun reload() {
        _state.update { it.withFreshData() }
    }

    // ─── Notebooks page ──────────────────────────

    fun setNbSort(sort: NbSort) {
        prefs.edit().putString(KEY_NB_SORT, sort.key).apply()
        _state.update { it.copy(sort = sort).withFreshData() }
    }

    fun setNbView(view: NbView) {
        prefs.edit().putString(KEY_NB_VIEW, view.key).apply()
        _state.update { it.copy(view = view) }
    }

    /** `openModal(id)` — null id = new notebook. */
    fun openForm(id: String? = null) {
        _state.update { it.copy(formOpen = true, editing = id?.let(store::getNotebook)) }
    }

    fun closeForm() {
        _state.update { it.copy(formOpen = false, editing = null) }
    }

    /** `submitNb` — a blank name keeps the modal open (the Save button does nothing). */
    fun saveForm(rawName: String, color: String, icon: String) {
        val name = rawName.trim()
        if (name.isEmpty()) return
        val existing = _state.value.editing
        val now = Iso.now()
        store.saveNotebook(
            existing?.copy(name = name, color = color, icon = icon, updatedAt = now)
                ?: Notebook(id = genId(), name = name, color = color, icon = icon, createdAt = now, updatedAt = now),
        )
        _state.update { it.copy(formOpen = false, editing = null).withFreshData() }
        showToast(if (existing != null) "Notebook updated" else "Notebook created", R.drawable.ic_fa_book_open)
    }

    fun openCtx(id: String) {
        _state.update { it.copy(ctxId = id) }
    }

    fun closeCtx() {
        _state.update { it.copy(ctxId = null) }
    }

    /** Context sheet → "Delete notebook": closes the sheet, opens the confirm modal. */
    fun requestDelete(id: String) {
        _state.update { it.copy(ctxId = null, deleteId = id) }
    }

    fun dismissDelete() {
        _state.update { it.copy(deleteId = null) }
    }

    /** `doDelete` */
    fun confirmDelete() {
        val id = _state.value.deleteId ?: return
        store.deleteNotebook(id)
        _state.update { it.copy(ctxId = null, deleteId = null).withFreshData() }
        showToast("Notebook deleted", R.drawable.ic_fa_trash_can)
    }

    // ─── NotebookDetail page ─────────────────────

    /** Same sort the Home page uses (`setSortOrder`). */
    fun setSortOrder(order: SortOrder) {
        store.setSortOrder(order)
        reload()
    }

    fun openDetailMenu() {
        _state.update { it.copy(detailMenuOpen = true) }
    }

    fun closeDetailMenu() {
        _state.update { it.copy(detailMenuOpen = false) }
    }

    fun requestDetailDelete() {
        _state.update { it.copy(detailMenuOpen = false, detailDeleteOpen = true) }
    }

    fun dismissDetailDelete() {
        _state.update { it.copy(detailDeleteOpen = false) }
    }

    /** The screen notices the notebook is gone and leaves (web: `if (!nb) navigate('/notebooks')`). */
    fun confirmDetailDelete(id: String) {
        store.deleteNotebook(id)
        _state.update { it.copy(detailDeleteOpen = false).withFreshData() }
    }

    // ─── toast ───────────────────────────────────

    fun dismissToast() {
        _state.update { it.copy(toast = null) }
    }

    private fun showToast(message: String, icon: Int) {
        _state.update { it.copy(toast = ToastState(System.nanoTime(), message, icon)) }
    }

    private companion object {
        const val KEY_NB_SORT = "iw_nb_sort"
        const val KEY_NB_VIEW = "iw_nb_view"

        /** Newest / oldest first by creation time, name A→Z, or most notes first. */
        fun sortNotebooks(list: List<Notebook>, counts: Map<String, Int>, sort: NbSort): List<Notebook> {
            val collator = Collator.getInstance()
            return when (sort) {
                NbSort.Name -> list.sortedWith(Comparator { a, b -> collator.compare(a.name, b.name) })
                NbSort.Count -> list.sortedByDescending { counts[it.id] ?: 0 }
                NbSort.Newest -> list.sortedByDescending { Iso.millis(it.createdAt) }
                NbSort.Oldest -> list.sortedBy { Iso.millis(it.createdAt) }
            }
        }
    }
}
