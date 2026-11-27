package com.inkwell.notes.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.inkwell.notes.model.Note
import com.inkwell.notes.model.Notebook
import com.inkwell.notes.model.NotebookSort
import com.inkwell.notes.model.SortOrder
import kotlin.random.Random

class ToastState {
    var message by mutableStateOf("")
    var visible by mutableStateOf(false)
    var token by mutableStateOf(0)
    fun show(text: String) {
        message = text
        visible = true
        token++
    }
}

/**
 * In-memory store for the UI parts. Later (data-layer part) this gets replaced by real
 * persistence (same JSON shape as the web app: iw_notes / iw_notebooks) and Drive sync.
 */
class InkStore {
    val notes = mutableStateListOf<Note>().apply { addAll(SampleData.notes()) }
    val notebooks = mutableStateListOf<Notebook>().apply { addAll(SampleData.notebooks) }
    var sort by mutableStateOf(SortOrder.Modified)
    var notebookSort by mutableStateOf(NotebookSort.Name)
    var newNotebookRequested by mutableStateOf(false)
    val toast = ToastState()

    fun notebook(id: String?): Notebook? = notebooks.firstOrNull { it.id == id }
    fun noteCount(notebookId: String): Int = notes.count { it.notebookId == notebookId }

    /** Pinned first (by pinOrder), then the chosen sort. Same rules as web sortNotes(). */
    fun sortedNotes(list: List<Note> = notes): List<Note> {
        val inner: Comparator<Note> = when (sort) {
            SortOrder.Modified -> compareByDescending<Note> { it.updatedAt }
            SortOrder.Created -> compareByDescending<Note> { it.createdAt }
            SortOrder.Title -> compareBy<Note> { it.title.lowercase() }
            SortOrder.Tags -> compareBy<Note> { it.tags.firstOrNull()?.lowercase() ?: "\uFFFF" }
        }
        val pinnedFirst = compareByDescending<Note> { it.pinned }
            .thenBy { if (it.pinned) (it.pinOrder ?: Int.MAX_VALUE) else 0 }
        return list.sortedWith(pinnedFirst.then(inner))
    }

    fun sortedNotebooks(): List<Notebook> = when (notebookSort) {
        NotebookSort.Name -> notebooks.sortedBy { it.name.lowercase() }
        NotebookSort.Notes -> notebooks.sortedByDescending { noteCount(it.id) }
        NotebookSort.Created -> notebooks.sortedByDescending { it.createdAt }
    }

    /** id == null creates a new notebook, otherwise updates that one. */
    fun saveNotebook(id: String?, name: String, color: String, icon: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        if (id == null) {
            notebooks.add(Notebook(newId(), clean, color, icon, System.currentTimeMillis()))
            toast.show("Notebook created")
        } else {
            val i = notebooks.indexOfFirst { it.id == id }
            if (i >= 0) {
                notebooks[i] = notebooks[i].copy(name = clean, color = color, icon = icon)
                toast.show("Notebook updated")
            }
        }
    }

    /** Notes are kept, they just lose their notebook (same as the web app). */
    fun deleteNotebook(id: String) {
        notebooks.removeAll { it.id == id }
        for (i in notes.indices) {
            if (notes[i].notebookId == id) notes[i] = notes[i].copy(notebookId = null)
        }
        toast.show("Notebook deleted")
    }

    private fun newId(): String =
        System.currentTimeMillis().toString(36) + Random.nextInt(46656).toString(36)
}
