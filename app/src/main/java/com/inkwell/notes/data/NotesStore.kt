package com.inkwell.notes.data

import android.content.Context
import com.inkwell.notes.data.model.Note
import com.inkwell.notes.data.model.Notebook
import com.inkwell.notes.data.model.SearchMode
import com.inkwell.notes.data.model.SortOrder
import com.inkwell.notes.data.model.Stats
import com.inkwell.notes.util.Iso
import com.inkwell.notes.util.stripHtml
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

    /**
     * Home multi-select "Pin / Unpin" (web `handleBulkPin`).
     * Pinning keeps an existing pinOrder or appends after the highest one; unpinning clears it.
     */
    fun setPinned(ids: Set<String>, pinned: Boolean) {
        val all = getNotes()
        var nextOrder = (all.filter { it.pinned }.mapNotNull { it.pinOrder }.maxOrNull() ?: -1) + 1
        val now = Iso.now()
        saveNotes(all.map { n ->
            if (n.id !in ids) n
            else n.copy(
                pinned = pinned,
                pinOrder = if (pinned) (n.pinOrder ?: nextOrder++) else null,
                updatedAt = now,
            )
        })
    }

    /**
     * Pinned drag-reorder (web `movePinnedNote`): moves [activeId] to where [overId] is
     * and re-numbers every pinned note's pinOrder 0..n-1. updatedAt is left alone.
     * Returns false when nothing changed.
     */
    fun movePinned(activeId: String, overId: String): Boolean {
        if (activeId == overId) return false
        val all = getNotes()
        val pinned = sortNotes(all.filter { it.pinned }, getSortOrder()).toMutableList()
        val from = pinned.indexOfFirst { it.id == activeId }
        val to = pinned.indexOfFirst { it.id == overId }
        if (from < 0 || to < 0 || from == to) return false
        pinned.add(to, pinned.removeAt(from))
        val orderById = pinned.mapIndexed { i, n -> n.id to i }.toMap()
        saveNotes(all.map { n -> orderById[n.id]?.let { n.copy(pinOrder = it) } ?: n })
        return true
    }

    /** Deletes several notes at once and returns what was removed (for Undo). */
    fun deleteNotes(ids: Set<String>): List<Note> {
        val all = getNotes()
        val removed = all.filter { it.id in ids }
        if (removed.isNotEmpty()) saveNotes(all.filter { it.id !in ids })
        return removed
    }

    /** Undo of [deleteNotes] — same as calling `saveNote` for each (re-added at the end if missing). */
    fun restoreNotes(notes: List<Note>) {
        if (notes.isEmpty()) return
        val all = getNotes().toMutableList()
        notes.forEach { n ->
            val i = all.indexOfFirst { it.id == n.id }
            if (i >= 0) all[i] = n else all.add(n)
        }
        saveNotes(all)
    }

    // ─── Tags ─────────────────────────────────
    /**
     * Renames [old] to [new] on every note (merging if the note already has [new]).
     * Returns how many notes changed. updatedAt is left alone so a bulk rename doesn't reshuffle lists.
     */
    fun renameTag(old: String, new: String): Int {
        var changed = 0
        saveNotes(getNotes().map { n ->
            if (old !in n.tags) n
            else {
                changed++
                n.copy(tags = n.tags.map { if (it == old) new else it }.distinct())
            }
        })
        return changed
    }

    /** Removes [tag] from every note. Returns how many notes changed. */
    fun deleteTag(tag: String): Int {
        var changed = 0
        saveNotes(getNotes().map { n ->
            if (tag !in n.tags) n
            else {
                changed++
                n.copy(tags = n.tags.filter { it != tag })
            }
        })
        return changed
    }

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

    // ─── Recent searches ──────────────────────
    /** `getSearches()` — newest first, max 8. */
    fun getSearches(): List<String> = try {
        val raw = prefs.getString(AppPrefs.KEY_SEARCHES, null)
        if (raw.isNullOrEmpty()) emptyList()
        else JSONArray(raw).let { a -> (0 until a.length()).map { a.optString(it) }.filter { it.isNotEmpty() } }
    } catch (_: Exception) {
        emptyList()
    }

    /** `addSearch()` — de-duplicates, puts [q] first, keeps the latest 8. */
    fun addSearch(q: String) {
        val t = q.trim()
        if (t.isEmpty()) return
        val next = (listOf(t) + getSearches().filter { it != t }).take(8)
        prefs.edit().putString(AppPrefs.KEY_SEARCHES, JSONArray(next).toString()).apply()
    }

    /** `clearSearches()` */
    fun clearSearches() {
        prefs.edit().remove(AppPrefs.KEY_SEARCHES).apply()
    }

    // ─── Search ───────────────────────────────
    /** `searchNotes(query, mode)` — case-insensitive "contains" on title / content / tags. */
    fun searchNotes(query: String, mode: SearchMode = SearchMode.All): List<Note> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        return getNotes().filter { n ->
            val inTitle = { n.title.lowercase().contains(q) }
            val inContent = { stripHtml(n.content).lowercase().contains(q) }
            val inTags = { n.tags.any { it.lowercase().contains(q) } }
            when (mode) {
                SearchMode.Title -> inTitle()
                SearchMode.Content -> inContent()
                SearchMode.Tags -> inTags()
                SearchMode.All -> inTitle() || inContent() || inTags()
            }
        }
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
