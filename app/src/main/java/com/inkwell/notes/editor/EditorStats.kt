package com.inkwell.notes.editor

/** One toolbar tool in the customiser list (web `TOOLBAR_GROUPS`). The glyph is drawn by the UI. */
class ToolDef(val key: String, val title: String)

/** Toolbar layout + the "hidden tools" pref (web key `iw_toolbar_hidden_tools`, a JSON array of keys). */
object ToolbarTools {
    val groups: List<List<ToolDef>> = listOf(
        listOf(ToolDef("undo", "Undo"), ToolDef("redo", "Redo")),
        listOf(ToolDef("markdown", "Markdown text mode")),
        listOf(ToolDef("bold", "Bold"), ToolDef("italic", "Italic"), ToolDef("underline", "Underline"), ToolDef("strike", "Strikethrough")),
        listOf(ToolDef("h1", "Heading 1"), ToolDef("h2", "Heading 2"), ToolDef("h3", "Heading 3"), ToolDef("p", "Paragraph")),
        listOf(ToolDef("ul", "Bullet list"), ToolDef("ol", "Ordered list"), ToolDef("check", "Checklist"), ToolDef("bq", "Blockquote")),
        listOf(
            ToolDef("left", "Align left"), ToolDef("center", "Align center"), ToolDef("right", "Align right"),
            ToolDef("indent", "Indent"), ToolDef("outdent", "Outdent"),
        ),
        listOf(
            ToolDef("highlight", "Highlight"), ToolDef("table", "Insert table"), ToolDef("datetime", "Insert date/time"),
            ToolDef("sup", "Superscript"), ToolDef("sub", "Subscript"),
        ),
        listOf(
            ToolDef("link", "Insert link"), ToolDef("photo", "Add photo"), ToolDef("draw", "Draw sketch"), ToolDef("hr", "Divider"),
            ToolDef("code", "Inline code"), ToolDef("codeblock", "Code block"), ToolDef("clear", "Clear format"),
        ),
    )

    val all: List<ToolDef> get() = groups.flatten()

    /** Groups with the hidden tools removed; a group that ends up empty (and its separator) disappears. */
    fun visible(hidden: Set<String>): List<List<ToolDef>> =
        groups.map { g -> g.filter { it.key !in hidden } }.filter { it.isNotEmpty() }

    fun encode(hidden: Set<String>): String =
        hidden.sorted().joinToString(",", "[", "]") { "\"" + it.replace("\\", "").replace("\"", "") + "\"" }

    /** Lenient: bad / empty input → nothing hidden; unknown keys are dropped. */
    fun decode(json: String?): Set<String> {
        if (json.isNullOrBlank()) return emptySet()
        val known = all.map { it.key }.toSet()
        return Regex("\"([^\"]+)\"").findAll(json).map { it.groupValues[1] }.filter { it in known }.toSet()
    }
}

object EditorStats {
    /**
     * Writing streak (web Editor footer): consecutive days, ending today, with at least one note edit.
     * Today may be empty without breaking the run only for i = 0, exactly like the web loop.
     * [dayKeys] are `yyyy-MM-dd`; [keyDaysAgo] gives the key for "i days ago".
     */
    fun streak(dayKeys: Set<String>, keyDaysAgo: (Int) -> String): Int {
        var s = 0
        for (i in 0 until 365) {
            if (keyDaysAgo(i) in dayKeys) s++ else if (i > 0) break
        }
        return s
    }

    /** Words inside the selection `[start, end)` of [text] (0 for a caret). */
    fun selectedWords(text: String, start: Int, end: Int, count: (String) -> Int): Int {
        val a = minOf(start, end).coerceIn(0, text.length)
        val b = maxOf(start, end).coerceIn(0, text.length)
        if (a == b) return 0
        return count(text.substring(a, b).replace(OBJ.toString(), " "))
    }
}
