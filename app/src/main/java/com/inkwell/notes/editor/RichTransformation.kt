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
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/** Colours / sizes the transformation needs (pulled from the theme + Settings by the screen). */
class RichStyle(
    val fontSizeSp: Float,
    val lineHeightFactor: Float,
    val textColor: Color,
    val quoteColor: Color,
    val accent: Color,
    val highlightBg: Color,
    val serif: FontFamily,
)

/**
 * Draws a [RichDoc] inside a plain `BasicTextField`: the field keeps editing the raw text, this
 * transformation paints marks (bold, italic …), block looks (headings, quotes, centred text) and
 * adds the list markers / indentation as a non-editable prefix in front of each paragraph.
 * The offset mapping keeps the caret and selection on the real text, never on the prefix.
 */
class RichTransformation(
    private val doc: RichDoc,
    private val style: RichStyle,
) : VisualTransformation {

    private val n = doc.paraCount
    private val prefixes: Array<String> = Array(n) { "" }
    /** cum[i] = total prefix length of paragraphs 0..i */
    private val cum = IntArray(n)
    private val tStart = IntArray(n)

    init {
        var olCount = 0
        var prev: BlockType? = null
        var total = 0
        for (i in 0 until n) {
            val p = doc.paras[i]
            olCount = if (p.type == BlockType.OL) (if (prev == BlockType.OL) olCount + 1 else 1) else 0
            val marker = when (p.type) {
                BlockType.UL -> "•  "
                BlockType.OL -> "$olCount.  "
                BlockType.QUOTE -> "▎ "
                else -> ""
            }
            prefixes[i] = "    ".repeat(p.indent) + marker
            tStart[i] = doc.starts[i] + total
            total += prefixes[i].length
            cum[i] = total
            prev = p.type
        }
    }

    override fun filter(text: AnnotatedString): TransformedText {
        // The field and the document are updated together; if they ever disagree, show the raw text.
        if (text.text != doc.text) return TransformedText(text, OffsetMapping.Identity)
        return TransformedText(build(), mapping)
    }

    private fun build(): AnnotatedString = buildAnnotatedString {
        for (i in 0 until n) {
            val p = doc.paras[i]
            val s = doc.starts[i]
            val e = doc.paraEnd(i)

            val align = when (p.align) {
                Align.Center -> TextAlign.Center
                Align.End -> TextAlign.End
                Align.Start -> null
            }
            val headingScale = when (p.type) {
                BlockType.H1 -> 1.4f
                BlockType.H2 -> 1.15f
                BlockType.H3 -> 1.0f
                else -> 0f
            }
            val needsParagraphStyle = align != null || headingScale > 0f
            if (needsParagraphStyle) {
                val lh: TextUnit = if (headingScale > 0f) {
                    (style.fontSizeSp * headingScale * 1.4f).sp
                } else TextUnit.Unspecified
                pushStyle(ParagraphStyle(textAlign = align ?: TextAlign.Unspecified, lineHeight = lh))
            }

            val prefix = prefixes[i]
            if (prefix.isNotEmpty()) {
                pushStyle(SpanStyle(color = style.accent, fontWeight = FontWeight.Bold))
                append(prefix)
                pop()
            }

            val block = blockSpan(p.type)
            if (block != null) pushStyle(block)
            var a = s
            while (a < e) {
                var b = a + 1
                while (b < e && doc.marks[b] == doc.marks[a]) b++
                val span = markSpan(doc.marks[a])
                if (span != null) pushStyle(span)
                append(doc.text.substring(a, b))
                if (span != null) pop()
                a = b
            }
            if (i < n - 1) append("\n")
            if (block != null) pop()

            if (needsParagraphStyle) pop()
        }
    }

    private fun blockSpan(type: BlockType): SpanStyle? = when (type) {
        BlockType.H1 -> SpanStyle(fontSize = (style.fontSizeSp * 1.4f).sp, fontWeight = FontWeight.Bold, fontFamily = style.serif)
        BlockType.H2 -> SpanStyle(fontSize = (style.fontSizeSp * 1.15f).sp, fontWeight = FontWeight.SemiBold, fontFamily = style.serif)
        BlockType.H3 -> SpanStyle(fontSize = style.fontSizeSp.sp, fontWeight = FontWeight.SemiBold, fontFamily = style.serif)
        BlockType.QUOTE -> SpanStyle(color = style.quoteColor, fontStyle = FontStyle.Italic)
        else -> null
    }

    private fun markSpan(m: Int): SpanStyle? {
        if (m == 0) return null
        val deco = buildList {
            if (m and Mark.UNDERLINE != 0) add(TextDecoration.Underline)
            if (m and Mark.STRIKE != 0) add(TextDecoration.LineThrough)
        }
        val baseline = when {
            m and Mark.SUP != 0 -> BaselineShift.Superscript
            m and Mark.SUB != 0 -> BaselineShift.Subscript
            else -> null
        }
        return SpanStyle(
            fontWeight = if (m and Mark.BOLD != 0) FontWeight.Bold else null,
            fontStyle = if (m and Mark.ITALIC != 0) FontStyle.Italic else null,
            textDecoration = if (deco.isEmpty()) null else TextDecoration.combine(deco),
            baselineShift = baseline,
            fontSize = if (baseline != null) (style.fontSizeSp * 0.75f).sp else TextUnit.Unspecified,
            background = if (m and Mark.HIGHLIGHT != 0) style.highlightBg else Color.Unspecified,
        )
    }

    private val mapping = object : OffsetMapping {
        override fun originalToTransformed(offset: Int): Int {
            val o = offset.coerceIn(0, doc.text.length)
            return o + cum[doc.paraIndexAt(o)]
        }

        override fun transformedToOriginal(offset: Int): Int {
            // last paragraph whose prefix starts at or before [offset]
            var lo = 0
            var hi = n - 1
            while (lo < hi) {
                val mid = (lo + hi + 1) ushr 1
                if (tStart[mid] <= offset) lo = mid else hi = mid - 1
            }
            val prefixEnd = tStart[lo] + prefixes[lo].length
            val o = if (offset < prefixEnd) doc.starts[lo] else offset - cum[lo]
            return o.coerceIn(0, doc.text.length)
        }
    }
}
