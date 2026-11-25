package com.inkwell.notes.editor

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Editor Part 2 — stored HTML ⇄ [Doc] (and the markdown mirror of a Doc).
 *
 * [parse] understands exactly what Part 2 can edit: p / div / h1-h3 / ul / ol / li / blockquote
 * (plus Chrome's indent form `<blockquote style="margin: 0 0 0 40px; border: none">`), and the inline
 * strong / em / u / s / sup / sub / mark. Anything else (tables, images, code, links, checklists …)
 * makes it return null — the editor then keeps the note read-only, so those notes are never flattened.
 */
object HtmlCodec {

    // ═════════════════════════════════════════════
    // Tiny HTML tree
    // ═════════════════════════════════════════════

    internal sealed class HNode
    internal class HText(val text: String) : HNode()
    internal class HEl(val tag: String, val attrs: Map<String, String>) : HNode() {
        val children = ArrayList<HNode>()
    }

    private val VOID = setOf("br", "hr", "img", "input", "meta", "link", "wbr", "col")
    private val ENTITY = Regex("&(#x[0-9a-fA-F]+|#[0-9]+|[a-zA-Z]+);")

    internal fun decodeEntities(s: String): String {
        if ('&' !in s) return s
        return ENTITY.replace(s) { m ->
            val v = m.groupValues[1]
            when {
                v.startsWith("#x") -> v.substring(2).toIntOrNull(16)?.let { cp(it) } ?: m.value
                v.startsWith("#") -> v.substring(1).toIntOrNull()?.let { cp(it) } ?: m.value
                else -> when (v) {
                    "amp" -> "&"
                    "lt" -> "<"
                    "gt" -> ">"
                    "quot" -> "\""
                    "apos" -> "'"
                    "nbsp" -> " "
                    else -> m.value
                }
            }
        }
    }

    private fun cp(code: Int): String =
        if (code in 0..0x10FFFF) String(Character.toChars(code)) else ""

    internal fun parseTree(html: String): List<HNode> {
        val root = HEl("#root", emptyMap())
        val stack = ArrayList<HEl>()
        stack.add(root)
        var i = 0
        val n = html.length
        val text = StringBuilder()

        fun flushText() {
            if (text.isNotEmpty()) {
                stack.last().children.add(HText(decodeEntities(text.toString())))
                text.setLength(0)
            }
        }

        while (i < n) {
            val c = html[i]
            if (c != '<') {
                text.append(c); i++; continue
            }
            if (html.startsWith("<!--", i)) {
                val end = html.indexOf("-->", i + 4)
                i = if (end < 0) n else end + 3
                continue
            }
            if (i + 1 < n && html[i + 1] == '/') {
                val end = html.indexOf('>', i)
                if (end < 0) { text.append(html.substring(i)); i = n; continue }
                flushText()
                val name = html.substring(i + 2, end).trim().lowercase()
                val idx = stack.indexOfLast { it.tag == name }
                if (idx > 0) while (stack.size > idx) stack.removeAt(stack.size - 1)
                i = end + 1
                continue
            }
            if (i + 1 < n && (html[i + 1].isLetter())) {
                var j = i + 1
                while (j < n && (html[j].isLetterOrDigit() || html[j] == '-')) j++
                val name = html.substring(i + 1, j).lowercase()
                val attrs = LinkedHashMap<String, String>()
                var selfClose = false
                while (j < n && html[j] != '>') {
                    val ch = html[j]
                    if (ch.isWhitespace()) { j++; continue }
                    if (ch == '/') { selfClose = true; j++; continue }
                    var k = j
                    while (k < n && !html[k].isWhitespace() && html[k] != '=' && html[k] != '>' && html[k] != '/') k++
                    val key = html.substring(j, k).lowercase()
                    j = k
                    while (j < n && html[j].isWhitespace()) j++
                    var value = ""
                    if (j < n && html[j] == '=') {
                        j++
                        while (j < n && html[j].isWhitespace()) j++
                        if (j < n && (html[j] == '"' || html[j] == '\'')) {
                            val q = html[j]
                            val e = html.indexOf(q, j + 1)
                            val end = if (e < 0) n else e
                            value = html.substring(j + 1, end)
                            j = if (e < 0) n else e + 1
                        } else {
                            var e = j
                            while (e < n && !html[e].isWhitespace() && html[e] != '>') e++
                            value = html.substring(j, e)
                            j = e
                        }
                    }
                    if (key.isNotEmpty()) attrs[key] = decodeEntities(value)
                }
                if (j < n) j++ // '>'
                flushText()
                // Implicit closes: <p> inside <p>, <li> inside <li>.
                if (name == "p" && stack.last().tag == "p") stack.removeAt(stack.size - 1)
                if (name == "li" && stack.last().tag == "li") stack.removeAt(stack.size - 1)
                val el = HEl(name, attrs)
                stack.last().children.add(el)
                if (name !in VOID && !selfClose) stack.add(el)
                i = j
                continue
            }
            text.append(c); i++
        }
        flushText()
        return root.children
    }

    // ═════════════════════════════════════════════
    // HTML → Doc
    // ═════════════════════════════════════════════

    /** Null when the HTML uses anything this editor can't write back losslessly. */
    fun parse(html: String): Doc? {
        if (html.isBlank()) return Doc.EMPTY
        val imp = Importer()
        imp.walkContainer(parseTree(html), Ctx(0, Align.LEFT, false))
        if (imp.failed) return null
        if (imp.blocks.isEmpty()) return Doc.EMPTY
        return Doc.of(imp.sb.toString(), imp.blocks, imp.spans)
    }

    private data class Ctx(val indent: Int, val align: Align, val quote: Boolean) {
        val paraType: BlockType get() = if (quote) BlockType.QUOTE else BlockType.P
    }

    private val BLOCK_TAGS = setOf(
        "p", "div", "center", "h1", "h2", "h3", "h4", "h5", "h6", "blockquote", "ul", "ol", "li",
        "table", "pre", "figure", "hr", "details", "section", "article", "header", "footer",
    )

    private fun parseStyle(el: HEl): Map<String, String> {
        val raw = el.attrs["style"] ?: return emptyMap()
        val out = HashMap<String, String>()
        for (part in raw.split(';')) {
            val k = part.substringBefore(':', "").trim().lowercase()
            val v = part.substringAfter(':', "").trim().lowercase()
            if (k.isNotEmpty()) out[k] = v
        }
        return out
    }

    private fun alignOf(el: HEl): Align? {
        val a = (parseStyle(el)["text-align"] ?: el.attrs["align"] ?: "").trim().lowercase()
        return when (a) {
            "center" -> Align.CENTER
            "right", "end" -> Align.RIGHT
            "left", "start" -> Align.LEFT
            else -> null
        }
    }

    /** `margin-left: 40px` / `margin: 0 0 0 40px` / `padding-left` → indent levels (40 px each). */
    private fun styleIndent(el: HEl): Int {
        val st = parseStyle(el)
        val px = (st["margin-left"] ?: st["padding-left"]
            ?: st["margin"]?.split(Regex("\\s+"))?.let { if (it.size == 4) it[3] else null })
            ?.removeSuffix("px")?.trim()?.toFloatOrNull() ?: return 0
        return if (px <= 0f) 0 else max(1, (px / 40f).roundToInt())
    }

    private fun isIndentQuote(el: HEl): Boolean {
        val st = parseStyle(el)
        return st["border"] == "none" || st.containsKey("margin-left") ||
            (st["margin"]?.contains("40px") == true)
    }

    private class Importer {
        val sb = StringBuilder()
        val blocks = ArrayList<BlockFmt>()
        val spans = ArrayList<Span>()
        var failed = false
        private var cur = BlockFmt()

        fun startLine(fmt: BlockFmt) {
            if (blocks.isNotEmpty()) sb.append('\n')
            blocks.add(fmt)
            cur = fmt
        }

        private fun clean(t: String): String =
            t.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ').replace('\u00A0', ' ')

        private fun appendText(t: String, kinds: Set<SpanKind>) {
            if (t.isEmpty()) return
            val s = sb.length
            sb.append(t)
            for (k in kinds) spans.add(Span(k, s, sb.length))
        }

        private fun hasBlockChild(el: HEl): Boolean =
            el.children.any { it is HEl && it.tag in BLOCK_TAGS }

        /** Children of a block-level container: stray inline runs become implicit paragraphs. */
        fun walkContainer(nodes: List<HNode>, ctx: Ctx) {
            var implicitOpen = false
            for (n in nodes) {
                if (failed) return
                when {
                    n is HText -> {
                        val t = clean(n.text)
                        if (!implicitOpen) {
                            if (t.isBlank()) continue
                            startLine(BlockFmt(ctx.paraType, ctx.align, ctx.indent))
                            implicitOpen = true
                        }
                        appendText(t, emptySet())
                    }
                    n is HEl && n.tag in BLOCK_TAGS -> {
                        implicitOpen = false
                        walkBlock(n, ctx)
                    }
                    n is HEl && n.tag == "br" -> {
                        if (implicitOpen) implicitOpen = false
                        else startLine(BlockFmt(ctx.paraType, ctx.align, ctx.indent))
                    }
                    n is HEl -> {
                        if (!implicitOpen) {
                            startLine(BlockFmt(ctx.paraType, ctx.align, ctx.indent))
                            implicitOpen = true
                        }
                        inline(n, emptySet(), isLast = false)
                    }
                }
            }
        }

        private fun walkBlock(el: HEl, ctx: Ctx) {
            when (el.tag) {
                "p", "div", "center" -> {
                    val align = alignOf(el) ?: (if (el.tag == "center") Align.CENTER else ctx.align)
                    val c = ctx.copy(align = align, indent = ctx.indent + styleIndent(el))
                    if (hasBlockChild(el)) walkContainer(el.children, c) else leaf(el, c, c.paraType)
                }
                "h1", "h2", "h3" -> {
                    val c = ctx.copy(align = alignOf(el) ?: ctx.align, indent = ctx.indent + styleIndent(el))
                    leaf(el, c, when (el.tag) { "h1" -> BlockType.H1; "h2" -> BlockType.H2; else -> BlockType.H3 })
                }
                "blockquote" -> {
                    if (isIndentQuote(el)) {
                        walkContainer(el.children, ctx.copy(indent = ctx.indent + max(1, styleIndent(el))))
                    } else {
                        val c = ctx.copy(quote = true, align = alignOf(el) ?: ctx.align)
                        if (hasBlockChild(el)) walkContainer(el.children, c) else leaf(el, c, BlockType.QUOTE)
                    }
                }
                "ul", "ol" -> walkList(el, ctx, 0)
                else -> failed = true // table, pre, figure, hr, h4-h6, details …
            }
        }

        private fun leaf(el: HEl, ctx: Ctx, type: BlockType) {
            startLine(BlockFmt(type, ctx.align, ctx.indent))
            val ch = el.children
            for ((i, c) in ch.withIndex()) inline(c, emptySet(), isLast = i == ch.lastIndex)
        }

        private fun walkList(el: HEl, ctx: Ctx, depth: Int) {
            val isChecklist = el.attrs["class"]?.contains("check") == true || el.attrs["data-type"] == "taskList"
            if (isChecklist) { failed = true; return }
            val type = if (el.tag == "ol") BlockType.OL else BlockType.UL
            for (li in el.children) {
                if (failed) return
                if (li is HText) {
                    if (li.text.isBlank()) continue
                    failed = true; return
                }
                li as HEl
                if (li.tag != "li") { failed = true; return }
                if (li.attrs.containsKey("data-checked") || li.attrs["class"]?.contains("check") == true) {
                    failed = true; return
                }
                val fmt = BlockFmt(type, alignOf(li) ?: ctx.align, min(ctx.indent + depth, DocOps.MAX_INDENT))
                startLine(fmt)
                var lineStarted = true
                var lineStartPos = sb.length
                for ((idx, ch) in li.children.withIndex()) {
                    val last = idx == li.children.lastIndex
                    when {
                        ch is HEl && (ch.tag == "ul" || ch.tag == "ol") -> {
                            walkList(ch, ctx, depth + 1)
                            lineStarted = false
                            lineStartPos = sb.length
                        }
                        ch is HEl && (ch.tag == "p" || ch.tag == "div") -> {
                            if (!lineStarted) { startLine(fmt); lineStarted = true; lineStartPos = sb.length }
                            if (hasBlockChild(ch)) { failed = true; return }
                            for ((i2, c2) in ch.children.withIndex()) inline(c2, emptySet(), i2 == ch.children.lastIndex)
                        }
                        ch is HEl && ch.tag == "br" -> {
                            // `<li><br><ul>…` is just the placeholder of an empty item; only a <br> after
                            // real text, followed by more inline content, is a soft line break.
                            val onlyListsAfter = li.children.drop(idx + 1).all { it is HEl && (it.tag == "ul" || it.tag == "ol") }
                            if (lineStarted && sb.length > lineStartPos && !onlyListsAfter && !last) {
                                startLine(fmt); lineStartPos = sb.length
                            }
                        }
                        ch is HText && !lineStarted && ch.text.isBlank() -> Unit
                        else -> {
                            if (!lineStarted) { startLine(fmt); lineStarted = true; lineStartPos = sb.length }
                            inline(ch, emptySet(), last)
                        }
                    }
                }
            }
        }

        private fun transparent(v: String): Boolean =
            v.isEmpty() || v == "transparent" || v == "none" || v == "initial" || v == "inherit" ||
                v.replace(" ", "") == "rgba(0,0,0,0)"

        private fun inline(node: HNode, kinds: Set<SpanKind>, isLast: Boolean) {
            if (failed) return
            if (node is HText) {
                appendText(clean(node.text), kinds)
                return
            }
            node as HEl
            var k = kinds
            when (node.tag) {
                "br" -> {
                    if (!isLast) startLine(cur)
                    return
                }
                "strong", "b" -> k = k + SpanKind.BOLD
                "em", "i" -> k = k + SpanKind.ITALIC
                "u" -> k = k + SpanKind.UNDERLINE
                "s", "strike", "del" -> k = k + SpanKind.STRIKE
                "sup" -> k = k + SpanKind.SUP
                "sub" -> k = k + SpanKind.SUB
                "mark" -> k = k + SpanKind.HIGHLIGHT
                "span", "font", "small" -> {
                    val st = parseStyle(node)
                    val w = st["font-weight"]
                    if (w == "bold" || w == "bolder" || (w?.toIntOrNull() ?: 0) >= 600) k = k + SpanKind.BOLD
                    if (st["font-style"] == "italic") k = k + SpanKind.ITALIC
                    val deco = (st["text-decoration"] ?: st["text-decoration-line"]).orEmpty()
                    if ("underline" in deco) k = k + SpanKind.UNDERLINE
                    if ("line-through" in deco) k = k + SpanKind.STRIKE
                    val bg = st["background-color"] ?: st["background"]
                    if (bg != null && !transparent(bg)) k = k + SpanKind.HIGHLIGHT
                }
                else -> { failed = true; return }
            }
            val ch = node.children
            for ((i, c) in ch.withIndex()) inline(c, k, isLast && i == ch.lastIndex)
        }
    }

    // ═════════════════════════════════════════════
    // Doc → HTML
    // ═════════════════════════════════════════════

    private val KIND_ORDER = listOf(
        SpanKind.BOLD to "strong",
        SpanKind.ITALIC to "em",
        SpanKind.UNDERLINE to "u",
        SpanKind.STRIKE to "s",
        SpanKind.SUP to "sup",
        SpanKind.SUB to "sub",
        SpanKind.HIGHLIGHT to "mark",
    )

    internal fun esc(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun runs(doc: Doc, ls: Int, le: Int): List<Triple<Int, Int, Set<SpanKind>>> {
        if (le <= ls) return emptyList()
        val cuts = sortedSetOf(ls, le)
        for (sp in doc.spans) {
            if (sp.end <= ls || sp.start >= le) continue
            cuts.add(max(sp.start, ls)); cuts.add(min(sp.end, le))
        }
        val list = cuts.toList()
        val out = ArrayList<Triple<Int, Int, Set<SpanKind>>>()
        for (i in 0 until list.size - 1) {
            val x = list[i]
            val y = list[i + 1]
            val kinds = doc.spans.filter { it.start <= x && it.end >= y }.map { it.kind }.toSet()
            out.add(Triple(x, y, kinds))
        }
        return out
    }

    private fun inlineHtml(doc: Doc, ls: Int, le: Int): String {
        val sb = StringBuilder()
        for ((x, y, kinds) in runs(doc, ls, le)) {
            val tags = KIND_ORDER.filter { it.first in kinds }.map { it.second }
            tags.forEach { sb.append('<').append(it).append('>') }
            sb.append(esc(doc.text.substring(x, y)))
            tags.asReversed().forEach { sb.append("</").append(it).append('>') }
        }
        return sb.toString()
    }

    private fun alignAttr(a: Align): String = when (a) {
        Align.LEFT -> ""
        Align.CENTER -> " style=\"text-align: center;\""
        Align.RIGHT -> " style=\"text-align: right;\""
    }

    private fun listTag(t: BlockType) = if (t == BlockType.OL) "ol" else "ul"

    private const val INDENT_OPEN = "<blockquote style=\"margin: 0 0 0 40px; border: none; padding: 0px;\">"

    fun toHtml(doc: Doc): String {
        if (doc.text.isEmpty() && doc.blocks.size == 1 && doc.blocks[0] == BlockFmt()) return ""
        val sb = StringBuilder()
        val open = ArrayList<BlockType>()
        fun closeAll() {
            while (open.isNotEmpty()) {
                sb.append("</li></").append(listTag(open.last())).append('>')
                open.removeAt(open.size - 1)
            }
        }
        for (i in 0 until doc.lineCount) {
            val f = doc.blocks[i]
            val inner = inlineHtml(doc, doc.lineStarts[i], doc.lineEnd(i)).ifEmpty { "<br>" }
            if (f.type.isList) {
                val d = min(f.indent, open.size)
                while (open.size > d + 1) {
                    sb.append("</li></").append(listTag(open.last())).append('>')
                    open.removeAt(open.size - 1)
                }
                if (open.size == d + 1) {
                    sb.append("</li>")
                    if (open[d] != f.type) {
                        sb.append("</").append(listTag(open[d])).append('>')
                        open[d] = f.type
                        sb.append('<').append(listTag(f.type)).append('>')
                    }
                } else {
                    sb.append('<').append(listTag(f.type)).append('>')
                    open.add(f.type)
                }
                sb.append("<li").append(alignAttr(f.align)).append('>').append(inner)
            } else {
                closeAll()
                val tag = when (f.type) {
                    BlockType.H1 -> "h1"
                    BlockType.H2 -> "h2"
                    BlockType.H3 -> "h3"
                    BlockType.QUOTE -> "blockquote"
                    else -> "p"
                }
                repeat(f.indent) { sb.append(INDENT_OPEN) }
                sb.append('<').append(tag).append(alignAttr(f.align)).append('>').append(inner)
                    .append("</").append(tag).append('>')
                repeat(f.indent) { sb.append("</blockquote>") }
            }
        }
        closeAll()
        return sb.toString()
    }

    // ═════════════════════════════════════════════
    // Doc → markdown (what the web's htmlToMarkdown would write)
    // ═════════════════════════════════════════════

    private fun mdInline(doc: Doc, ls: Int, le: Int): String {
        val sb = StringBuilder()
        for ((x, y, kinds) in runs(doc, ls, le)) {
            val raw = doc.text.substring(x, y)
            val core = raw.trim()
            if (core.isEmpty()) { sb.append(raw); continue }
            val lead = raw.substring(0, raw.indexOf(core))
            val trail = raw.substring(lead.length + core.length)
            var t = core
            if (SpanKind.UNDERLINE in kinds) t = "<u>$t</u>"
            if (SpanKind.STRIKE in kinds) t = "~~$t~~"
            if (SpanKind.ITALIC in kinds) t = "*$t*"
            if (SpanKind.BOLD in kinds) t = "**$t**"
            sb.append(lead).append(t).append(trail)
        }
        return sb.toString()
    }

    fun toMarkdown(doc: Doc): String {
        val out = StringBuilder()
        var prevType: BlockType? = null
        val counters = IntArray(DocOps.MAX_INDENT + 2)
        var first = true
        for (i in 0 until doc.lineCount) {
            val f = doc.blocks[i]
            val body = mdInline(doc, doc.lineStarts[i], doc.lineEnd(i))
            if (body.isBlank()) { prevType = null; counters.fill(0); continue }
            val isList = f.type.isList
            val line = when (f.type) {
                BlockType.H1 -> "# $body"
                BlockType.H2 -> "## $body"
                BlockType.H3 -> "### $body"
                BlockType.QUOTE -> "> $body"
                BlockType.UL -> "  ".repeat(f.indent) + "- $body"
                BlockType.OL -> {
                    val d = f.indent.coerceIn(0, counters.lastIndex)
                    for (k in d + 1..counters.lastIndex) counters[k] = 0
                    counters[d]++
                    "  ".repeat(f.indent) + "${counters[d]}. $body"
                }
                BlockType.P -> body
            }
            if (!isList) counters.fill(0)
            if (!first) out.append(if (isList && prevType == f.type) "\n" else "\n\n")
            out.append(line)
            prevType = f.type
            first = false
        }
        return out.toString()
    }
}
