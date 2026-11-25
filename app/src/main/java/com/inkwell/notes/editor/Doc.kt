package com.inkwell.notes.editor

import kotlin.math.max
import kotlin.math.min

/**
 * Editor Part 2 — the document model.
 *
 * The note body is ONE string (lines separated by '\n') plus two side tables:
 *  - [blocks]: one [BlockFmt] per line (heading / list / quote, alignment, indent)
 *  - [spans]:  inline formatting as character ranges over the whole string
 *
 * Invariant: `blocks.size == text.count { it == '\n' } + 1`, and spans are normalised
 * (per kind: sorted, non-empty, non-overlapping, non-adjacent). Everything here is pure Kotlin
 * (no Compose) so it can be unit-tested on the JVM.
 */
enum class BlockType {
    P, H1, H2, H3, UL, OL, QUOTE;

    val isList: Boolean get() = this == UL || this == OL
    val isHeading: Boolean get() = this == H1 || this == H2 || this == H3
}

enum class Align { LEFT, CENTER, RIGHT }

data class BlockFmt(
    val type: BlockType = BlockType.P,
    val align: Align = Align.LEFT,
    val indent: Int = 0,
)

enum class SpanKind { BOLD, ITALIC, UNDERLINE, STRIKE, SUP, SUB, HIGHLIGHT }

/** [start] inclusive, [end] exclusive. */
data class Span(val kind: SpanKind, val start: Int, val end: Int)

class Doc private constructor(
    val text: String,
    val blocks: List<BlockFmt>,
    val spans: List<Span>,
) {
    /** Start offset of every line. */
    val lineStarts: IntArray by lazy {
        val out = IntArray(blocks.size)
        var line = 1
        for (i in text.indices) {
            if (text[i] == '\n' && line < out.size) out[line++] = i + 1
        }
        out
    }

    val lineCount: Int get() = blocks.size

    fun lineEnd(i: Int): Int = if (i + 1 < lineStarts.size) lineStarts[i + 1] - 1 else text.length

    /** Line that holds [offset] (an offset at a line's end belongs to that line). */
    fun lineIndexAt(offset: Int): Int {
        var lo = 0
        var hi = lineStarts.size - 1
        val o = offset.coerceIn(0, text.length)
        while (lo < hi) {
            val mid = (lo + hi + 1) ushr 1
            if (lineStarts[mid] <= o) lo = mid else hi = mid - 1
        }
        return lo
    }

    /** Lines touched by the selection [s, e] (a selection that ends exactly at a line start excludes that line). */
    fun lineRange(s: Int, e: Int): IntRange {
        val a = min(s, e)
        val b = max(s, e)
        val first = lineIndexAt(a)
        val last = if (b > a) lineIndexAt(b - 1) else first
        return first..max(first, last)
    }

    fun withBlocks(newBlocks: List<BlockFmt>): Doc = Doc(text, newBlocks, spans)

    fun withSpans(newSpans: List<Span>): Doc = Doc(text, blocks, normalizeSpans(newSpans, text.length))

    override fun equals(other: Any?): Boolean =
        other is Doc && other.text == text && other.blocks == blocks && other.spans == spans

    override fun hashCode(): Int = (text.hashCode() * 31 + blocks.hashCode()) * 31 + spans.hashCode()

    companion object {
        val EMPTY: Doc = Doc("", listOf(BlockFmt()), emptyList())

        /** Builds a valid Doc: fixes the block count and normalises spans. */
        fun of(text: String, blocks: List<BlockFmt>, spans: List<Span>): Doc {
            val lines = text.count { it == '\n' } + 1
            val fixed = when {
                blocks.size == lines -> blocks
                blocks.size > lines -> blocks.take(lines)
                else -> blocks + List(lines - blocks.size) { blocks.lastOrNull() ?: BlockFmt() }
            }
            return Doc(text, fixed, normalizeSpans(spans, text.length))
        }

        fun plain(text: String): Doc = of(text, emptyList(), emptyList())
    }
}

/** Per kind: sort, clamp, drop empties, merge overlapping / touching ranges. */
fun normalizeSpans(spans: List<Span>, textLen: Int): List<Span> {
    val out = ArrayList<Span>()
    for (kind in SpanKind.entries) {
        val list = spans.asSequence()
            .filter { it.kind == kind }
            .map { Span(kind, it.start.coerceIn(0, textLen), it.end.coerceIn(0, textLen)) }
            .filter { it.end > it.start }
            .sortedBy { it.start }
            .toList()
        var cs = -1
        var ce = -1
        for (sp in list) {
            if (cs < 0) {
                cs = sp.start; ce = sp.end
            } else if (sp.start <= ce) {
                ce = max(ce, sp.end)
            } else {
                out.add(Span(kind, cs, ce)); cs = sp.start; ce = sp.end
            }
        }
        if (cs >= 0) out.add(Span(kind, cs, ce))
    }
    return out
}

object DocOps {

    // ─── queries ─────────────────────────────────────────

    /** Every non-newline character of [s, e) carries [kind]. */
    fun covers(doc: Doc, kind: SpanKind, s: Int, e: Int): Boolean {
        val a = min(s, e).coerceIn(0, doc.text.length)
        val b = max(s, e).coerceIn(0, doc.text.length)
        if (a >= b) return false
        var any = false
        var i = a
        while (i < b) {
            if (doc.text[i] == '\n') { i++; continue }
            var j = i
            while (j < b && doc.text[j] != '\n') j++
            any = true
            if (doc.spans.none { it.kind == kind && it.start <= i && it.end >= j }) return false
            i = j
        }
        return any
    }

    /** Character just before the cursor carries [kind] (what the next typed character would inherit). */
    fun coversBefore(doc: Doc, kind: SpanKind, pos: Int): Boolean {
        if (pos <= 0 || pos > doc.text.length || doc.text[pos - 1] == '\n') return false
        return doc.spans.any { it.kind == kind && it.start <= pos - 1 && it.end >= pos }
    }

    // ─── span edits ──────────────────────────────────────

    fun addRange(spans: List<Span>, kind: SpanKind, s: Int, e: Int): List<Span> =
        if (e <= s) spans else spans + Span(kind, s, e)

    fun removeRange(spans: List<Span>, kind: SpanKind, s: Int, e: Int): List<Span> {
        if (e <= s) return spans
        val out = ArrayList<Span>()
        for (sp in spans) {
            if (sp.kind != kind || sp.end <= s || sp.start >= e) {
                out.add(sp)
                continue
            }
            if (sp.start < s) out.add(Span(kind, sp.start, s))
            if (sp.end > e) out.add(Span(kind, e, sp.end))
        }
        return out
    }

    /** Bold / italic / … on a non-empty selection: remove when fully set, add otherwise. */
    fun toggleSpan(doc: Doc, kind: SpanKind, s0: Int, e0: Int): Doc {
        val s = min(s0, e0)
        val e = max(s0, e0)
        if (s >= e) return doc
        var spans = doc.spans
        if (covers(doc, kind, s, e)) {
            spans = removeRange(spans, kind, s, e)
        } else {
            spans = addRange(spans, kind, s, e)
            if (kind == SpanKind.SUP) spans = removeRange(spans, SpanKind.SUB, s, e)
            if (kind == SpanKind.SUB) spans = removeRange(spans, SpanKind.SUP, s, e)
        }
        return doc.withSpans(spans)
    }

    // ─── block edits ─────────────────────────────────────

    private fun mapLines(doc: Doc, s: Int, e: Int, f: (BlockFmt) -> BlockFmt): Doc {
        val r = doc.lineRange(s, e)
        val nb = doc.blocks.mapIndexed { i, b -> if (i in r) f(b) else b }
        return doc.withBlocks(nb)
    }

    /** Same type on every selected line → back to paragraph (toggle); otherwise set it. */
    fun setBlockType(doc: Doc, s: Int, e: Int, type: BlockType): Doc {
        val r = doc.lineRange(s, e)
        val all = r.all { doc.blocks[it].type == type }
        val target = if (all) BlockType.P else type
        return mapLines(doc, s, e) { it.copy(type = target) }
    }

    fun setAlign(doc: Doc, s: Int, e: Int, align: Align): Doc {
        val r = doc.lineRange(s, e)
        val all = r.all { doc.blocks[it].align == align }
        val target = if (all) Align.LEFT else align
        return mapLines(doc, s, e) { it.copy(align = target) }
    }

    const val MAX_INDENT = 6

    fun indent(doc: Doc, s: Int, e: Int, delta: Int): Doc =
        mapLines(doc, s, e) { it.copy(indent = (it.indent + delta).coerceIn(0, MAX_INDENT)) }

    /** Remove inline formatting from the selection (the whole line when collapsed) and reset the selected lines to plain paragraphs. */
    fun clearFormat(doc: Doc, s0: Int, e0: Int): Doc {
        val s = min(s0, e0)
        val e = max(s0, e0)
        val r = doc.lineRange(s, e)
        val a = if (s == e) doc.lineStarts[r.first] else s
        val b = if (s == e) doc.lineEnd(r.last) else e
        var spans = doc.spans
        for (k in SpanKind.entries) spans = removeRange(spans, k, a, b)
        val nb = doc.blocks.mapIndexed { i, f -> if (i in r) BlockFmt() else f }
        return Doc.of(doc.text, nb, spans)
    }

    // ─── typing ──────────────────────────────────────────

    /**
     * The text field reported [newText]; derive what changed and carry blocks + spans across.
     * [cursor] is the new caret (used to disambiguate repeated characters), [pending] are
     * toolbar toggles set with a collapsed caret ("bold on" before typing).
     */
    fun applyTextEdit(old: Doc, newText: String, cursor: Int, pending: Map<SpanKind, Boolean>): Doc {
        val oldT = old.text
        if (oldT == newText) return old
        val oldLen = oldT.length
        val newLen = newText.length

        // Generic window: longest common prefix, then longest common suffix.
        var p0 = 0
        val lim = min(oldLen, newLen)
        while (p0 < lim && oldT[p0] == newText[p0]) p0++
        var s0 = 0
        val lim2 = lim - p0
        while (s0 < lim2 && oldT[oldLen - 1 - s0] == newText[newLen - 1 - s0]) s0++

        var a = p0
        var sfx = s0
        // Caret-based window: the inserted text ends at the caret.
        if (cursor in 0..newLen) {
            val cs = newLen - cursor
            if (cs <= oldLen && oldT.regionMatches(oldLen - cs, newText, cursor, cs)) {
                val limit = min(oldLen - cs, newLen - cs)
                var pc = 0
                while (pc < limit && oldT[pc] == newText[pc]) pc++
                val sizeCursor = (oldLen - pc - cs) + (newLen - pc - cs)
                val sizeGeneric = (oldLen - p0 - s0) + (newLen - p0 - s0)
                if (sizeCursor <= sizeGeneric) {
                    a = pc
                    sfx = cs
                }
            }
        }
        val b = oldLen - sfx          // end of the removed range in the old text
        val ins = newLen - sfx - a    // inserted length
        val removed = oldT.substring(a, b)
        val inserted = newText.substring(a, a + ins)
        val delta = ins - (b - a)

        // ── inline spans ──
        fun mapStart(x: Int): Int = when {
            x < a -> x
            x >= b -> x + delta
            else -> a + ins
        }
        fun mapEnd(y: Int): Int = when {
            y <= a -> y
            y >= b -> y + delta
            else -> a
        }
        val inherited = HashSet<SpanKind>()
        if (a > 0 && oldT[a - 1] != '\n') {
            for (sp in old.spans) if (sp.start <= a - 1 && sp.end >= a) inherited.add(sp.kind)
        }
        var spans: List<Span> = old.spans.mapNotNull { sp ->
            val ns = mapStart(sp.start)
            val ne = mapEnd(sp.end)
            if (ne > ns) Span(sp.kind, ns, ne) else null
        }
        if (ins > 0) {
            for (kind in SpanKind.entries) {
                val on = pending[kind] ?: (kind in inherited)
                spans = if (on) addRange(spans, kind, a, a + ins) else removeRange(spans, kind, a, a + ins)
            }
            if (pending[SpanKind.SUP] == true) spans = removeRange(spans, SpanKind.SUB, a, a + ins)
            if (pending[SpanKind.SUB] == true) spans = removeRange(spans, SpanKind.SUP, a, a + ins)
        }

        // ── blocks ──
        val la = old.lineIndexAt(a)
        val removedNl = removed.count { it == '\n' }
        val insNl = inserted.count { it == '\n' }
        val fmtLa = old.blocks[la]
        val restOfLineEmpty = old.lineEnd(min(la + removedNl, old.lineCount - 1)) == b
        val newFmt = if (insNl > 0 && fmtLa.type.isHeading && restOfLineEmpty) fmtLa.copy(type = BlockType.P) else fmtLa
        val blocks = ArrayList<BlockFmt>(old.blocks.size + insNl)
        for (i in 0 until la) blocks.add(old.blocks[i])
        blocks.add(fmtLa)
        repeat(insNl) { blocks.add(newFmt) }
        for (i in (la + removedNl + 1) until old.blocks.size) blocks.add(old.blocks[i])

        return Doc.of(newText, blocks, spans)
    }
}
