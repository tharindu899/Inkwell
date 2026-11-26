package com.inkwell.notes.editor

/**
 * Stored note HTML (`content`) ⇄ [RichDoc], plus the markdown / preview text derived from it.
 *
 * Understands: `p div br h1–h6 ul ol li (nested) blockquote pre/code strong/b em/i u s/strike/del sup sub mark span label`
 * with `style="text-align:…; margin-left:…px"`, the web checklist markup (`ul.check-list`, `li[data-checked]`,
 * `span.check-box`, `span.check-text`) and `li[data-collapsed]`. Anything else (tables, inline code, images,
 * links, hr …) makes [parse] return null — the editor then shows the note read-only instead of
 * flattening it. Later parts extend this list.
 */
object HtmlCodec {
    private const val INDENT_PX = 24

    // ─────────────────────────────────────────
    // HTML → RichDoc
    // ─────────────────────────────────────────

    /** @return the document, or null when [html] uses something this editor version can't represent yet. */
    fun parse(html: String): RichDoc? {
        if (html.isBlank()) return RichDoc.EMPTY
        return Parser(html).run()
    }

    private val ALLOWED_ATTRS = setOf(
        "style", "dir", "lang", "spellcheck", "align",
        "data-checked", "data-collapsed", "data-check-label", "data-type",
        "role", "aria-checked", "aria-readonly", "tabindex", "contenteditable",
    )
    private val SAFE_CLASSES = setOf(
        "check-list", "check-box", "check-text", "li-collapsible", "li-collapsed", "code-scroll", "ed-code",
    )
    private val LANG_CLASS = Regex("^language-[a-z0-9_-]+$", RegexOption.IGNORE_CASE)
    private val ATTR_RE = Regex("""([a-zA-Z_:][-a-zA-Z0-9_:.]*)\s*(?:=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'>]+)))?""")
    private val WS_RE = Regex("\\s+")

    private class Elem(
        val name: String,
        val align: Align?,
        val indent: Int?,
        val checked: Boolean = false,
        val collapsed: Boolean = false,
    )

    private class Parser(private val src: String) {
        private val texts = ArrayList<String>()
        private val markLists = ArrayList<IntArray>()
        private val paras = ArrayList<Para>()

        private var open = false
        private var cur = Para()
        private val curText = StringBuilder()
        private val curMarks = ArrayList<Int>()

        private val elems = ArrayList<Elem>()
        private val lists = ArrayList<BlockType>()
        private var quoteDepth = 0
        private var headingLevel = 0
        private var preDepth = 0
        private var preStartParas = 0
        private val counts = IntArray(7) // b i u s sup sub mark

        fun run(): RichDoc? {
            var pos = 0
            val n = src.length
            while (pos < n) {
                val lt = src.indexOf('<', pos)
                if (lt == -1) {
                    text(src.substring(pos))
                    break
                }
                if (lt > pos) text(src.substring(pos, lt))
                if (src.startsWith("<!--", lt)) {
                    val e = src.indexOf("-->", lt + 4)
                    pos = if (e == -1) n else e + 3
                    continue
                }
                val gt = findTagEnd(lt)
                if (gt == -1) { // stray '<' → literal text
                    text(src.substring(lt))
                    break
                }
                if (!tag(src.substring(lt + 1, gt))) return null
                pos = gt + 1
            }
            closePara(force = false)
            if (paras.isEmpty()) return RichDoc.EMPTY

            val sb = StringBuilder()
            val marks = ArrayList<Int>()
            for (i in paras.indices) {
                if (i > 0) { sb.append('\n'); marks.add(0) }
                sb.append(texts[i])
                for (m in markLists[i]) marks.add(m)
            }
            return RichDoc.create(sb.toString(), marks.toIntArray(), paras)
        }

        private fun findTagEnd(lt: Int): Int {
            var quote = 0.toChar()
            var k = lt + 1
            while (k < src.length) {
                val ch = src[k]
                if (quote != 0.toChar()) {
                    if (ch == quote) quote = 0.toChar()
                } else if (ch == '"' || ch == '\'') quote = ch
                else if (ch == '>') return k
                k++
            }
            return -1
        }

        /** @return false = unsupported markup. */
        private fun tag(raw: String): Boolean {
            var body = raw.trim()
            if (body.isEmpty()) return true
            if (body.startsWith("!") || body.startsWith("?")) return true // doctype etc.
            val closing = body.startsWith("/")
            if (closing) body = body.substring(1).trim()
            val selfClosing = body.endsWith("/")
            if (selfClosing) body = body.dropLast(1).trim()
            val nameEnd = body.indexOfFirst { it.isWhitespace() }
            val name = (if (nameEnd == -1) body else body.substring(0, nameEnd)).lowercase()
            val attrs = if (nameEnd == -1) "" else body.substring(nameEnd)

            return if (closing) closeTag(name) else openTag(name, attrs)
        }

        private fun openTag(name: String, attrSrc: String): Boolean {
            var align: Align? = null
            var indent: Int? = null
            var checked = false
            var collapsed = false
            var taskList = false
            var checkBox = false
            for (m in ATTR_RE.findAll(attrSrc)) {
                val an = m.groupValues[1].lowercase()
                val av = m.groupValues[2].ifEmpty { m.groupValues[3].ifEmpty { m.groupValues[4] } }
                if (an == "class") {
                    for (cls in av.split(WS_RE).filter { it.isNotEmpty() }) {
                        if (cls !in SAFE_CLASSES && !LANG_CLASS.matches(cls)) return false
                        if (cls == "check-list") taskList = true
                        if (cls == "check-box") checkBox = true
                        if (cls == "li-collapsed") collapsed = true
                    }
                    continue
                }
                if (an !in ALLOWED_ATTRS) return false
                if (an == "data-type" && av == "taskList") taskList = true
                if ((an == "data-checked" || an == "aria-checked") && av == "true") checked = true
                if (an == "data-collapsed" && av == "true") collapsed = true
                if (an == "align") align = alignOf(av)
                if (an == "style") {
                    for (decl in av.split(';')) {
                        val kv = decl.split(':', limit = 2)
                        if (kv.size != 2) continue
                        val k = kv[0].trim().lowercase()
                        val v = kv[1].trim().lowercase()
                        if (k == "text-align") align = alignOf(v)
                        if (k == "margin-left") {
                            val px = v.removeSuffix("px").trim().toDoubleOrNull() ?: 0.0
                            indent = (px / INDENT_PX).let { Math.round(it).toInt() }.coerceIn(0, RichDoc.MAX_INDENT)
                        }
                    }
                }
            }

            when (name) {
                "p", "div" -> {
                    if (preDepth > 0) { elems.add(Elem(name, null, null)); return true }
                    closePara(false); elems.add(Elem(name, align, indent)); startPara()
                }
                "h1", "h2", "h3", "h4", "h5", "h6" -> {
                    closePara(false)
                    headingLevel = minOf(name[1] - '0', 3)
                    elems.add(Elem(name, align, indent))
                    startPara()
                }
                "ul", "ol" -> {
                    closePara(false)
                    lists.add(if (name == "ol") BlockType.OL else if (taskList) BlockType.TASK else BlockType.UL)
                    elems.add(Elem(name, align, indent))
                }
                "li" -> {
                    closePara(false)
                    if (lists.isEmpty()) lists.add(BlockType.UL) // stray <li>
                    elems.add(Elem(name, align, indent, checked, collapsed))
                    startPara()
                }
                "label" -> elems.add(Elem(name, null, null))
                "blockquote" -> {
                    closePara(false)
                    quoteDepth++
                    elems.add(Elem(name, align, indent))
                }
                "pre" -> {
                    closePara(false)
                    preDepth++
                    preStartParas = paras.size
                    elems.add(Elem(name, align, indent))
                    startPara()
                }
                "code" -> {
                    if (preDepth == 0) return false // inline code arrives with Part 5
                    elems.add(Elem(name, null, null))
                }
                "br" -> {
                    if (!open) startPara()
                    commit()
                    if (preDepth > 0) startPara()
                }
                "strong", "b" -> { counts[0]++; elems.add(Elem("b", null, null)) }
                "em", "i" -> { counts[1]++; elems.add(Elem("i", null, null)) }
                "u" -> { counts[2]++; elems.add(Elem("u", null, null)) }
                "s", "strike", "del" -> { counts[3]++; elems.add(Elem("s", null, null)) }
                "sup" -> { counts[4]++; elems.add(Elem("sup", null, null)) }
                "sub" -> { counts[5]++; elems.add(Elem("sub", null, null)) }
                "mark" -> { counts[6]++; elems.add(Elem("mark", null, null)) }
                "span" -> {
                    elems.add(Elem("span", align, indent))
                    if (checkBox && open && cur.type == BlockType.TASK) cur = cur.copy(checked = checked)
                }
                else -> return false
            }
            return true
        }

        private fun closeTag(name: String): Boolean {
            val key = when (name) {
                "strong" -> "b"
                "em" -> "i"
                "strike", "del" -> "s"
                "h1", "h2", "h3", "h4", "h5", "h6" -> name
                else -> name
            }
            // pop up to the matching open element (tolerates sloppy nesting)
            val idx = elems.indexOfLast { it.name == key }
            if (idx == -1) return name in SUPPORTED_CLOSE
            while (elems.size > idx) elems.removeAt(elems.lastIndex)

            when (key) {
                "p", "div" -> if (open && preDepth == 0) commit()
                "li" -> if (open) commit()
                "pre" -> {
                    if (open) { if (curText.isNotEmpty()) commit() else open = false }
                    if (paras.size == preStartParas) { // empty block → keep one empty code line
                        cur = Para(BlockType.CODE)
                        curText.setLength(0)
                        curMarks.clear()
                        open = true
                        commit()
                    }
                    if (preDepth > 0) preDepth--
                }
                "h1", "h2", "h3", "h4", "h5", "h6" -> { if (open) commit(); headingLevel = 0 }
                "ul", "ol" -> { closePara(false); if (lists.isNotEmpty()) lists.removeAt(lists.lastIndex) }
                "blockquote" -> { closePara(false); if (quoteDepth > 0) quoteDepth-- }
                "b" -> counts[0] = maxOf(0, counts[0] - 1)
                "i" -> counts[1] = maxOf(0, counts[1] - 1)
                "u" -> counts[2] = maxOf(0, counts[2] - 1)
                "s" -> counts[3] = maxOf(0, counts[3] - 1)
                "sup" -> counts[4] = maxOf(0, counts[4] - 1)
                "sub" -> counts[5] = maxOf(0, counts[5] - 1)
                "mark" -> counts[6] = maxOf(0, counts[6] - 1)
            }
            return true
        }

        private val SUPPORTED_CLOSE = setOf(
            "p", "div", "li", "ul", "ol", "blockquote", "span", "label", "pre", "code", "b", "strong", "i", "em", "u", "s",
            "strike", "del", "sup", "sub", "mark", "h1", "h2", "h3", "h4", "h5", "h6", "br",
        )

        private fun currentMarks(): Int {
            var m = 0
            if (counts[0] > 0) m = m or Mark.BOLD
            if (counts[1] > 0) m = m or Mark.ITALIC
            if (counts[2] > 0) m = m or Mark.UNDERLINE
            if (counts[3] > 0) m = m or Mark.STRIKE
            if (counts[4] > 0) m = m or Mark.SUP
            if (counts[5] > 0) m = m or Mark.SUB
            if (counts[6] > 0) m = m or Mark.HIGHLIGHT
            return m
        }

        private fun startPara() {
            val liElem = elems.lastOrNull { it.name == "li" }
            val inLi = liElem != null
            val type = when {
                preDepth > 0 -> BlockType.CODE
                inLi && lists.isNotEmpty() -> lists.last()
                headingLevel > 0 -> when (headingLevel) { 1 -> BlockType.H1; 2 -> BlockType.H2; else -> BlockType.H3 }
                quoteDepth > 0 -> BlockType.QUOTE
                else -> BlockType.P
            }
            val align = elems.lastOrNull { it.align != null }?.align ?: Align.Start
            val margin = elems.lastOrNull { it.indent != null }?.indent ?: 0
            // list items: nesting level (+ any Part-2 style margin); everything else: margin step
            val indent = if (type.isList) (lists.size - 1 + margin).coerceIn(0, RichDoc.MAX_INDENT) else margin
            cur = Para(
                type = type,
                align = align,
                indent = indent,
                checked = type == BlockType.TASK && liElem?.checked == true,
                collapsed = type.isList && liElem?.collapsed == true,
            )
            curText.setLength(0)
            curMarks.clear()
            open = true
        }

        private fun commit() {
            if (!open) return
            val t = curText.toString()
            val trimmed = if (cur.type == BlockType.CODE) t else t.trimEnd()
            texts.add(trimmed)
            markLists.add(IntArray(trimmed.length) { curMarks[it] })
            paras.add(cur)
            open = false
        }

        private fun closePara(force: Boolean) {
            if (!open) return
            if (force || curText.isNotEmpty()) commit() else open = false
        }

        private fun codeText(raw: String) {
            // whitespace between <pre> and <code> is layout, not code
            if (elems.none { it.name == "code" } && raw.isBlank()) return
            val s = decode(raw).replace("\u200B", "")
            for (ch in s) {
                if (!open) startPara()
                if (ch == '\r') continue
                if (ch == '\n') { commit(); startPara(); continue }
                curText.append(ch)
                curMarks.add(0)
            }
        }

        private fun text(raw: String) {
            if (raw.isEmpty()) return
            if (preDepth > 0) { codeText(raw); return }
            var s = decode(raw).replace(WS_RE, " ")
            if (!open) {
                if (s.isBlank()) return // whitespace between tags
                startPara()
            }
            if (curText.isEmpty()) s = s.trimStart()
            if (s.isEmpty()) return
            val m = currentMarks()
            for (ch in s) {
                curText.append(ch)
                curMarks.add(m)
            }
        }

        private fun alignOf(v: String): Align = when (v.trim().lowercase()) {
            "center" -> Align.Center
            "right", "end" -> Align.End
            else -> Align.Start
        }
    }

    private fun decode(s: String): String {
        if ('&' !in s) return s
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val ch = s[i]
            if (ch == '&') {
                val semi = s.indexOf(';', i + 1)
                if (semi != -1 && semi - i <= 10) {
                    val ent = s.substring(i + 1, semi)
                    val rep: String? = when {
                        ent == "amp" -> "&"
                        ent == "lt" -> "<"
                        ent == "gt" -> ">"
                        ent == "quot" -> "\""
                        ent == "apos" || ent == "#39" -> "'"
                        ent == "nbsp" -> " "
                        ent.startsWith("#x") || ent.startsWith("#X") ->
                            ent.substring(2).toIntOrNull(16)?.let { String(Character.toChars(it)) }
                        ent.startsWith("#") -> ent.substring(1).toIntOrNull()?.let { String(Character.toChars(it)) }
                        else -> null
                    }
                    if (rep != null) {
                        sb.append(rep)
                        i = semi + 1
                        continue
                    }
                }
            }
            sb.append(ch)
            i++
        }
        return sb.toString()
    }

    // ─────────────────────────────────────────
    // RichDoc → HTML
    // ─────────────────────────────────────────

    fun toHtml(doc: RichDoc): String {
        if (doc.text.isEmpty() && doc.paras.first() == Para()) return ""
        val out = StringBuilder()
        var i = 0
        while (i < doc.paraCount) {
            val p = doc.paras[i]
            if (p.type.isList) {
                i = listRun(doc, i, out)
                continue
            }
            if (p.type == BlockType.CODE) {
                val lines = ArrayList<String>()
                while (i < doc.paraCount && doc.paras[i].type == BlockType.CODE) {
                    lines.add(doc.text.substring(doc.starts[i], doc.paraEnd(i)))
                    i++
                }
                out.append("<pre><code>").append(escape(lines.joinToString("\n"))).append("</code></pre>")
                continue
            }
            val tag = when (p.type) {
                BlockType.H1 -> "h1"
                BlockType.H2 -> "h2"
                BlockType.H3 -> "h3"
                BlockType.QUOTE -> "blockquote"
                else -> "p"
            }
            out.append('<').append(tag).append(styleAttr(p, margin = true)).append('>')
            out.append(inlineHtml(doc, i))
            out.append("</").append(tag).append('>')
            i++
        }
        return out.toString()
    }

    /** Writes the run of list items starting at [from] as (nested) `<ul>` / `<ol>`; returns the index after it. */
    private fun listRun(doc: RichDoc, from: Int, out: StringBuilder): Int {
        val open = ArrayList<BlockType>() // list type per open nesting level
        var i = from
        while (i < doc.paraCount && doc.paras[i].type.isList) {
            val p = doc.paras[i]
            val level = p.indent.coerceIn(0, open.size) // RichDoc keeps this ≤ depth, clamp anyway
            while (open.size > level + 1) {
                out.append("</li></").append(listTag(open.removeAt(open.lastIndex))).append('>')
            }
            if (open.size == level + 1) {
                out.append("</li>")
                if (open[level] != p.type) {
                    out.append("</").append(listTag(open[level])).append('>')
                    open[level] = p.type
                    out.append(listOpen(p.type))
                }
            } else {
                open.add(p.type)
                out.append(listOpen(p.type))
            }
            out.append("<li")
            if (p.type == BlockType.TASK) out.append(" data-checked=\"").append(p.checked).append('"')
            if (p.collapsed && doc.hasChildren(i)) out.append(" data-collapsed=\"true\"")
            out.append(styleAttr(p, margin = false)).append('>')
            if (p.type == BlockType.TASK) {
                out.append("<label data-check-label=\"true\"><span class=\"check-box\" contenteditable=\"false\" role=\"checkbox\" ")
                    .append("aria-checked=\"").append(p.checked).append("\" data-checked=\"").append(p.checked)
                    .append("\" tabindex=\"-1\"></span><span class=\"check-text\">")
                    .append(inlineHtml(doc, i)).append("</span></label>")
            } else {
                out.append(inlineHtml(doc, i))
            }
            i++
        }
        while (open.isNotEmpty()) {
            out.append("</li></").append(listTag(open.removeAt(open.lastIndex))).append('>')
        }
        return i
    }

    private fun listTag(t: BlockType) = if (t == BlockType.OL) "ol" else "ul"

    private fun listOpen(t: BlockType): String = when (t) {
        BlockType.OL -> "<ol>"
        BlockType.TASK -> "<ul class=\"check-list\" data-type=\"taskList\">"
        else -> "<ul>"
    }

    private fun styleAttr(p: Para, margin: Boolean): String {
        val parts = ArrayList<String>()
        when (p.align) {
            Align.Center -> parts.add("text-align:center")
            Align.End -> parts.add("text-align:right")
            Align.Start -> {}
        }
        if (margin && p.indent > 0) parts.add("margin-left:${p.indent * INDENT_PX}px")
        return if (parts.isEmpty()) "" else " style=\"${parts.joinToString(";")}\""
    }

    private fun inlineHtml(doc: RichDoc, para: Int): String {
        val s = doc.starts[para]
        val e = doc.paraEnd(para)
        if (s == e) return "<br>"
        val sb = StringBuilder()
        forRuns(doc, s, e) { a, b, m ->
            val open = StringBuilder()
            val close = StringBuilder()
            for ((flag, tag) in HTML_TAGS) {
                if (m and flag != 0) {
                    open.append('<').append(tag).append('>')
                    close.insert(0, "</$tag>")
                }
            }
            sb.append(open).append(escape(doc.text.substring(a, b))).append(close)
        }
        return sb.toString()
    }

    private val HTML_TAGS = listOf(
        Mark.BOLD to "strong",
        Mark.ITALIC to "em",
        Mark.UNDERLINE to "u",
        Mark.STRIKE to "s",
        Mark.SUP to "sup",
        Mark.SUB to "sub",
        Mark.HIGHLIGHT to "mark",
    )

    private inline fun forRuns(doc: RichDoc, s: Int, e: Int, f: (Int, Int, Int) -> Unit) {
        var a = s
        while (a < e) {
            var b = a + 1
            while (b < e && doc.marks[b] == doc.marks[a]) b++
            f(a, b, doc.marks[a])
            a = b
        }
    }

    private fun escape(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    // ─────────────────────────────────────────
    // RichDoc → markdown (same conventions as the web `htmlToMarkdown`)
    // ─────────────────────────────────────────

    fun toMarkdown(doc: RichDoc): String {
        val sb = StringBuilder()
        val nums = doc.olNumbers
        var prevType: BlockType? = null
        var wrote = false
        var i = 0
        while (i < doc.paraCount) {
            val p = doc.paras[i]
            val s = doc.starts[i]
            val e = doc.paraEnd(i)

            if (p.type == BlockType.CODE) {
                val lines = ArrayList<String>()
                while (i < doc.paraCount && doc.paras[i].type == BlockType.CODE) {
                    lines.add(doc.text.substring(doc.starts[i], doc.paraEnd(i)))
                    i++
                }
                if (wrote) sb.append("\n\n")
                sb.append("```\n").append(lines.joinToString("\n")).append("\n```")
                wrote = true
                prevType = BlockType.CODE
                continue
            }
            i++

            if (s == e) { prevType = null; continue } // blank lines aren't content in markdown

            val inline = StringBuilder()
            forRuns(doc, s, e) { a, b, m ->
                var t = doc.text.substring(a, b)
                if (m and Mark.STRIKE != 0) t = "~~$t~~"
                if (m and Mark.BOLD != 0) t = "**$t**"
                if (m and Mark.ITALIC != 0) t = "*$t*"
                if (m and Mark.UNDERLINE != 0) t = "<u>$t</u>"
                if (m and Mark.SUP != 0) t = "<sup>$t</sup>"
                if (m and Mark.SUB != 0) t = "<sub>$t</sub>"
                inline.append(t)
            }
            val k = i - 1
            val prefix = when (p.type) {
                BlockType.H1 -> "# "
                BlockType.H2 -> "## "
                BlockType.H3 -> "### "
                BlockType.UL -> "- "
                BlockType.OL -> "${nums[k]}. "
                BlockType.TASK -> if (p.checked) "- [x] " else "- [ ] "
                BlockType.QUOTE -> "> "
                else -> ""
            }
            val lead = if (p.type.isList) "  ".repeat(p.indent) else ""
            if (wrote) sb.append(if (p.type.isList && prevType?.isList == true) "\n" else "\n\n")
            sb.append(lead).append(prefix).append(inline)
            wrote = true
            prevType = p.type
        }
        return sb.toString()
    }

    // ─────────────────────────────────────────
    // Read-only preview text for notes the editor can't represent yet
    // ─────────────────────────────────────────

    private val ANY_TAG = Regex("<[^>]*>")
    private val BREAK = Regex("<br\\s*/?>", RegexOption.IGNORE_CASE)
    private val BLOCK_END = Regex("</(p|div|h[1-6]|li|blockquote|pre|tr)>", RegexOption.IGNORE_CASE)

    fun previewText(html: String): String =
        decode(html.replace(BREAK, "\n").replace(BLOCK_END, "\n").replace(ANY_TAG, "")).trim()
}
