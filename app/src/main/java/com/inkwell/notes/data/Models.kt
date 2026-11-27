package com.inkwell.notes.data

import org.json.JSONArray
import org.json.JSONObject

private fun JSONObject.str(name: String, def: String = ""): String =
    if (isNull(name)) def else optString(name, def)

/**
 * Same JSON shape as the old Capacitor app (iw_notes / Drive backup),
 * so backup & restore stay compatible. Unknown fields are kept in [raw].
 */
data class Note(
    val id: String,
    val title: String = "",
    val content: String = "",
    val markdown: String = "",
    val notebookId: String? = null,
    val tags: List<String> = emptyList(),
    val pinned: Boolean = false,
    val pinOrder: Int? = null,
    val favorite: Boolean = false,
    val createdAt: String = "",
    val updatedAt: String = "",
    val wordCount: Int = 0,
    val raw: JSONObject = JSONObject(),
) {
    fun toJson(): JSONObject {
        val o = JSONObject(raw.toString())
        o.put("id", id)
        o.put("title", title)
        o.put("content", content)
        o.put("markdown", markdown)
        o.put("notebookId", notebookId ?: JSONObject.NULL)
        o.put("tags", JSONArray(tags))
        o.put("pinned", pinned)
        if (pinOrder != null) o.put("pinOrder", pinOrder) else o.remove("pinOrder")
        o.put("favorite", favorite)
        o.put("createdAt", createdAt)
        o.put("updatedAt", updatedAt)
        o.put("wordCount", wordCount)
        return o
    }

    companion object {
        fun fromJson(o: JSONObject): Note? {
            val id = o.str("id")
            if (id.isEmpty()) return null
            val tags = ArrayList<String>()
            o.optJSONArray("tags")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val t = arr.optString(i, "")
                    if (t.isNotEmpty()) tags.add(t)
                }
            }
            return Note(
                id = id,
                title = o.str("title"),
                content = o.str("content"),
                markdown = o.str("markdown"),
                notebookId = if (o.isNull("notebookId")) null else o.optString("notebookId").ifEmpty { null },
                tags = tags,
                pinned = o.optBoolean("pinned", false),
                pinOrder = if (o.isNull("pinOrder")) null else o.optInt("pinOrder"),
                favorite = o.optBoolean("favorite", false),
                createdAt = o.str("createdAt"),
                updatedAt = o.str("updatedAt").ifEmpty { o.str("createdAt") },
                wordCount = o.optInt("wordCount", 0),
                raw = o,
            )
        }
    }
}

data class Notebook(
    val id: String,
    val name: String = "",
    val color: String = "#E07B39",
    val createdAt: String = "",
    val raw: JSONObject = JSONObject(),
) {
    fun toJson(): JSONObject {
        val o = JSONObject(raw.toString())
        o.put("id", id)
        o.put("name", name)
        o.put("color", color)
        o.put("createdAt", createdAt)
        return o
    }

    companion object {
        fun fromJson(o: JSONObject): Notebook? {
            val id = o.str("id")
            if (id.isEmpty()) return null
            return Notebook(
                id = id,
                name = o.str("name"),
                color = o.str("color", "#E07B39"),
                createdAt = o.str("createdAt"),
                raw = o,
            )
        }
    }
}

enum class SortOrder(val key: String, val label: String) {
    Modified("modified", "Modified"),
    Created("created", "Created"),
    Title("title", "Title"),
    Tags("tags", "Tags");

    companion object {
        fun fromKey(key: String?): SortOrder = entries.firstOrNull { it.key == key } ?: Modified
    }
}
