package com.inkwell.notes.data

import android.content.Context
import com.inkwell.notes.data.model.Note
import com.inkwell.notes.data.model.Notebook
import com.inkwell.notes.data.model.SortOrder
import com.inkwell.notes.data.model.Stats
import com.inkwell.notes.util.Iso
import org.json.JSONArray
import java.text.Collator

/**
 * Port of `src/store/storage.js` (notes / notebooks / sort / stats) + `seedIfEmpty()`.
 * Same SharedPreferences keys as the web localStorage keys, same JSON shape.
 * This is the "API" every screen talks to — new functions get added here step by step.
 */
class NotesStore(context: Context) {
    private val prefs = AppPrefs.of(context)

    // ─── Notes CRUD ───────────────────────────
    fun getNotes(): List<Note> = readArray(AppPrefs.KEY_NOTES) { Note.fromJson(it) }

    fun saveNotes(list: List<Note>) {
        prefs.edit().putString(AppPrefs.KEY_NOTES, JSONArray(list.map { it.toJson() }).toString()).apply()
    }

    fun getNote(id: String): Note? = getNotes().firstOrNull { it.id == id }

    fun saveNote(note: Note) {
        val arr = getNotes().toMutableList()
        val i = arr.indexOfFirst { it.id == note.id }
        if (i >= 0) arr[i] = note else arr.add(note)
        saveNotes(arr)
    }

    fun deleteNote(id: String) = saveNotes(getNotes().filter { it.id != id })

    // ─── Notebooks CRUD ───────────────────────
    fun getNotebooks(): List<Notebook> = readArray(AppPrefs.KEY_NOTEBOOKS) { Notebook.fromJson(it) }

    fun saveNotebooks(list: List<Notebook>) {
        prefs.edit().putString(AppPrefs.KEY_NOTEBOOKS, JSONArray(list.map { it.toJson() }).toString()).apply()
    }

    fun getNotebook(id: String): Notebook? = getNotebooks().firstOrNull { it.id == id }

    fun saveNotebook(nb: Notebook) {
        val arr = getNotebooks().toMutableList()
        val i = arr.indexOfFirst { it.id == nb.id }
        if (i >= 0) arr[i] = nb else arr.add(nb)
        saveNotebooks(arr)
    }

    /** Also detaches the notebook's notes (same as web `deleteNotebook`). */
    fun deleteNotebook(id: String) {
        saveNotebooks(getNotebooks().filter { it.id != id })
        saveNotes(getNotes().map { if (it.notebookId == id) it.copy(notebookId = null) else it })
    }

    fun noteCountForNotebook(id: String): Int = getNotes().count { it.notebookId == id }

    // ─── Sort order ───────────────────────────
    fun getSortOrder(): SortOrder = SortOrder.fromKey(prefs.getString(AppPrefs.KEY_SORT, null))

    fun setSortOrder(order: SortOrder) {
        prefs.edit().putString(AppPrefs.KEY_SORT, order.key).apply()
    }

    // ─── Stats ────────────────────────────────
    fun getStats(): Stats {
        val notes = getNotes()
        return Stats(
            notes = notes.size,
            notebooks = getNotebooks().size,
            words = notes.sumOf { it.wordCount },
        )
    }

    // ─── Seed ─────────────────────────────────
    /** `seedIfEmpty()` — creates / refreshes only the built-in welcome note. */
    fun seedIfEmpty() {
        val raw = prefs.getString(AppPrefs.KEY_NOTES, null)
        val alreadySeeded = prefs.getString(AppPrefs.KEY_SEED_VERSION, null) == Seed.CURRENT_VERSION

        fun seedWelcome() {
            saveNotes(listOf(Seed.welcomeNote()))
            prefs.edit().putString(AppPrefs.KEY_SEED_VERSION, Seed.CURRENT_VERSION).apply()
        }

        if (raw == null) return seedWelcome()

        val notes = try {
            JSONArray(raw).let { a -> (0 until a.length()).mapNotNull { Note.fromJson(a.getJSONObject(it)) } }
        } catch (_: Exception) {
            return seedWelcome()
        }
        if (notes.isEmpty()) return seedWelcome()

        val idx = notes.indexOfFirst { it.id == Seed.WELCOME_ID }
        if (idx >= 0 && !alreadySeeded) {
            val current = notes[idx]
            val fresh = Seed.welcomeNote()
            val next = notes.toMutableList()
            next[idx] = fresh.copy(
                createdAt = current.createdAt.ifEmpty { fresh.createdAt },
                pinned = current.pinned,
            )
            saveNotes(next)
            prefs.edit().putString(AppPrefs.KEY_SEED_VERSION, Seed.CURRENT_VERSION).apply()
        } else if (!alreadySeeded) {
            prefs.edit().putString(AppPrefs.KEY_SEED_VERSION, Seed.CURRENT_VERSION).apply()
        }
    }

    private fun <T> readArray(key: String, parse: (org.json.JSONObject) -> T?): List<T> = try {
        val raw = prefs.getString(key, null)
        if (raw.isNullOrEmpty()) emptyList()
        else JSONArray(raw).let { a -> (0 until a.length()).mapNotNull { parse(a.getJSONObject(it)) } }
    } catch (_: Exception) {
        emptyList()
    }

    companion object {
        /**
         * `sortNotes()` — pinned always first (ordered by pinOrder), then by [order].
         * Stable sort, so equal items keep their stored order like JS Array.sort.
         */
        fun sortNotes(notes: List<Note>, order: SortOrder): List<Note> {
            val collator = Collator.getInstance()
            return notes.sortedWith(Comparator { a, b ->
                if (a.pinned != b.pinned) return@Comparator if (b.pinned) 1 else -1
                if (a.pinned && b.pinned) {
                    val ao = a.pinOrder ?: 999_999
                    val bo = b.pinOrder ?: 999_999
                    if (ao != bo) return@Comparator ao - bo
                }
                when (order) {
                    SortOrder.Created -> Iso.millis(b.createdAt).compareTo(Iso.millis(a.createdAt))
                    SortOrder.Title -> collator.compare(a.title, b.title)
                    SortOrder.Tags -> {
                        val at = (a.tags.firstOrNull() ?: "").lowercase()
                        val bt = (b.tags.firstOrNull() ?: "").lowercase()
                        val byTag = collator.compare(at, bt)
                        if (byTag != 0) byTag else Iso.millis(b.updatedAt).compareTo(Iso.millis(a.updatedAt))
                    }
                    SortOrder.Modified -> Iso.millis(b.updatedAt).compareTo(Iso.millis(a.updatedAt))
                }
            })
        }
    }
}
