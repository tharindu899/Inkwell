package com.inkwell.notes.data

import android.content.ContentResolver
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.inkwell.notes.model.FontSize
import com.inkwell.notes.model.Note
import com.inkwell.notes.model.Notebook
import com.inkwell.notes.model.NotebookSort
import com.inkwell.notes.model.SearchMode
import com.inkwell.notes.model.SortOrder
import com.inkwell.notes.model.ThemeMode
import com.inkwell.notes.model.UserProfile
import com.inkwell.notes.util.countWords
import com.inkwell.notes.util.stripHtml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
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
 * The single source of truth for the UI. Notes and notebooks are saved to disk automatically (see [runPersistence]),
 * the profile comes from Google Sign-In, and backup / restore use Google Drive's appDataFolder.
 */
class InkStore(private val repo: InkRepository, private val auth: AuthProvider) {
    val notes = mutableStateListOf<Note>()
    val notebooks = mutableStateListOf<Notebook>()
    val toast = ToastState()
    val recentSearches = mutableStateListOf<String>()

    var sort by mutableStateOf(SortOrder.Modified)
    var notebookSort by mutableStateOf(NotebookSort.Name)
    var newNotebookRequested by mutableStateOf(false)
    /** Set by the FAB "Create tag" dialog, picked up by the next new note in the editor. */
    var pendingNewTag by mutableStateOf<String?>(null)

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
    var syncError by mutableStateOf<String?>(null)
    var updateInfo by mutableStateOf<UpdateInfo?>(null)

    val isAuthConfigured: Boolean get() = auth.isConfigured

    init {
        if (repo.hasNotesFile()) {
            notes.addAll(repo.loadNotes())
        } else {
            notes.add(WelcomeNote.create())
            repo.saveNotes(notes.toList(), emptyList())
        }
        notebooks.addAll(repo.loadNotebooks())
        val s = repo.loadSettings()
        themeMode = s.theme; fontSize = s.fontSize; sort = s.sort; notebookSort = s.notebookSort
        spellcheck = s.spellcheck; autosave = s.autosave; markdownMode = s.markdownMode; serifBody = s.serifBody
        recentSearches.addAll(s.searches)
        user = s.user
    }

    // ── Saving to disk ──
    private fun currentSettings() = AppSettings(
        themeMode, fontSize, sort, notebookSort, spellcheck, autosave, markdownMode, serifBody,
        recentSearches.toList(), user,
    )

    /** Call once from the root composable (LaunchedEffect). Saves shortly after every change. */
    suspend fun runPersistence() = coroutineScope {
        launch {
            snapshotFlow { notes.toList() to notebooks.toList() }.drop(1).collectLatest { (n, nb) ->
                delay(400)
                withContext(Dispatchers.IO) {
                    repo.saveNotes(n, nb)
                    repo.saveNotebooks(nb)
                }
            }
        }
        launch {
            snapshotFlow { currentSettings() }.drop(1).collectLatest {
                delay(200)
                withContext(Dispatchers.IO) { repo.saveSettings(it) }
            }
        }
    }

    // ── Notes ──
    fun noteById(id: String?): Note? = notes.firstOrNull { it.id == id }

    fun newNote(notebookId: String? = null, tag: String? = null): Note {
        val now = System.currentTimeMillis()
        return Note(
            id = newId(), title = "", content = "",
            tags = if (tag.isNullOrBlank()) emptyList() else listOf(tag),
            notebookId = notebookId, createdAt = now, updatedAt = now,
        )
    }

    /** Insert or update. Drops the web's stale notebookName / notebookColor when a note leaves its notebook. */
    fun saveNote(note: Note) {
        var n = note.copy(wordCount = countWords(note.content))
        val i = notes.indexOfFirst { it.id == n.id }
        if (i >= 0) {
            if (notes[i].notebookId != null && n.notebookId == null && n.extraJson.isNotBlank()) {
                val o = JSONObject(n.extraJson)
                o.remove("notebookName"); o.remove("notebookColor")
                n = n.copy(extraJson = if (o.length() == 0) "" else o.toString())
            }
            notes[i] = n
        } else {
            notes.add(n)
        }
    }

    fun deleteNote(id: String) {
        notes.removeAll { it.id == id }
        toast.show("Note deleted")
    }

    fun togglePin(id: String) {
        val i = notes.indexOfFirst { it.id == id }
        if (i < 0) return
        val n = notes[i]
        val nextPinned = !n.pinned
        val maxOrder = notes.filter { it.pinned && it.pinOrder != null }.maxOfOrNull { it.pinOrder ?: 0 } ?: -1
        notes[i] = n.copy(pinned = nextPinned, pinOrder = if (nextPinned) (n.pinOrder ?: (maxOrder + 1)) else null)
    }

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

    // ── Notebooks ──
    fun notebook(id: String?): Notebook? = notebooks.firstOrNull { it.id == id }
    fun noteCount(notebookId: String): Int = notes.count { it.notebookId == notebookId }

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
    fun tagCounts(): List<Pair<String, Int>> =
        notes.flatMap { it.tags }.groupingBy { it }.eachCount().toList().sortedBy { it.first.lowercase() }

    // ── Search ──
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

    fun addSearch(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        recentSearches.remove(q)
        recentSearches.add(0, q)
        while (recentSearches.size > 8) recentSearches.removeAt(recentSearches.lastIndex)
    }

    fun clearSearches() = recentSearches.clear()

    // ── Stats ──
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

    // ── Google account ──
    suspend fun signIn() {
        if (signingIn) return
        signInError = null
        signingIn = true
        try {
            user = auth.signIn()
        } catch (e: AuthException) {
            signInError = e.message
        } catch (e: Exception) {
            signInError = e.message ?: "Sign-in failed"
        } finally {
            signingIn = false
        }
    }

    suspend fun signOut() {
        auth.signOut()
        user = null
        lastBackup = null
    }

    // ── Google Drive backup (appDataFolder, same file and JSON as the web app) ──
    private fun payload(version: Int, timeKey: String): String {
        val u = user
        val profile = JSONObject().put("name", u?.name ?: "").put("email", u?.email ?: "")
        return JSONObject()
            .put("version", version)
            .put(timeKey, InkJson.toIso(System.currentTimeMillis()))
            .put("notes", InkJson.notesToArray(notes.toList(), notebooks.toList()))
            .put("notebooks", InkJson.notebooksToArray(notebooks.toList()))
            .put("profile", profile)
            .toString(2)
    }

    suspend fun refreshBackupInfo() {
        try {
            val token = auth.driveToken() ?: return
            lastBackup = DriveApi.find(token)?.modifiedTime?.let { InkJson.parseIso(it) }?.takeIf { it > 0 }
        } catch (e: Exception) {
            // Non-fatal: the row just says "No backup yet".
        }
    }

    suspend fun backup() {
        if (backupBusy || restoreBusy) return
        backupBusy = true
        syncError = null
        try {
            val token = auth.driveToken() ?: throw IOException("Google Drive access was not granted")
            val modified = DriveApi.backup(token, payload(2, "backedUpAt"))
            lastBackup = modified?.let { InkJson.parseIso(it) }?.takeIf { it > 0 } ?: System.currentTimeMillis()
            toast.show("Backed up to Google Drive")
        } catch (e: Exception) {
            syncError = e.message ?: "Backup failed"
            toast.show("Backup failed")
        } finally {
            backupBusy = false
        }
    }

    suspend fun restore() {
        if (backupBusy || restoreBusy) return
        restoreBusy = true
        syncError = null
        try {
            val token = auth.driveToken() ?: throw IOException("Google Drive access was not granted")
            val found = DriveApi.restore(token)
            if (found == null) {
                toast.show("No backup found on Drive")
            } else {
                val data = JSONObject(found.first)
                data.optJSONArray("notes")?.let { notes.clear(); notes.addAll(InkJson.notesFromArray(it)) }
                data.optJSONArray("notebooks")?.let { notebooks.clear(); notebooks.addAll(InkJson.notebooksFromArray(it)) }
                found.second?.let { lastBackup = InkJson.parseIso(it).takeIf { t -> t > 0 } }
                toast.show("Restored ${notes.size} notes")
            }
        } catch (e: Exception) {
            syncError = e.message ?: "Restore failed"
            toast.show("Restore failed")
        } finally {
            restoreBusy = false
        }
    }

    // ── Local export / import (JSON file the user picks) ──
    suspend fun exportTo(resolver: ContentResolver, uri: Uri) {
        try {
            val text = payload(1, "exportedAt")
            withContext(Dispatchers.IO) {
                resolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(text) } ?: throw IOException("Cannot write file")
            }
            toast.show("Export saved")
        } catch (e: Exception) {
            toast.show("Export failed")
        }
    }

    suspend fun importFrom(resolver: ContentResolver, uri: Uri) {
        try {
            val text = withContext(Dispatchers.IO) {
                resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: throw IOException("Cannot read file")
            }
            val data = JSONObject(text)
            var added = 0
            var skipped = 0
            data.optJSONArray("notes")?.let { arr ->
                val ids = notes.map { it.id }.toHashSet()
                for (n in InkJson.notesFromArray(arr)) {
                    if (ids.add(n.id)) { notes.add(n); added++ } else skipped++
                }
            }
            data.optJSONArray("notebooks")?.let { arr ->
                val ids = notebooks.map { it.id }.toHashSet()
                for (nb in InkJson.notebooksFromArray(arr)) {
                    if (ids.add(nb.id)) notebooks.add(nb)
                }
            }
            toast.show(
                "Imported $added note${if (added != 1) "s" else ""}" +
                    if (skipped > 0) " ($skipped duplicate${if (skipped != 1) "s" else ""} skipped)" else "",
            )
        } catch (e: Exception) {
            toast.show("Import failed — invalid file")
        }
    }

    // ── App updates (GitHub Releases) ──
    suspend fun checkForUpdates(currentVersion: String, repoName: String) {
        if (repoName.isBlank()) {
            toast.show("Update source is not configured")
            return
        }
        try {
            val latest = UpdateChecker.latest(repoName)
            if (UpdateChecker.isNewer(latest.version, currentVersion)) updateInfo = latest
            else toast.show("You're on the latest version")
        } catch (e: Exception) {
            toast.show("Could not check for updates")
        }
    }

    // ── Danger zone ──
    fun deleteAllNotes() {
        notes.clear()
        toast.show("All notes deleted")
    }

    /** Wipes notes, notebooks, settings and the profile (same as the web "Reset all data"). */
    fun resetAll() {
        repo.clearAll()
        notes.clear()
        notes.add(WelcomeNote.create())
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

    private fun newId(): String =
        System.currentTimeMillis().toString(36) + Random.nextInt(46656).toString(36)
}
