package com.inkwell.notes.editor

/**
 * Toolbar actions for Markdown text mode: plain string edits on (text, selection).
 * Pure so they can be tested without Android.
 */
object SourceEdit {
    class Result(val text: String, val start: Int, val end: Int)

    private val LINE_PREFIX = Regex("^(\\s*)(#{1,6} |[-*+] \\[[ xX]\\] |[-*+] |\\d+\\. |> )?")

    /** Wraps the selection in [open]/[close]; if it is already wrapped (inside or around), unwraps instead. */
    fun wrap(text: String, s0: Int, e0: Int, open: String, close: String = open): Result {
        val s = minOf(s0, e0).coerceIn(0, text.length)
        val e = maxOf(s0, e0).coerceIn(0, text.length)
        // markers just outside the selection
        if (s >= open.length && text.startsWith(open, s - open.length) && text.startsWith(close, e) && e + close.length <= text.length) {
            val t = text.substring(0, s - open.length) + text.substring(s, e) + text.substring(e + close.length)
            return Result(t, s - open.length, e - open.length)
        }
        // markers inside the selection
        val sel = text.substring(s, e)
        if (sel.length >= open.length + close.length && sel.startsWith(open) && sel.endsWith(close)) {
            val inner = sel.substring(open.length, sel.length - close.length)
            return Result(text.substring(0, s) + inner + text.substring(e), s, s + inner.length)
        }
        if (s == e) {
            // Nothing selected: type the opening marker; pressing again (inside it) types the closing one.
            val mark = if (isInside(text, s, open, close)) close else open
            val t = text.substring(0, s) + mark + text.substring(e)
            return Result(t, s + mark.length, s + mark.length)
        }
        val t = text.substring(0, s) + open + sel + close + text.substring(e)
        return Result(t, s + open.length, e + open.length)
    }

    /** True when the caret sits after an unclosed [open] marker on its line. */
    fun isInside(text: String, pos: Int, open: String, close: String): Boolean {
        val p = pos.coerceIn(0, text.length)
        val line = text.substring(text.lastIndexOf('\n', p - 1).let { if (p == 0) 0 else it + 1 }, p)
        return when {
            open != close -> line.windowed(open.length).count { it == open } > line.windowed(close.length).count { it == close }
            open == "*" -> line.replace("**", "").count { it == '*' } % 2 == 1
            else -> Regex(Regex.escape(open)).findAll(line).count() % 2 == 1
        }
    }

    /** Line range `[from, to)` covering the selection (to excludes the trailing newline). */
    private fun lines(text: String, s: Int, e: Int): Pair<Int, Int> {
        val a = text.lastIndexOf('\n', (minOf(s, e) - 1).coerceAtLeast(-1)).let { if (minOf(s, e) == 0) 0 else it + 1 }
        val hi = maxOf(s, e).coerceIn(0, text.length)
        val from = if (a < 0) 0 else a
        var to = text.indexOf('\n', if (hi > from && text.getOrNull(hi - 1) == '\n') hi - 1 else hi)
        if (to < 0) to = text.length
        return from to to
    }

    /**
     * Sets the block prefix of every selected line ("# ", "- ", "> " …). [prefix] "" = plain paragraph.
     * If every non-empty line already has exactly this prefix it is removed (toggle). [numbered] counts 1., 2., …
     */
    fun linePrefix(text: String, s: Int, e: Int, prefix: String, numbered: Boolean = false): Result {
        val (from, to) = lines(text, s, e)
        val lns = text.substring(from, to).split("\n")
        fun pfx(l: String) = LINE_PREFIX.find(l)!!.groupValues[2]
        val allHave = prefix.isNotEmpty() && lns.filter { it.isNotBlank() }.let { ne ->
            ne.isNotEmpty() && ne.all { l -> pfx(l).let { p -> if (numbered) p.matches(Regex("\\d+\\. ")) else p == prefix } }
        }
        var n = 0
        val out = lns.map { l ->
            val m = LINE_PREFIX.find(l)!!
            val lead = m.groupValues[1]
            val rest = l.substring(m.value.length)
            when {
                allHave -> lead + rest
                prefix.isEmpty() -> lead + rest
                l.isBlank() && lns.size > 1 -> l
                numbered -> { n++; "$lead$n. $rest" }
                else -> lead + prefix + rest
            }
        }
        val joined = out.joinToString("\n")
        val t = text.substring(0, from) + joined + text.substring(to)
        return keepCaret(t, from, to, joined.length, s, e)
    }

    /** Indent (+1) / outdent (−1) by two spaces per selected line. */
    fun indent(text: String, s: Int, e: Int, delta: Int): Result {
        val (from, to) = lines(text, s, e)
        val out = text.substring(from, to).split("\n").joinToString("\n") { l ->
            if (delta > 0) (if (l.isBlank()) l else "  $l")
            else if (l.startsWith("  ")) l.substring(2) else l.trimStart(' ').let { if (l.startsWith(" ")) l.substring(1) else l }
        }
        val t = text.substring(0, from) + out + text.substring(to)
        return keepCaret(t, from, to, out.length, s, e)
    }

    /** A caret stays a caret (moved by the length change); a multi-line selection keeps covering its lines. */
    private fun keepCaret(t: String, from: Int, oldTo: Int, newLen: Int, s: Int, e: Int): Result {
        if (s != e) return Result(t, from, from + newLen)
        val c = (s + (newLen - (oldTo - from))).coerceIn(from, from + newLen)
        return Result(t, c, c)
    }

    /** Replaces the selection with [ins]; the caret lands at [caretFromStart] (default: after it). */
    fun insert(text: String, s0: Int, e0: Int, ins: String, selStartInIns: Int = ins.length, selEndInIns: Int = selStartInIns): Result {
        val s = minOf(s0, e0).coerceIn(0, text.length)
        val e = maxOf(s0, e0).coerceIn(0, text.length)
        return Result(text.substring(0, s) + ins + text.substring(e), s + selStartInIns, s + selEndInIns)
    }

    /** Puts [block] on its own lines (blank-line separated) after the selection's line. */
    fun insertBlock(text: String, s: Int, e: Int, block: String): Result {
        val at = lines(text, s, e).second
        val before = text.substring(0, at)
        val after = text.substring(at)
        val lead = if (before.isEmpty() || before.endsWith("\n\n")) "" else if (before.endsWith("\n")) "\n" else "\n\n"
        val tail = if (after.startsWith("\n\n") || after.isEmpty()) (if (after.isEmpty()) "\n" else "") else if (after.startsWith("\n")) "\n" else "\n\n"
        val t = before + lead + block + tail + after
        val caret = before.length + lead.length + block.length
        return Result(t, caret, caret)
    }

    /** Fenced code block around the selected lines (again = unfence). */
    fun codeFence(text: String, s: Int, e: Int): Result {
        val (from, to) = lines(text, s, e)
        val seg = text.substring(from, to)
        if (seg.startsWith("```") && seg.trimEnd().endsWith("```") && seg.trim().length >= 6) {
            val inner = seg.trim().removePrefix("```").substringAfter("\n", "").removeSuffix("```").trimEnd('\n')
            return Result(text.substring(0, from) + inner + text.substring(to), from, from + inner.length)
        }
        val block = "```\n$seg\n```"
        return Result(text.substring(0, from) + block + text.substring(to), from + 4, from + 4 + seg.length)
    }

    /** Drops inline markers from the selection (`**`, `*`, `~~`, backticks, `<u>` / `<sup>` / `<sub>` / `<mark>`). */
    fun clearInline(text: String, s0: Int, e0: Int): Result {
        val s = minOf(s0, e0).coerceIn(0, text.length)
        val e = maxOf(s0, e0).coerceIn(0, text.length)
        if (s == e) return Result(text, s, e)
        val c = text.substring(s, e)
            .replace(Regex("</?(u|sup|sub|mark)>"), "")
            .replace("**", "").replace("~~", "").replace("`", "")
            .replace(Regex("(?<![*\\w])\\*(?!\\s)([^*\\n]+?)\\*(?![*\\w])"), "$1")
        return Result(text.substring(0, s) + c + text.substring(e), s, s + c.length)
    }

    const val TABLE_TEMPLATE = "| Column 1 | Column 2 |\n| --- | --- |\n|  |  |"
}
