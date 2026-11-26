package com.inkwell.notes.editor

/**
 * Markdown / pasted-text → [RichDoc] fragment (Part 5 paste handling), plus the detectors that decide how a
 * pasted block of text is treated. Mirrors what the web editor renders with `marked` (GFM): headings, lists,
 * task lists, quotes, fenced code, tables, dividers, and inline `**bold** *italic* ~~strike~~ `code` [link](url)`.
 *
 * Lines stay lines: a markdown paragraph that is hard-wrapped becomes one paragraph per line, blank lines
 * are separators and don't create empty paragraphs.
 */
object MarkdownCodec {
    // ── detectors (ports of the web `looksLikeMarkdown` / `looksLikeHtmlSource`) ──

    private val MD_BLOCK = Regex("""(^|\n)\s{0,3}(#{1,6}\s+|[-*+]\s+|\d+[.)]\s+|>\s?|```|~~~|[-*+]\s+\[[ xX]\]\s+)""")
    private val MD_TABLE_SEP = Regex("""\|\s*:?-{3,}:?\s*\|""")
    private val MD_INLINE = Regex("""(!?\[[^\]]+\]\(https?://[^\s)]+\)|`[^`\n]+`|\*\*[^*]+\*\*|__[^_]+__)""")
    private val HTML_SRC = Regex(
        """</?(div|p|h[1-6]|img|a|br|ul|ol|li|table|thead|tbody|tr|th|td|blockquote|pre|code|strong|b|em|i|u|s|span|center|hr|mark|kbd|details|summary)\b[^>]*>""",
        RegexOption.IGNORE_CASE,
    )

    fun looksLikeMarkdown(text: String): Boolean {
        if (text.isBlank()) return false
        return MD_BLOCK.containsMatchIn(text) || MD_TABLE_SEP.containsMatchIn(text) || MD_INLINE.containsMatchIn(text)
    }

    fun looksLikeHtmlSource(text: String): Boolean = text.isNotBlank() && HTML_SRC.containsMatchIn(text)

    private val CODE_HINTS = Regex(
        """(;\s*$|\{\s*$|^\s*\}|\)\s*\{|=>|->|::|==|!=|&&|\|\||^\s*(def|class|function|import|from|return|const|let|var|public|private|static|void|if|else|for|while|try|catch|package|using|#include|fun|val)\b|^\s*(//|/\*|\*/)|</?\w+[^>]*>$)""",
    )

    /**
     * Multi-line text that reads like source code (most lines end in `;` `{` `}`, start with a keyword,
     * are indented, ...). Conservative on purpose: needs 3+ non-blank lines and a clear majority.
     */
    fun looksLikeCode(text: String): Boolean {
        if (text.contains("```") || text.contains("~~~")) return false
        val lines = text.split('\n').filter { it.isNotBlank() }
        if (lines.size < 3) return false
        var score = 0
        for (l in lines) {
            if (CODE_HINTS.containsMatchIn(l) || l.startsWith("\t") || l.startsWith("    ")) score++
        }
        return score * 100 >= lines.size * 60
    }

    // ── block parser ──

    private val FENCE = Regex("""^\s{0,3}(`{3,}|~{3,})\s*([^`\s]*)\s*$""")
    private val HEADING = Regex("""^\s{0,3}(#{1,6})\s+(.*?)\s*#*\s*$""")
    private val HR = Regex("""^\s{0,3}([-*_])(\s*\1){2,}\s*$""")
    private val QUOTE = Regex("""^\s{0,3}>\s?(.*)$""")
    private val TASK = Regex("""^(\s*)[-*+]\s+\[([ xX])\]\s+(.*)$""")
    private val UL = Regex("""^(\s*)[-*+]\s+(.*)$""")
    private val OL = Regex("""^(\s*)\d+[.)]\s+(.*)$""")
    private val IMG_LINE = Regex("""^\s*!\[([^\]]*)\]\(\s*(\S+?)(?:\s+"[^"]*")?\s*\)\s*$""")
    private val LINKED_IMG_LINE = Regex("""^\s*\[!\[([^\]]*)\]\(\s*(\S+?)\s*\)\]\([^)]*\)\s*$""")
    private val TABLE_SEP = Regex("""^\s*\|?\s*:?-{3,}:?\s*(\|\s*:?-{3,}:?\s*)*\|?\s*$""")

    fun parse(markdown: String): RichDoc {
        val lines = markdown.replace("\r\n", "\n").replace('\r', '\n').replace(OBJ.toString(), "").split('\n')
        val out = ArrayList<Chunk>()
        val indentStack = ArrayList<Int>()
        var i = 0

        fun add(text: String, marks: IntArray, para: Para) {
            out.add(Chunk(text, marks, para))
        }

        fun addInline(src: String, para: Para) {
            val r = inline(src.trim())
            add(r.first, r.second, para)
        }

        fun level(spaces: Int): Int {
            while (indentStack.isNotEmpty() && spaces < indentStack.last()) indentStack.removeAt(indentStack.lastIndex)
            if (indentStack.isEmpty() || spaces > indentStack.last()) indentStack.add(spaces)
            return (indentStack.size - 1).coerceAtMost(RichDoc.MAX_INDENT)
        }

        while (i < lines.size) {
            val line = lines[i]
            val expanded = line.replace("\t", "    ")

            // fenced code
            val fence = FENCE.matchEntire(expanded)
            if (fence != null) {
                val marker = fence.groupValues[1]
                i++
                val code = ArrayList<String>()
                while (i < lines.size) {
                    val t = lines[i].trim()
                    if (t.startsWith(marker.substring(0, 3)) && t.all { it == marker[0] } && t.length >= marker.length) break
                    code.add(lines[i])
                    i++
                }
                i++ // closing fence
                if (code.isEmpty()) code.add("")
                for (c in code) add(c, IntArray(c.length), Para(BlockType.CODE))
                indentStack.clear()
                continue
            }

            if (line.isBlank()) { indentStack.clear(); i++; continue }

            // table: header row + separator row
            if ('|' in line && i + 1 < lines.size && TABLE_SEP.matches(lines[i + 1])) {
                val rows = ArrayList<List<Cell>>()
                rows.add(splitRow(line).map { cellOf(it) })
                i += 2
                while (i < lines.size && lines[i].isNotBlank() && '|' in lines[i]) {
                    rows.add(splitRow(lines[i]).map { cellOf(it) })
                    i++
                }
                val cols = rows.first().size
                val fixed = rows.map { r -> if (r.size > cols) r.take(cols) else r }
                add(OBJ.toString(), IntArray(1), Para(BlockType.TABLE, table = TableData(fixed, header = true)))
                indentStack.clear()
                continue
            }

            val imgLine = IMG_LINE.matchEntire(line) ?: LINKED_IMG_LINE.matchEntire(line)
            val image = imgLine?.let { ImageData.fromMarkdown(it.groupValues[1], it.groupValues[2]) }
            if (image != null) {
                add(OBJ.toString(), IntArray(1), Para(BlockType.IMAGE, image = image))
                indentStack.clear()
                i++
                continue
            }

            if (HR.matches(expanded)) {
                add(OBJ.toString(), IntArray(1), Para(BlockType.HR))
                indentStack.clear()
                i++
                continue
            }

            val heading = HEADING.matchEntire(expanded)
            if (heading != null) {
                val type = when (heading.groupValues[1].length) { 1 -> BlockType.H1; 2 -> BlockType.H2; else -> BlockType.H3 }
                addInline(heading.groupValues[2], Para(type))
                indentStack.clear()
                i++
                continue
            }

            val quote = QUOTE.matchEntire(expanded)
            if (quote != null) {
                addInline(quote.groupValues[1], Para(BlockType.QUOTE))
                indentStack.clear()
                i++
                continue
            }

            val task = TASK.matchEntire(expanded)
            if (task != null) {
                val lv = level(task.groupValues[1].length)
                addInline(task.groupValues[3], Para(BlockType.TASK, indent = lv, checked = task.groupValues[2] != " "))
                i++
                continue
            }
            val ul = UL.matchEntire(expanded)
            if (ul != null) {
                val lv = level(ul.groupValues[1].length)
                addInline(ul.groupValues[2], Para(BlockType.UL, indent = lv))
                i++
                continue
            }
            val ol = OL.matchEntire(expanded)
            if (ol != null) {
                val lv = level(ol.groupValues[1].length)
                addInline(ol.groupValues[2], Para(BlockType.OL, indent = lv))
                i++
                continue
            }

            indentStack.clear()
            addInline(line, Para())
            i++
        }
        if (out.isEmpty()) return RichDoc.EMPTY
        return RichDoc.fromChunks(out)
    }

    /** Fenced-code fragment for text detected as source code. */
    fun codeFragment(text: String): RichDoc {
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').replace(OBJ.toString(), "").trimEnd('\n').split('\n')
        return RichDoc.fromChunks(lines.map { Chunk(it, IntArray(it.length), Para(BlockType.CODE)) })
    }

    private fun splitRow(row: String): List<String> {
        var r = row.trim()
        if (r.startsWith("|")) r = r.substring(1)
        if (r.endsWith("|") && !r.endsWith("\\|")) r = r.dropLast(1)
        val cells = ArrayList<String>()
        val sb = StringBuilder()
        var k = 0
        var inCode = false
        while (k < r.length) {
            val ch = r[k]
            if (ch == '\\' && k + 1 < r.length && r[k + 1] == '|') { sb.append('|'); k += 2; continue }
            if (ch == '`') inCode = !inCode
            if (ch == '|' && !inCode) { cells.add(sb.toString().trim()); sb.setLength(0); k++; continue }
            sb.append(ch)
            k++
        }
        cells.add(sb.toString().trim())
        return cells
    }

    private fun cellOf(src: String): Cell {
        val r = inline(src.replace(Regex("(?i)<br\\s*/?>"), "\n"))
        return Cell(r.first, r.second)
    }

    // ── inline parser ──

    private val URL_OK = Regex("""^(https?://|mailto:|tel:)""", RegexOption.IGNORE_CASE)
    private val BARE_URL = Regex("""^https?://[^\s<>"]+""", RegexOption.IGNORE_CASE)

    /** Inline markdown → plain text + marks. */
    fun inline(src: String): Pair<String, IntArray> {
        val sb = StringBuilder()
        val marks = ArrayList<Int>()
        parseInline(src, 0, sb, marks)
        return sb.toString() to marks.toIntArray()
    }

    private fun isWord(c: Char?): Boolean = c != null && (c.isLetterOrDigit())

    private fun parseInline(s: String, base: Int, sb: StringBuilder, marks: ArrayList<Int>) {
        fun put(ch: Char, m: Int) { sb.append(ch); marks.add(m) }
        var i = 0
        while (i < s.length) {
            val c = s[i]
            // escapes
            if (c == '\\' && i + 1 < s.length && s[i + 1] in "\\`*_{}[]()#+-.!|~<>") { put(s[i + 1], base); i += 2; continue }

            // inline code (1–2 backticks)
            if (c == '`') {
                var n = 1
                while (i + n < s.length && s[i + n] == '`') n++
                val fence = "`".repeat(n)
                val end = s.indexOf(fence, i + n)
                if (end > i + n) {
                    val body = s.substring(i + n, end).let { if (n > 1) it.trim() else it }
                    for (ch in body) put(ch, base or Mark.CODE)
                    i = end + n
                    continue
                }
                for (k in 0 until n) put('`', base)
                i += n
                continue
            }

            // image → alt text (images arrive with Part 6)
            if (c == '!' && i + 1 < s.length && s[i + 1] == '[') {
                val close = s.indexOf(']', i + 2)
                if (close != -1 && close + 1 < s.length && s[close + 1] == '(') {
                    val pe = s.indexOf(')', close + 2)
                    if (pe != -1) {
                        parseInline(s.substring(i + 2, close), base, sb, marks)
                        i = pe + 1
                        continue
                    }
                }
            }

            // link [text](url "title")
            if (c == '[') {
                val close = matchBracket(s, i)
                if (close != -1 && close + 1 < s.length && s[close + 1] == '(') {
                    val pe = s.indexOf(')', close + 2)
                    if (pe != -1) {
                        var url = s.substring(close + 2, pe).trim()
                        val sp = url.indexOf(' ')
                        if (sp > 0) url = url.substring(0, sp)
                        url = url.trim('<', '>')
                        val id = if (URL_OK.containsMatchIn(url)) LinkTable.idOf(url) else 0
                        parseInline(s.substring(i + 1, close), if (id == 0) base else Mark.withLink(base, id), sb, marks)
                        i = pe + 1
                        continue
                    }
                }
            }

            // <https://…> autolink and a few inline tags
            if (c == '<') {
                val gt = s.indexOf('>', i)
                if (gt != -1) {
                    val inner = s.substring(i + 1, gt)
                    if (URL_OK.containsMatchIn(inner) && ' ' !in inner) {
                        val id = LinkTable.idOf(inner)
                        for (ch in inner) put(ch, Mark.withLink(base, id))
                        i = gt + 1
                        continue
                    }
                    val tag = inner.lowercase()
                    val flag = when (tag) { "u" -> Mark.UNDERLINE; "sup" -> Mark.SUP; "sub" -> Mark.SUB; "mark" -> Mark.HIGHLIGHT; else -> 0 }
                    if (flag != 0) {
                        val closeTag = "</$tag>"
                        val end = s.indexOf(closeTag, gt + 1, ignoreCase = true)
                        if (end != -1) {
                            parseInline(s.substring(gt + 1, end), base or flag, sb, marks)
                            i = end + closeTag.length
                            continue
                        }
                    }
                    if (tag == "br" || tag == "br/" || tag == "br /") { put('\n', 0); i = gt + 1; continue }
                }
            }

            // bare URL
            if ((c == 'h' || c == 'H') && (i == 0 || !isWord(s[i - 1]))) {
                val m = BARE_URL.find(s.substring(i))
                if (m != null) {
                    var url = m.value
                    while (url.isNotEmpty() && url.last() in ".,;:!?)'\"") url = url.dropLast(1)
                    if (url.length > 8) {
                        val id = LinkTable.idOf(url)
                        for (ch in url) put(ch, Mark.withLink(base, id))
                        i += url.length
                        continue
                    }
                }
            }

            // emphasis: ** __ ~~ * _
            val delim = when {
                s.startsWith("**", i) -> "**"
                s.startsWith("__", i) -> "__"
                s.startsWith("~~", i) -> "~~"
                c == '*' -> "*"
                c == '_' -> "_"
                else -> null
            }
            if (delim != null) {
                val flag = when (delim) { "**", "__" -> Mark.BOLD; "~~" -> Mark.STRIKE; else -> Mark.ITALIC }
                val prevWord = i > 0 && isWord(s[i - 1])
                val intraword = delim[0] == '_' && prevWord
                val after = s.getOrNull(i + delim.length)
                if (!intraword && after != null && !after.isWhitespace()) {
                    var from = i + delim.length
                    var end = -1
                    while (true) {
                        val k = s.indexOf(delim, from)
                        if (k == -1) break
                        val before = s[k - 1]
                        val next = s.getOrNull(k + delim.length)
                        val okClose = k > i + delim.length && !before.isWhitespace() &&
                            !(delim[0] == '_' && isWord(next)) &&
                            // `*` must not close on the first half of a `**`
                            !(delim.length == 1 && next == delim[0])
                        if (okClose) { end = k; break }
                        from = k + 1
                    }
                    if (end != -1) {
                        parseInline(s.substring(i + delim.length, end), base or flag, sb, marks)
                        i = end + delim.length
                        continue
                    }
                }
            }

            put(c, base)
            i++
        }
    }

    private fun matchBracket(s: String, open: Int): Int {
        var depth = 0
        var k = open
        while (k < s.length) {
            when (s[k]) {
                '\\' -> k++
                '[' -> depth++
                ']' -> { depth--; if (depth == 0) return k }
            }
            k++
        }
        return -1
    }
}
