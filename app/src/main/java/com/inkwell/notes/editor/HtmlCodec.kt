package com.inkwell.notes.editor

/**
 * Stored note HTML (`content`) ⇄ [RichDoc], plus the markdown / preview text derived from it.
 *
 * Understands: `p div br h1–h6 ul ol li (nested) blockquote pre/code strong/b em/i u s/strike/del sup sub mark span label`
 * with `style="text-align:…; margin-left:…px"`, the web checklist markup (`ul.check-list`, `li[data-checked]`,
 * `span.check-box`, `span.check-text`) and `li[data-collapsed]`.
 * Part 5 adds inline `code`, links (`a[href]`), `hr` and tables (`table/thead/tbody/tr/th/td`, one-line-per-cell
 * text with inline marks; colspan / rowspan are not representable).
 * Anything else (images, …) makes [parse] return null — the editor then shows the note read-only instead of
 * flattening it. Later parts extend this list.
 *
 * [parseLenient] is the forgiving cousin used for pasted HTML: unknown tags are dropped (their text kept),
 * scripts / styles are skipped, unsupported attributes are ignored. It never returns null for non-blank input.
 */
object HtmlCodec {
    private const val INDENT_PX = 24

    // ─────────────────────────────────────────
    // HTML → RichDoc
    // ─────────────────────────────────────────

    /** @return the document, or null when [html] uses something this editor version can't represent yet. */
    fun parse(html: String): RichDoc? {
        if (html.isBlank()) return RichDoc.EMPTY
        return Parser(html, lenient = false).run()
    }

    /** Pasted / foreign HTML → document: drops what it can't represent instead of refusing. */
    fun parseLenient(html: String): RichDoc? {
        if (html.isBlank()) return null
        return Parser(html, lenient = true).run()
    }

    private val ALLOWED_ATTRS = setOf(
        "style", "dir", "lang", "spellcheck", "align",
        "data-checked", "data-collapsed", "data-check-label", "data-type",
        "role", "aria-checked", "aria-readonly", "tabindex", "contenteditable",
    )
    private val SAFE_CLASSES = setOf(
        "check-list", "check-box", "check-text", "li-collapsible", "li-collapsed", "code-scroll", "ed-code",
        "md-table-wrap",
    )
    private val TABLE_TAGS = setOf("table", "thead", "tbody", "tfoot", "tr", "td", "th", "colgroup", "col", "caption")
    private val SKIP_CONTENT = setOf("script", "style", "head", "title", "noscript", "iframe", "object", "embed", "template", "textarea", "select")
    private val BLOCKISH = setOf(
        "section", "article", "header", "footer", "main", "nav", "aside", "figure", "figcaption", "details", "summary",
        "center", "dl", "dt", "dd", "address", "form", "fieldset", "html", "body",
    )
    private val INLINEISH = setOf("font", "small", "big", "abbr", "cite", "q", "var", "samp", "dfn", "time", "bdi", "bdo", "ins", "wbr")
    private val LANG_CLASS = Regex("^language-[a-z0-9_-]+$", RegexOption.IGNORE_CASE)
    private val ATTR_RE = Regex("""([a-zA-Z_:][-a-zA-Z0-9_:.]*)\s*(?:=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'>]+)))?""")
    private val WS_RE = Regex("\\s+")
    private val PLACEHOLDER_RE = Regex("""class\s*=\s*["'][^"']*\bimage-placeholder\b""")

    private class Elem(
        val name: String,
        val align: Align?,
        val indent: Int?,
        val checked: Boolean = false,
        val collapsed: Boolean = false,
        /** `<code>` outside a `<pre>`: carries the inline-code mark. */
        val inline: Boolean = false,
        /** `<a>`: link id in [LinkTable]. */
        val link: Int = 0,
    )

    private class TableBuilder {
        val rows = ArrayList<List<Cell>>()
        var cur: ArrayList<Cell>? = null
        var curAllTh = true
        var inThead = false
        var headerFirst = false
    }

    private class Parser(private val src: String, private val lenient: Boolean) {
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
        private val counts = IntArray(8) // b i u s sup sub mark code
        private var failed = false
        private var skipTo: String? = null
        private var tbl: TableBuilder? = null
        private var tableNest = 0
        private var cellOpen = false
        private var fig: FigBuilder? = null

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
                val skip = skipTo
                if (skip != null) { // lenient: drop <script>/<style>… including their content
                    skipTo = null
                    val close = src.indexOf("</$skip", pos, ignoreCase = true)
                    if (close == -1) {
                        pos = n
                    } else {
                        val g2 = src.indexOf('>', close)
                        pos = if (g2 == -1) n else g2 + 1
                    }
                }
                if (failed) return null
            }
            if (failed) return null
            if (tbl != null) endTable()
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

        private fun openTag(rawName: String, attrSrc: String): Boolean {
            var name = rawName
            if (lenient) {
                if (name in SKIP_CONTENT) { skipTo = name; return true }
                if (name in BLOCKISH) name = "div"
                if (name in INLINEISH) name = "span"
                if (name == "kbd") name = "code"
            }
            // The web "Image note" starter placeholder: nothing to keep, drop it with its content.
            if (name == "div" && PLACEHOLDER_RE.containsMatchIn(attrSrc)) { skipTo = "div"; return true }
            var align: Align? = null
            var indent: Int? = null
            var checked = false
            var collapsed = false
            var taskList = false
            var checkBox = false
            var href: String? = null
            var imgSrc = ""
            var imgAlt = ""
            var mediaDrawing: Boolean? = null
            var figW: Int? = null
            var figX = 0
            var figY = 0
            for (m in ATTR_RE.findAll(attrSrc)) {
                val an = m.groupValues[1].lowercase()
                val av = m.groupValues[2].ifEmpty { m.groupValues[3].ifEmpty { m.groupValues[4] } }
                if (name == "figure" || name == "img") {
                    when (an) {
                        "src" -> imgSrc = decode(av)
                        "alt" -> imgAlt = decode(av)
                        "data-media" -> mediaDrawing = av.trim().equals("drawing", true)
                        "width" -> av.trim().removeSuffix("px").toDoubleOrNull()?.let { figW = it.toInt() }
                        "style" -> for (decl in av.split(';')) {
                            val kv = decl.split(':', limit = 2)
                            if (kv.size != 2) continue
                            val px = kv[1].trim().lowercase().removeSuffix("px").trim().toDoubleOrNull() ?: continue
                            when (kv[0].trim().lowercase()) {
                                "width" -> figW = px.toInt()
                                "margin-left" -> figX = px.toInt()
                                "margin-top" -> figY = px.toInt()
                            }
                        }
                        "class", "loading", "draggable", "contenteditable", "height", "decoding", "crossorigin",
                        "referrerpolicy", "srcset", "sizes", "title" -> {}
                        else -> if (!lenient) return false
                    }
                    continue
                }
                if (name == "a" && an == "href") { href = decode(av); continue }
                if (name == "a" && (an == "target" || an == "rel" || an == "title")) continue
                if ((name == "td" || name == "th") && (an == "colspan" || an == "rowspan")) {
                    val v = av.trim()
                    if (v.isEmpty() || v == "1" || lenient) continue
                    return false // merged cells can't be represented
                }
                if (an == "class") {
                    for (cls in av.split(WS_RE).filter { it.isNotEmpty() }) {
                        if (cls !in SAFE_CLASSES && !LANG_CLASS.matches(cls)) { if (lenient) continue else return false }
                        if (cls == "check-list") taskList = true
                        if (cls == "check-box") checkBox = true
                        if (cls == "li-collapsed") collapsed = true
                    }
                    continue
                }
                if (an !in ALLOWED_ATTRS) { if (lenient) continue else return false }
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

            if (tbl != null) return tableOpen(name, attrSrc, align)

            when (name) {
                "hr" -> {
                    if (!lenient && (lists.isNotEmpty() || quoteDepth > 0 || preDepth > 0)) return false
                    if (preDepth > 0) return true
                    closePara(false)
                    addObject(Para(type = BlockType.HR))
                }
                "table" -> {
                    if (!lenient && (lists.isNotEmpty() || quoteDepth > 0 || preDepth > 0)) return false
                    if (preDepth > 0) return true
                    closePara(false)
                    tbl = TableBuilder()
                    elems.add(Elem("table", null, null))
                }
                "a" -> {
                    val id = if (href.isNullOrBlank()) 0 else LinkTable.idOf(href)
                    elems.add(Elem("a", null, null, link = id))
                }
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
                    if (preDepth > 0) {
                        elems.add(Elem("code", null, null))
                    } else {
                        counts[7]++
                        elems.add(Elem("code", null, null, inline = true))
                    }
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
                "figure" -> {
                    if (!lenient && (lists.isNotEmpty() || quoteDepth > 0 || preDepth > 0)) return false
                    closePara(false)
                    val dr = mediaDrawing ?: false
                    fig = FigBuilder(dr, figW, figX, figY)
                    elems.add(Elem("figure", null, null))
                }
                "img" -> {
                    if (preDepth > 0) return true
                    if (!lenient && (lists.isNotEmpty() || quoteDepth > 0)) return false
                    val s = imgSrc.trim()
                    if (!ImageData.isSupportedSrc(s)) return lenient // relative / odd sources: unsupported (strict) or dropped
                    val f = fig
                    if (f != null) {
                        f.src = s
                        f.alt = imgAlt
                        if (mediaDrawing != null) f.drawing = mediaDrawing!!
                    } else {
                        closePara(false)
                        val dr = mediaDrawing ?: ImageData.looksLikeDrawing(imgAlt)
                        addObject(Para(type = BlockType.IMAGE, image = makeImage(s, imgAlt, dr, figW, figX, figY)))
                    }
                }
                else -> return lenient // unknown tag: ignored in lenient mode, unsupported otherwise
            }
            return true
        }

        private class FigBuilder(var drawing: Boolean, val w: Int?, val x: Int, val y: Int) {
            var src = ""
            var alt = ""
        }

        private fun makeImage(src: String, alt: String, drawing: Boolean, w: Int?, x: Int, y: Int): ImageData =
            ImageData(src, alt.ifBlank { "image" }, drawing, (w ?: ImageData.defaultWidth(drawing)).coerceIn(ImageData.MIN_WIDTH, ImageData.MAX_WIDTH))
                .withOffset(x, y)

        private fun addObject(p: Para) {
            texts.add(OBJ.toString())
            markLists.add(IntArray(1))
            paras.add(p)
        }

        // ── tables ──

        private fun cellBreak() {
            if (cellOpen && curText.isNotEmpty() && curText.last() != '\n') { curText.append('\n'); curMarks.add(0) }
        }

        private fun beginCell() {
            endCell()
            cur = Para()
            curText.setLength(0)
            curMarks.clear()
            open = true
            cellOpen = true
        }

        private fun endCell() {
            val t = tbl ?: return
            if (!cellOpen) return
            val s = curText.toString()
            var a = 0
            var b = s.length
            while (a < b && s[a].isWhitespace()) a++
            while (b > a && s[b - 1].isWhitespace()) b--
            val marks = IntArray(b - a) { curMarks[a + it] }
            t.cur?.add(Cell(s.substring(a, b), marks))
            cellOpen = false
            open = false
            curText.setLength(0)
            curMarks.clear()
        }

        private fun endRow() {
            val t = tbl ?: return
            endCell()
            val row = t.cur ?: return
            if (row.isNotEmpty()) {
                if (t.rows.isEmpty()) t.headerFirst = t.inThead || t.curAllTh
                t.rows.add(row)
            }
            t.cur = null
        }

        private fun endTable() {
            val t = tbl ?: return
            endRow()
            tbl = null
            tableNest = 0
            open = false
            if (t.rows.isNotEmpty()) addObject(Para(type = BlockType.TABLE, table = TableData(t.rows, t.headerFirst)))
        }

        /** Tags seen while inside a `<table>`. */
        private fun tableOpen(name: String, attrSrc: String, align: Align?): Boolean {
            val t = tbl ?: return true
            if (tableNest > 0) {
                if (name in TABLE_TAGS) { if (name == "table") tableNest++; return true }
            }
            when (name) {
                "table" -> { if (lenient) { tableNest++; return true } else return false }
                "thead" -> { t.inThead = true; return true }
                "tbody", "tfoot", "colgroup", "col" -> return true
                "caption" -> return lenient
                "tr" -> {
                    endRow()
                    t.cur = ArrayList()
                    t.curAllTh = true
                    elems.add(Elem("tr", null, null))
                    return true
                }
                "td", "th" -> {
                    if (t.cur == null) { t.cur = ArrayList(); t.curAllTh = true }
                    if (name == "td") t.curAllTh = false
                    beginCell()
                    elems.add(Elem(name, null, null))
                    return true
                }
                "br" -> {
                    if (cellOpen) { curText.append('\n'); curMarks.add(0) }
                    return true
                }
                "p", "div" -> { cellBreak(); elems.add(Elem(name, null, null)); return true }
                "li", "h1", "h2", "h3", "h4", "h5", "h6", "blockquote", "pre", "ul", "ol", "hr" -> {
                    if (!lenient) return false
                    cellBreak()
                    elems.add(Elem(name, null, null))
                    return true
                }
                "strong", "b" -> { counts[0]++; elems.add(Elem("b", null, null)) }
                "em", "i" -> { counts[1]++; elems.add(Elem("i", null, null)) }
                "u" -> { counts[2]++; elems.add(Elem("u", null, null)) }
                "s", "strike", "del" -> { counts[3]++; elems.add(Elem("s", null, null)) }
                "sup" -> { counts[4]++; elems.add(Elem("sup", null, null)) }
                "sub" -> { counts[5]++; elems.add(Elem("sub", null, null)) }
                "mark" -> { counts[6]++; elems.add(Elem("mark", null, null)) }
                "code" -> { counts[7]++; elems.add(Elem("code", null, null, inline = true)) }
                "span", "label" -> elems.add(Elem(name, null, null))
                "a" -> {
                    val href = ATTR_RE.findAll(attrSrc).firstOrNull { it.groupValues[1].equals("href", true) }
                        ?.let { m -> decode(m.groupValues[2].ifEmpty { m.groupValues[3].ifEmpty { m.groupValues[4] } }) }
                    elems.add(Elem("a", null, null, link = if (href.isNullOrBlank()) 0 else LinkTable.idOf(href)))
                }
                "img" -> return lenient
                else -> return lenient
            }
            return true
        }

        private fun release(e: Elem) {
            when (e.name) {
                "b" -> counts[0] = maxOf(0, counts[0] - 1)
                "i" -> counts[1] = maxOf(0, counts[1] - 1)
                "u" -> counts[2] = maxOf(0, counts[2] - 1)
                "s" -> counts[3] = maxOf(0, counts[3] - 1)
                "sup" -> counts[4] = maxOf(0, counts[4] - 1)
                "sub" -> counts[5] = maxOf(0, counts[5] - 1)
                "mark" -> counts[6] = maxOf(0, counts[6] - 1)
                "code" -> if (e.inline) counts[7] = maxOf(0, counts[7] - 1)
            }
        }

        private fun closeTag(name: String): Boolean {
            var nm = name
            if (lenient) {
                if (nm in BLOCKISH) nm = "div"
                if (nm in INLINEISH) nm = "span"
                if (nm == "kbd") nm = "code"
            }
            val key = when (nm) {
                "strong" -> "b"
                "em" -> "i"
                "strike", "del" -> "s"
                else -> nm
            }
            if (key == "thead") tbl?.inThead = false
            if (tbl != null && tableNest > 0 && key in TABLE_TAGS) {
                if (key == "table") tableNest--
                return true
            }
            // pop up to the matching open element (tolerates sloppy nesting)
            val idx = elems.indexOfLast { it.name == key }
            if (idx == -1) return lenient || key in SUPPORTED_CLOSE
            while (elems.size > idx) release(elems.removeAt(elems.lastIndex))

            if (tbl != null) {
                when (key) {
                    "td", "th" -> endCell()
                    "tr" -> endRow()
                    "table" -> endTable()
                }
                return true
            }

            when (key) {
                "figure" -> {
                    val f = fig
                    fig = null
                    if (f != null && f.src.isNotEmpty()) {
                        closePara(false)
                        addObject(Para(type = BlockType.IMAGE, image = makeImage(f.src, f.alt, f.drawing, f.w, f.x, f.y)))
                    }
                }
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
            }
            return true
        }

        private val SUPPORTED_CLOSE = setOf(
            "p", "div", "li", "ul", "ol", "blockquote", "span", "label", "pre", "code", "b", "strong", "i", "em", "u", "s",
            "strike", "del", "sup", "sub", "mark", "h1", "h2", "h3", "h4", "h5", "h6", "br", "hr", "a", "figure",
            "table", "thead", "tbody", "tfoot", "tr", "td", "th", "colgroup", "col",
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
            if (counts[7] > 0) m = m or Mark.CODE
            return m
        }

        private fun currentLink(): Int = elems.lastOrNull { it.name == "a" }?.link ?: 0

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
            if (tbl != null && !cellOpen) {
                if (s.isNotBlank() && tableNest == 0 && !lenient) failed = true // loose text between cells
                return
            }
            if (!open) {
                if (s.isBlank()) return // whitespace between tags
                startPara()
            }
            if (curText.isEmpty()) s = s.trimStart()
            if (s.isEmpty()) return
            val m = Mark.withLink(currentMarks(), currentLink())
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
            if (p.type == BlockType.HR) {
                out.append("<hr>")
                i++
                continue
            }
            if (p.type == BlockType.TABLE) {
                p.table?.let { out.append(tableHtml(it)) }
                i++
                continue
            }
            if (p.type == BlockType.IMAGE) {
                p.image?.let { out.append(imageHtml(it)) }
                i++
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

    private fun inlineHtml(doc: RichDoc, para: Int): String =
        inlineHtml(doc.text, doc.marks, doc.starts[para], doc.paraEnd(para))

    /** Inline HTML of `text[s, e)`: links wrap whole runs, then one tag per active flag. Line breaks (table cells) become `<br>`. */
    private fun inlineHtml(text: String, marks: IntArray, s: Int, e: Int): String {
        if (s == e) return "<br>"
        val sb = StringBuilder()
        var a = s
        while (a < e) {
            val link = Mark.linkId(marks[a])
            var b = a + 1
            while (b < e && Mark.linkId(marks[b]) == link) b++
            val url = if (link != 0) LinkTable.urlOf(link) else null
            if (url != null) sb.append("<a href=\"").append(escapeAttr(url)).append("\" target=\"_blank\" rel=\"noreferrer\">")
            var c = a
            while (c < b) {
                var d = c + 1
                while (d < b && Mark.flags(marks[d]) == Mark.flags(marks[c])) d++
                val m = marks[c]
                val open = StringBuilder()
                val close = StringBuilder()
                for ((flag, tag) in HTML_TAGS) {
                    if (m and flag != 0) {
                        open.append('<').append(tag).append('>')
                        close.insert(0, "</$tag>")
                    }
                }
                sb.append(open).append(escape(text.substring(c, d)).replace("\n", "<br>")).append(close)
                c = d
            }
            if (url != null) sb.append("</a>")
            a = b
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
        Mark.CODE to "code",
    )

    private fun tableHtml(t: TableData): String {
        val sb = StringBuilder("<table>")
        val bodyFrom = if (t.header) 1 else 0
        for (r in 0 until t.rowCount) {
            val head = t.header && r == 0
            if (head) sb.append("<thead>") else if (r == bodyFrom) sb.append("<tbody>")
            sb.append("<tr>")
            val tag = if (head) "th" else "td"
            for (cell in t.rows[r]) {
                sb.append('<').append(tag).append('>')
                    .append(inlineHtml(cell.text, cell.marks, 0, cell.text.length))
                    .append("</").append(tag).append('>')
            }
            sb.append("</tr>")
            if (head) sb.append("</thead>")
        }
        if (t.rowCount > bodyFrom) sb.append("</tbody>")
        return sb.append("</table>").toString()
    }

    /** The web figure markup (`ensureMediaControls` adds its buttons at runtime, they're never saved). */
    private fun imageHtml(im: ImageData): String {
        val cls = if (im.drawing) "drawing-figure" else "photo-figure"
        val media = if (im.drawing) "drawing" else "image"
        val style = "width:${im.width}px" +
            (if (im.x > 0) ";margin-left:${im.x}px" else "") +
            (if (im.y > 0) ";margin-top:${im.y}px" else "")
        return "<figure class=\"editor-figure $cls\" contenteditable=\"false\" data-media=\"$media\" style=\"$style\">" +
            "<img src=\"${escapeAttr(im.src)}\" alt=\"${escapeAttr(im.alt)}\" class=\"editor-inline-image\" loading=\"lazy\"></figure>"
    }

    private fun escapeAttr(s: String): String = escape(s).replace("\"", "&quot;")

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

            if (p.type == BlockType.HR) {
                if (wrote) sb.append("\n\n")
                sb.append("---")
                wrote = true
                prevType = BlockType.HR
                continue
            }
            if (p.type == BlockType.IMAGE) {
                val im = p.image
                if (im != null) {
                    if (wrote) sb.append("\n\n")
                    sb.append("![").append(im.alt.replace("]", "\\]")).append("](").append(im.src).append(")")
                    wrote = true
                    prevType = BlockType.IMAGE
                }
                continue
            }
            if (p.type == BlockType.TABLE) {
                val tbl = p.table
                if (tbl != null) {
                    if (wrote) sb.append("\n\n")
                    sb.append(tableMarkdown(tbl))
                    wrote = true
                    prevType = BlockType.TABLE
                }
                continue
            }

            if (s == e) { prevType = null; continue } // blank lines aren't content in markdown

            val inline = StringBuilder(mdInline(doc.text, doc.marks, s, e))
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

    /** Inline markdown of `text[s, e)` (links outermost, inline code innermost). */
    internal fun mdInline(text: String, marks: IntArray, s: Int, e: Int): String {
        val sb = StringBuilder()
        var a = s
        while (a < e) {
            val link = Mark.linkId(marks[a])
            var b = a + 1
            while (b < e && Mark.linkId(marks[b]) == link) b++
            val url = if (link != 0) LinkTable.urlOf(link) else null
            val seg = StringBuilder()
            var c = a
            while (c < b) {
                var d = c + 1
                while (d < b && Mark.flags(marks[d]) == Mark.flags(marks[c])) d++
                val m = marks[c]
                var t = text.substring(c, d)
                if (m and Mark.CODE != 0) t = if ('`' in t) "`` $t ``" else "`$t`"
                if (m and Mark.STRIKE != 0) t = "~~$t~~"
                if (m and Mark.BOLD != 0) t = "**$t**"
                if (m and Mark.ITALIC != 0) t = "*$t*"
                if (m and Mark.UNDERLINE != 0) t = "<u>$t</u>"
                if (m and Mark.SUP != 0) t = "<sup>$t</sup>"
                if (m and Mark.SUB != 0) t = "<sub>$t</sub>"
                seg.append(t)
                c = d
            }
            if (url != null) sb.append('[').append(seg).append("](").append(url.replace(" ", "%20").replace(")", "%29")).append(')')
            else sb.append(seg)
            a = b
        }
        return sb.toString()
    }

    private fun tableMarkdown(t: TableData): String {
        fun cellMd(c: Cell): String =
            mdInline(c.text, c.marks, 0, c.text.length).replace("\n", "<br>").replace("|", "\\|")
        val lines = ArrayList<String>()
        for ((r, row) in t.rows.withIndex()) {
            lines.add("| " + row.joinToString(" | ") { cellMd(it) } + " |")
            if (r == 0) lines.add("| " + List(t.colCount) { "---" }.joinToString(" | ") + " |")
        }
        return lines.joinToString("\n")
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
