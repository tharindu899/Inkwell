package com.inkwell.notes.editor

/** Inline formatting flags, one Int bitmask per character (see [RichDoc.marks]). */
object Mark {
    const val BOLD = 1
    const val ITALIC = 2
    const val UNDERLINE = 4
    const val STRIKE = 8
    const val SUP = 16
    const val SUB = 32
    const val HIGHLIGHT = 64
}

enum class BlockType {
    P, H1, H2, H3, UL, OL, QUOTE;

    val isList: Boolean get() = this == UL || this == OL
    val isHeading: Boolean get() = this == H1 || this == H2 || this == H3
}

enum class Align { Start, Center, End }

/** Block-level attributes of one paragraph (= one line of [RichDoc.text]). */
data class Para(
    val type: BlockType = BlockType.P,
    val align: Align = Align.Start,
    val indent: Int = 0,
)

/** Result of [RichDoc.edit]. [caret] is set when the edit was rewritten and the caret must move. */
class EditResult(val doc: RichDoc, val caret: Int? = null)

/**
 * The editor's document model (Part 2).
 *
 * Deliberately flat so it maps 1:1 onto a Compose text field:
 *  - [text]  : the whole body, paragraphs separated by `'\n'`
 *  - [marks] : inline formatting per character (same length as [text])
 *  - [paras] : block attributes per paragraph (count = number of `'\n'` + 1)
 *
 * Immutable — every operation returns a new document, which is what makes undo / redo trivial.
 */
class RichDoc private constructor(
    val text: String,
    val marks: IntArray,
    val paras: List<Para>,
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
     * and [paras] in step, and applies the block rules (Enter on an empty list item leaves the list,
     * Enter in a heading continues with a paragraph, Backspace at the start of a block turns it into
     * a plain paragraph).
     *
     * [typingMarks] = formatting for newly typed characters (toolbar toggles with a collapsed caret);
     * null = inherit from the neighbouring character.
     */
    fun edit(newText: String, typingMarks: Int?): EditResult {
        if (newText == text) return EditResult(this)

        val oldLen = text.length
        val newLen = newText.length
        val minLen = minOf(oldLen, newLen)
        var p = 0
        while (p < minLen && text[p] == newText[p]) p++
        var s = 0
        while (s < minLen - p && text[oldLen - 1 - s] == newText[newLen - 1 - s]) s++

        val removedEnd = oldLen - s
        val insEnd = newLen - s
        val removed = text.substring(p, removedEnd)
        val inserted = newText.substring(p, insEnd)
        val i = paraIndexAt(p)

        // Backspace at the start of a styled block → plain paragraph, newline stays.
        if (removed == "\n" && inserted.isEmpty()) {
            val j = i + 1
            if (j < paras.size && (paras[j].type != BlockType.P || paras[j].indent > 0)) {
                return EditResult(withPara(j, paras[j].copy(type = BlockType.P, indent = 0)), caret = p + 1)
            }
        }

        // Enter on an empty list item / quote → leave it.
        if (inserted == "\n" && removed.isEmpty()) {
            val cur = paras[i]
            val empty = starts[i] == paraEnd(i)
            if (empty && (cur.type.isList || cur.type == BlockType.QUOTE)) {
                return EditResult(withPara(i, cur.copy(type = BlockType.P, indent = 0)), caret = p)
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
            val atStart = p == starts[i] && p < paraEnd(i)
            if (inserted == "\n" && removed.isEmpty() && atStart) {
                // Enter at the very start of a line: empty line above, the text keeps its block type.
                val above = if (cur.type.isHeading) cur.copy(type = BlockType.P) else cur
                newParas.add(i, above)
            } else {
                val next = continuation(cur)
                repeat(insertedNl) { newParas.add(i + 1, next) }
            }
        }

        val ins = typingMarks
            ?: if (removed.isNotEmpty() && p < oldLen) marks[p]
            else if (p > 0) marks[p - 1]
            else if (oldLen > 0) marks[0]
            else 0

        val newMarks = IntArray(newLen)
        System.arraycopy(marks, 0, newMarks, 0, p)
        for (k in p until insEnd) newMarks[k] = ins
        System.arraycopy(marks, removedEnd, newMarks, insEnd, s)

        return EditResult(create(newText, newMarks, newParas))
    }

    /** Attributes of a paragraph created by pressing Enter at the end of [cur]. */
    private fun continuation(cur: Para): Para =
        if (cur.type.isHeading) cur.copy(type = BlockType.P) else cur

    private fun withPara(index: Int, para: Para): RichDoc {
        val l = ArrayList(paras)
        l[index] = para
        return RichDoc(text, marks, l)
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
        return RichDoc(text, m, paras)
    }

    /** Heading / list / quote button: applies [type], or goes back to a paragraph when all selected lines already have it. */
    fun setBlock(type: BlockType, start: Int, end: Int): RichDoc {
        val r = paraRange(start, end)
        val allSame = r.all { paras[it].type == type }
        val target = if (allSame) BlockType.P else type
        return mapParas(r) { it.copy(type = target) }
    }

    fun setAlign(align: Align, start: Int, end: Int): RichDoc {
        val r = paraRange(start, end)
        val allSame = r.all { paras[it].align == align }
        val target = if (allSame) Align.Start else align
        return mapParas(r) { it.copy(align = target) }
    }

    fun changeIndent(delta: Int, start: Int, end: Int): RichDoc =
        mapParas(paraRange(start, end)) { it.copy(indent = (it.indent + delta).coerceIn(0, MAX_INDENT)) }

    /** "Clear format": drops inline marks in the selection and resets the touched lines to plain paragraphs. */
    fun clearFormat(start: Int, end: Int): RichDoc {
        val a = minOf(start, end).coerceIn(0, text.length)
        val b = maxOf(start, end).coerceIn(0, text.length)
        val m = marks.copyOf()
        for (k in a until b) m[k] = 0
        val l = ArrayList(paras)
        for (k in paraRange(start, end)) l[k] = Para()
        return RichDoc(text, m, l)
    }

    private fun mapParas(range: IntRange, f: (Para) -> Para): RichDoc {
        val l = ArrayList(paras)
        for (k in range) l[k] = f(l[k])
        return RichDoc(text, marks, l)
    }

    companion object {
        const val MAX_INDENT = 5

        val EMPTY: RichDoc = create("", IntArray(0), listOf(Para()))

        /** Builds a document, repairing mismatched array sizes instead of throwing. */
        fun create(text: String, marks: IntArray, paras: List<Para>): RichDoc {
            val m = if (marks.size == text.length) marks else marks.copyOf(text.length)
            val need = text.count { it == '\n' } + 1
            val p: List<Para> = when {
                paras.size == need -> paras
                paras.size > need -> paras.subList(0, need)
                else -> paras + List(need - paras.size) { Para() }
            }
            return RichDoc(text, m, p)
        }

        fun plain(text: String): RichDoc = create(text, IntArray(text.length), emptyList())
    }
}
