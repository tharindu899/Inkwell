package com.inkwell.notes.data

import com.inkwell.notes.model.Note
import com.inkwell.notes.model.Notebook
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.OffsetDateTime

/** Reads and writes the SAME JSON shape as the web app (iw_notes / iw_notebooks), so Drive backups work both ways. */
object InkJson {
    private val NOTE_KEYS = setOf(
        "id", "title", "content", "markdown", "notebookId", "tags", "pinned", "favorite",
        "pinOrder", "createdAt", "updatedAt", "wordCount",
    )
    private val NOTEBOOK_KEYS = setOf("id", "name", "color", "icon", "createdAt")

    fun parseIso(s: String?): Long {
        if (s.isNullOrBlank()) return 0L
        return try {
            Instant.parse(s).toEpochMilli()
        } catch (e: Exception) {
            try { OffsetDateTime.parse(s).toInstant().toEpochMilli() } catch (e2: Exception) { 0L }
        }
    }

    fun toIso(ms: Long): String = Instant.ofEpochMilli(ms).toString()

    private fun extras(o: JSONObject, known: Set<String>): String {
        val out = JSONObject()
        val keys = o.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            if (k !in known) out.put(k, o.get(k))
        }
        return if (out.length() == 0) "" else out.toString()
    }

    fun noteFromJson(o: JSONObject): Note {
        val tagsArr = o.optJSONArray("tags")
        val tags = if (tagsArr == null) emptyList() else List(tagsArr.length()) { tagsArr.optString(it) }.filter { it.isNotBlank() }
        val created = parseIso(o.optString("createdAt"))
        val updated = parseIso(o.optString("updatedAt")).takeIf { it > 0 } ?: created
        val pinOrder = if (o.has("pinOrder") && !o.isNull("pinOrder")) o.optDouble("pinOrder").toInt() else null
        return Note(
            id = o.optString("id"),
            title = o.optString("title"),
            content = o.optString("content"),
            tags = tags,
            notebookId = if (o.isNull("notebookId")) null else o.optString("notebookId").ifBlank { null },
            pinned = o.optBoolean("pinned", false),
            pinOrder = pinOrder,
            wordCount = o.optInt("wordCount", 0),
            createdAt = created,
            updatedAt = updated,
            markdown = o.optString("markdown"),
            favorite = o.optBoolean("favorite", false),
            extraJson = extras(o, NOTE_KEYS),
        )
    }

    fun noteToJson(n: Note, notebook: Notebook?): JSONObject {
        val o = if (n.extraJson.isBlank()) JSONObject() else JSONObject(n.extraJson)
        o.put("id", n.id)
        o.put("title", n.title)
        o.put("content", n.content)
        o.put("markdown", n.markdown)
        o.put("notebookId", n.notebookId ?: JSONObject.NULL)
        if (notebook != null) {
            o.put("notebookName", notebook.name)
            o.put("notebookColor", notebook.color)
        }
        o.put("tags", JSONArray(n.tags))
        o.put("pinned", n.pinned)
        o.put("favorite", n.favorite)
        if (n.pinOrder != null) o.put("pinOrder", n.pinOrder) else o.remove("pinOrder")
        o.put("createdAt", toIso(n.createdAt))
        o.put("updatedAt", toIso(n.updatedAt))
        o.put("wordCount", n.wordCount)
        return o
    }

    fun notebookFromJson(o: JSONObject): Notebook = Notebook(
        id = o.optString("id"),
        name = o.optString("name"),
        color = o.optString("color").ifBlank { "#c9a96e" },
        icon = o.optString("icon").ifBlank { "book" },
        createdAt = parseIso(o.optString("createdAt")),
        extraJson = extras(o, NOTEBOOK_KEYS),
    )

    fun notebookToJson(nb: Notebook): JSONObject {
        val o = if (nb.extraJson.isBlank()) JSONObject() else JSONObject(nb.extraJson)
        o.put("id", nb.id)
        o.put("name", nb.name)
        o.put("color", nb.color)
        o.put("icon", nb.icon)
        o.put("createdAt", toIso(if (nb.createdAt > 0) nb.createdAt else System.currentTimeMillis()))
        return o
    }

    fun notesFromArray(arr: JSONArray): List<Note> =
        List(arr.length()) { noteFromJson(arr.getJSONObject(it)) }.filter { it.id.isNotBlank() }

    fun notebooksFromArray(arr: JSONArray): List<Notebook> =
        List(arr.length()) { notebookFromJson(arr.getJSONObject(it)) }.filter { it.id.isNotBlank() }

    fun notesToArray(notes: List<Note>, notebooks: List<Notebook>): JSONArray {
        val byId = notebooks.associateBy { it.id }
        val arr = JSONArray()
        notes.forEach { arr.put(noteToJson(it, byId[it.notebookId])) }
        return arr
    }

    fun notebooksToArray(notebooks: List<Notebook>): JSONArray {
        val arr = JSONArray()
        notebooks.forEach { arr.put(notebookToJson(it)) }
        return arr
    }
}
