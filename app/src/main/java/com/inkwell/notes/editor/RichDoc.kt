package com.inkwell.notes.editor

/**
 * Inline formatting, one Int per character (see [RichDoc.marks]).
 * Bits 0–7 are flags; the bits above them hold the character's link id ([LinkTable]), 0 = no link.
 */
object Mark {
    const val BOLD = 1
    const val ITALIC = 2
    const val UNDERLINE = 4
    const val STRIKE = 8
    const val SUP = 16
    const val SUB = 32
    const val HIGHLIGHT = 64
    const val CODE = 128

    /** Mask of the flag bits (everything below the link id). */
    const val FLAGS = 0xFF
    const val LINK_SHIFT = 8

    fun flags(m: Int): Int = m and FLAGS
    fun linkId(m: Int): Int = m ushr LINK_SHIFT
    fun withLink(m: Int, id: Int): Int = (m and FLAGS) or (id shl LINK_SHIFT)
}

enum class BlockType {
    P, H1, H2, H3, UL, OL, TASK, QUOTE, CODE,

    /** Divider (`<hr>`). Object paragraph: its text is exactly [OBJ]. */
    HR,

    /** Table. Object paragraph: its text is exactly [OBJ], the cells live in [Para.table]. */
    TABLE,

    /** Photo or drawing. Object paragraph: its text is exactly [OBJ], the picture lives in [Para.image]. */
    IMAGE;

    /** Bullet, numbered and checklist items — they nest by [Para.indent]. */
    val isList: Boolean get() = this == UL || this == OL || this == TASK
    val isHeading: Boolean get() = this == H1 || this == H2 || this == H3

    /** Paragraphs that are not text lines but a drawn object (divider, table). The caret never sits inside one. */
    val isObject: Boolean get() = this == HR || this == TABLE || this == IMAGE
}

enum class Align { Start, Center, End }

/**
 * Block-level attributes of one paragraph (= one line of [RichDoc.text]).
 *
 * [indent]    : plain paragraphs → left margin step; list items → nesting level (0 = top level).
 * [checked]   : checklist items only.
 * [collapsed] : list items only — hides the nested items below this one.
 * [table]     : [BlockType.TABLE] only — the cells.
 * [image]     : [BlockType.IMAGE] only — the picture.
 */
data class Para(
    val type: BlockType = BlockType.P,
    val align: Align = Align.Start,
    val indent: Int = 0,
    val checked: Boolean = false,
    val collapsed: Boolean = false,
    val table: TableData? = null,
    val image: ImageData? = null,
)

/** Result of [RichDoc.edit]. [caret] is set when the edit was rewritten and the caret must move. */
class EditResult(val doc: RichDoc, val caret: Int? = null)

/** What a text change replaced: old `[start, removedEnd)` became new `[start, insEnd)`. */
class Diff(val start: Int, val removedEnd: Int, val insEnd: Int)

/** One paragraph taken apart (text without the newline, its marks, its block attributes). */
class Chunk(val text: String, val marks: IntArray, val para: Para) {
    /** This paragraph followed on the same line by [o]'s text (block attributes of this one win). */
    fun concat(o: Chunk): Chunk = Chunk(text + o.text, marks + o.marks, para)

    /** Plain paragraph with no block formatting — safe to merge into the line around a paste. */
    val isPlain: Boolean get() = para.type == BlockType.P && para.indent == 0 && para.align == Align.Start
}

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
 * Part 5 — object paragraphs: a divider or a table is one paragraph whose text is the single character
 * [OBJ]; the screen draws the divider / lays the table over the blank lines reserved for it. The document
 * always ends with a normal paragraph (so there is somewhere to type after a table), and an object
 * paragraph whose text gets damaged is repaired ([repairObjects]).
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

    /**
     * Mark flags "active" for a caret / selection: caret → the char before it, range → flags shared by every char.
     * Link ids are not part of the result (see [linkIdAt]).
     */
    fun marksAt(start: Int, end: Int): Int {
        val a = minOf(start, end).coerceIn(0, text.length)
        val b = maxOf(start, end).coerceIn(0, text.length)
        if (a == b) {
            return Mark.flags(
                when {
                    a > 0 -> marks[a - 1]
                    text.isNotEmpty() -> marks[0]
                    else -> 0
                },
            )
        }
        var acc = -1
        for (i in a until b) {
            if (text[i] == '\n' || text[i] == OBJ) continue
            acc = if (acc == -1) Mark.flags(marks[i]) else acc and marks[i]
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

    /** Text for the word counter: object characters out, table cell text in. */
    val countText: String by lazy {
        val sb = StringBuilder(text.length)
        for (k in paras.indices) {
            val p = paras[k]
            if (k > 0) sb.append('\n')
            if (p.type.isObject) {
                p.table?.let { sb.append(it.allText) }
            } else {
                sb.append(text, starts[k], paraEnd(k))
            }
        }
        sb.toString()
    }

    /** Number of visible characters (object placeholders don't count). */
    val charCount: Int get() = text.length - text.count { it == OBJ }

    // ─── links + marked runs (Part 5) ─────────

    private fun linkOf(i: Int): Int = if (text[i] == '\n') 0 else Mark.linkId(marks[i])

    /** Link id at a caret: the character before it, else the one after it (0 = not in a link). */
    fun linkIdAt(offset: Int): Int {
        val o = offset.coerceIn(0, text.length)
        if (o > 0) {
            val l = linkOf(o - 1)
            if (l != 0) return l
        }
        if (o < text.length) return linkOf(o)
        return 0
    }

    /** `[start, end)` of the link around [offset], or null. */
    fun linkSpan(offset: Int): Pair<Int, Int>? {
        val id = linkIdAt(offset)
        if (id == 0) return null
        val o = offset.coerceIn(0, text.length)
        val seed = if (o > 0 && linkOf(o - 1) == id) o - 1 else o
        var s = seed
        while (s > 0 && linkOf(s - 1) == id) s--
        var e = seed + 1
        while (e < text.length && linkOf(e) == id) e++
        return s to e
    }

    /** `[start, end)` of the run of characters carrying [flag] around a caret, or null. */
    fun markRun(flag: Int, offset: Int): Pair<Int, Int>? {
        val o = offset.coerceIn(0, text.length)
        fun has(i: Int) = text[i] != '\n' && text[i] != OBJ && marks[i] and flag != 0
        val seed = when {
            o > 0 && has(o - 1) -> o - 1
            o < text.length && has(o) -> o
            else -> return null
        }
        var s = seed
        while (s > 0 && has(s - 1)) s--
        var e = seed + 1
        while (e < text.length && has(e)) e++
        return s to e
    }

    /** Applies (or, with [url] null, removes) a link over `[start, end)`. */
    fun setLink(start: Int, end: Int, url: String?): RichDoc {
        val a = minOf(start, end).coerceIn(0, text.length)
        val b = maxOf(start, end).coerceIn(0, text.length)
        if (a == b) return this
        val id = if (url == null) 0 else LinkTable.idOf(url)
        if (url != null && id == 0) return this
        val m = marks.copyOf()
        for (k in a until b) {
            if (text[k] == '\n' || text[k] == OBJ) continue
            m[k] = Mark.withLink(m[k], id)
        }
        return RichDoc(text, m, paras)
    }

    /** Clears [flag] over `[start, end)`. */
    fun clearMark(flag: Int, start: Int, end: Int): RichDoc {
        val a = minOf(start, end).coerceIn(0, text.length)
        val b = maxOf(start, end).coerceIn(0, text.length)
        if (a == b) return this
        val m = marks.copyOf()
        for (k in a until b) m[k] = m[k] and flag.inv()
        return RichDoc(text, m, paras)
    }

    /**
     * Replaces `[start, end)` with [s] (no line breaks) carrying [flags] and [link] — date / time, link text,
     * the inline-code placeholder. Paragraphs inside the replaced range are merged into the first one.
     */
    fun insertInline(start: Int, end: Int, s: String, flags: Int = 0, link: Int = 0): RichDoc {
        val clean = s.replace("\n", " ").replace(OBJ.toString(), "")
        val a = minOf(start, end).coerceIn(0, text.length)
        val b = maxOf(start, end).coerceIn(0, text.length)
        val i0 = paraIndexAt(a)
        val i1 = paraIndexAt(b)
        if ((i0..i1).any { paras[it].type.isObject }) return this
        val head = chunkRange(i0, starts[i0], a)
        val tail = chunkRange(i1, b, paraEnd(i1))
        val mid = Chunk(clean, IntArray(clean.length) { Mark.withLink(flags and Mark.FLAGS, link) }, paras[i0])
        val merged = head.concat(mid).concat(tail)
        val list = chunks().toMutableList()
        for (k in i1 downTo i0 + 1) list.removeAt(k)
        list[i0] = merged
        return fromChunks(list)
    }

    // ─── text edits ───────────────────────────

    /** What [newText] changed compared to [text] (anchored to [caret] for pure insertions / deletions, see [edit]). */
    fun diffFor(newText: String, caret: Int?): Diff {
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
                if (ip in 0..oldLen && ip != p && newText.removeRange(ip, ip + ins0) == text
                ) {
                    p = ip
                }
            } else if (ins0 == 0 && rem0 > 0) {
                if (caret != p && caret >= 0 && caret + rem0 <= oldLen && text.removeRange(caret, caret + rem0) == newText) {
                    p = caret
                }
            }
            removedEnd = p + rem0
            insEnd = p + ins0
        }
        return Diff(p, removedEnd, insEnd)
    }

    /**
     * Applies a text change coming from the text field: works out what was replaced, keeps [marks]
     * and [paras] in step, and applies the block rules:
     *  - Enter on an empty list item / quote leaves it (a nested item first steps out one level)
     *  - Enter in a heading continues with a paragraph; Enter in a list / checklist continues the list
     *  - Backspace at the start of a block outdents / turns it into a plain paragraph
     *  - Backspace right after a divider / table deletes it (Delete at the end of the line above does too)
     *  - Markdown shortcuts (when [shortcuts]): `# ` `## ` `### ` `> ` `- ` `1. ` `[] ` `` ``` `` (+ `/check` `/code` + Enter),
     *    and `` `text` `` → inline code when the closing backtick is typed
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

        val oldLen = text.length
        val newLen = newText.length
        val d = diffFor(newText, caret)
        val p = d.start
        val removedEnd = d.removedEnd
        val insEnd = d.insEnd
        val s = oldLen - removedEnd
        val removed = text.substring(p, removedEnd)
        val inserted = newText.substring(p, insEnd)
        val i = paraIndexAt(p)

        // Backspace at the start of a line.
        if (removed == "\n" && inserted.isEmpty()) {
            val j = i + 1
            if (j < paras.size) {
                val pi = paras[i]
                val pj = paras[j]
                if (pi.type.isObject) {
                    // Backspace at the start of the line right after a divider / table → delete the object.
                    return EditResult(removeParaAt(i), caret = starts[i])
                }
                if (pj.type.isObject) {
                    // Delete at the end of the line above an object → delete the object.
                    return EditResult(removeParaAt(j), caret = p)
                }
                if (hidden[i]) {
                    // The line before is tucked away inside a collapsed item: show it instead of merging blindly.
                    return EditResult(expandAround(i), caret = p + 1)
                }
                if (pi.type == BlockType.CODE && pj.type == BlockType.CODE) {
                    // inside a code block Backspace joins the lines (falls through to the normal merge)
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
                } else if (!cur.type.isObject) {
                    val t = line.trim()
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
            if (cur.type != BlockType.CODE && !cur.type.isObject && p == paraEnd(i)) {
                val marker = text.substring(starts[i], p)
                val target = SHORTCUTS[marker]
                if (target != null) {
                    return EditResult(clearLine(i, blockPara(cur, target)), caret = starts[i])
                }
            }
        }

        // `code` → inline code the moment the closing backtick is typed.
        if (shortcuts && inserted == "`" && removed.isEmpty()) {
            val cur = paras[i]
            if (cur.type != BlockType.CODE && !cur.type.isObject) {
                val ps = starts[i]
                val before = text.substring(ps, p)
                val q = before.lastIndexOf('`')
                if (q >= 0 && q < before.length - 1) {
                    val inner = before.substring(q + 1)
                    val open = ps + q
                    if (inner.isNotBlank() && inner.indexOf('`') < 0 && (open until p).none { marks[it] and Mark.CODE != 0 }) {
                        val nt = text.substring(0, open) + inner + text.substring(p)
                        val nm = IntArray(nt.length)
                        System.arraycopy(marks, 0, nm, 0, open)
                        for (k in inner.indices) nm[open + k] = marks[open + 1 + k] or Mark.CODE
                        System.arraycopy(marks, p, nm, open + inner.length, oldLen - p)
                        return EditResult(create(nt, nm, paras), caret = open + inner.length)
                    }
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

        // Formatting of the new characters: flags follow the typing format / the neighbours; a link and inline
        // code only carry on when BOTH neighbours have them (typing right after a link or code span leaves it).
        val inherited = typingMarks
            ?: if (removed.isNotEmpty() && p < oldLen) marks[p]
            else if (p > 0) marks[p - 1]
            else if (oldLen > 0) marks[0]
            else 0
        val leftM = if (p > 0 && text[p - 1] != '\n') marks[p - 1] else -1
        val rightM = if (removedEnd < oldLen && text[removedEnd] != '\n') marks[removedEnd] else -1
        var flags = Mark.flags(inherited)
        if (typingMarks == null && flags and Mark.CODE != 0 && !(leftM >= 0 && rightM >= 0 && Mark.flags(rightM) and Mark.CODE != 0)) {
            flags = flags and Mark.CODE.inv()
        }
        val link = if (leftM >= 0 && rightM >= 0 && Mark.linkId(leftM) == Mark.linkId(rightM)) Mark.linkId(leftM) else 0
        val ins = Mark.withLink(flags, link)

        val newMarks = IntArray(newLen)
        System.arraycopy(marks, 0, newMarks, 0, p)
        for (k in p until insEnd) newMarks[k] = if (newText[k] == '\n' || newText[k] == OBJ) 0 else ins
        System.arraycopy(marks, removedEnd, newMarks, insEnd, s)

        val fixed = repairObjects(newText, newMarks, newParas)
        if (fixed != null) {
            return EditResult(fixed.doc, caret = fixed.map[(caret ?: insEnd).coerceIn(0, newLen)])
        }
        return EditResult(create(newText, newMarks, newParas))
    }

    /** Attributes of a paragraph created by pressing Enter at the end of [cur]. */
    private fun continuation(cur: Para): Para = when {
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
        return create(newText, newMarks, l)
    }

    private fun withPara(index: Int, para: Para): RichDoc {
        val l = ArrayList(paras)
        l[index] = para
        return RichDoc(text, marks, normalize(l))
    }

    // ─── paragraphs as chunks (Part 5) ────────

    private fun chunkRange(para: Int, from: Int, to: Int): Chunk =
        Chunk(text.substring(from, to), marks.copyOfRange(from, to), paras[para])

    fun chunk(i: Int): Chunk = chunkRange(i, starts[i], paraEnd(i))

    fun chunks(): List<Chunk> = paras.indices.map { chunk(it) }

    /** Deletes paragraph [k] completely (an empty paragraph stays when it was the only one). */
    fun removeParaAt(k: Int): RichDoc {
        val l = chunks().toMutableList()
        if (k in l.indices) l.removeAt(k)
        return fromChunks(l)
    }

    // ─── objects: divider, table (Part 5) ─────

    /** Index of the object paragraph whose line contains [offset], or −1. */
    fun objectParaAt(offset: Int): Int {
        val i = paraIndexAt(offset)
        return if (paras[i].type.isObject) i else -1
    }

    /**
     * Moves a collapsed caret out of any object paragraph: forward (toward the next paragraph) when [forward],
     * otherwise to the end of the previous one. Offsets outside objects come back unchanged.
     */
    fun escapeObject(offset: Int, forward: Boolean): Int {
        var o = offset.coerceIn(0, text.length)
        var dir = forward
        repeat(paras.size + 1) {
            val i = objectParaAt(o)
            if (i < 0) return o
            o = if (dir) {
                minOf(paraEnd(i) + 1, text.length)
            } else if (i > 0) {
                starts[i] - 1
            } else {
                dir = true
                minOf(paraEnd(i) + 1, text.length)
            }
        }
        return o.coerceIn(0, text.length)
    }

    /**
     * Inserts [obj] (a divider or a table paragraph) at [offset]: on an empty line it replaces the line, at the
     * start of a text line it goes above it, otherwise below the paragraph (below the whole code block inside
     * one). A paragraph is kept after it to type in. Returns the document and the caret (in that paragraph).
     */
    fun insertObject(offset: Int, obj: Para): Pair<RichDoc, Int> {
        val o = offset.coerceIn(0, text.length)
        val i = paraIndexAt(o)
        val cur = paras[i]
        val list = chunks().toMutableList()
        val objChunk = Chunk(OBJ.toString(), IntArray(1), obj)
        val blank = Chunk("", IntArray(0), Para())
        val caretPara: Int
        if (cur.type.isObject) {
            list.add(i + 1, objChunk)
            list.add(i + 2, blank)
            caretPara = i + 2
        } else if (starts[i] == paraEnd(i) && cur.type != BlockType.CODE) {
            list[i] = objChunk
            list.add(i + 1, blank)
            caretPara = i + 1
        } else if (o == starts[i] && cur.type != BlockType.CODE) {
            list.add(i, objChunk)
            caretPara = i + 1
        } else {
            val after = codeBlockRange(i)?.last ?: i
            list.add(after + 1, objChunk)
            list.add(after + 2, blank)
            caretPara = after + 2
        }
        val nd = fromChunks(list)
        return nd to nd.starts[caretPara.coerceIn(0, nd.paraCount - 1)]
    }

    /** New picture settings (size, position) for image paragraph [i]. */
    fun setImage(i: Int, image: ImageData): RichDoc {
        if (i !in paras.indices || paras[i].type != BlockType.IMAGE) return this
        return withPara(i, paras[i].copy(image = image))
    }

    /** New cells for table paragraph [i]. */
    fun setTable(i: Int, table: TableData): RichDoc {
        if (i !in paras.indices || paras[i].type != BlockType.TABLE) return this
        return withPara(i, paras[i].copy(table = table))
    }

    // ─── code blocks (Part 5) ─────────────────

    /** Paragraph indexes of the code block that line [i] belongs to (null when it isn't code). */
    fun codeBlockRange(i: Int): IntRange? {
        if (i !in paras.indices || paras[i].type != BlockType.CODE) return null
        var a = i
        while (a > 0 && paras[a - 1].type == BlockType.CODE) a--
        var b = i
        while (b + 1 < paras.size && paras[b + 1].type == BlockType.CODE) b++
        return a..b
    }

    /** The code of the block around line [i], lines joined by `'\n'`. */
    fun codeBlockText(i: Int): String {
        val r = codeBlockRange(i) ?: return ""
        return (r.first..r.last).joinToString("\n") { text.substring(starts[it], paraEnd(it)) }
    }

    /** Replaces the code block around line [i] by one empty paragraph; returns the document and the caret. */
    fun deleteCodeBlock(i: Int): Pair<RichDoc, Int> {
        val r = codeBlockRange(i) ?: return this to starts[i.coerceIn(0, paras.size - 1)]
        val list = chunks().toMutableList()
        for (k in r.last downTo r.first) list.removeAt(k)
        list.add(r.first, Chunk("", IntArray(0), Para()))
        val nd = fromChunks(list)
        return nd to nd.starts[r.first]
    }

    /** Leaves the code block around line [i]: the caret goes to the paragraph below it (one is added when missing). */
    fun exitCodeBlock(i: Int): Pair<RichDoc, Int> {
        val r = codeBlockRange(i) ?: return this to starts[i.coerceIn(0, paras.size - 1)]
        if (r.last + 1 < paras.size) return this to starts[r.last + 1]
        val list = chunks().toMutableList()
        list.add(Chunk("", IntArray(0), Para()))
        val nd = fromChunks(list)
        return nd to nd.starts[nd.paraCount - 1]
    }

    /** Adds an empty paragraph at the end when the document ends in a code block (so there is a way out). */
    fun withParagraphAfterCode(): RichDoc {
        if (paras.last().type != BlockType.CODE) return this
        val list = chunks().toMutableList()
        list.add(Chunk("", IntArray(0), Para()))
        return fromChunks(list)
    }

    // ─── paste (Part 5) ───────────────────────

    /**
     * Replaces `[start, end)` with the paragraphs of [frag] (parsed markdown / HTML / detected code). A plain
     * first / last paragraph of the fragment merges into the text around the caret; everything else becomes its
     * own paragraph. Returns the document and the caret (end of the pasted content).
     */
    fun replaceWithFragment(start: Int, end: Int, frag: RichDoc): Pair<RichDoc, Int> {
        val a = minOf(start, end).coerceIn(0, text.length)
        val b = maxOf(start, end).coerceIn(0, text.length)
        val i0 = paraIndexAt(a)
        val i1 = paraIndexAt(b)
        if (paras[i0].type.isObject || paras[i1].type.isObject) return this to a

        val hostHead = chunkRange(i0, starts[i0], a)
        val hostTail = chunkRange(i1, b, paraEnd(i1))
        val mid = ArrayList(frag.chunks())

        // The first pasted line, when it is plain, continues the paragraph the caret is in (it keeps that
        // paragraph's block type, e.g. a bullet). Otherwise the head stays on its own — or vanishes when empty.
        var headC: Chunk? = hostHead
        if (mid.isNotEmpty() && mid.first().isPlain) {
            headC = hostHead.concat(mid.removeAt(0))
        } else if (hostHead.text.isEmpty()) {
            headC = null
        }

        val out = ArrayList<Chunk>()
        if (headC != null) out.add(headC)
        out.addAll(mid)

        // The caret goes to the end of what was pasted, i.e. just before the text that followed the caret.
        var caretIdx = out.size - 1
        var caretIn = if (caretIdx >= 0) out[caretIdx].text.length else 0

        if (hostTail.text.isNotEmpty()) {
            if (out.isNotEmpty() && (mid.isEmpty() || mid.last().isPlain)) {
                out[out.size - 1] = out.last().concat(hostTail) // the paragraph simply continues
            } else {
                out.add(hostTail)
            }
        }
        if (out.isEmpty()) out.add(Chunk("", IntArray(0), Para()))
        if (caretIdx < 0) {
            caretIdx = 0
            caretIn = 0
        }

        val before = (0 until i0).map { chunk(it) }
        val after = (i1 + 1 until paras.size).map { chunk(it) }
        val nd = fromChunks(before + out + after)
        val ci = (before.size + caretIdx).coerceIn(0, nd.paraCount - 1)
        val caret = (nd.starts[ci] + caretIn).coerceAtMost(nd.paraEnd(ci))
        return nd to caret
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
            if (text[k] == '\n' || text[k] == OBJ) continue
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
            if (text[k] == '\n' || text[k] == OBJ) continue
            m[k] = if (all) m[k] and mark.inv() else (m[k] or mark) and exclusive.inv()
        }
        return RichDoc(text, m, paras)
    }

    /** Heading / list / checklist / quote button: applies [type], or goes back to a paragraph when all selected lines already have it. */
    fun setBlock(type: BlockType, start: Int, end: Int): RichDoc {
        val r = paraRange(start, end)
        val allSame = r.all { paras[it].type == type }
        val target = if (allSame) BlockType.P else type
        return mapParas(r) { blockPara(it, target) }
    }

    fun setAlign(align: Align, start: Int, end: Int): RichDoc {
        val r = paraRange(start, end)
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
        return RichDoc(text, marks, normalize(l))
    }

    /**
     * "Clear format": drops inline marks in the selection (links stay) and resets the touched lines to plain
     * paragraphs. Dividers and tables are left alone.
     */
    fun clearFormat(start: Int, end: Int): RichDoc {
        val a = minOf(start, end).coerceIn(0, text.length)
        val b = maxOf(start, end).coerceIn(0, text.length)
        val m = marks.copyOf()
        for (k in a until b) m[k] = m[k] and Mark.FLAGS.inv()
        val l = ArrayList(paras)
        for (k in paraRange(start, end)) if (!l[k].type.isObject) l[k] = Para()
        return RichDoc(text, m, normalize(l))
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
        return if (changed) RichDoc(text, marks, normalize(l)) else this
    }

    private fun mapParas(range: IntRange, f: (Para) -> Para): RichDoc {
        val l = ArrayList(paras)
        for (k in range) if (!l[k].type.isObject) l[k] = f(l[k])
        return RichDoc(text, marks, normalize(l))
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
                }
                if (q.checked && q.type != BlockType.TASK) q = q.copy(checked = false)
                if (q !== p) changed = true
                out.add(q)
            }
            return if (changed) out else paras
        }

        /**
         * Builds a document, repairing mismatched array sizes instead of throwing. A document never ends with a
         * divider / table: an empty paragraph is added so there is always somewhere to type.
         */
        fun create(text: String, marks: IntArray, paras: List<Para>): RichDoc {
            var t = text
            var m = if (marks.size == text.length) marks else marks.copyOf(text.length)
            val need = t.count { it == '\n' } + 1
            var p: List<Para> = when {
                paras.size == need -> paras
                paras.size > need -> paras.subList(0, need)
                else -> paras + List(need - paras.size) { Para() }
            }
            if (p.last().type.isObject) {
                t += "\n"
                m = m.copyOf(t.length)
                p = p + Para()
            }
            return RichDoc(t, m, normalize(p))
        }

        fun plain(text: String): RichDoc = create(text, IntArray(text.length), emptyList())

        /** Joins paragraphs (each without a newline) into a document. */
        fun fromChunks(list: List<Chunk>): RichDoc {
            if (list.isEmpty()) return EMPTY
            val sb = StringBuilder()
            val total = list.sumOf { it.text.length } + list.size - 1
            val m = IntArray(total)
            var pos = 0
            for ((idx, c) in list.withIndex()) {
                if (idx > 0) { sb.append('\n'); pos++ }
                sb.append(c.text)
                System.arraycopy(c.marks, 0, m, pos, c.text.length)
                pos += c.text.length
            }
            return create(sb.toString(), m, list.map { it.para })
        }

        private class Repaired(val doc: RichDoc, val map: IntArray)

        /**
         * Object paragraphs must be exactly [OBJ], and [OBJ] must not appear anywhere else. After an edit that broke
         * this (typing into a table line, pasting text that holds the placeholder, deleting only the placeholder) the
         * stray text becomes ordinary paragraphs around the object. Returns null when everything is fine; [Repaired.map]
         * maps old offsets to new ones so the caret can follow.
         */
        private fun repairObjects(text: String, marks: IntArray, paras: List<Para>): Repaired? {
            if (text.indexOf(OBJ) < 0 && paras.none { it.type.isObject }) return null
            val lines = text.split('\n')
            var bad = false
            for (k in lines.indices) {
                val ty = paras.getOrNull(k)?.type ?: BlockType.P
                val l = lines[k]
                if (ty.isObject) {
                    if (l.length != 1 || l[0] != OBJ) { bad = true; break }
                } else if (l.indexOf(OBJ) >= 0) { bad = true; break }
            }
            if (!bad) return null

            val outT = StringBuilder()
            val outM = ArrayList<Int>()
            val outP = ArrayList<Para>()
            val map = IntArray(text.length + 1)

            fun emit(srcStart: Int, piece: String, para: Para, stripObj: Boolean) {
                if (outP.isNotEmpty()) { outT.append('\n'); outM.add(0) }
                for (x in piece.indices) {
                    val ch = piece[x]
                    map[srcStart + x] = outT.length
                    if (stripObj && ch == OBJ) continue
                    outT.append(ch)
                    outM.add(marks[srcStart + x])
                }
                outP.add(para)
            }

            var a = 0
            for (k in lines.indices) {
                val l = lines[k]
                val p = paras.getOrNull(k) ?: Para()
                if (p.type.isObject) {
                    val q = l.indexOf(OBJ)
                    if (q < 0) {
                        emit(a, l, Para(), true)
                    } else {
                        if (q > 0) emit(a, l.substring(0, q), Para(), true)
                        val endBefore = outT.length
                        emit(a + q, OBJ.toString(), p, false)
                        if (q > 0) map[a + q] = endBefore // a caret right before the object stays with the text typed before it
                        val rest = l.substring(q + 1)
                        if (rest.isNotEmpty()) emit(a + q + 1, rest, Para(), true)
                    }
                } else {
                    emit(a, l, p, true)
                }
                map[a + l.length] = outT.length
                a += l.length + 1
            }
            val doc = create(outT.toString(), outM.toIntArray(), outP)
            return Repaired(doc, map)
        }
    }
}
