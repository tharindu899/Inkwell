package com.inkwell.notes.util

import androidx.core.text.HtmlCompat
import com.inkwell.notes.data.Note
import com.inkwell.notes.data.SortOrder
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Calendar
import java.util.Date
import java.util.Locale

fun nowIso(): String = Instant.now().truncatedTo(ChronoUnit.MILLIS).toString()

fun parseTime(iso: String): Long =
    try { Instant.parse(iso).toEpochMilli() } catch (_: Exception) { 0L }

fun greetingWord(): String {
    val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when {
        h in 5..11 -> "Morning"
        h in 12..16 -> "Afternoon"
        h in 17..20 -> "Evening"
        else -> "Night"
    }
}

fun formatDate(iso: String): String {
    val t = parseTime(iso)
    if (t == 0L) return ""
    val diff = System.currentTimeMillis() - t
    return when {
        diff < 60_000 -> "Just now"
        diff < 3_600_000 -> "${diff / 60_000}m ago"
        diff < 86_400_000 -> "${diff / 3_600_000}h ago"
        diff < 604_800_000 -> "${diff / 86_400_000}d ago"
        else -> SimpleDateFormat("MMM d", Locale.ENGLISH).format(Date(t))
    }
}

fun fmtNum(n: Int): String = if (n > 999) String.format(Locale.US, "%.1fk", n / 1000f) else n.toString()

fun countWords(text: String): Int {
    val t = text.trim()
    return if (t.isEmpty()) 0 else t.split(Regex("\\s+")).size
}

fun stripHtml(html: String): String =
    HtmlCompat.fromHtml(html.take(6000), HtmlCompat.FROM_HTML_MODE_LEGACY)
        .toString()
        .replace('\uFFFC', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()

private fun stripMarkdown(md: String): String =
    md.take(6000)
        .replace(Regex("```[^\\n]*\\n([\\s\\S]*?)```"), "$1")
        .replace(Regex("!\\[([^\\]]*)\\]\\([^)]*\\)"), "$1")
        .replace(Regex("\\[([^\\]]+)\\]\\([^)]*\\)"), "$1")
        .replace(Regex("(?m)^\\s{0,3}#{1,6}\\s+"), "")
        .replace(Regex("(?m)^\\s{0,3}>\\s?"), "")
        .replace(Regex("(?m)^\\s{0,3}[-*+]\\s+(\\[[ xX]\\]\\s+)?"), "")
        .replace(Regex("[*_~`|]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

/** Short text shown inside a note card. */
fun notePreview(note: Note): String {
    val body = if (note.markdown.isNotBlank()) stripMarkdown(note.markdown) else stripHtml(note.content)
    val noTitle = if (note.title.isNotBlank()) body.removePrefix(note.title).trim() else body
    return noTitle.ifBlank { "No content yet." }
}

/** Same ordering rules as the old app: pinned first (by pinOrder), then the chosen sort. */
fun sortNotes(list: List<Note>, order: SortOrder): List<Note> {
    val base: Comparator<Note> = when (order) {
        SortOrder.Modified -> compareByDescending<Note> { parseTime(it.updatedAt) }
        SortOrder.Created -> compareByDescending<Note> { parseTime(it.createdAt) }
        SortOrder.Title -> Comparator { a, b -> a.title.compareTo(b.title, ignoreCase = true) }
        SortOrder.Tags -> Comparator<Note> { a, b ->
            a.tags.firstOrNull().orEmpty().lowercase().compareTo(b.tags.firstOrNull().orEmpty().lowercase())
        }.thenByDescending { parseTime(it.updatedAt) }
    }
    val pinnedFirst = Comparator<Note> { a, b ->
        if (a.pinned != b.pinned) {
            if (a.pinned) -1 else 1
        } else if (a.pinned) {
            (a.pinOrder ?: 999_999).compareTo(b.pinOrder ?: 999_999)
        } else 0
    }
    return list.sortedWith(pinnedFirst.then(base))
}
