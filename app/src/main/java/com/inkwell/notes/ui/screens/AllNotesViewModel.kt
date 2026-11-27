package com.inkwell.notes.ui.screens

import androidx.lifecycle.ViewModel
import com.inkwell.notes.data.DateField
import com.inkwell.notes.data.DateRange
import com.inkwell.notes.data.DayPart
import com.inkwell.notes.data.NoteFilter
import com.inkwell.notes.data.NoteOrder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class AllNotesState(
    val filter: NoteFilter = NoteFilter(),
    val filterOpen: Boolean = false,
    /** The tag manager sheet (list of tags with edit / delete). */
    val manageOpen: Boolean = false,
    /** Tag being renamed (rename modal open). */
    val renaming: String? = null,
    /** Tag waiting for delete confirmation. */
    val deleting: String? = null,
)

/** UI state of the All Notes page: the filter + which sheet / modal is open. Notes themselves come from [HomeViewModel]. */
class AllNotesViewModel : ViewModel() {
    private val _state = MutableStateFlow(AllNotesState())
    val state: StateFlow<AllNotesState> = _state.asStateFlow()

    private fun edit(block: (NoteFilter) -> NoteFilter) = _state.update { it.copy(filter = block(it.filter)) }

    // ── sheets / modals ──
    fun openFilter() = _state.update { it.copy(filterOpen = true, manageOpen = false) }
    fun closeFilter() = _state.update { it.copy(filterOpen = false) }
    fun openManage() = _state.update { it.copy(manageOpen = true, filterOpen = false) }
    fun closeManage() = _state.update { it.copy(manageOpen = false) }
    fun startRename(tag: String) = _state.update { it.copy(renaming = tag) }
    fun cancelRename() = _state.update { it.copy(renaming = null) }
    fun askDelete(tag: String) = _state.update { it.copy(deleting = tag) }
    fun cancelDelete() = _state.update { it.copy(deleting = null) }

    /** System Back: closes the top-most layer. */
    fun closeTopLayer() = _state.update {
        when {
            it.renaming != null -> it.copy(renaming = null)
            it.deleting != null -> it.copy(deleting = null)
            it.manageOpen -> it.copy(manageOpen = false)
            it.filterOpen -> it.copy(filterOpen = false)
            else -> it
        }
    }

    val anyLayerOpen: Boolean
        get() = _state.value.let { it.renaming != null || it.deleting != null || it.manageOpen || it.filterOpen }

    // ── filter edits ──
    fun toggleTag(tag: String) = edit { f -> f.copy(tags = if (tag in f.tags) f.tags - tag else f.tags + tag) }
    fun clearTags() = edit { it.copy(tags = emptySet()) }
    fun setMatchAll(all: Boolean) = edit { it.copy(matchAll = all) }
    fun setOrder(o: NoteOrder) = edit { it.copy(order = o) }
    fun setDateField(d: DateField) = edit { it.copy(dateField = d) }
    fun setRange(r: DateRange) = edit { it.copy(range = r) }
    fun toggleDayPart(p: DayPart) = edit { f -> f.copy(dayParts = if (p in f.dayParts) f.dayParts - p else f.dayParts + p) }
    fun clearDayParts() = edit { it.copy(dayParts = emptySet()) }
    fun resetFilter() = edit { NoteFilter() }

    /** Tapping a tag in the manager: show only that tag. */
    fun filterOnlyTag(tag: String) = _state.update {
        it.copy(filter = it.filter.copy(tags = setOf(tag)), manageOpen = false)
    }

    // ── keep the filter valid when tags change ──
    fun onTagRenamed(old: String, new: String) = edit { f ->
        if (old in f.tags) f.copy(tags = f.tags - old + new) else f
    }

    /** Drops selected tags that no longer exist on any note. */
    fun prune(existing: Set<String>) = _state.update {
        if (it.filter.tags.all { t -> t in existing }) it
        else it.copy(filter = it.filter.copy(tags = it.filter.tags.filter { t -> t in existing }.toSet()))
    }
}
