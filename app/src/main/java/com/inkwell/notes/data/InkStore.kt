package com.inkwell.notes.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.inkwell.notes.model.FontSize
import com.inkwell.notes.model.Note
import com.inkwell.notes.model.Notebook
import com.inkwell.notes.model.NotebookSort
import com.inkwell.notes.model.SearchMode
import com.inkwell.notes.model.SortOrder
import com.inkwell.notes.model.ThemeMode
import com.inkwell.notes.model.UserProfile
import com.inkwell.notes.util.stripHtml
import kotlinx.coroutines.delay
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

    // ── Account / settings (UI-only for now) ──
    var themeMode by mutableStateOf(ThemeMode.Dark)
    var fontSize by mutableStateOf(FontSize.Medium)
    var spellcheck by mutableStateOf(true)
    var autosave by mutableStateOf(true)
    var markdownMode by mutableStateOf(true)
    var serifBody by mutableStateOf(false)
    var user by mutableStateOf<UserProfile?>(null)
    var signingIn by mutableStateOf(false)
    var signInError by mutableStateOf<String?>(null)
    var lastBackup by mutableStateOf<Long?>(null)
    var backupBusy by mutableStateOf(false)
    var restoreBusy by mutableStateOf(false)

    val totalWords: Int get() = notes.sumOf { it.wordCount }
    fun storageKb(): Int =
        maxOf(1, (notes.sumOf { it.title.length + it.content.length } + notebooks.sumOf { it.name.length }) / 1024)

    fun setPref(key: String, value: Boolean) {
        when (key) {
            "spellcheck" -> spellcheck = value
            "autosave" -> autosave = value
            "markdownMode" -> markdownMode = value
            "serifBody" -> serifBody = value
        }
    }

    // DEMO actions. The data-layer part replaces these with real Google Sign-In and Drive appData sync.
    suspend fun signInDemo() {
        if (signingIn) return
        signInError = null
        signingIn = true
        delay(1100)
        user = UserProfile("Rema", "you@gmail.com")
        signingIn = false
    }

    fun signOut() { user = null }

    suspend fun backupDemo() {
        if (backupBusy || restoreBusy) return
        backupBusy = true
        delay(1400)
        lastBackup = System.currentTimeMillis()
        backupBusy = false
        toast.show("Backed up to Google Drive")
    }

    suspend fun restoreDemo() {
        if (backupBusy || restoreBusy) return
        if (lastBackup == null) {
            toast.show("No backup found on Drive")
            return
        }
        restoreBusy = true
        delay(1400)
        restoreBusy = false
        toast.show("Restored ${notes.size} notes")
    }

    fun deleteAllNotes() {
        notes.clear()
        toast.show("All notes deleted")
    }

    /** Wipes notes, notebooks, settings and the profile (same as the web "Reset all data"). */
    fun resetAll() {
        notes.clear()
        notebooks.clear()
        recentSearches.clear()
        sort = SortOrder.Modified
        notebookSort = NotebookSort.Name
        themeMode = ThemeMode.Dark
        fontSize = FontSize.Medium
        spellcheck = true; autosave = true; markdownMode = true; serifBody = false
        lastBackup = null
        user = null
    }
    val recentSearches = mutableStateListOf("launch plan", "groceries")
    /** Set by the FAB "Create tag" dialog. The editor part will open a new note with this tag. */
    var pendingNewTag by mutableStateOf<String?>(null)

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

    // ── Tags ──
    /** (tag, number of notes) sorted A-Z, same as the web Tags page. */
    fun tagCounts(): List<Pair<String, Int>> =
        notes.flatMap { it.tags }.groupingBy { it }.eachCount().toList().sortedBy { it.first.lowercase() }

    // ── Search (same rules as web searchNotes) ──
    fun search(query: String, mode: SearchMode, notebookId: String? = null): List<Note> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        return notes.filter { n ->
            val inTitle = n.title.lowercase().contains(q)
            val inContent = n.content.stripHtml().lowercase().contains(q)
            val inTags = n.tags.any { it.lowercase().contains(q) }
            val match = when (mode) {
                SearchMode.Title -> inTitle
                SearchMode.Content -> inContent
                SearchMode.Tags -> inTags
                SearchMode.All -> inTitle || inContent || inTags
            }
            match && (notebookId == null || n.notebookId == notebookId)
        }
    }

    /** Keeps the last 8 distinct searches, newest first. */
    fun addSearch(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        recentSearches.remove(q)
        recentSearches.add(0, q)
        while (recentSearches.size > 8) recentSearches.removeAt(recentSearches.lastIndex)
    }

    fun clearSearches() = recentSearches.clear()

    private fun newId(): String =
        System.currentTimeMillis().toString(36) + Random.nextInt(46656).toString(36)
}
