package com.inkwell.notes.editor

/**
 * Inline formatting flags, one Int bitmask per character (see [RichDoc.marks]).
 *
 * Bits 0–7 are the format flags; bits 8–23 hold a link id (0 = no link, n = [RichDoc.urls] index n − 1).
 * Keeping the link inside the same Int means every operation that copies marks (typing, undo, paste …)
 * carries links along with no extra bookkeeping, and two neighbouring characters only share a run when
 * both their formatting and their link are identical.
 */
object Mark {
    const val BOLD = 1
    const val ITALIC = 2
    const val UNDERLINE = 4
    const val STRIKE = 8
    const val SUP = 16
    const val SUB = 32
    const val HIGHLIGHT = 64
    /** Inline `code` (Part 5). */
    const val CODE = 128

    const val FORMAT_MASK = 0xFF
    const val LINK_SHIFT = 8
    const val LINK_MASK = 0xFFFF shl LINK_SHIFT

    fun linkId(m: Int): Int = (m ushr LINK_SHIFT) and 0xFFFF
    fun withLink(m: Int, id: Int): Int = (m and LINK_MASK.inv()) or ((id and 0xFFFF) shl LINK_SHIFT)
}

enum class BlockType {
    P, H1, H2, H3, UL, OL, TASK, QUOTE, CODE, HR, TABLE;

    /** Bullet, numbered and checklist items — they nest by [Para.indent]. */
    val isList: Boolean get() = this == UL || this == OL || this == TASK
    val isHeading: Boolean get() = this == H1 || this == H2 || this == H3

    /**
     * Divider and table (Part 5): blocks that are drawn by the screen, not typed into. Their line of text is
     * always empty, and the caret is never meant to rest on it.
     */
    val isObject: Boolean get() = this == HR || this == TABLE
}

/**
 * Table content: plain-text cells (the editor's table sheet edits them), first row = header when [header].
 * [wrapped] remembers a `<div class="md-table-wrap">` around the table so a web-made note round-trips.
 */
data class TableData(
    val rows: List<List<String>>,
    val header: Boolean = true,
    val wrapped: Boolean = false,
) {
    val rowCount: Int get() = rows.size
    val colCount: Int get() = rows.maxOfOrNull { it.size } ?: 0

    /** Every row padded / trimmed to the same number of columns (at least 1 × 1). */
    fun normalized(): TableData {
        val cols = maxOf(1, colCount)
        val r = if (rows.isEmpty()) listOf(listOf("")) else rows
        return copy(rows = r.map { row -> List(cols) { c -> row.getOrElse(c) { "" } } })
    }

    fun cell(r: Int, c: Int): String = rows.getOrNull(r)?.getOrNull(c).orEmpty()

    fun withCell(r: Int, c: Int, value: String): TableData {
        val t = normalized()
        if (r !in t.rows.indices || c !in 0 until t.colCount) return t
        return t.copy(rows = t.rows.mapIndexed { ri, row -> if (ri == r) row.mapIndexed { ci, v -> if (ci == c) value else v } else row })
    }

    fun addRow(after: Int = rowCount - 1): TableData {
        val t = normalized()
        val at = (after + 1).coerceIn(0, t.rowCount)
        return t.copy(rows = t.rows.toMutableList().apply { add(at, List(t.colCount) { "" }) })
    }

    fun addColumn(after: Int = colCount - 1): TableData {
        val t = normalized()
        val at = (after + 1).coerceIn(0, t.colCount)
        return t.copy(rows = t.rows.map { row -> row.toMutableList().apply { add(at, "") } })
    }

    /** Removes row [r]; the last remaining row can't be removed (delete the whole table instead). */
    fun removeRow(r: Int): TableData {
        val t = normalized()
        if (t.rowCount <= 1 || r !in t.rows.indices) return t
        return t.copy(rows = t.rows.filterIndexed { i, _ -> i != r })
    }

    fun removeColumn(c: Int): TableData {
        val t = normalized()
        if (t.colCount <= 1 || c !in 0 until t.colCount) return t
        return t.copy(rows = t.rows.map { row -> row.filterIndexed { i, _ -> i != c } })
    }

    companion object {
        /** What the toolbar inserts — same starter as the web (`Column 1 | Column 2` + one `Text | Text` row). */
        fun starter(): TableData = TableData(
            rows = listOf(listOf("Column 1", "Column 2"), listOf("Text", "Text")),
            header = true,
        )
    }
}

/** One paragraph pulled out of a [RichDoc] (text + its marks + block attributes) — used by structural edits. */
class Line(val text: String, val marks: IntArray, val para: Para)

enum class Align { Start, Center, End }

/**
 * Block-level attributes of one paragraph (= one line of [RichDoc.text]).
 *
 * [indent]    : plain paragraphs → left margin step; list items → nesting level (0 = top level).
 * [checked]   : checklist items only.
 * [collapsed] : list items only — hides the nested items below this one.
 */
data class Para(
    val type: BlockType = BlockType.P,
    val align: Align = Align.Start,
    val indent: Int = 0,
    val checked: Boolean = false,
    val collapsed: Boolean = false,
    /** [BlockType.TABLE] only. */
    val table: TableData? = null,
)

/** Result of [RichDoc.edit]. [caret] is set when the edit was rewritten and the caret must move. */
class EditResult(val doc: RichDoc, val caret: Int? = null)

/**
 * The editor's document model (Part 2, extended in Parts 3 and 5).
 *
 * Deliberately flat so it maps 1:1 onto a Compose text field:
 *  - [text]  : the whole body, paragraphs separated by `'\n'`
 *  - [marks] : inline formatting per character (same length as [text])
 *  - [paras] : block attributes per paragraph (count = number of `'\n'` + 1)
 *
 * Nested lists, checklists and collapsed items are expressed purely through [Para] (indent / checked /
 * collapsed) — the text itself never changes, so the text field and undo history stay simple.
 *
 * Immutable — every operation returns a new document, which is what makes undo / redo trivial.
 */
class RichDoc private constructor(
    val text: String,
    val marks: IntArray,
    val paras: List<Para>,
    /** Link targets; a char's link id n (see [Mark]) points at `urls[n − 1]`. */
    val urls: List<String> = emptyList(),
) {
    /** Start offset of every paragraph. */
    val starts: IntArray = run {
        val list = ArrayList<Int>(paras.size)
        list.add(0)
        for (i in text.indices) if (text[i] == '\n') list.add(i + 1)
        list.toIntArray()
    }

    val paraCount: Int get() = paras.size

    /** End offset (exclusive, before the newline) of paragraph [i]. */
    fun paraEnd(i: Int): Int = if (i + 1 < starts.size) starts[i + 1] - 1 else text.length

    /** Index of the paragraph that contains caret [offset]. */
    fun paraIndexAt(offset: Int): Int {
        val o = offset.coerceIn(0, text.length)
        var lo = 0
        var hi = starts.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) ushr 1
            if (starts[mid] <= o) lo = mid else hi = mid - 1
        }
        return lo
    }

    // ─── list structure (Part 3) ──────────────

    /** True when list item [i] is followed by deeper list items. */
    fun hasChildren(i: Int): Boolean =
        i in 0 until paras.size - 1 &&
            paras[i].type.isList && paras[i + 1].type.isList &&
            paras[i + 1].indent > paras[i].indent

    /** Index of the last item nested under [i] (== [i] when it has none). */
    fun subtreeEnd(i: Int): Int {
        if (!paras[i].type.isList) return i
        var m = i
        while (m + 1 < paras.size && paras[m + 1].type.isList && paras[m + 1].indent > paras[i].indent) m++
        return m
    }

    /** Paragraphs hidden because an ancestor list item is collapsed. */
    val hidden: BooleanArray by lazy {
        val h = BooleanArray(paras.size)
        var hideBelow = -1
        for (k in paras.indices) {
            val p = paras[k]
            if (!p.type.isList) { hideBelow = -1; continue }
            if (hideBelow >= 0 && p.indent > hideBelow) { h[k] = true; continue }
            hideBelow = -1
            if (p.collapsed && hasChildren(k)) hideBelow = p.indent
        }
        h
    }

    val hasHidden: Boolean get() = hidden.any { it }

    /** Number shown for every ordered-list item (0 for other paragraphs); restarts per level / list. */
    val olNumbers: IntArray by lazy {
        val nums = IntArray(paras.size)
        val counter = IntArray(MAX_INDENT + 2)
        val last = arrayOfNulls<BlockType>(MAX_INDENT + 2)
        for (k in paras.indices) {
            val p = paras[k]
            if (!p.type.isList) { last.fill(null); continue }
            val lv = p.indent.coerceIn(0, MAX_INDENT)
            for (d in lv + 1..MAX_INDENT + 1) last[d] = null
            if (p.type == BlockType.OL) {
                counter[lv] = if (last[lv] == BlockType.OL) counter[lv] + 1 else 1
                nums[k] = counter[lv]
            }
            last[lv] = p.type
        }
        nums
    }

    // ─── queries (toolbar state) ──────────────

    /** Marks "active" for a caret / selection: caret → the char before it, range → marks shared by every char. */
    fun marksAt(start: Int, end: Int): Int {
        val a = minOf(start, end).coerceIn(0, text.length)
        val b = maxOf(start, end).coerceIn(0, text.length)
        if (a == b) {
            return when {
                a > 0 -> marks[a - 1]
                text.isNotEmpty() -> marks[0]
                else -> 0
            }
        }
        var acc = -1
        for (i in a until b) {
            if (text[i] == '\n') continue
            acc = if (acc == -1) marks[i] else acc and marks[i]
        }
        return if (acc == -1) 0 else acc
    }

    fun paraAt(offset: Int): Para = paras[paraIndexAt(offset)]

    /** Paragraph indexes touched by a selection (a range ending exactly at a paragraph start excludes it). */
    fun paraRange(start: Int, end: Int): IntRange {
        val a = minOf(start, end)
        val b = maxOf(start, end)
        val i0 = paraIndexAt(a)
        var i1 = paraIndexAt(b)
        if (b > a && b == starts[i1] && i1 > i0) i1--
        return i0..i1
    }

    // ─── text edits ───────────────────────────

    /**
     * Applies a text change coming from the text field: works out what was replaced, keeps [marks]
     * and [paras] in step, and applies the block rules:
     *  - Enter on an empty list item / quote leaves it (a nested item first steps out one level)
     *  - Enter in a heading continues with a paragraph; Enter in a list / checklist continues the list
     *  - Backspace at the start of a block outdents / turns it into a plain paragraph
     *  - Markdown shortcuts (when [shortcuts]): `# ` `## ` `### ` `> ` `- ` `1. ` `[] ` `` ``` `` (+ `/check` `/code` + Enter)
     *
     * [typingMarks] = formatting for newly typed characters (toolbar toggles with a collapsed caret);
     * null = inherit from the neighbouring character.
     *
     * [caret] = the caret the text field reports after the change. A text diff alone can't tell WHICH of two
     * equal neighbouring characters changed (Enter at the end of a bullet looks like Enter at the start of the
     * next line), so for pure insertions / deletions the change is anchored to the caret.
     */
    fun edit(newText: String, typingMarks: Int?, shortcuts: Boolean = true, caret: Int? = null): EditResult {
        if (newText == text) return EditResult(this)

        val d = diff(newText, caret)
        val oldLen = text.length
        val newLen = newText.length
        val p = d.p
        val removedEnd = d.removedEnd
        val insEnd = d.insEnd
        val s = d.s

        val removed = text.substring(p, removedEnd)
        val inserted = newText.substring(p, insEnd)
        val i = paraIndexAt(p)

        // Divider / table lines are drawn by the screen and never hold text (Part 5).
        if (paras[i].type.isObject) {
            // Backspace at the start of the line after one → the divider / table goes away.
            if (removed == "\n" && inserted.isEmpty()) return EditResult(removeLine(i), caret = p)
            // Text typed or pasted onto it moves to a fresh paragraph below. (Enter falls through: continuation() = P.)
            if (removed.isEmpty() && inserted.isNotEmpty() && inserted != "\n") {
                return typedAfterObject(i, inserted, typingMarks)
            }
        }

        // Backspace at the start of a line.
        if (removed == "\n" && inserted.isEmpty()) {
            val j = i + 1
            if (j < paras.size) {
                val pi = paras[i]
                val pj = paras[j]
                if (hidden[i]) {
                    // The line before is tucked away inside a collapsed item: show it instead of merging blindly.
                    return EditResult(expandAround(i), caret = p + 1)
                }
                if (pi.type == BlockType.CODE && pj.type == BlockType.CODE) {
                    // inside a code block Backspace joins the lines (falls through to the normal merge)
                } else if (pj.type.isObject) {
                    // Backspace into a divider / table: the normal merge below drops its (empty) line.
                } else if (pj.type != BlockType.P || pj.indent > 0) {
                    val np = if (pj.type.isList && pj.indent > 0) {
                        pj.copy(indent = pj.indent - 1) // nested item: step out one level first
                    } else {
                        pj.copy(type = BlockType.P, indent = 0, checked = false, collapsed = false)
                    }
                    return EditResult(withPara(j, np), caret = p + 1)
                }
            }
        }

        if (inserted == "\n" && removed.isEmpty()) {
            val cur = paras[i]
            val lineStart = starts[i]
            val lineEnd = paraEnd(i)
            val empty = lineStart == lineEnd

            // Enter on an empty list item / quote → leave it (a nested item steps out one level first).
            if (empty && (cur.type.isList || cur.type == BlockType.QUOTE)) {
                val np = if (cur.type.isList && cur.indent > 0) {
                    cur.copy(indent = cur.indent - 1)
                } else {
                    cur.copy(type = BlockType.P, indent = 0, checked = false, collapsed = false)
                }
                return EditResult(withPara(i, np), caret = p)
            }

            // Enter on an empty last line of a code block (after another code line) → leave the block.
            if (empty && cur.type == BlockType.CODE && i > 0 && paras[i - 1].type == BlockType.CODE &&
                (i + 1 >= paras.size || paras[i + 1].type != BlockType.CODE)
            ) {
                return EditResult(withPara(i, cur.copy(type = BlockType.P, indent = 0)), caret = p)
            }

            // "```" / "~~~" / "/code" / "/check" typed on a line, then Enter.
            if (shortcuts && p == lineEnd) {
                val line = text.substring(lineStart, lineEnd)
                if (cur.type == BlockType.CODE) {
                    if (line.trim() == "```") {
                        return EditResult(clearLine(i, cur.copy(type = BlockType.P, indent = 0)), caret = lineStart)
                    }
                } else {
                    val t = line.trim()
                    if (t == "---" || t == "***" || t.equals("/divider", ignoreCase = true) || t.equals("/hr", ignoreCase = true)) {
                        val base = clearLine(i, Para(type = BlockType.HR))
                        return EditResult(base, caret = base.starts[(i + 1).coerceAtMost(base.paraCount - 1)])
                    }
                    val target = when {
                        t == "```" || t == "~~~" || t.equals("/code", ignoreCase = true) -> BlockType.CODE
                        t.equals("/check", ignoreCase = true) -> BlockType.TASK
                        else -> null
                    }
                    if (target != null) {
                        return EditResult(clearLine(i, blockPara(cur, target)), caret = lineStart)
                    }
                }
            }
        }

        // Markdown shortcut: marker + ONE space typed at the end of an otherwise empty line.
        if (shortcuts && inserted == " " && removed.isEmpty()) {
            val cur = paras[i]
            if (cur.type != BlockType.CODE && p == paraEnd(i)) {
                val marker = text.substring(starts[i], p)
                val target = SHORTCUTS[marker]
                if (target != null) {
                    return EditResult(clearLine(i, blockPara(cur, target)), caret = starts[i])
                }
            }
        }

        val removedNl = removed.count { it == '\n' }
        val insertedNl = inserted.count { it == '\n' }

        val newParas = ArrayList(paras)
        if (removedNl > 0) {
            repeat(removedNl) { if (i + 1 < newParas.size) newParas.removeAt(i + 1) }
        }
        if (insertedNl > 0) {
            val cur = paras[i]
            // Enter on a collapsed item opens it first, so the new line can't land between it and its hidden children.
            val shown = if (cur.collapsed) cur.copy(collapsed = false) else cur
            newParas[i] = shown
            val atStart = p == starts[i] && p < paraEnd(i)
            if (inserted == "\n" && removed.isEmpty() && atStart) {
                // Enter at the very start of a line: empty line above, the text keeps its block type.
                val above = when {
                    shown.type.isHeading -> shown.copy(type = BlockType.P)
                    shown.type.isList -> shown.copy(checked = false, collapsed = false)
                    else -> shown
                }
                newParas.add(i, above)
            } else {
                val next = continuation(shown)
                repeat(insertedNl) { newParas.add(i + 1, next) }
            }
        }

        var ins = typingMarks
            ?: if (removed.isNotEmpty() && p < oldLen) marks[p]
            else if (p > 0) marks[p - 1]
            else if (oldLen > 0) marks[0]
            else 0
        // A link only grows when you type INSIDE it — typing right after its last character leaves it.
        val lid = Mark.linkId(ins)
        if (lid != 0) {
            val before = if (p > 0) Mark.linkId(marks[p - 1]) else 0
            val after = if (removedEnd < oldLen) Mark.linkId(marks[removedEnd]) else 0
            if (before != lid || after != lid) ins = Mark.withLink(ins, 0)
        }

        val newMarks = IntArray(newLen)
        System.arraycopy(marks, 0, newMarks, 0, p)
        for (k in p until insEnd) newMarks[k] = ins
        System.arraycopy(marks, removedEnd, newMarks, insEnd, s)

        return EditResult(create(newText, newMarks, newParas, urls))
    }

    private class Diff(val p: Int, val removedEnd: Int, val insEnd: Int, val s: Int)

    /**
     * What [newText] changed compared to [text]: [Diff.p, Diff.removedEnd) of the old text became
     * [Diff.p, Diff.insEnd) of the new one. [caret] anchors pure insertions / deletions (see [edit]).
     */
    private fun diff(newText: String, caret: Int?): Diff {
        val oldLen = text.length
        val newLen = newText.length
        val minLen = minOf(oldLen, newLen)
        var p = 0
        while (p < minLen && text[p] == newText[p]) p++
        var s = 0
        while (s < minLen - p && text[oldLen - 1 - s] == newText[newLen - 1 - s]) s++

        var removedEnd = oldLen - s
        var insEnd = newLen - s
        if (caret != null) {
            val ins0 = insEnd - p
            val rem0 = removedEnd - p
            if (rem0 == 0 && ins0 > 0) {
                val ip = caret - ins0
                if (ip in 0..oldLen && ip != p && newText.removeRange(ip, ip + ins0) == text) {
                    p = ip
                }
            } else if (ins0 == 0 && rem0 > 0) {
                if (caret != p && caret >= 0 && caret + rem0 <= oldLen && text.removeRange(caret, caret + rem0) == newText) {
                    p = caret
                }
            }
            removedEnd = p + rem0
            insEnd = p + ins0
            s = oldLen - removedEnd
        }
        return Diff(p, removedEnd, insEnd, s)
    }

    /** Text inserted by a change coming from the text field: it replaced [start, end) of the old text. */
    class Paste(val start: Int, val end: Int, val text: String)

    /** The inserted text of a field change (null when it only deleted) — the paste handler's way in. */
    fun pasteOf(newText: String, caret: Int?): Paste? {
        if (newText == text) return null
        val d = diff(newText, caret)
        if (d.insEnd <= d.p) return null
        return Paste(d.p, d.removedEnd, newText.substring(d.p, d.insEnd))
    }

    /** Attributes of a paragraph created by pressing Enter at the end of [cur]. */
    private fun continuation(cur: Para): Para = when {
        cur.type.isObject -> Para()
        cur.type.isHeading -> cur.copy(type = BlockType.P)
        cur.type.isList -> cur.copy(checked = false, collapsed = false)
        else -> cur
    }

    /** [cur] re-typed as [target] (shortcut / toolbar): list ↔ non-list resets the nesting level. */
    private fun blockPara(cur: Para, target: BlockType): Para = cur.copy(
        type = target,
        indent = if (target.isList == cur.type.isList) cur.indent else 0,
        checked = false,
        collapsed = false,
        table = null,
    )

    /** Removes the text of paragraph [i] (the typed marker) and gives it [para]. */
    private fun clearLine(i: Int, para: Para): RichDoc {
        val s0 = starts[i]
        val e0 = paraEnd(i)
        val newText = text.substring(0, s0) + text.substring(e0)
        val newMarks = IntArray(newText.length)
        System.arraycopy(marks, 0, newMarks, 0, s0)
        System.arraycopy(marks, e0, newMarks, s0, text.length - e0)
        val l = ArrayList(paras)
        l[i] = para
        return create(newText, newMarks, l, urls)
    }

    private fun withPara(index: Int, para: Para): RichDoc {
        val l = ArrayList(paras)
        l[index] = para
        return RichDoc(text, marks, normalize(l), urls)
    }

    // ─── formatting ───────────────────────────

    /** Toggles [mark] over a selection: cleared when every character already has it, otherwise set. */
    fun toggleMark(mark: Int, start: Int, end: Int): RichDoc {
        val a = minOf(start, end).coerceIn(0, text.length)
        val b = maxOf(start, end).coerceIn(0, text.length)
        if (a == b) return this
        var any = false
        var all = true
        for (k in a until b) {
            if (text[k] == '\n') continue
            any = true
            if (marks[k] and mark == 0) all = false
        }
        if (!any) return this
        val m = marks.copyOf()
        val exclusive = when (mark) {
            Mark.SUP -> Mark.SUB
            Mark.SUB -> Mark.SUP
            else -> 0
        }
        for (k in a until b) {
            if (text[k] == '\n') continue
            m[k] = if (all) m[k] and mark.inv() else (m[k] or mark) and exclusive.inv()
        }
        return RichDoc(text, m, paras, urls)
    }

    /** Heading / list / checklist / quote / code button: applies [type], or goes back to a paragraph when all selected lines already have it. */
    fun setBlock(type: BlockType, start: Int, end: Int): RichDoc {
        if (type.isObject) return this
        val r = paraRange(start, end).filter { !paras[it].type.isObject }
        if (r.isEmpty()) return this
        val allSame = r.all { paras[it].type == type }
        val target = if (allSame) BlockType.P else type
        return mapParas(r) { blockPara(it, target) }
    }

    fun setAlign(align: Align, start: Int, end: Int): RichDoc {
        val r = paraRange(start, end).filter { !paras[it].type.isObject }
        if (r.isEmpty()) return this
        val allSame = r.all { paras[it].align == align }
        val target = if (allSame) Align.Start else align
        return mapParas(r) { it.copy(align = target) }
    }

    /**
     * Indent / outdent. Plain paragraphs move their left margin; list items change nesting level and
     * take the items nested under them along. An item can only nest under a list item right above it.
     */
    fun changeIndent(delta: Int, start: Int, end: Int): RichDoc {
        val r = paraRange(start, end)
        val n = paras.size
        val firstList = r.firstOrNull { paras[it].type.isList }
        var listsAllowed = true
        if (firstList != null && delta > 0) {
            val prev = if (firstList > 0 && paras[firstList - 1].type.isList) paras[firstList - 1].indent else -1
            if (paras[firstList].indent > prev) listsAllowed = false
        }
        val shift = IntArray(n)
        for (k in r) {
            val p = paras[k]
            if (p.type.isObject) continue
            if (!p.type.isList) { shift[k] = delta; continue }
            if (!listsAllowed) continue
            val eff = if (delta < 0 && p.indent == 0) 0 else delta
            if (eff == 0) continue
            shift[k] = eff
            var m = k + 1
            while (m < n && paras[m].type.isList && paras[m].indent > p.indent) {
                shift[m] = eff
                m++
            }
        }
        if (shift.all { it == 0 }) return this
        val l = ArrayList(paras)
        for (k in 0 until n) if (shift[k] != 0) l[k] = l[k].copy(indent = (l[k].indent + shift[k]).coerceIn(0, MAX_INDENT))
        return RichDoc(text, marks, normalize(l), urls)
    }

    /**
     * "Clear format": drops inline marks in the selection (links stay, like the web `removeFormat`) and resets the
     * touched lines to plain paragraphs. Dividers and tables are left alone.
     */
    fun clearFormat(start: Int, end: Int): RichDoc {
        val a = minOf(start, end).coerceIn(0, text.length)
        val b = maxOf(start, end).coerceIn(0, text.length)
        val m = marks.copyOf()
        for (k in a until b) m[k] = m[k] and Mark.LINK_MASK
        val l = ArrayList(paras)
        for (k in paraRange(start, end)) if (!l[k].type.isObject) l[k] = Para()
        return RichDoc(text, m, normalize(l), urls)
    }

    /** Tap on a checklist box. */
    fun toggleChecked(i: Int): RichDoc {
        if (i !in paras.indices || paras[i].type != BlockType.TASK) return this
        return withPara(i, paras[i].copy(checked = !paras[i].checked))
    }

    /** Tap on a collapse chevron. */
    fun toggleCollapse(i: Int): RichDoc {
        if (i !in paras.indices || !hasChildren(i)) return this
        return withPara(i, paras[i].copy(collapsed = !paras[i].collapsed))
    }

    /** Opens every collapsed item that currently hides paragraph [i]. */
    fun expandAround(i: Int): RichDoc {
        val l = ArrayList(paras)
        var changed = false
        for (j in 0 until i) {
            if (l[j].collapsed && hasChildren(j) && subtreeEnd(j) >= i) {
                l[j] = l[j].copy(collapsed = false)
                changed = true
            }
        }
        return if (changed) RichDoc(text, marks, normalize(l), urls) else this
    }

    // ─── links (Part 5) ───────────────────────

    /** A link run: chars [start, end) point at [url]. */
    class LinkInfo(val start: Int, val end: Int, val url: String)

    fun urlOf(id: Int): String? = urls.getOrNull(id - 1)

    /**
     * The link at a caret (the char just before it — so the end of a link still counts) or the link a selection
     * is made of entirely. null = not on a link.
     */
    fun linkAt(start: Int, end: Int): LinkInfo? {
        val a = minOf(start, end).coerceIn(0, text.length)
        val b = maxOf(start, end).coerceIn(0, text.length)
        val ref: Int
        val id: Int
        if (a == b) {
            ref = a - 1
            id = if (a > 0) Mark.linkId(marks[a - 1]) else 0
        } else {
            var first = -1
            var shared = -1
            for (k in a until b) {
                if (text[k] == '\n') continue
                if (first == -1) first = k
                val l = Mark.linkId(marks[k])
                shared = if (shared == -1 || shared == l) l else 0
                if (shared == 0) break
            }
            ref = first
            id = if (first == -1 || shared < 0) 0 else shared
        }
        if (id == 0 || ref < 0) return null
        val url = urlOf(id) ?: return null
        var lo = ref
        var hi = ref
        while (lo > 0 && Mark.linkId(marks[lo - 1]) == id) lo--
        while (hi + 1 < text.length && Mark.linkId(marks[hi + 1]) == id) hi++
        return LinkInfo(lo, hi + 1, url)
    }

    /** Makes [start, end) a link to [url] (replacing any link already there). */
    fun setLink(start: Int, end: Int, url: String): RichDoc {
        val a = minOf(start, end).coerceIn(0, text.length)
        val b = maxOf(start, end).coerceIn(0, text.length)
        if (a == b) return this
        val list = urls.toMutableList()
        var idx = list.indexOf(url)
        if (idx < 0) { list.add(url); idx = list.lastIndex }
        val id = idx + 1
        if (id > 0xFFFF) return this
        val m = marks.copyOf()
        for (k in a until b) if (text[k] != '\n') m[k] = Mark.withLink(m[k], id)
        return RichDoc(text, m, paras, list)
    }

    fun removeLink(start: Int, end: Int): RichDoc {
        val a = minOf(start, end).coerceIn(0, text.length)
        val b = maxOf(start, end).coerceIn(0, text.length)
        if (a == b) return this
        val m = marks.copyOf()
        for (k in a until b) m[k] = Mark.withLink(m[k], 0)
        return RichDoc(text, m, paras, urls)
    }

    // ─── structural edits (Part 5) ────────────

    /** Every paragraph as a [Line] (copies — safe to modify the list). */
    fun lines(): MutableList<Line> {
        val out = ArrayList<Line>(paras.size)
        for (k in paras.indices) {
            val s = starts[k]
            val e = paraEnd(k)
            out.add(Line(text.substring(s, e), marks.copyOfRange(s, e), paras[k]))
        }
        return out
    }

    /** Replaces [start, end) with single-line [ins] carrying [insMarks]; the caret goes after it. Paragraph attributes stay. */
    fun replace(start: Int, end: Int, ins: String, insMarks: Int): EditResult {
        val a = minOf(start, end).coerceIn(0, text.length)
        val b = maxOf(start, end).coerceIn(0, text.length)
        val clean = ins.replace('\n', ' ')
        val i = paraIndexAt(a)
        if (paras[i].type.isObject) return EditResult(this)
        val removedNl = (a until b).count { text[it] == '\n' }
        val newText = text.substring(0, a) + clean + text.substring(b)
        val newMarks = IntArray(newText.length)
        System.arraycopy(marks, 0, newMarks, 0, a)
        for (k in a until a + clean.length) newMarks[k] = insMarks
        System.arraycopy(marks, b, newMarks, a + clean.length, text.length - b)
        val newParas = ArrayList(paras)
        repeat(removedNl) { if (i + 1 < newParas.size) newParas.removeAt(i + 1) }
        return EditResult(create(newText, newMarks, newParas, urls), caret = a + clean.length)
    }

    /**
     * Puts a divider / table block at [at]: an empty line becomes the block, a line with text is split around it.
     * The caret ends up in the paragraph right after the block (one is added when needed).
     */
    fun insertObject(at: Int, obj: Para): EditResult {
        val o = at.coerceIn(0, text.length)
        val i = paraIndexAt(o)
        val ls = lines()
        val cur = ls[i]
        val objLine = Line("", IntArray(0), obj)
        val blank = { Line("", IntArray(0), Para()) }
        if (cur.para.type.isObject) {
            ls.add(i + 1, objLine)
            ls.add(i + 2, blank())
            val d = fromLines(ls, urls)
            return EditResult(d, caret = d.starts[i + 2])
        }
        val cut = o - starts[i]
        val headT = cur.text.substring(0, cut)
        val headM = cur.marks.copyOfRange(0, cut)
        val tailT = cur.text.substring(cut)
        val tailM = cur.marks.copyOfRange(cut, cur.text.length)
        val out = ArrayList<Line>(ls.subList(0, i))
        val caretPara: Int
        when {
            headT.isEmpty() && tailT.isEmpty() -> { out.add(objLine); out.add(blank()); caretPara = i + 1 }
            headT.isEmpty() -> { out.add(objLine); out.add(Line(tailT, tailM, cur.para)); caretPara = i + 1 }
            tailT.isEmpty() -> { out.add(Line(headT, headM, cur.para)); out.add(objLine); out.add(blank()); caretPara = i + 2 }
            else -> {
                out.add(Line(headT, headM, cur.para)); out.add(objLine)
                out.add(Line(tailT, tailM, continuation(cur.para))); caretPara = i + 2
            }
        }
        out.addAll(ls.subList(i + 1, ls.size))
        val d = fromLines(out, urls)
        return EditResult(d, caret = d.starts[caretPara])
    }

    /** Text typed / pasted onto a divider or table line goes onto new paragraph(s) right below it. */
    private fun typedAfterObject(i: Int, inserted: String, typingMarks: Int?): EditResult {
        val m = Mark.withLink(typingMarks ?: 0, 0)
        val ls = lines()
        val parts = inserted.split('\n')
        var at = i + 1
        for (part in parts) ls.add(at++, Line(part, IntArray(part.length) { m }, Para()))
        val d = fromLines(ls, urls)
        return EditResult(d, caret = d.starts[at - 1] + parts.last().length)
    }

    private fun removeLine(i: Int): RichDoc {
        val ls = lines()
        ls.removeAt(i)
        if (ls.isEmpty()) ls.add(Line("", IntArray(0), Para()))
        return fromLines(ls, urls)
    }

    /** Removes a divider / table paragraph; the caret goes to the paragraph that moved up into its place. */
    fun deleteObject(i: Int): EditResult {
        if (i !in paras.indices || !paras[i].type.isObject) return EditResult(this)
        val d = removeLine(i)
        return EditResult(d, caret = d.starts[minOf(i, d.paraCount - 1)])
    }

    /**
     * Pastes a whole document [ins] over [start, end): a single plain paragraph just joins the current line,
     * lists / headings / code / tables / dividers become their own blocks, and the text after the selection
     * continues after the last pasted block. The caret ends at the end of what was pasted.
     */
    fun splice(start: Int, end: Int, ins: RichDoc): EditResult {
        val a = minOf(start, end).coerceIn(0, text.length)
        val b = maxOf(start, end).coerceIn(0, text.length)
        val i = paraIndexAt(a)
        val j = paraIndexAt(b)
        val ls = lines()
        val urlList = ArrayList(urls)
        val mid = ArrayList<Line>()
        for (ln in ins.lines()) mid.add(Line(ln.text, remapLinks(ln.marks, ins.urls, urlList), ln.para))
        if (mid.isEmpty()) return EditResult(this)

        if (ls[i].para.type.isObject) {
            ls.addAll(i + 1, mid)
            val d = fromLines(ls, urlList)
            val last = i + mid.size
            return EditResult(d, caret = d.starts[last] + mid.last().text.length)
        }

        val headLine = ls[i]
        val tailLine = ls[j]
        val headLen = a - starts[i]
        val tailFrom = b - starts[j]
        val headText = headLine.text.substring(0, headLen)
        val headMarks = headLine.marks.copyOfRange(0, headLen)
        val tailText = tailLine.text.substring(tailFrom)
        val tailMarks = tailLine.marks.copyOfRange(tailFrom, tailLine.text.length)

        val out = ArrayList<Line>(ls.subList(0, i))
        val first = mid[0]
        val plainFirst = first.para.type == BlockType.P && first.para.indent == 0 && first.para.align == Align.Start
        val rest = ArrayList<Line>(mid)
        if (plainFirst) {
            rest.removeAt(0)
            out.add(Line(headText + first.text, headMarks + first.marks, headLine.para))
        } else if (headText.isNotEmpty()) {
            out.add(Line(headText, headMarks, headLine.para))
        }
        out.addAll(rest)

        val last = out.last()
        val caretLine = out.size - 1
        val caretOff = last.text.length
        if (tailText.isNotEmpty()) {
            if (last.para.type == BlockType.P && !last.para.type.isObject) {
                out[caretLine] = Line(last.text + tailText, last.marks + tailMarks, last.para)
            } else {
                out.add(Line(tailText, tailMarks, tailLine.para))
            }
        }
        out.addAll(ls.subList(j + 1, ls.size))
        val d = fromLines(out, urlList)
        return EditResult(d, caret = d.starts[caretLine] + caretOff)
    }

    private fun remapLinks(m: IntArray, src: List<String>, dst: MutableList<String>): IntArray {
        if (m.none { Mark.linkId(it) != 0 }) return m
        val out = m.copyOf()
        for (k in out.indices) {
            val id = Mark.linkId(out[k])
            if (id == 0) continue
            val url = src.getOrNull(id - 1)
            if (url == null) { out[k] = Mark.withLink(out[k], 0); continue }
            var idx = dst.indexOf(url)
            if (idx < 0) { dst.add(url); idx = dst.lastIndex }
            out[k] = Mark.withLink(out[k], idx + 1)
        }
        return out
    }

    // ─── code blocks (Part 5) ─────────────────

    /** Paragraph indexes of the code block containing [offset] (consecutive code lines), or null. */
    fun codeBlockAt(offset: Int): IntRange? {
        val i = paraIndexAt(offset)
        if (paras[i].type != BlockType.CODE) return null
        var lo = i
        var hi = i
        while (lo > 0 && paras[lo - 1].type == BlockType.CODE) lo--
        while (hi + 1 < paras.size && paras[hi + 1].type == BlockType.CODE) hi++
        return lo..hi
    }

    fun codeBlockText(r: IntRange): String = text.substring(starts[r.first], paraEnd(r.last))

    /** Offset right after the last character of a code block. */
    fun codeBlockEnd(r: IntRange): Int = paraEnd(r.last)

    /** Replaces a whole code block with one empty paragraph. */
    fun deleteCodeBlock(r: IntRange): EditResult {
        val ls = lines()
        repeat(r.last - r.first + 1) { ls.removeAt(r.first) }
        ls.add(r.first, Line("", IntArray(0), Para()))
        val d = fromLines(ls, urls)
        return EditResult(d, caret = d.starts[r.first])
    }

    // ─── tables (Part 5) ──────────────────────

    fun setTable(i: Int, data: TableData): RichDoc {
        if (i !in paras.indices || paras[i].type != BlockType.TABLE) return this
        return withPara(i, paras[i].copy(table = data.normalized()))
    }

    private fun mapParas(range: Iterable<Int>, f: (Para) -> Para): RichDoc {
        val l = ArrayList(paras)
        for (k in range) l[k] = f(l[k])
        return RichDoc(text, marks, normalize(l), urls)
    }

    companion object {
        const val MAX_INDENT = 5

        /** Typed marker (line text before the space) → block it turns into. */
        private val SHORTCUTS: Map<String, BlockType> = mapOf(
            "#" to BlockType.H1,
            "##" to BlockType.H2,
            "###" to BlockType.H3,
            ">" to BlockType.QUOTE,
            "-" to BlockType.UL,
            "1." to BlockType.OL,
            "[]" to BlockType.TASK,
            "[ ]" to BlockType.TASK,
            "-[]" to BlockType.TASK,
            "- [ ]" to BlockType.TASK,
            "```" to BlockType.CODE,
            "~~~" to BlockType.CODE,
        )

        val EMPTY: RichDoc = create("", IntArray(0), listOf(Para()))

        /** Keeps list nesting valid (an item is at most one level deeper than the item above) and drops stray flags. */
        internal fun normalize(paras: List<Para>): List<Para> {
            var prev = -1
            var changed = false
            val out = ArrayList<Para>(paras.size)
            for (p in paras) {
                var q = p
                if (q.type.isList) {
                    if (q.indent > prev + 1) q = q.copy(indent = prev + 1)
                    prev = q.indent
                } else {
                    prev = -1
                    if (q.collapsed) q = q.copy(collapsed = false)
                    if (q.type.isObject) {
                        if (q.type == BlockType.TABLE && q.table == null) q = Para() // a table without content is nothing
                        else if (q.indent != 0 || q.align != Align.Start) q = q.copy(indent = 0, align = Align.Start)
                    }
                }
                if (q.checked && q.type != BlockType.TASK) q = q.copy(checked = false)
                if (q.table != null && q.type != BlockType.TABLE) q = q.copy(table = null)
                if (q !== p) changed = true
                out.add(q)
            }
            return if (changed) out else paras
        }

        /** Builds a document, repairing mismatched array sizes instead of throwing. */
        fun create(text: String, marks: IntArray, paras: List<Para>, urls: List<String> = emptyList()): RichDoc {
            val m = if (marks.size == text.length) marks else marks.copyOf(text.length)
            val need = text.count { it == '\n' } + 1
            val p0: List<Para> = when {
                paras.size == need -> paras
                paras.size > need -> paras.subList(0, need)
                else -> paras + List(need - paras.size) { Para() }
            }
            val p = normalize(p0)
            if (p.none { it.type.isObject }) return RichDoc(text, m, p, urls)
            return repairObjects(text, m, p, urls)
        }

        /** Divider / table lines hold no text (a line that does becomes a plain paragraph), and the document never ends on one. */
        private fun repairObjects(text: String, marks: IntArray, paras: List<Para>, urls: List<String>): RichDoc {
            var ps: MutableList<Para> = ArrayList(paras)
            var pos = 0
            for (k in ps.indices) {
                val nl = text.indexOf('\n', pos)
                val end = if (nl == -1) text.length else nl
                if (ps[k].type.isObject && end > pos) ps[k] = Para()
                pos = end + 1
            }
            var t = text
            var mk = marks
            if (ps.last().type.isObject) {
                t = text + "\n"
                mk = marks.copyOf(marks.size + 1)
                ps.add(Para())
            }
            return RichDoc(t, mk, normalize(ps), urls)
        }

        /** Builds a document from paragraphs (inverse of [lines]). */
        fun fromLines(ls: List<Line>, urls: List<String> = emptyList()): RichDoc {
            if (ls.isEmpty()) return EMPTY
            val total = ls.sumOf { it.text.length } + (ls.size - 1)
            val sb = StringBuilder(total)
            val m = IntArray(total)
            var pos = 0
            for ((k, ln) in ls.withIndex()) {
                if (k > 0) { sb.append('\n'); m[pos++] = 0 }
                sb.append(ln.text)
                System.arraycopy(ln.marks, 0, m, pos, ln.text.length)
                pos += ln.text.length
            }
            return create(sb.toString(), m, ls.map { it.para }, urls)
        }

        fun plain(text: String): RichDoc = create(text, IntArray(text.length), emptyList())
    }
}
