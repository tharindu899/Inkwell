package com.inkwell.notes.editor

/**
 * Markdown text → [RichDoc] (Part 5, paste handling). The reverse direction is [HtmlCodec.toMarkdown].
 *
 * Block level: ATX headings (`#`–`######`, 4–6 shown as H3), bullet / numbered / `[ ]` checklist items (nested by
 * indentation), `>` quotes, fenced ``` / ~~~ code, `---` dividers and GitHub pipe tables (cells become plain text).
 * Inline: `code`, **bold**, *italic*, ~~strike~~, [links](url), bare URLs, `\` escapes and the handful of HTML tags
 * READMEs use (`u sup sub mark b i em strong code kbd a br img`). Unknown tags are dropped, their text is kept.
 *
 * Every source line becomes its own paragraph (blank lines only separate blocks), which is how the editor's
 * own paragraphs behave. Images can't be shown until Part 6, so `![alt](src)` becomes a link with the alt text.
 */
object MarkdownCodec {
    private val FENCE = Regex("^\\s{0,3}(`{3,}|~{3,})\\s*([^`]*)$")
    private val HR = Regex("^\\s{0,3}([-*_])(?:\\s*\\1){2,}\\s*$")
    private val HEADING = Regex("^\\s{0,3}(#{1,6})\\s+(.*?)(?:\\s+#+)?\\s*$")
    private val LIST = Regex("^(\\s*)([-*+]|\\d{1,9}[.)])\\s+(.*)$")
    private val QUOTE = Regex("^\\s{0,3}>\\s?(.*)$")
    private val TASK = Regex("^\\[([ xX])]\\s+(.*)$")
    private val SEP_CELL = Regex("^:?-{3,}:?$")
    private val TAG = Regex("^<(/?)([a-zA-Z][a-zA-Z0-9]*)\\b([^>]*)>")
    private val ATTR_HREF = Regex("href\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')", RegexOption.IGNORE_CASE)
    private val ATTR_ALT = Regex("alt\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')", RegexOption.IGNORE_CASE)

    fun parse(markdown: String): RichDoc {
        val src = preprocess(markdown)
        val lines = src.split('\n')
        val urls = ArrayList<String>()
        val out = ArrayList<Line>()
        val listStack = ArrayList<Int>() // indentation widths of the open list levels

        var i = 0
        val n = lines.size
        while (i < n) {
            val raw = lines[i]

            if (raw.isBlank()) { listStack.clear(); i++; continue }

            // fenced code
            val fence = FENCE.matchEntire(raw)
            if (fence != null) {
                listStack.clear()
                val mark = fence.groupValues[1]
                i++
                val code = ArrayList<String>()
                while (i < n) {
                    val t = lines[i].trim()
                    if (t.startsWith(mark[0].toString().repeat(mark.length)) && t.all { it == mark[0] }) { i++; break }
                    code.add(lines[i])
                    i++
                }
                if (code.isEmpty()) code.add("")
                for (c in code) out.add(Line(c, IntArray(c.length), Para(type = BlockType.CODE)))
                continue
            }

            if (HR.matches(raw)) {
                listStack.clear()
                out.add(Line("", IntArray(0), Para(type = BlockType.HR)))
                i++
                continue
            }

            val heading = HEADING.matchEntire(raw)
            if (heading != null) {
                listStack.clear()
                val level = minOf(heading.groupValues[1].length, 3)
                val type = when (level) { 1 -> BlockType.H1; 2 -> BlockType.H2; else -> BlockType.H3 }
                out.add(inlineLine(heading.groupValues[2], Para(type = type), urls))
                i++
                continue
            }

            // pipe table
            if (raw.contains('|') && i + 1 < n && isSeparator(lines[i + 1])) {
                listStack.clear()
                val header = splitRow(raw)
                val rows = ArrayList<List<String>>()
                rows.add(header.map { flatten(it) })
                i += 2
                while (i < n && lines[i].isNotBlank() && lines[i].contains('|')) {
                    rows.add(splitRow(lines[i]).map { flatten(it) })
                    i++
                }
                val data = TableData(rows = rows, header = true).normalized()
                out.add(Line("", IntArray(0), Para(type = BlockType.TABLE, table = data)))
                continue
            }

            val quote = QUOTE.matchEntire(raw)
            if (quote != null) {
                listStack.clear()
                out.add(inlineLine(quote.groupValues[1], Para(type = BlockType.QUOTE), urls))
                i++
                continue
            }

            val item = LIST.matchEntire(raw)
            if (item != null) {
                val width = indentWidth(item.groupValues[1])
                while (listStack.isNotEmpty() && listStack.last() > width) listStack.removeAt(listStack.lastIndex)
                if (listStack.isEmpty() || listStack.last() < width) listStack.add(width)
                val level = (listStack.size - 1).coerceIn(0, RichDoc.MAX_INDENT)
                var body = item.groupValues[3]
                val ordered = item.groupValues[2][0].isDigit()
                var type = if (ordered) BlockType.OL else BlockType.UL
                var checked = false
                if (!ordered) {
                    val task = TASK.matchEntire(body)
                    if (task != null) {
                        type = BlockType.TASK
                        checked = task.groupValues[1] != " "
                        body = task.groupValues[2]
                    }
                }
                i++
                // indented follow-on lines belong to the same item
                val sbBody = StringBuilder(body)
                while (i < n && lines[i].isNotBlank() && indentWidth(lines[i].takeWhile { it == ' ' || it == '\t' }) > width &&
                    LIST.matchEntire(lines[i]) == null && FENCE.matchEntire(lines[i]) == null
                ) {
                    sbBody.append(' ').append(lines[i].trim())
                    i++
                }
                out.add(inlineLine(sbBody.toString(), Para(type = type, indent = level, checked = checked), urls))
                continue
            }

            listStack.clear()
            out.add(inlineLine(raw.trim(), Para(), urls))
            i++
        }

        if (out.isEmpty()) return RichDoc.EMPTY
        return RichDoc.fromLines(out, urls)
    }

    // ─── helpers ─────────────────────────────

    /** README-style raw HTML wrappers → plain line breaks (same idea as the web `preprocessPastedMarkdown`). */
    private fun preprocess(md: String): String {
        var t = md.replace("\r\n", "\n").replace('\r', '\n')
        t = t.replace(Regex("</?(div|center|p|details)\\b[^>]*>", RegexOption.IGNORE_CASE), "\n")
        t = t.replace(Regex("<summary\\b[^>]*>([\\s\\S]*?)</summary>", RegexOption.IGNORE_CASE), "\n**$1**\n")
        t = t.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        return t
    }

    private fun indentWidth(ws: String): Int {
        var w = 0
        for (ch in ws) w += if (ch == '\t') 4 else 1
        return w
    }

    private fun isSeparator(line: String): Boolean {
        if (!line.contains('-')) return false
        val cells = splitRow(line)
        return cells.isNotEmpty() && cells.all { SEP_CELL.matches(it) }
    }

    /** Splits a pipe-table row into trimmed cells (`\|` stays a literal pipe). */
    private fun splitRow(row: String): List<String> {
        var t = row.trim()
        if (t.startsWith("|")) t = t.substring(1)
        if (t.endsWith("|") && !t.endsWith("\\|")) t = t.dropLast(1)
        val cells = ArrayList<String>()
        val cur = StringBuilder()
        var k = 0
        while (k < t.length) {
            val ch = t[k]
            if (ch == '\\' && k + 1 < t.length && t[k + 1] == '|') { cur.append('|'); k += 2; continue }
            if (ch == '|') { cells.add(cur.toString().trim()); cur.setLength(0) } else cur.append(ch)
            k++
        }
        cells.add(cur.toString().trim())
        return cells
    }

    /** Inline markdown → plain text (table cells hold no formatting). */
    private fun flatten(s: String): String {
        val o = InlineOut(ArrayList())
        inline(s, o, 0, 0)
        return o.text()
    }

    private fun inlineLine(text: String, para: Para, urls: MutableList<String>): Line {
        val o = InlineOut(urls)
        inline(text, o, 0, 0)
        return Line(o.text(), o.marks(), para)
    }

    // ─── inline ──────────────────────────────

    private class InlineOut(val urls: MutableList<String>) {
        private val sb = StringBuilder()
        private val marks = ArrayList<Int>()

        fun add(ch: Char, flags: Int, link: Int) {
            sb.append(ch)
            marks.add(Mark.withLink(flags, link))
        }

        fun add(s: String, flags: Int, link: Int) { for (ch in s) add(ch, flags, link) }

        fun linkId(url: String): Int {
            var idx = urls.indexOf(url)
            if (idx < 0) { urls.add(url); idx = urls.lastIndex }
            return idx + 1
        }

        fun text(): String = sb.toString()
        fun marks(): IntArray = marks.toIntArray()
    }

    private const val ESCAPABLE = "\\`*_{}[]()#+-.!|~<>"

    private fun isWordChar(c: Char) = c.isLetterOrDigit()

    private fun inline(t: String, out: InlineOut, flags: Int, link: Int) {
        var i = 0
        val n = t.length
        while (i < n) {
            val c = t[i]

            if (c == '\\' && i + 1 < n && t[i + 1] in ESCAPABLE) {
                out.add(t[i + 1], flags, link)
                i += 2
                continue
            }

            if (c == '`') {
                var run = 1
                while (i + run < n && t[i + run] == '`') run++
                val delim = "`".repeat(run)
                var j = t.indexOf(delim, i + run)
                while (j != -1 && j + run < n && t[j + run] == '`') j = t.indexOf(delim, j + 1)
                if (j != -1) {
                    var code = t.substring(i + run, j)
                    if (code.length > 1 && code.startsWith(" ") && code.endsWith(" ")) code = code.substring(1, code.length - 1)
                    out.add(code, flags or Mark.CODE, link)
                    i = j + run
                    continue
                }
                out.add(delim, flags, link)
                i += run
                continue
            }

            // [text](url) and ![alt](src)
            if (c == '[' || (c == '!' && i + 1 < n && t[i + 1] == '[')) {
                val image = c == '!'
                val open = if (image) i + 1 else i
                val close = matchBracket(t, open)
                if (close != -1 && close + 1 < n && t[close + 1] == '(') {
                    val end = matchParen(t, close + 1)
                    if (end != -1) {
                        val label = t.substring(open + 1, close)
                        val url = t.substring(close + 2, end).trim().substringBefore(' ').trim('<', '>')
                        val text = if (image && label.isBlank()) "image" else label
                        val id = if (link != 0 || url.isEmpty()) link else out.linkId(url)
                        inline(text, out, flags, id)
                        i = end + 1
                        continue
                    }
                }
            }

            if (c == '<') {
                val m = TAG.find(t.substring(i))
                if (m != null) {
                    val closing = m.groupValues[1] == "/"
                    val name = m.groupValues[2].lowercase()
                    val attrs = m.groupValues[3]
                    val after = i + m.value.length
                    if (!closing) {
                        val flag = when (name) {
                            "b", "strong" -> Mark.BOLD
                            "i", "em" -> Mark.ITALIC
                            "u", "ins" -> Mark.UNDERLINE
                            "s", "strike", "del" -> Mark.STRIKE
                            "sup" -> Mark.SUP
                            "sub" -> Mark.SUB
                            "mark" -> Mark.HIGHLIGHT
                            "code", "kbd" -> Mark.CODE
                            else -> 0
                        }
                        if (flag != 0) {
                            val end = t.indexOf("</$name>", after, ignoreCase = true)
                            if (end != -1) {
                                inline(t.substring(after, end), out, flags or flag, link)
                                i = end + name.length + 3
                                continue
                            }
                        }
                        if (name == "a") {
                            val href = ATTR_HREF.find(attrs)?.let { it.groupValues[1].ifEmpty { it.groupValues[2] } }.orEmpty()
                            val end = t.indexOf("</a>", after, ignoreCase = true)
                            if (end != -1) {
                                val id = if (link != 0 || href.isEmpty()) link else out.linkId(href)
                                inline(t.substring(after, end), out, flags, id)
                                i = end + 4
                                continue
                            }
                        }
                        if (name == "img") {
                            val alt = ATTR_ALT.find(attrs)?.let { it.groupValues[1].ifEmpty { it.groupValues[2] } }.orEmpty()
                            if (alt.isNotBlank()) out.add(alt, flags, link)
                            i = after
                            continue
                        }
                    }
                    i = after // unknown / closing tag: drop it, keep the text around it
                    continue
                }
                // <https://example.com>
                val gt = t.indexOf('>', i + 1)
                if (gt != -1) {
                    val inner = t.substring(i + 1, gt)
                    if (inner.startsWith("http://") || inner.startsWith("https://") || inner.startsWith("mailto:")) {
                        out.add(inner, flags, if (link != 0) link else out.linkId(inner))
                        i = gt + 1
                        continue
                    }
                }
            }

            // ***bold italic***
            if (t.startsWith("***", i)) {
                val j = findClose(t, "***", i + 3)
                if (j != -1) {
                    inline(t.substring(i + 3, j), out, flags or Mark.BOLD or Mark.ITALIC, link)
                    i = j + 3
                    continue
                }
            }
            // **bold** / __bold__
            if (t.startsWith("**", i) || t.startsWith("__", i)) {
                val d = t.substring(i, i + 2)
                val intraword = d == "__" && i > 0 && isWordChar(t[i - 1])
                if (!intraword) {
                    val j = findClose(t, d, i + 2)
                    if (j != -1) {
                        inline(t.substring(i + 2, j), out, flags or Mark.BOLD, link)
                        i = j + 2
                        continue
                    }
                }
            }
            // ~~strike~~
            if (t.startsWith("~~", i)) {
                val j = findClose(t, "~~", i + 2)
                if (j != -1) {
                    inline(t.substring(i + 2, j), out, flags or Mark.STRIKE, link)
                    i = j + 2
                    continue
                }
            }
            // *italic* / _italic_
            if ((c == '*' || c == '_') && i + 1 < n && !t[i + 1].isWhitespace() && t[i + 1] != c) {
                val intraword = c == '_' && i > 0 && isWordChar(t[i - 1])
                if (!intraword) {
                    val j = findSingleClose(t, c, i + 1)
                    if (j != -1) {
                        inline(t.substring(i + 1, j), out, flags or Mark.ITALIC, link)
                        i = j + 1
                        continue
                    }
                }
            }

            // bare URL
            if (c == 'h' && link == 0 && (i == 0 || !isWordChar(t[i - 1])) &&
                (t.startsWith("http://", i) || t.startsWith("https://", i))
            ) {
                var j = i
                while (j < n && !t[j].isWhitespace() && t[j] != '<') j++
                var url = t.substring(i, j)
                while (url.isNotEmpty() && url.last() in ".,;:!?)'\"") url = url.dropLast(1)
                if (url.length > 8) {
                    out.add(url, flags, out.linkId(url))
                    i += url.length
                    continue
                }
            }

            out.add(c, flags, link)
            i++
        }
    }

    private fun matchBracket(t: String, open: Int): Int {
        var depth = 0
        var k = open
        while (k < t.length) {
            when (t[k]) {
                '\\' -> k++
                '[' -> depth++
                ']' -> { depth--; if (depth == 0) return k }
            }
            k++
        }
        return -1
    }

    private fun matchParen(t: String, open: Int): Int {
        var depth = 0
        var k = open
        while (k < t.length) {
            when (t[k]) {
                '\\' -> k++
                '(' -> depth++
                ')' -> { depth--; if (depth == 0) return k }
            }
            k++
        }
        return -1
    }

    /** Closing double delimiter after [from]: not preceded by whitespace, with something inside. */
    private fun findClose(t: String, d: String, from: Int): Int {
        var j = t.indexOf(d, from)
        while (j != -1) {
            if (j > from && !t[j - 1].isWhitespace()) return j
            j = t.indexOf(d, j + 1)
        }
        return -1
    }

    private fun findSingleClose(t: String, d: Char, from: Int): Int {
        var j = from
        while (j < t.length) {
            if (t[j] == '\\') { j += 2; continue }
            if (t[j] == d && j > from && !t[j - 1].isWhitespace() && (j + 1 >= t.length || t[j + 1] != d)) {
                if (d == '_' && j + 1 < t.length && isWordChar(t[j + 1])) { j++; continue }
                return j
            }
            j++
        }
        return -1
    }
}
