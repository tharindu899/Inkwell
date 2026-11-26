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
    const val CODE = 128

    /** Bits 0–7 of a mark Int are the formatting flags above; bits 8+ hold the link id (0 = no link, see [LinkTable]). */
    const val FORMAT_MASK = 0xFF
    const val LINK_SHIFT = 8

    fun linkId(m: Int): Int = m ushr LINK_SHIFT
    fun withLink(m: Int, id: Int): Int = (m and FORMAT_MASK) or (id shl LINK_SHIFT)
}

/**
 * Interns link URLs so a character's link fits in the spare bits of its mark Int. Ids are never reused, so undo /
 * redo snapshots stay valid; the table only grows by one entry per distinct URL typed in this process.
 */
object LinkTable {
    private val urls = ArrayList<String>()
    private val ids = HashMap<String, Int>()

    @Synchronized
    fun idFor(url: String): Int = ids.getOrPut(url) {
        urls.add(url)
        urls.size // ids start at 1
    }

    @Synchronized
    fun urlOf(id: Int): String? = if (id in 1..urls.size) urls[id - 1] else null
}

enum class BlockType {
    P, H1, H2, H3, UL, OL, TASK, QUOTE, CODE,

    /** Divider: an empty paragraph the screen draws as a rule. */
    HR,

    /** One table row: cells separated by `'\t'`. Edited through the table editor, never by typing structure. */
    TABLE;

    /** Bullet, numbered and checklist items — they nest by [Para.indent]. */
    val isList: Boolean get() = this == UL || this == OL || this == TASK
    val isHeading: Boolean get() = this == H1 || this == H2 || this == H3

    /** Blocks that block-format buttons (headings, lists, align, indent, clear format) leave alone. */
    val isAtomic: Boolean get() = this == HR || this == TABLE
}

/** One paragraph on its way into a document (used by paste, divider, table and code-block operations). */
class Block(val text: String, val marks: IntArray, val para: Para)

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
)

/** Result of [RichDoc.edit]. [caret] is set when the edit was rewritten and the caret must move. */
class EditResult(val doc: RichDoc, val caret: Int? = null)

/**
 * The editor's document model (Part 2, extended in Part 3).
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
            s = oldLen - removedEnd
        }
        val removed = text.substring(p, removedEnd)
        val inserted = newText.substring(p, insEnd)
        val i = paraIndexAt(p)

        // Table rows: typing inside a cell is fine, but cell separators and row breaks are only changed by the
        // table editor, so any edit that would add / remove them is refused (the field snaps back).
        val tableTouched = (i..paraIndexAt(removedEnd)).any { paras[it].type == BlockType.TABLE }
        if (tableTouched) {
            val structural = removed.any { it == '\n' || it == '\t' } || inserted.any { it == '\n' || it == '\t' }
            if (structural) {
                val onlyInsertedBreaks = removed.none { it == '\n' || it == '\t' } && paras[i].type == BlockType.TABLE
                if (onlyInsertedBreaks && inserted != "\n") {
                    // pasted text with line breaks / tabs inside a cell → flatten to spaces
                    val clean = inserted.replace('\n', ' ').replace('\t', ' ')
                    return edit(text.substring(0, p) + clean + text.substring(removedEnd), typingMarks, false, null)
                }
                return EditResult(this, caret = if (removed.isEmpty()) p else removedEnd.coerceAtMost(oldLen))
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
            if (paras[i].type == BlockType.HR && removed == "\n") {
                newParas.removeAt(i) // Backspace on the line under a divider removes the divider
            } else {
                repeat(removedNl) { if (i + 1 < newParas.size) newParas.removeAt(i + 1) }
            }
        }
        if (insertedNl == 0 && inserted.isNotEmpty() && paras[i].type == BlockType.HR) {
            newParas[i] = Para() // typing on a divider line turns it into a normal paragraph
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
    private fun continuation(cur: Para): Para = when {
        cur.type.isHeading -> cur.copy(type = BlockType.P)
        cur.type.isList -> cur.copy(checked = false, collapsed = false)
        cur.type == BlockType.HR || cur.type == BlockType.TABLE -> Para()
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
            if (p.type.isAtomic) continue
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

    /** "Clear format": drops inline marks in the selection and resets the touched lines to plain paragraphs. */
    fun clearFormat(start: Int, end: Int): RichDoc {
        val a = minOf(start, end).coerceIn(0, text.length)
        val b = maxOf(start, end).coerceIn(0, text.length)
        val m = marks.copyOf()
        for (k in a until b) m[k] = 0
        val l = ArrayList(paras)
        for (k in paraRange(start, end)) if (!l[k].type.isAtomic) l[k] = Para()
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

    // ─── links (Part 5) ───────────────────────

    /** The link run under a caret / selection as [start, end) offsets, or null when there is no link there. */
    fun linkSpanAt(start: Int, end: Int): IntRange? {
        val a = minOf(start, end).coerceIn(0, text.length)
        val b = maxOf(start, end).coerceIn(0, text.length)
        val probes: IntProgression = if (a == b) (a - 1)..a else a until b
        var hit = -1
        for (k in probes) {
            if (k in 0 until text.length && text[k] != '\n' && Mark.linkId(marks[k]) != 0) { hit = k; break }
        }
        if (hit == -1) return null
        val id = Mark.linkId(marks[hit])
        var lo = hit
        var hi = hit + 1
        while (lo > 0 && text[lo - 1] != '\n' && Mark.linkId(marks[lo - 1]) == id) lo--
        while (hi < text.length && text[hi] != '\n' && Mark.linkId(marks[hi]) == id) hi++
        return lo until hi // inclusive-start range; end offset = last + 1
    }

    /** URL of the link at [offset] (the character at / before it), or null. */
    fun urlAt(offset: Int): String? {
        val span = linkSpanAt(offset, offset) ?: return null
        return LinkTable.urlOf(Mark.linkId(marks[span.first]))
    }

    /** Sets ([url] != null) or clears the link over [start, end). */
    fun setLink(start: Int, end: Int, url: String?): RichDoc {
        val a = minOf(start, end).coerceIn(0, text.length)
        val b = maxOf(start, end).coerceIn(0, text.length)
        if (a == b) return this
        val id = if (url == null) 0 else LinkTable.idFor(url)
        val m = marks.copyOf()
        for (k in a until b) {
            if (text[k] == '\n') continue
            m[k] = Mark.withLink(m[k], id)
        }
        return RichDoc(text, m, paras)
    }

    // ─── paragraph-level splicing (Part 5) ────

    private fun blockAt(k: Int): Block =
        Block(text.substring(starts[k], paraEnd(k)), marks.copyOfRange(starts[k], paraEnd(k)), paras[k])

    /** Replaces paragraphs [from, toExclusive) with [blocks] (use `from == toExclusive` to insert). */
    fun replaceParas(from: Int, toExclusive: Int, blocks: List<Block>): RichDoc {
        val f = from.coerceIn(0, paras.size)
        val t = toExclusive.coerceIn(f, paras.size)
        val all = ArrayList<Block>()
        for (k in 0 until f) all.add(blockAt(k))
        all.addAll(blocks)
        for (k in t until paras.size) all.add(blockAt(k))
        if (all.isEmpty()) all.add(Block("", IntArray(0), Para()))
        return fromBlocks(all)
    }

    /** Contiguous table rows around paragraph [i], or null when [i] isn't a table row. */
    fun tableRun(i: Int): IntRange? {
        if (i !in paras.indices || paras[i].type != BlockType.TABLE) return null
        var a = i
        var b = i
        while (a > 0 && paras[a - 1].type == BlockType.TABLE) a--
        while (b + 1 < paras.size && paras[b + 1].type == BlockType.TABLE) b++
        return a..b
    }

    /** Cell texts of the table rows in [run]. */
    fun tableRows(run: IntRange): List<List<String>> =
        run.map { text.substring(starts[it], paraEnd(it)).split('\t') }

    /** Contiguous code lines around paragraph [i], or null when [i] isn't code. */
    fun codeRun(i: Int): IntRange? {
        if (i !in paras.indices || paras[i].type != BlockType.CODE) return null
        var a = i
        var b = i
        while (a > 0 && paras[a - 1].type == BlockType.CODE) a--
        while (b + 1 < paras.size && paras[b + 1].type == BlockType.CODE) b++
        return a..b
    }

    /** The text of paragraphs in [run] joined by newlines. */
    fun textOf(run: IntRange): String = run.joinToString("\n") { text.substring(starts[it], paraEnd(it)) }

    /** Text + marks of paragraph [i], split at [offset] (used by paste). */
    fun paraBefore(i: Int, offset: Int): Block {
        val e = offset.coerceIn(starts[i], paraEnd(i))
        return Block(text.substring(starts[i], e), marks.copyOfRange(starts[i], e), paras[i])
    }

    fun paraAfter(i: Int, offset: Int): Block {
        val s0 = offset.coerceIn(starts[i], paraEnd(i))
        return Block(text.substring(s0, paraEnd(i)), marks.copyOfRange(s0, paraEnd(i)), paras[i])
    }

    private fun mapParas(range: IntRange, f: (Para) -> Para): RichDoc {
        val l = ArrayList(paras)
        for (k in range) if (!l[k].type.isAtomic) l[k] = f(l[k])
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

        /** Builds a document, repairing mismatched array sizes instead of throwing. */
        fun create(text: String, marks: IntArray, paras: List<Para>): RichDoc {
            val m = if (marks.size == text.length) marks else marks.copyOf(text.length)
            val need = text.count { it == '\n' } + 1
            val p: List<Para> = when {
                paras.size == need -> paras
                paras.size > need -> paras.subList(0, need)
                else -> paras + List(need - paras.size) { Para() }
            }
            return RichDoc(text, m, normalize(p))
        }

        fun plain(text: String): RichDoc = create(text, IntArray(text.length), emptyList())

        /** Joins [blocks] (one paragraph each) into a document. */
        fun fromBlocks(blocks: List<Block>): RichDoc {
            if (blocks.isEmpty()) return EMPTY
            val sb = StringBuilder()
            val m = ArrayList<Int>()
            for ((k, b) in blocks.withIndex()) {
                if (k > 0) { sb.append('\n'); m.add(0) }
                sb.append(b.text)
                for (x in b.marks) m.add(x)
            }
            return create(sb.toString(), m.toIntArray(), blocks.map { it.para })
        }
    }
}
