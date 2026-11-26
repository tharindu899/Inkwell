package com.inkwell.notes.editor

/**
 * Paste handling (Part 5). The text field only ever hands us plain text, so a paste is spotted as a multi-line
 * insertion; [classify] then decides what it is and [toBlocks] turns it into document blocks:
 *  - MARKDOWN → headings / lists / checklists / quotes / fenced code / tables / dividers / inline formatting
 *  - CODE     → code lines (also HTML source, which is code when it arrives as plain text)
 *  - PLAIN    → inserted as typed text (after [sanitize]) by the normal edit path
 */
object PasteHandler {
    enum class Kind { PLAIN, MARKDOWN, CODE }

    /** Old text [start, end) was replaced by [text]. */
    class Change(val start: Int, val end: Int, val text: String)

    /** Which part of [old] was replaced to get [new]; [caret] anchors pure insertions (same idea as `RichDoc.edit`). */
    fun diff(old: String, new: String, caret: Int?): Change? {
        if (old == new) return null
        val minLen = minOf(old.length, new.length)
        var p = 0
        while (p < minLen && old[p] == new[p]) p++
        var s = 0
        while (s < minLen - p && old[old.length - 1 - s] == new[new.length - 1 - s]) s++
        val rem0 = old.length - s - p
        val ins0 = new.length - s - p
        if (caret != null && rem0 == 0 && ins0 > 0) {
            val ip = caret - ins0
            if (ip in 0..old.length && new.removeRange(ip, ip + ins0) == old) p = ip
        }
        return Change(p, p + rem0, new.substring(p, p + ins0))
    }

    /** CR / CRLF / U+2028 → `\n`, tabs → two spaces, control and zero-width characters dropped. */
    fun sanitize(raw: String): String {
        val sb = StringBuilder(raw.length)
        var i = 0
        while (i < raw.length) {
            val ch = raw[i]
            when {
                ch == '\r' -> {
                    sb.append('\n')
                    if (i + 1 < raw.length && raw[i + 1] == '\n') i++
                }
                ch == '\u2028' || ch == '\u2029' -> sb.append('\n')
                ch == '\n' -> sb.append('\n')
                ch == '\t' -> sb.append("  ")
                ch.code < 32 || ch.code == 127 || ch == '\u200B' || ch == '\uFEFF' || ch == '\u00AD' -> {}
                else -> sb.append(ch)
            }
            i++
        }
        return sb.toString()
    }

    private val STRONG_CODE = Regex(
        """([;{}]\s*$)|(\)\s*\{?\s*$)|(=>|->|::|&&|\|\||\+\+|!==?|===)|(^\s*(//|/\*|\*/|#include|#define|@\w+))""" +
            """|(^\s*(function|def|class|import|from|const|let|var|return|public|private|static|package|using|elif|else|try|except|print)\b)""" +
            """|(^\s*</?[a-zA-Z][^>]*>\s*$)""",
    )
    private val HTML_START = Regex("""^\s*<(!doctype|html|head|body|div|span|p|a|ul|ol|li|table|tr|td|h[1-6]|script|style|section|nav|img|form|input|button)\b""", RegexOption.IGNORE_CASE)
    private val MD_LINE = Regex(
        """(^\s{0,3}#{1,6}\s+\S)|(^\s*[-*+]\s+\S)|(^\s*\d+[.)]\s+\S)|(^\s{0,3}>\s?\S)|(^\s*\|.*\|\s*$)|(^\s*([-*_])\1{2,}\s*$)""" +
            """|(\*\*[^*\n]+\*\*)|(\[[^\]\n]+]\([^)\s]+\))|(`[^`\n]+`)|(~~[^~\n]+~~)""",
    )
    private val MD_STRONG = Regex("""(^\s{0,3}#{1,6}\s+\S)|(^\s*\|.*\|\s*$)|(^\s*([-*_])\1{2,}\s*$)|(^\s*(```|~~~))""")

    fun classify(text: String): Kind {
        val lines = text.split('\n').filter { it.isNotBlank() }
        if (lines.size < 2) return Kind.PLAIN
        if (lines.any { it.trim().startsWith("```") || it.trim().startsWith("~~~") }) return Kind.MARKDOWN
        if (HTML_START.containsMatchIn(lines.first()) && lines.first().trimEnd().endsWith(">")) return Kind.CODE

        val strong = lines.count { STRONG_CODE.containsMatchIn(it) }
        if (strong >= 2 && strong * 2 >= lines.size) return Kind.CODE

        val md = lines.count { MD_LINE.containsMatchIn(it) }
        val mdStrong = lines.any { MD_STRONG.containsMatchIn(it) }
        if (md >= 1 && (md * 3 >= lines.size || mdStrong)) return Kind.MARKDOWN
        return Kind.PLAIN
    }

    /** Blocks for a [Kind.CODE] or [Kind.MARKDOWN] paste. */
    fun toBlocks(kind: Kind, text: String): List<Block> = when (kind) {
        Kind.CODE -> codeBlocks(text)
        Kind.MARKDOWN -> MarkdownImport.parse(text)
        Kind.PLAIN -> emptyList()
    }

    fun codeBlocks(text: String): List<Block> {
        val lines = text.split('\n').toMutableList()
        while (lines.isNotEmpty() && lines.first().isBlank()) lines.removeAt(0)
        while (lines.isNotEmpty() && lines.last().isBlank()) lines.removeAt(lines.lastIndex)
        if (lines.isEmpty()) lines.add("")
        return lines.map { Block(it.trimEnd(), IntArray(it.trimEnd().length), Para(BlockType.CODE)) }
    }

    /**
     * Puts [blocks] where old text [start, end) was. A single plain paragraph is merged into the surrounding
     * paragraph; anything else splits the paragraph around the paste.
     */
    fun splice(doc: RichDoc, start: Int, end: Int, blocks: List<Block>): EditResult {
        if (blocks.isEmpty()) return EditResult(doc)
        val i = doc.paraIndexAt(start)
        val j = doc.paraIndexAt(end)
        val head = doc.paraBefore(i, start)
        val tail = doc.paraAfter(j, end)
        val list = ArrayList<Block>()

        if (blocks.size == 1 && blocks[0].para.type == BlockType.P) {
            val b = blocks[0]
            list.add(Block(head.text + b.text + tail.text, head.marks + b.marks + tail.marks, doc.paras[i]))
            val next = doc.replaceParas(i, j + 1, list)
            return EditResult(next, next.starts[i] + head.text.length + b.text.length)
        }

        if (head.text.isNotEmpty()) list.add(head)
        val firstPasted = list.size
        list.addAll(blocks)
        var lastIdx = list.size - 1
        if (tail.text.isNotEmpty()) {
            list.add(Block(tail.text, tail.marks, doc.paras[j]))
        } else if (blocks.last().para.type.isAtomic) {
            list.add(Block("", IntArray(0), Para())) // somewhere to keep typing after a table / divider
            lastIdx = list.size - 1
        }
        val next = doc.replaceParas(i, j + 1, list)
        val target = i + (if (tail.text.isEmpty() && blocks.last().para.type.isAtomic) lastIdx else firstPasted + blocks.size - 1)
        return EditResult(next, next.paraEnd(target.coerceIn(0, next.paraCount - 1)))
    }
}

/** Minimal markdown → blocks for pasted notes (no raw HTML, no reference links). */
object MarkdownImport {
    private val HEADING = Regex("""^(#{1,6})\s+(.*)$""")
    private val QUOTE = Regex("""^>\s?(.*)$""")
    private val TASK = Regex("""^(\s*)[-*+]\s+\[( |x|X)]\s+(.*)$""")
    private val BULLET = Regex("""^(\s*)[-*+]\s+(.*)$""")
    private val ORDERED = Regex("""^(\s*)\d+[.)]\s+(.*)$""")
    private val RULE = Regex("""^([-*_])\1{2,}$""")
    private val SEPARATOR = Regex("""^\|?\s*:?-{2,}:?\s*(\|\s*:?-{2,}:?\s*)*\|?$""")
    private val LINK = Regex("""\[([^\]]+)]\((\S+?)\)""")
    private val SAFE_HREF = Regex("^(https?:|mailto:|tel:)", RegexOption.IGNORE_CASE)

    fun parse(src: String): List<Block> {
        val lines = src.split('\n')
        val out = ArrayList<Block>()
        var i = 0
        while (i < lines.size) {
            val t = lines[i].trim()

            if (t.startsWith("```") || t.startsWith("~~~")) {
                val fence = t.take(3)
                i++
                val code = ArrayList<String>()
                while (i < lines.size && !lines[i].trim().startsWith(fence)) {
                    code.add(lines[i].trimEnd())
                    i++
                }
                i++ // the closing fence (or the end of the paste)
                if (code.isEmpty()) code.add("")
                for (c in code) out.add(Block(c, IntArray(c.length), Para(BlockType.CODE)))
                continue
            }
            if (t.isEmpty()) { i++; continue }

            if (t.startsWith("|") && i + 1 < lines.size && SEPARATOR.matches(lines[i + 1].trim())) {
                val rows = ArrayList<List<String>>()
                rows.add(cells(t))
                i += 2
                while (i < lines.size && lines[i].trim().startsWith("|")) { rows.add(cells(lines[i].trim())); i++ }
                val cols = rows.maxOf { it.size }
                for (r in rows) {
                    val txt = (0 until cols).joinToString("\t") { k -> inline(r.getOrElse(k) { "" }).first }
                    out.add(Block(txt, IntArray(txt.length), Para(BlockType.TABLE)))
                }
                out.add(Block("", IntArray(0), Para())) // keeps two tables apart and gives the caret a place
                continue
            }

            if (RULE.matches(t)) { out.add(Block("", IntArray(0), Para(BlockType.HR))); i++; continue }

            val line = lines[i].trimEnd()
            val h = HEADING.matchEntire(line.trimStart())
            val q = QUOTE.matchEntire(line.trimStart())
            val task = TASK.matchEntire(line)
            val bullet = BULLET.matchEntire(line)
            val ordered = ORDERED.matchEntire(line)
            when {
                h != null -> out.add(block(h.groupValues[2], Para(headingType(h.groupValues[1].length))))
                q != null -> out.add(block(q.groupValues[1], Para(BlockType.QUOTE)))
                task != null -> out.add(
                    block(task.groupValues[3], Para(BlockType.TASK, indent = level(task.groupValues[1]), checked = task.groupValues[2] != " ")),
                )
                bullet != null -> out.add(block(bullet.groupValues[2], Para(BlockType.UL, indent = level(bullet.groupValues[1]))))
                ordered != null -> out.add(block(ordered.groupValues[2], Para(BlockType.OL, indent = level(ordered.groupValues[1]))))
                else -> out.add(block(line.trim(), Para()))
            }
            i++
        }
        if (out.isEmpty()) out.add(Block("", IntArray(0), Para()))
        return out
    }

    private fun headingType(n: Int) = when (n) { 1 -> BlockType.H1; 2 -> BlockType.H2; else -> BlockType.H3 }

    private fun level(ws: String): Int = (ws.length / 2).coerceIn(0, RichDoc.MAX_INDENT)

    private fun cells(row: String): List<String> =
        row.trim().trim('|').split('|').map { it.trim().replace("\\|", "|") }

    private fun block(content: String, para: Para): Block {
        val (t, m) = inline(content)
        return Block(t, m, para)
    }

    /** `**bold**` `*italic*` `~~strike~~` `` `code` `` `[text](url)` → text + per-character marks. */
    fun inline(src: String): Pair<String, IntArray> {
        val sb = StringBuilder()
        val ms = ArrayList<Int>()
        parseInto(src, 0, sb, ms)
        return sb.toString() to ms.toIntArray()
    }

    private fun add(sb: StringBuilder, ms: ArrayList<Int>, ch: Char, m: Int) {
        sb.append(ch)
        ms.add(m)
    }

    private fun parseInto(s: String, base: Int, sb: StringBuilder, ms: ArrayList<Int>) {
        var i = 0
        while (i < s.length) {
            val ch = s[i]
            if (ch == '\\' && i + 1 < s.length && s[i + 1] in "\\`*_{}[]()#+-.!|~>") {
                add(sb, ms, s[i + 1], base)
                i += 2
                continue
            }
            if (ch == '`') {
                val j = s.indexOf('`', i + 1)
                if (j > i + 1) {
                    for (k in i + 1 until j) add(sb, ms, s[k], base or Mark.CODE)
                    i = j + 1
                    continue
                }
            }
            if (ch == '[') {
                val m = LINK.find(s, i)
                if (m != null && m.range.first == i && SAFE_HREF.containsMatchIn(m.groupValues[2])) {
                    parseInto(m.groupValues[1], Mark.withLink(base, LinkTable.idFor(m.groupValues[2])), sb, ms)
                    i = m.range.last + 1
                    continue
                }
            }
            if (s.startsWith("**", i) || s.startsWith("__", i) || s.startsWith("~~", i)) {
                val tok = s.substring(i, i + 2)
                val j = s.indexOf(tok, i + 2)
                if (j > i + 2) {
                    val flag = if (tok == "~~") Mark.STRIKE else Mark.BOLD
                    parseInto(s.substring(i + 2, j), base or flag, sb, ms)
                    i = j + 2
                    continue
                }
            }
            if ((ch == '*' || ch == '_') && i + 1 < s.length && s[i + 1] != ' ' && (ch == '*' || i == 0 || !s[i - 1].isLetterOrDigit())) {
                val j = s.indexOf(ch, i + 1)
                if (j > i + 1 && s[j - 1] != ' ') {
                    parseInto(s.substring(i + 1, j), base or Mark.ITALIC, sb, ms)
                    i = j + 1
                    continue
                }
            }
            add(sb, ms, ch, base)
            i++
        }
    }
}
