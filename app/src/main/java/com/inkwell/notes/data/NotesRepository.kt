package com.inkwell.notes.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Local store. Notes + notebooks live in one JSON file (filesDir/inkwell_data.json)
 * in the same format as the old app's backup: {"notes":[...],"notebooks":[...]}.
 * UI reads StateFlows; every change is written to disk in the background.
 */
class NotesRepository(context: Context) {

    private val appContext = context.applicationContext
    private val file = File(appContext.filesDir, "inkwell_data.json")
    private val prefs = appContext.getSharedPreferences("inkwell_prefs", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeLock = Mutex()

    private val _notes = MutableStateFlow<List<Note>>(emptyList())
    private val _notebooks = MutableStateFlow<List<Notebook>>(emptyList())
    private val _sort = MutableStateFlow(SortOrder.fromKey(prefs.getString("sort", null)))

    val notes: StateFlow<List<Note>> = _notes.asStateFlow()
    val notebooks: StateFlow<List<Notebook>> = _notebooks.asStateFlow()
    val sortOrder: StateFlow<SortOrder> = _sort.asStateFlow()

    private val _recent = MutableStateFlow(loadSearches())
    val recentSearches: StateFlow<List<String>> = _recent.asStateFlow()

    init {
        val firstRun = !file.exists()
        load()
        if (firstRun && _notes.value.isEmpty()) {
            _notes.value = listOf(Seed.welcomeNote())
            persist()
        }
    }

    // ── reads ──
    fun getNote(id: String): Note? = _notes.value.firstOrNull { it.id == id }

    // ── writes ──
    fun upsert(note: Note) {
        _notes.value = _notes.value.toMutableList().also { list ->
            val i = list.indexOfFirst { it.id == note.id }
            if (i >= 0) list[i] = note else list.add(note)
        }
        persist()
    }

    fun update(ids: Set<String>, transform: (Note) -> Note) {
        _notes.value = _notes.value.map { if (it.id in ids) transform(it) else it }
        persist()
    }

    fun delete(ids: Set<String>) {
        _notes.value = _notes.value.filterNot { it.id in ids }
        persist()
    }

    /** Used by Undo: puts deleted notes back (skips ones that already exist). */
    fun restore(notes: List<Note>) {
        val existing = _notes.value.map { it.id }.toSet()
        _notes.value = _notes.value + notes.filter { it.id !in existing }
        persist()
    }

    // ── notebooks ──
    fun upsertNotebook(nb: Notebook) {
        _notebooks.value = _notebooks.value.toMutableList().also { list ->
            val i = list.indexOfFirst { it.id == nb.id }
            if (i >= 0) list[i] = nb else list.add(nb)
        }
        persist()
    }

    /** Deleting a notebook keeps its notes — they just become unorganised. */
    fun deleteNotebook(id: String) {
        _notebooks.value = _notebooks.value.filterNot { it.id == id }
        _notes.value = _notes.value.map { if (it.notebookId == id) it.copy(notebookId = null) else it }
        persist()
    }

    // ── recent searches ──
    fun addSearch(q: String) {
        val t = q.trim()
        if (t.isEmpty()) return
        _recent.value = (listOf(t) + _recent.value.filter { it != t }).take(8)
        prefs.edit().putString("searches", JSONArray(_recent.value).toString()).apply()
    }

    fun clearSearches() {
        _recent.value = emptyList()
        prefs.edit().remove("searches").apply()
    }

    private fun loadSearches(): List<String> = try {
        val a = JSONArray(prefs.getString("searches", "[]"))
        (0 until a.length()).map { a.optString(it) }.filter { it.isNotEmpty() }
    } catch (_: Exception) { emptyList() }

    fun setSort(order: SortOrder) {
        _sort.value = order
        prefs.edit().putString("sort", order.key).apply()
    }

    // ── disk ──
    private fun load() {
        try {
            if (!file.exists()) return
            val root = JSONObject(file.readText())
            val n = ArrayList<Note>()
            root.optJSONArray("notes")?.let { a ->
                for (i in 0 until a.length()) a.optJSONObject(i)?.let { Note.fromJson(it) }?.let(n::add)
            }
            val b = ArrayList<Notebook>()
            root.optJSONArray("notebooks")?.let { a ->
                for (i in 0 until a.length()) a.optJSONObject(i)?.let { Notebook.fromJson(it) }?.let(b::add)
            }
            _notes.value = n
            _notebooks.value = b
        } catch (_: Exception) {
            // corrupted file — keep the broken copy so nothing is lost
            runCatching { file.copyTo(File(file.parentFile, "inkwell_data.broken.json"), overwrite = true) }
        }
    }

    private fun persist() {
        val notes = _notes.value
        val books = _notebooks.value
        scope.launch {
            writeLock.withLock {
                try {
                    val root = JSONObject()
                        .put("notes", JSONArray(notes.map { it.toJson() }))
                        .put("notebooks", JSONArray(books.map { it.toJson() }))
                    val tmp = File(file.parentFile, file.name + ".tmp")
                    tmp.writeText(root.toString())
                    tmp.renameTo(file)
                } catch (_: Exception) {
                }
            }
        }
    }
}
