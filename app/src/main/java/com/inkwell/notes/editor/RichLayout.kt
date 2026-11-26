package com.inkwell.notes.editor

/**
 * Pure geometry of the text shown in the field (no Compose types, unit-testable): which prefix every
 * paragraph gets (indent, collapse chevron, bullet / number / checkbox / quote bar), which paragraphs are
 * hidden inside collapsed items, and the offset mapping between the real text and the shown text.
 *
 * Chevrons and checkboxes are blank slots here; the screen paints them as vector shapes.
 * Shown text = visible paragraphs joined by `'\n'`, each as `prefix + text`.
 */
class RichLayout(private val doc: RichDoc) {
    private val n = doc.paraCount
    private val hidden = doc.hidden
    private val nums = doc.olNumbers

    val prefixes: Array<String> = Array(n) { "" }

    /** Char index of the collapse chevron inside [prefixes] (−1 = none). */
    private val chevronIdx = IntArray(n) { -1 }

    /** Char index of the checkbox glyph inside [prefixes] (−1 = not a checklist item). */
    private val boxIdx = IntArray(n) { -1 }

    /** Shown offset where the prefix of a paragraph starts (−1 = hidden paragraph). */
    private val tPrefixStart = IntArray(n) { -1 }
    private val tTextStart = IntArray(n) { -1 }
    private val visible: IntArray

    /** Length of the shown text. */
    val length: Int

    private val o2t = IntArray(doc.text.length + 1)
    private val t2o: IntArray

    init {
        for (k in 0 until n) {
            val p = doc.paras[k]
            val sb = StringBuilder("    ".repeat(p.indent))
            if (p.type.isList && doc.hasChildren(k)) {
                chevronIdx[k] = sb.length
                sb.append(CHEVRON_SLOT) // the screen draws the triangle here
            }
            when (p.type) {
                BlockType.UL -> sb.append("•  ")
                BlockType.OL -> sb.append(nums[k]).append(".  ")
                BlockType.TASK -> { boxIdx[k] = sb.length; sb.append(BOX_SLOT) } // the screen draws the box here
                BlockType.QUOTE -> sb.append("▎ ")
                BlockType.HR -> sb.append(HR_SLOT) // the screen draws the rule across this line
                else -> {}
            }
            prefixes[k] = sb.toString()
        }

        val vis = ArrayList<Int>(n)
        var t = 0
        for (k in 0 until n) {
            if (hidden[k]) continue
            if (vis.isNotEmpty()) t += 1 // the '\n' between two shown paragraphs
            tPrefixStart[k] = t
            t += prefixes[k].length
            tTextStart[k] = t
            t += doc.paraEnd(k) - doc.starts[k]
            vis.add(k)
        }
        visible = vis.toIntArray()
        length = t

        // original → shown
        var anchor = 0
        for (k in 0 until n) {
            val s = doc.starts[k]
            val e = doc.paraEnd(k)
            if (hidden[k]) {
                for (o in s..e) o2t[o] = anchor
            } else {
                for (o in s..e) o2t[o] = tTextStart[k] + (o - s)
                anchor = tTextStart[k] + (e - s)
            }
        }

        // shown → original
        t2o = IntArray(length + 1)
        for (k in visible) {
            val s = doc.starts[k]
            val len = doc.paraEnd(k) - s
            for (x in tPrefixStart[k] until tTextStart[k]) t2o[x] = s
            for (x in 0..len) t2o[tTextStart[k] + x] = s + x
            // the '\n' index is tTextStart + len, covered above; the next prefix starts one later
        }
    }

    companion object {
        /** Blank space reserved for the collapse chevron (1 em) — drawn on a Canvas by the editor screen. */
        const val CHEVRON_SLOT = "\u2003"

        /** Blank space reserved for the checkbox (1.5 em) — drawn on a Canvas by the editor screen. */
        const val BOX_SLOT = "\u2003\u2002"

        /** One blank em so an empty divider paragraph keeps a line to draw on. */
        const val HR_SLOT = "\u2003"
    }

    fun originalToTransformed(offset: Int): Int = o2t[offset.coerceIn(0, doc.text.length)]

    fun transformedToOriginal(offset: Int): Int = t2o[offset.coerceIn(0, length)]

    /** Paragraph that owns shown offset [t] (a hidden paragraph is never returned). */
    fun paraAtTransformed(t: Int): Int {
        if (visible.isEmpty()) return 0
        var lo = 0
        var hi = visible.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) ushr 1
            if (tPrefixStart[visible[mid]] <= t) lo = mid else hi = mid - 1
        }
        return visible[lo]
    }

    /** Shown offset of the first character of paragraph [k]'s prefix (−1 when hidden). */
    fun prefixStart(k: Int): Int = tPrefixStart[k]

    /** Shown offset of the real text of paragraph [k] (−1 when hidden). */
    fun textStart(k: Int): Int = tTextStart[k]

    /** Shown offset of the collapse chevron glyph of [k], or −1. */
    fun chevronOffset(k: Int): Int = if (tPrefixStart[k] >= 0 && chevronIdx[k] >= 0) tPrefixStart[k] + chevronIdx[k] else -1

    /** Shown offset of the checkbox glyph of [k], or −1. */
    fun boxOffset(k: Int): Int = if (tPrefixStart[k] >= 0 && boxIdx[k] >= 0) tPrefixStart[k] + boxIdx[k] else -1
}
