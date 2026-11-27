package com.inkwell.notes.editor

/**
 * File contents for "Download note" (web `utils/exportNote.js`): plain text, Markdown, a standalone HTML page
 * and an Excel-readable `.xls` table. Pure string work; the PDF is drawn by `util/PdfExport`.
 */
object NoteExport {
    /** Title → safe file name stem (same rule as the web: no `\/:*?"<>|`, 80 chars max). */
    fun safeFileName(name: String): String {
        val cleaned = name.trim()
            .replace(Regex("[\\\\/:*?\"<>|]+"), "-")
            .replace(Regex("\\s+"), " ")
            .take(80)
            .trim()
        return cleaned.ifEmpty { "note" }
    }

    /** The document as plain lines: list markers kept, tables as `a | b` rows, dividers / pictures as short placeholders. */
    fun plainText(doc: RichDoc): String {
        val out = StringBuilder()
        val counters = IntArray(32)
        for (k in 0 until doc.paraCount) {
            val p = doc.paras[k]
            val text = doc.text.substring(doc.starts[k], doc.paraEnd(k))
            if (!p.type.isList) java.util.Arrays.fill(counters, 0)
            val line = when (p.type) {
                BlockType.HR -> "----------"
                BlockType.IMAGE -> "[Image: ${p.image?.alt ?: "image"}]"
                BlockType.TABLE -> p.table?.rows?.joinToString("\n") { row -> row.joinToString(" | ") { it.text } }.orEmpty()
                BlockType.UL -> "  ".repeat(p.indent) + "• " + text
                BlockType.TASK -> "  ".repeat(p.indent) + (if (p.checked) "[x] " else "[ ] ") + text
                BlockType.OL -> {
                    val level = p.indent.coerceIn(0, counters.size - 1)
                    for (i in level + 1 until counters.size) counters[i] = 0
                    counters[level]++
                    "  ".repeat(p.indent) + counters[level] + ". " + text
                }
                else -> {
                    if (p.type.isList) "" else text
                }
            }
            if (p.type == BlockType.UL || p.type == BlockType.TASK) {
                val level = p.indent.coerceIn(0, counters.size - 1)
                for (i in level + 1 until counters.size) counters[i] = 0
            }
            out.append(line).append('\n')
        }
        return out.toString().replace(Regex("\n{3,}"), "\n\n").trim()
    }

    /** `.txt`: the title, a rule under it, then the plain text. */
    fun text(title: String, plain: String): String =
        "$title\n${"─".repeat(minOf(title.length, 60))}\n\n$plain\n"

    /** `.md`: `# Title` then the markdown body. */
    fun markdown(title: String, md: String): String = "# $title\n\n${md.trim()}\n"

    /** `.html`: a standalone, readable page around the note's HTML. */
    fun html(title: String, bodyHtml: String): String =
        "<!doctype html>\n<html lang=\"en\"><head><meta charset=\"utf-8\">" +
            "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">" +
            "<title>${esc(title)}</title><style>" +
            "body{font-family:system-ui,-apple-system,Segoe UI,sans-serif;max-width:720px;margin:2rem auto;padding:0 1rem;line-height:1.72;color:#222}" +
            "h1,h2,h3{line-height:1.3}pre{background:#1c1a17;color:#d9f7c7;padding:12px 14px;border-radius:10px;overflow:auto;white-space:pre-wrap}" +
            "code{background:#eee;padding:1px 5px;border-radius:5px}pre code{background:none;padding:0}" +
            "img{max-width:100%;height:auto;border-radius:10px}table{border-collapse:collapse;max-width:100%}" +
            "td,th{border:1px solid #ccc;padding:6px 10px}th{background:#f4f4f4}" +
            "blockquote{border-left:3px solid #ccc;margin-left:0;padding-left:12px;color:#555}mark{background:#fde68a}" +
            "</style></head><body>\n<h1>${esc(title)}</h1>\n$bodyHtml\n</body></html>\n"

    /** `.xls`: an HTML table Excel opens (web `xls` export). */
    fun xls(title: String, updatedAt: String, words: Int, plain: String): String =
        "<!doctype html><html><head><meta charset=\"utf-8\" /></head><body><table border=\"1\">" +
            "<tr><th>Title</th><td>${esc(title)}</td></tr>" +
            "<tr><th>Updated</th><td>${esc(updatedAt)}</td></tr>" +
            "<tr><th>Words</th><td>$words</td></tr>" +
            "<tr><th>Content</th><td>${esc(plain).replace("\n", "<br>")}</td></tr>" +
            "</table></body></html>"

    private fun esc(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
