package com.inkwell.notes.editor

/**
 * The single character that stands for a block object (divider, table) inside [RichDoc.text].
 * An object paragraph is always exactly this one character; its payload lives in [Para.table].
 */
const val OBJ = '￼'

/**
 * Link targets, interned: a character's link is stored as an id in the upper bits of its mark Int
 * (see [Mark.LINK_SHIFT]), so links survive every text edit exactly like bold / italic do.
 * Append-only for the life of the process, so ids inside undo snapshots never go stale.
 */
object LinkTable {
    private val urls = ArrayList<String>()
    private val ids = HashMap<String, Int>()

    /** 1-based id of [url] (0 for a blank url). */
    @Synchronized
    fun idOf(url: String): Int {
        val u = url.trim()
        if (u.isEmpty()) return 0
        ids[u]?.let { return it }
        urls.add(u)
        val id = urls.size
        ids[u] = id
        return id
    }

    @Synchronized
    fun urlOf(id: Int): String? = if (id in 1..urls.size) urls[id - 1] else null
}

/** One table cell: text plus per-character marks (same encoding as [RichDoc.marks], links included). */
class Cell(text: String, marks: IntArray = IntArray(text.length)) {
    val text: String = text
    val marks: IntArray = if (marks.size == text.length) marks else marks.copyOf(text.length)

    /** New text from the cell editor; characters outside the edited middle keep their formatting. */
    fun withText(newText: String): Cell {
        if (newText == text) return this
        val minLen = minOf(text.length, newText.length)
        var p = 0
        while (p < minLen && text[p] == newText[p]) p++
        var s = 0
        while (s < minLen - p && text[text.length - 1 - s] == newText[newText.length - 1 - s]) s++
        val m = IntArray(newText.length)
        System.arraycopy(marks, 0, m, 0, p)
        System.arraycopy(marks, text.length - s, m, newText.length - s, s)
        return Cell(newText, m)
    }
}

/**
 * Immutable table payload of a [BlockType.TABLE] paragraph. Rows are padded to the widest row, there is
 * always at least one row and one column, and [header] means "the first row is a header row".
 */
class TableData(rawRows: List<List<Cell>>, val header: Boolean = true) {
    val colCount: Int = maxOf(1, rawRows.maxOfOrNull { it.size } ?: 0)
    val rows: List<List<Cell>> = (if (rawRows.isEmpty()) listOf(emptyList()) else rawRows).map { row ->
        if (row.size < colCount) row + List(colCount - row.size) { Cell("") } else row
    }
    val rowCount: Int get() = rows.size

    fun cell(r: Int, c: Int): Cell = rows.getOrNull(r)?.getOrNull(c) ?: Cell("")

    fun withCell(r: Int, c: Int, text: String): TableData {
        if (r !in rows.indices || c !in 0 until colCount) return this
        val old = rows[r][c]
        val upd = old.withText(text)
        if (upd === old) return this
        return TableData(rows.mapIndexed { ri, row -> if (ri == r) row.toMutableList().also { it[c] = upd } else row }, header)
    }

    /** Inserts an empty row so it ends up at index [at] (never above the header row). */
    fun addRow(at: Int): TableData {
        val idx = at.coerceIn(if (header) 1 else 0, rowCount)
        val blank = List(colCount) { Cell("") }
        return TableData(rows.take(idx) + listOf(blank) + rows.drop(idx), header)
    }

    fun addCol(at: Int): TableData {
        val idx = at.coerceIn(0, colCount)
        return TableData(rows.map { row -> row.take(idx) + Cell("") + row.drop(idx) }, header)
    }

    fun removeRow(r: Int): TableData {
        if (rowCount <= 1 || r !in rows.indices) return this
        return TableData(rows.filterIndexed { i, _ -> i != r }, header)
    }

    fun removeCol(c: Int): TableData {
        if (colCount <= 1 || c !in 0 until colCount) return this
        return TableData(rows.map { row -> row.filterIndexed { i, _ -> i != c } }, header)
    }

    fun withHeader(h: Boolean): TableData = if (h == header) this else TableData(rows, h)

    /** Plain text of every cell, for word counts. */
    val allText: String get() = rows.joinToString(" ") { row -> row.joinToString(" ") { it.text } }

    companion object {
        /** What the toolbar inserts: same as the web (`Column 1 | Column 2` / `Text | Text`). */
        fun blank(): TableData = TableData(
            listOf(
                listOf(Cell("Column 1"), Cell("Column 2")),
                listOf(Cell("Text"), Cell("Text")),
            ),
            header = true,
        )
    }
}
