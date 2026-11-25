package com.inkwell.notes.editor

/**
 * Editor Part 1 body format: plain text ⇄ the note's stored HTML (`content`).
 *
 * One line = one `<p>` (an empty line = `<p><br></p>`), exactly the shape the web editor
 * produces for plain paragraphs, so notes written here open fine in the web app.
 * Later parts replace this with the block / span model (headings, lists, checklists …);
 * [isPlainHtml] is what tells the editor whether a stored note can still be edited as text.
 */
object NoteText {
    private val SIMPLE_TAG = Regex("</?(p|br|div)\\s*/?>", RegexOption.IGNORE_CASE)
    private val ANY_TAG = Regex("<[^>]*>")
    private val EMPTY_P = Regex("<p>\\s*<br\\s*/?>\\s*</p>", RegexOption.IGNORE_CASE)
    private val BREAK = Regex("<br\\s*/?>", RegexOption.IGNORE_CASE)
    // Block ends become line breaks. Plain notes only contain p/div; the rest keeps the read-only
    // preview of rich notes ("<h1>A</h1><p>B</p>") on separate lines.
    private val BLOCK_END = Regex("</(p|div|h[1-6]|li|blockquote|pre|tr)>", RegexOption.IGNORE_CASE)

    /** True when [html] holds nothing but paragraphs / line breaks (so flattening it to text loses nothing). */
    fun isPlainHtml(html: String): Boolean =
        !ANY_TAG.containsMatchIn(html.replace(SIMPLE_TAG, ""))

    /** Stored HTML → editable text. Only meaningful when [isPlainHtml] is true. */
    fun htmlToPlain(html: String): String {
        if (html.isBlank()) return ""
        val s = html
            .replace(EMPTY_P, "<p></p>")
            .replace(BREAK, "\n")
            .replace(BLOCK_END, "\n")
            .replace(ANY_TAG, "")
        return unescape(s).removeSuffix("\n")
    }

    /** Editable text → stored HTML. Empty text → empty string (same as an empty web editor body). */
    fun plainToHtml(text: String): String {
        if (text.isEmpty()) return ""
        return text.replace("\r\n", "\n").split("\n").joinToString("") { line ->
            if (line.isEmpty()) "<p><br></p>" else "<p>${escape(line)}</p>"
        }
    }

    private fun escape(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun unescape(s: String): String =
        s.replace("&nbsp;", " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&amp;", "&")
}
