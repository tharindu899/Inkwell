package com.inkwell.notes.data

import com.inkwell.notes.data.model.Note
import com.inkwell.notes.util.Iso
import java.text.Collator
import java.util.Calendar

/** Which timestamp the date range / time-of-day filters and the newest/oldest order look at. */
enum class DateField(val label: String) { Modified("Modified"), Created("Created") }

enum class NoteOrder(val label: String) {
    Newest("Newest first"),
    Oldest("Oldest first"),
    TitleAZ("Title A–Z"),
    TitleZA("Title Z–A"),
}

enum class DateRange(val label: String) {
    Any("Any time"),
    Today("Today"),
    Week("Last 7 days"),
    Month("Last 30 days"),
    Year("This year"),
}

/** Time of day (device local time) a note was modified / created. */
enum class DayPart(val label: String, val hours: String) {
    Morning("Morning", "5am–12pm"),
    Afternoon("Afternoon", "12–5pm"),
    Evening("Evening", "5–9pm"),
    Night("Night", "9pm–5am");

    companion object {
        fun ofHour(h: Int): DayPart = when (h) {
            in 5..11 -> Morning
            in 12..16 -> Afternoon
            in 17..20 -> Evening
            else -> Night
        }
    }
}

/** Everything the All Notes filter sheet can set. */
data class NoteFilter(
    val tags: Set<String> = emptySet(),
    /** true = a note must have every selected tag, false = any one of them. */
    val matchAll: Boolean = false,
    val order: NoteOrder = NoteOrder.Newest,
    val dateField: DateField = DateField.Modified,
    val range: DateRange = DateRange.Any,
    val dayParts: Set<DayPart> = emptySet(),
) {
    /** How many things differ from the defaults (shown as the badge on the filter icon). */
    val activeCount: Int
        get() = (if (tags.isNotEmpty()) 1 else 0) +
            (if (order != NoteOrder.Newest) 1 else 0) +
            (if (dateField != DateField.Modified) 1 else 0) +
            (if (range != DateRange.Any) 1 else 0) +
            (if (dayParts.isNotEmpty()) 1 else 0)

    val isActive: Boolean get() = activeCount > 0
}

/** Filters + sorts [notes]. Pinned notes stay on top, like everywhere else in the app. */
fun applyNoteFilter(notes: List<Note>, f: NoteFilter): List<Note> {
    fun stamp(n: Note): Long = Iso.millis(if (f.dateField == DateField.Created) n.createdAt else n.updatedAt)

    val now = System.currentTimeMillis()
    val since: Long? = when (f.range) {
        DateRange.Any -> null
        DateRange.Today -> Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        DateRange.Week -> now - 7L * 86_400_000L
        DateRange.Month -> now - 30L * 86_400_000L
        DateRange.Year -> Calendar.getInstance().apply {
            set(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    val matched = notes.filter { n ->
        val tagOk = when {
            f.tags.isEmpty() -> true
            f.matchAll -> n.tags.containsAll(f.tags)
            else -> n.tags.any { it in f.tags }
        }
        if (!tagOk) return@filter false
        val t = stamp(n)
        if (since != null && (t == 0L || t < since)) return@filter false
        if (f.dayParts.isNotEmpty()) {
            if (t == 0L) return@filter false
            val hour = Calendar.getInstance().apply { timeInMillis = t }.get(Calendar.HOUR_OF_DAY)
            if (DayPart.ofHour(hour) !in f.dayParts) return@filter false
        }
        true
    }

    val collator = Collator.getInstance()
    val sorted = when (f.order) {
        NoteOrder.Newest -> matched.sortedByDescending { stamp(it) }
        NoteOrder.Oldest -> matched.sortedBy { stamp(it) }
        NoteOrder.TitleAZ -> matched.sortedWith(Comparator { a, b -> collator.compare(a.title, b.title) })
        NoteOrder.TitleZA -> matched.sortedWith(Comparator { a, b -> collator.compare(b.title, a.title) })
    }
    // Pinned first (stable, so the chosen order is kept inside each group).
    return sorted.filter { it.pinned } + sorted.filter { !it.pinned }
}
