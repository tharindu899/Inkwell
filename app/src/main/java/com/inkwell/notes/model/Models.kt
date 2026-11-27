package com.inkwell.notes.model

/** Mirrors the web app's note object (iw_notes). `content` is HTML, same as the web editor. */
data class Note(
    val id: String,
    val title: String,
    val content: String = "",
    val tags: List<String> = emptyList(),
    val notebookId: String? = null,
    val pinned: Boolean = false,
    val pinOrder: Int? = null,
    val wordCount: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
)

/** Mirrors iw_notebooks. `color` is a hex string like "#c9a96e", `icon` is an icon key. */
data class Notebook(
    val id: String,
    val name: String,
    val color: String,
    val icon: String = "book",
    val createdAt: Long = 0L,
)

/** Same keys as the web sort bar: modified, created, title, tags. */
enum class SortOrder(val label: String) {
    Modified("Modified"), Created("Created"), Title("Title"), Tags("Tags")
}

enum class ThemeMode(val label: String) { Light("Light"), Dark("Dark"), GitHub("GitHub") }

/** Notebook list sort, same keys as the web app: name, count, created. */
enum class NotebookSort(val label: String) { Name("Name"), Notes("Notes"), Created("Created") }
