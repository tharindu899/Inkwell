package com.inkwell.notes.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.random.Random

/** ISO-8601 UTC timestamps, same format as JS `new Date().toISOString()`. */
object Iso {
    private fun fmt(pattern: String) = SimpleDateFormat(pattern, Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
        isLenient = true
    }

    fun now(): String = fmt("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").format(Date())

    /** ISO string → epoch millis, 0 when empty / unparsable (like `new Date(x).getTime()` → NaN → 0 here). */
    fun millis(iso: String?): Long {
        if (iso.isNullOrEmpty()) return 0L
        for (p in listOf("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd'T'HH:mm:ss'Z'")) {
            try {
                return fmt(p).parse(iso)?.time ?: continue
            } catch (_: Exception) {
            }
        }
        return 0L
    }
}

/** `genId()` */
fun genId(): String =
    java.lang.Long.toString(System.currentTimeMillis(), 36) +
        java.lang.Long.toString(Random.nextLong(36L * 36 * 36 * 36 * 36), 36).padStart(5, '0')

private val TAG_RE = Regex("<[^>]*>")
private val WS_RE = Regex("\\s+")

/** `stripHtml()` — tags become spaces so "<h1>A</h1><p>B</p>" reads "A B". */
fun stripHtml(html: String): String =
    html.replace(TAG_RE, " ")
        .replace("&nbsp;", " ")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&#96;", "`")
        .replace("&amp;", "&")
        .replace(WS_RE, " ")
        .trim()

/** `countWords()` */
fun countWords(text: String): Int {
    val t = text.trim()
    return if (t.isEmpty()) 0 else t.split(WS_RE).size
}

/** `formatDate()` — relative time, "Nov 25" after a week. */
fun formatDate(iso: String?): String {
    if (iso.isNullOrEmpty()) return ""
    val t = Iso.millis(iso)
    val diff = System.currentTimeMillis() - t
    return when {
        diff < 60_000L -> "Just now"
        diff < 3_600_000L -> "${diff / 60_000L}m ago"
        diff < 86_400_000L -> "${diff / 3_600_000L}h ago"
        diff < 604_800_000L -> "${diff / 86_400_000L}d ago"
        else -> SimpleDateFormat("MMM d", Locale.ENGLISH).format(Date(t))
    }
}

/** `fmtNum()` — 1234 → "1.2k" */
fun fmtNum(n: Int): String =
    if (n > 999) String.format(Locale.US, "%.1fk", n / 1000.0) else n.toString()

/** `useGreeting` → getGreeting() */
fun greetingForNow(): String {
    val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return when {
        h in 5..11 -> "Good Morning"
        h in 12..16 -> "Good Afternoon"
        h in 17..20 -> "Good Evening"
        else -> "Good Night"
    }
}

/** Card preview text (simplified `getNotePreview` — code-block previews come with the editor step). */
fun notePreview(content: String, markdown: String): String {
    if (markdown.isNotBlank()) {
        val md = markdown.replace("\r\n", "\n")
            .replace(Regex("!\\[([^\\]]*)\\]\\([^)]*\\)"), "$1")
            .replace(Regex("\\[([^\\]]+)\\]\\([^)]*\\)"), "$1")
            .replace(Regex("(?m)^\\s{0,3}#{1,6}\\s+"), "")
            .replace(Regex("(?m)^\\s{0,3}>\\s?"), "")
            .replace(Regex("(?m)^\\s{0,3}[-*+]\\s+\\[[ xX]\\]\\s+"), "")
            .replace(Regex("(?m)^\\s{0,3}[-*+]\\s+"), "")
            .replace(Regex("(?m)^\\s{0,3}\\d+[.)]\\s+"), "")
            .replace(Regex("[*_~`|]"), " ")
            .replace(WS_RE, " ")
            .trim()
        if (md.isNotEmpty()) return md
    }
    return stripHtml(content).ifEmpty { "No content yet." }
}
