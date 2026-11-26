package com.inkwell.notes.editor

/**
 * Paste handling (Part 5). A text field only hands us the pasted TEXT, so everything is decided from that:
 *
 *  - HTML source (`<div>…`, `<table>…`)   → sanitised, then read with [HtmlCodec]
 *  - markdown (`# `, `- `, `|---|`, ``` …) → [MarkdownCodec]
 *  - code (≥ 3 lines that read like code) → one code block
 *  - anything else                        → null: the text field's own plain insertion is kept
 *
 * Same detection rules as the web `looksLikeMarkdown` / `looksLikeHtmlSource`, plus the code check.
 */
object PasteCodec {
    enum class Kind { HTML, MARKDOWN, CODE }

    /** [title] = what the web would suggest as the note title (first `# Heading`), or null. */
    class Result(val kind: Kind, val doc: RichDoc, val title: String?)

    private val HTML_SOURCE = Regex(
        "</?(div|p|h[1-6]|img|a|br|ul|ol|li|table|thead|tbody|tr|th|td|blockquote|pre|code|strong|b|em|i|u|s|span|center|hr|mark|kbd|details|summary)\\b[^>]*>",
        RegexOption.IGNORE_CASE,
    )
    private val MARKDOWN = Regex(
        "(^|\\n)[ ]{0,3}(#{1,6}\\s+|[-*+]\\s+|\\d+[.)]\\s+|>\\s?|```|~~~|[-*+]\\s+\\[[ xX]]\\s+)" +
            "|\\|\\s*:?-{3,}:?\\s*\\|" +
            "|(!?\\[[^\\]]+]\\(https?://[^\\s)]+\\)|`[^`\\n]+`|\\*\\*[^*]+\\*\\*|__[^_]+__)",
    )
    private val CODE_LINE = Regex(
        "(;|\\{|\\}|=>|->|==|!=|&&|\\|\\||::|\\(\\)|\\[\\])\\s*$" +
            "|(=>|->|==|!=|&&|\\|\\||::|\\w\\(.*\\))" +
            "|^(def|fun|val|var|let|const|class|import|from|package|public|private|protected|static|return|void|if|for|while|else|elif|try|catch|#include|using|namespace|async|await|SELECT|INSERT|UPDATE|CREATE)\\b" +
            "|^(//|/\\*|\\*/|\\*\\s|<\\?php|@\\w+|\\$ )",
    )
    private val SYMBOLS = Regex("[(){}\\[\\]=;:<>]")
    private val PROSE_MD = Regex("\\*\\*[^*]+\\*\\*|]\\(https?://|^[-*+]\\s+\\S|^\\d+[.)]\\s+\\S|^>\\s")

    fun looksLikeHtmlSource(text: String): Boolean = text.isNotBlank() && HTML_SOURCE.containsMatchIn(text)

    fun looksLikeMarkdown(text: String): Boolean = text.isNotBlank() && MARKDOWN.containsMatchIn(text)

    /** Conservative: needs ≥ 3 non-empty lines, half of them code-shaped and few markdown-prose lines. */
    fun looksLikeCode(text: String): Boolean {
        if (text.contains("```") || text.contains("~~~")) return false
        val ls = text.lines().filter { it.isNotBlank() }
        if (ls.size < 3) return false
        var code = 0
        var prose = 0
        for (l in ls) {
            val t = l.trim()
            val indented = (l.startsWith("  ") || l.startsWith("\t")) && SYMBOLS.containsMatchIn(t)
            if (indented || CODE_LINE.containsMatchIn(t)) code++
            if (PROSE_MD.containsMatchIn(t)) prose++
        }
        return code * 2 >= ls.size && prose * 10 < ls.size * 3
    }

    /** Decides what a pasted [text] should become, or null to leave it as plain text. */
    fun analyze(text: String): Result? {
        if (text.isBlank()) return null
        val norm = text.replace("\r\n", "\n").replace('\r', '\n')

        if (looksLikeHtmlSource(norm)) {
            val doc = HtmlCodec.parse(sanitizeHtml(norm)) ?: MarkdownCodec.parse(norm)
            return Result(Kind.HTML, doc, extractTitle(norm))
        }
        if (looksLikeCode(norm)) {
            val ls = norm.trimEnd('\n').split('\n').map { Line(it, IntArray(it.length), Para(type = BlockType.CODE)) }
            return Result(Kind.CODE, RichDoc.fromLines(ls), null)
        }
        if (looksLikeMarkdown(norm)) {
            return Result(Kind.MARKDOWN, MarkdownCodec.parse(norm), extractTitle(norm))
        }
        return null
    }

    /** First `# Heading` (or `<h1>`) of a pasted source, cleaned like the web `extractTitleFromMarkdownSource`. */
    fun extractTitle(source: String): String? {
        val h1 = Regex("^\\s{0,3}#\\s+(.+?)\\s*#*\\s*$", RegexOption.MULTILINE).find(source)?.groupValues?.get(1)
        val html = Regex("<h1\\b[^>]*>([\\s\\S]*?)</h1>", RegexOption.IGNORE_CASE).find(source)?.groupValues?.get(1)
        val cand = h1 ?: html ?: return null
        val cleaned = cand
            .replace(Regex("<[^>]+>"), " ")
            .replace(Regex("!\\[[^]]*]\\([^)]*\\)"), "")
            .replace(Regex("\\[([^]]+)]\\([^)]*\\)"), "$1")
            .replace(Regex("[`*_~>#]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(80)
        return cleaned.ifEmpty { null }
    }

    /** True for the "Untitled" / "New Note" / empty titles the web treats as not yet named. */
    fun isDefaultTitle(title: String): Boolean {
        val t = title.trim()
        return t.isEmpty() || t.equals("untitled", ignoreCase = true) || t.equals("new note", ignoreCase = true)
    }

    // ─── HTML sanitiser ──────────────────────

    private val DROP_WITH_CONTENT = setOf(
        "script", "style", "iframe", "object", "embed", "form", "textarea", "select", "svg", "head", "title", "noscript",
        "template", "button", "input",
    )
    private val ALLOWED = setOf(
        "p", "div", "br", "h1", "h2", "h3", "h4", "h5", "h6", "ul", "ol", "li", "blockquote", "pre", "code",
        "strong", "b", "em", "i", "u", "s", "strike", "del", "sup", "sub", "mark", "span", "label", "a", "hr",
        "table", "thead", "tbody", "tfoot", "tr", "th", "td",
    )
    private val TABLE_TAGS = setOf("table", "thead", "tbody", "tfoot", "tr", "th", "td")
    private val VOID = setOf("br", "hr")
    private val SAFE_CLASS = setOf("check-list", "check-box", "check-text", "md-table-wrap")
    private val KEEP_ATTRS = setOf("data-checked", "data-collapsed", "data-type", "align")
    private val ATTR_RE = Regex("""([a-zA-Z_:][-a-zA-Z0-9_:.]*)\s*(?:=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'>]+)))?""")
    private val SAFE_HREF = Regex("^(https?:|mailto:|tel:)", RegexOption.IGNORE_CASE)

    /**
     * Keeps only tags the editor understands and the few attributes that matter (text-align / margin-left,
     * http(s) / mailto / tel links, checklist + collapse flags). Scripts, styles, frames, forms and event handlers go;
     * unknown tags are unwrapped so their text survives. Inside a table every tag except the table structure is
     * unwrapped, because table cells hold plain text.
     */
    fun sanitizeHtml(html: String): String {
        val out = StringBuilder()
        var pos = 0
        val n = html.length
        var skip: String? = null
        var tableDepth = 0
        var droppedLinks = 0
        while (pos < n) {
            val lt = html.indexOf('<', pos)
            if (lt == -1) { if (skip == null) out.append(html, pos, n); break }
            if (skip == null && lt > pos) out.append(html, pos, lt)
            if (html.startsWith("<!--", lt)) {
                val e = html.indexOf("-->", lt + 4)
                pos = if (e == -1) n else e + 3
                continue
            }
            val gt = tagEnd(html, lt)
            if (gt == -1) { if (skip == null) out.append(html.substring(lt)); break }
            val raw = html.substring(lt + 1, gt)
            pos = gt + 1
            if (raw.startsWith("!") || raw.startsWith("?")) continue

            val closing = raw.startsWith("/")
            var body = (if (closing) raw.substring(1) else raw).trim()
            val selfClosing = body.endsWith("/")
            if (selfClosing) body = body.dropLast(1).trim()
            val nameEnd = body.indexOfFirst { it.isWhitespace() }
            val name = (if (nameEnd == -1) body else body.substring(0, nameEnd)).lowercase()
            val attrs = if (nameEnd == -1) "" else body.substring(nameEnd)
            if (name.isEmpty()) continue

            if (skip != null) { if (closing && name == skip) skip = null; continue }
            if (!closing && name in DROP_WITH_CONTENT) { if (!selfClosing) skip = name; continue }
            if (closing && name in DROP_WITH_CONTENT) continue

            if (name == "img") {
                if (!closing) {
                    val alt = ATTR_RE.findAll(attrs).firstOrNull { it.groupValues[1].equals("alt", true) }
                        ?.let { it.groupValues[2].ifEmpty { it.groupValues[3].ifEmpty { it.groupValues[4] } } }.orEmpty()
                    if (alt.isNotBlank()) out.append(escape(alt))
                }
                continue
            }
            if (name == "br" && tableDepth > 0) { out.append(' '); continue }
            if (name == "table") {
                if (closing) { if (tableDepth > 0) { tableDepth--; out.append("</table>") } } else { tableDepth++; out.append(openTag(name, attrs, selfClosing)) }
                continue
            }
            if (tableDepth > 0 && name !in TABLE_TAGS) {
                // plain text only inside table cells; a wrapper <div> around the whole table is handled before tableDepth rises
                continue
            }
            val mapped = if (name == "kbd") "code" else name
            if (mapped !in ALLOWED) continue

            if (closing) {
                if (mapped == "a" && droppedLinks > 0) { droppedLinks--; continue }
                if (mapped in VOID) continue
                out.append("</").append(mapped).append('>')
                continue
            }
            if (mapped == "a") {
                val href = ATTR_RE.findAll(attrs).firstOrNull { it.groupValues[1].equals("href", true) }
                    ?.let { it.groupValues[2].ifEmpty { it.groupValues[3].ifEmpty { it.groupValues[4] } } }.orEmpty().trim()
                if (!SAFE_HREF.containsMatchIn(href)) { droppedLinks++; continue }
            }
            out.append(openTag(mapped, attrs, selfClosing))
        }
        return out.toString()
    }

    private fun openTag(name: String, attrSrc: String, selfClosing: Boolean): String {
        val sb = StringBuilder("<").append(name)
        for (m in ATTR_RE.findAll(attrSrc)) {
            val an = m.groupValues[1].lowercase()
            val av = m.groupValues[2].ifEmpty { m.groupValues[3].ifEmpty { m.groupValues[4] } }
            when {
                an == "style" -> {
                    val keep = av.split(';').mapNotNull { decl ->
                        val kv = decl.split(':', limit = 2)
                        if (kv.size != 2) return@mapNotNull null
                        val k = kv[0].trim().lowercase()
                        if (k == "text-align" || k == "margin-left") "$k:${kv[1].trim()}" else null
                    }
                    if (keep.isNotEmpty()) sb.append(" style=\"").append(escapeAttr(keep.joinToString(";"))).append('"')
                }
                an == "class" -> {
                    val keep = av.split(Regex("\\s+")).filter { it in SAFE_CLASS }
                    if (keep.isNotEmpty()) sb.append(" class=\"").append(keep.joinToString(" ")).append('"')
                }
                an == "href" && name == "a" -> sb.append(" href=\"").append(escapeAttr(av.trim())).append('"')
                an in KEEP_ATTRS -> sb.append(' ').append(an).append("=\"").append(escapeAttr(av)).append('"')
            }
        }
        sb.append('>')
        return sb.toString()
    }

    private fun tagEnd(src: String, lt: Int): Int {
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

    private fun escape(s: String): String = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    private fun escapeAttr(s: String): String = escape(s).replace("\"", "&quot;")
}
