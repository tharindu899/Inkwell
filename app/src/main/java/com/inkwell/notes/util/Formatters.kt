package com.inkwell.notes.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

fun String.stripHtml(): String =
    replace(Regex("<[^>]*>"), " ").replace("&nbsp;", " ").replace(Regex("\\s+"), " ").trim()

fun formatRelative(ts: Long, now: Long = System.currentTimeMillis()): String {
    val d = now - ts
    return when {
        d < 60_000 -> "Just now"
        d < 3_600_000 -> "${d / 60_000}m ago"
        d < 86_400_000 -> "${d / 3_600_000}h ago"
        d < 604_800_000 -> "${d / 86_400_000}d ago"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(ts))
    }
}

fun fmtNum(n: Int): String = if (n > 999) String.format(Locale.US, "%.1fk", n / 1000f) else n.toString()

/** Same buckets as the web useGreeting hook. */
fun greetingText(hour: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)): String = when (hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    in 17..20 -> "Good evening"
    else -> "Good night"
}
