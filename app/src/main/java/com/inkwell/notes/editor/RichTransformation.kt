package com.inkwell.notes.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.sp

/**
 * Editor Part 2 — draws a [Doc] inside the single BasicTextField.
 *
 * The field's real text is `doc.text`; this transformation only changes how it looks:
 *  - spans → bold / italic / underline / strike / sup / sub / highlight
 *  - block types → heading sizes, quote style, bullet / number prefixes (display-only characters)
 *  - align / indent → paragraph styles
 *
 * Each '\n' is shown as a zero-width space inside its own paragraph (so paragraph styles never fight
 * with a literal newline), and one extra zero-width space ends the last line so the caret can sit on
 * a trailing empty line. Offsets map 1:1 apart from the inserted list / quote prefixes.
 */
class RichTransformation(
    private val doc: Doc,
    private val baseSp: Float,
    private val bodyFamily: FontFamily,
    private val headingFamily: FontFamily,
    private val textColor: Color,
    private val quoteColor: Color,
    private val mutedColor: Color,
    private val accent: Color,
    private val highlight: Color = Color(0x55C9A96E),
) : VisualTransformation {

    private val prefixes: Array<String> = buildPrefixes()
    /** Total prefix characters in front of line i. */
    private val before: IntArray = IntArray(doc.lineCount).also {
        var sum = 0
        for (i in it.indices) { it[i] = sum; sum += prefixes[i].length }
    }

    private fun buildPrefixes(): Array<String> {
        val out = Array(doc.lineCount) { "" }
        val counters = IntArray(DocOps.MAX_INDENT + 2)
        for (i in out.indices) {
            val f = doc.blocks[i]
            val d = f.indent.coerceIn(0, counters.lastIndex)
            when (f.type) {
                BlockType.OL -> {
                    for (k in d + 1..counters.lastIndex) counters[k] = 0
                    counters[d]++
                    out[i] = "${counters[d]}. "
                }
                BlockType.UL -> {
                    for (k in d..counters.lastIndex) counters[k] = 0
                    out[i] = when (d % 3) { 0 -> "\u2022 "; 1 -> "\u25E6 "; else -> "\u25AA " }
                }
                BlockType.QUOTE -> {
                    counters.fill(0)
                    out[i] = "\u2503 "
                }
                else -> counters.fill(0)
            }
        }
        return out
    }

    override fun filter(text: AnnotatedString): TransformedText {
        if (text.text != doc.text) return TransformedText(text, OffsetMapping.Identity)

        val origLen = doc.text.length
        val totalPrefix = before.last() + prefixes.last().length
        val transLen = origLen + totalPrefix + 1 // + trailing zero-width space

        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                val o = offset.coerceIn(0, origLen)
                val i = doc.lineIndexAt(o)
                return (o + before[i] + prefixes[i].length).coerceIn(0, transLen)
            }

            override fun transformedToOriginal(offset: Int): Int {
                val t = offset.coerceIn(0, transLen)
                var lo = 0
                var hi = doc.lineCount - 1
                while (lo < hi) {
                    val mid = (lo + hi + 1) ushr 1
                    if (doc.lineStarts[mid] + before[mid] <= t) lo = mid else hi = mid - 1
                }
                val r = t - (doc.lineStarts[lo] + before[lo])
                val o = if (r < prefixes[lo].length) doc.lineStarts[lo] else doc.lineStarts[lo] + (r - prefixes[lo].length)
                return o.coerceIn(0, origLen)
            }
        }

        val built = buildAnnotatedString {
            for (i in 0 until doc.lineCount) {
                val f = doc.blocks[i]
                val size = when (f.type) {
                    BlockType.H1 -> baseSp * 1.5f
                    BlockType.H2 -> baseSp * 1.28f
                    BlockType.H3 -> baseSp * 1.1f
                    else -> baseSp
                }
                val lineHeight = size * (if (f.type.isHeading) 1.4f else 1.72f)
                val indentSp = f.indent * INDENT_SP
                val hang = if (prefixes[i].isEmpty()) 0f else prefixes[i].length * size * 0.45f
                val para = ParagraphStyle(
                    textAlign = when (f.align) {
                        Align.LEFT -> TextAlign.Start
                        Align.CENTER -> TextAlign.Center
                        Align.RIGHT -> TextAlign.End
                    },
                    textIndent = TextIndent(firstLine = indentSp.sp, restLine = (indentSp + hang).sp),
                    lineHeight = lineHeight.sp,
                )
                withStyle(para) {
                    if (prefixes[i].isNotEmpty()) {
                        withStyle(
                            SpanStyle(
                                color = if (f.type == BlockType.OL) mutedColor else accent,
                                fontSize = size.sp,
                                fontFamily = bodyFamily,
                                fontWeight = FontWeight.Normal,
                            ),
                        ) { append(prefixes[i]) }
                    }
                    val ls = doc.lineStarts[i]
                    val le = doc.lineEnd(i)
                    for ((x, y, kinds) in runsOf(ls, le)) {
                        withStyle(runStyle(f.type, size, kinds)) { append(doc.text.substring(x, y)) }
                    }
                    // '\n' (or the final extra char) → invisible, sized like the line so empty lines keep their height.
                    withStyle(SpanStyle(fontSize = size.sp, fontFamily = bodyFamily)) { append('\u200B') }
                }
            }
        }
        return TransformedText(built, mapping)
    }

    private fun runStyle(type: BlockType, lineSize: Float, kinds: Set<SpanKind>): SpanStyle {
        val small = SpanKind.SUP in kinds || SpanKind.SUB in kinds
        val deco = buildList {
            if (SpanKind.UNDERLINE in kinds) add(TextDecoration.Underline)
            if (SpanKind.STRIKE in kinds) add(TextDecoration.LineThrough)
        }
        return SpanStyle(
            color = if (type == BlockType.QUOTE) quoteColor else textColor,
            fontSize = (if (small) lineSize * 0.75f else lineSize).sp,
            fontWeight = if (type.isHeading || SpanKind.BOLD in kinds) FontWeight.Bold else FontWeight.Normal,
            fontStyle = if (type == BlockType.QUOTE || SpanKind.ITALIC in kinds) FontStyle.Italic else FontStyle.Normal,
            fontFamily = if (type.isHeading) headingFamily else bodyFamily,
            textDecoration = if (deco.isEmpty()) null else TextDecoration.combine(deco),
            baselineShift = when {
                SpanKind.SUP in kinds -> BaselineShift.Superscript
                SpanKind.SUB in kinds -> BaselineShift.Subscript
                else -> null
            },
            background = if (SpanKind.HIGHLIGHT in kinds) highlight else Color.Unspecified,
        )
    }

    /** Runs of equal formatting inside one line. */
    private fun runsOf(ls: Int, le: Int): List<Triple<Int, Int, Set<SpanKind>>> {
        if (le <= ls) return emptyList()
        val cuts = sortedSetOf(ls, le)
        for (sp in doc.spans) {
            if (sp.end <= ls || sp.start >= le) continue
            cuts.add(maxOf(sp.start, ls)); cuts.add(minOf(sp.end, le))
        }
        val list = cuts.toList()
        val out = ArrayList<Triple<Int, Int, Set<SpanKind>>>(list.size)
        for (k in 0 until list.size - 1) {
            val x = list[k]
            val y = list[k + 1]
            out.add(Triple(x, y, doc.spans.filter { it.start <= x && it.end >= y }.map { it.kind }.toSet()))
        }
        return out
    }

    companion object {
        const val INDENT_SP = 22f
    }
}
