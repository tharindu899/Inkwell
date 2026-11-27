package com.inkwell.notes.data.model

import org.json.JSONArray
import org.json.JSONObject

/** Same shape as the objects the web app keeps in localStorage `iw_notes`. */
data class Note(
    val id: String,
    val title: String = "",
    val content: String = "",
    val markdown: String = "",
    val notebookId: String? = null,
    val notebookName: String = "",
    val notebookColor: String = "",
    val tags: List<String> = emptyList(),
    val pinned: Boolean = false,
    val favorite: Boolean = false,
    val pinOrder: Int? = null,
    val createdAt: String = "",
    val updatedAt: String = "",
    val wordCount: Int = 0,
    /** Alarm time (epoch millis) set from the editor's bell; null = no reminder. */
    val reminderAt: Long? = null,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("content", content)
        put("markdown", markdown)
        put("notebookId", notebookId ?: JSONObject.NULL)
        put("notebookName", notebookName)
        put("notebookColor", notebookColor)
        put("tags", JSONArray(tags))
        put("pinned", pinned)
        put("favorite", favorite)
        if (pinOrder != null) put("pinOrder", pinOrder)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
        put("wordCount", wordCount)
        if (reminderAt != null) put("reminderAt", reminderAt)
    }

    companion object {
        fun fromJson(o: JSONObject): Note? {
            val id = o.optString("id", "")
            if (id.isEmpty()) return null
            val tagsArr = o.optJSONArray("tags")
            val tags = buildList {
                if (tagsArr != null) for (i in 0 until tagsArr.length()) add(tagsArr.optString(i))
            }
            return Note(
                id = id,
                title = o.optString("title", ""),
                content = o.optString("content", ""),
                markdown = o.optString("markdown", ""),
                notebookId = if (o.isNull("notebookId")) null else o.optString("notebookId").ifEmpty { null },
                notebookName = o.optString("notebookName", ""),
                notebookColor = o.optString("notebookColor", ""),
                tags = tags,
                pinned = o.optBoolean("pinned", false),
                favorite = o.optBoolean("favorite", false),
                pinOrder = if (o.has("pinOrder") && !o.isNull("pinOrder")) o.optDouble("pinOrder").toInt() else null,
                createdAt = o.optString("createdAt", ""),
                updatedAt = o.optString("updatedAt", ""),
                wordCount = o.optInt("wordCount", 0),
                reminderAt = if (o.has("reminderAt") && !o.isNull("reminderAt")) o.optLong("reminderAt") else null,
            )
        }
    }
}

/** Same shape as `iw_notebooks` entries. */
data class Notebook(
    val id: String,
    val name: String = "",
    val color: String = "#E07B39",
    val icon: String = "fa-book-open",
    val createdAt: String = "",
    val updatedAt: String = "",
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("color", color)
        put("icon", icon)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
    }

    companion object {
        fun fromJson(o: JSONObject): Notebook? {
            val id = o.optString("id", "")
            if (id.isEmpty()) return null
            return Notebook(
                id = id,
                name = o.optString("name", ""),
                color = o.optString("color", "#E07B39"),
                icon = o.optString("icon", "fa-book-open"),
                createdAt = o.optString("createdAt", ""),
                updatedAt = o.optString("updatedAt", ""),
            )
        }
    }
}

data class Stats(val notes: Int, val notebooks: Int, val words: Int)

/** `iw_sort` values — Home's "Sort by" chips. */
enum class SortOrder(val key: String, val label: String) {
    Modified("modified", "Modified"),
    Created("created", "Created"),
    Title("title", "Title"),
    Tags("tags", "Tags");

    companion object {
        fun fromKey(key: String?): SortOrder = entries.firstOrNull { it.key == key } ?: Modified
    }
}

/** Search page filter chips (`MODES` in `pages/Search.jsx`). */
enum class SearchMode(val label: String) {
    All("All"),
    Title("Title"),
    Content("Content"),
    Tags("Tags"),
}
