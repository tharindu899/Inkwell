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
import androidx.compose.ui.text.style.LineHeightStyle
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
    /** Text colour of a ticked checklist item. */
    val doneColor: Color,
    /** Background behind code-block lines. */
    val codeBg: Color,
    /** Colour of links (underlined). */
    val linkColor: Color,
    /** Background behind inline `code`. */
    val inlineCodeBg: Color,
    /** `LocalDensity.fontScale` — lets dp-sized blocks (tables) reserve the right height in sp. */
    val fontScale: Float,
)

/**
 * Draws a [RichDoc] inside a plain `BasicTextField`: the field keeps editing the raw text, this
 * transformation paints marks (bold, italic …), block looks (headings, quotes, code, centred text) and
 * adds the list markers / checkboxes / collapse chevrons / indentation as a non-editable prefix in front
 * of each paragraph. Items hidden inside a collapsed list item are left out of the shown text.
 * The offset mapping ([RichLayout]) keeps the caret and selection on the real text, never on the prefix.
 */
class RichTransformation(
    val doc: RichDoc,
    private val style: RichStyle,
) : VisualTransformation {

    /** Prefix / hidden-paragraph geometry; also used by the screen to hit-test checkbox and chevron taps. */
    val layout = RichLayout(doc)

    override fun filter(text: AnnotatedString): TransformedText {
        // The field and the document are updated together; if they ever disagree, show the raw text.
        if (text.text != doc.text) return TransformedText(text, OffsetMapping.Identity)
        return TransformedText(build(), mapping)
    }

    private fun build(): AnnotatedString = buildAnnotatedString {
        val n = doc.paraCount
        var lastShown = n - 1
        while (lastShown > 0 && doc.hidden[lastShown]) lastShown--
        for (i in 0 until n) {
            if (doc.hidden[i]) continue
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
            val isObject = p.type.isObject
            val needsParagraphStyle = align != null || headingScale > 0f || isObject
            if (needsParagraphStyle) {
                if (isObject) {
                    // Divider / table: one tall, untrimmed line — the screen paints the block into that space.
                    val dp = if (p.type == BlockType.TABLE) tableHeightDp(p.table?.rowCount ?: 1) else HR_HEIGHT_DP
                    pushStyle(
                        ParagraphStyle(
                            lineHeight = (dp / style.fontScale).sp,
                            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
                        ),
                    )
                } else {
                    val lh: TextUnit = if (headingScale > 0f) {
                        (style.fontSizeSp * headingScale * 1.4f).sp
                    } else TextUnit.Unspecified
                    pushStyle(ParagraphStyle(textAlign = align ?: TextAlign.Unspecified, lineHeight = lh))
                }
            }

            val prefix = layout.prefixes[i]
            if (prefix.isNotEmpty()) {
                pushStyle(SpanStyle(color = style.accent, fontWeight = FontWeight.Bold))
                append(prefix)
                pop()
            }

            val block = blockSpan(p)
            if (block != null) pushStyle(block)
            if (p.type == BlockType.CODE) {
                append(doc.text.substring(s, e))
            } else {
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
            }
            if (i < lastShown) append("\n")
            if (block != null) pop()

            if (needsParagraphStyle) pop()
        }
    }

    private fun blockSpan(p: Para): SpanStyle? = when (p.type) {
        BlockType.H1 -> SpanStyle(fontSize = (style.fontSizeSp * 1.4f).sp, fontWeight = FontWeight.Bold, fontFamily = style.serif)
        BlockType.H2 -> SpanStyle(fontSize = (style.fontSizeSp * 1.15f).sp, fontWeight = FontWeight.SemiBold, fontFamily = style.serif)
        BlockType.H3 -> SpanStyle(fontSize = style.fontSizeSp.sp, fontWeight = FontWeight.SemiBold, fontFamily = style.serif)
        BlockType.QUOTE -> SpanStyle(color = style.quoteColor, fontStyle = FontStyle.Italic)
        BlockType.TASK -> if (p.checked) SpanStyle(color = style.doneColor, textDecoration = TextDecoration.LineThrough) else null
        // the box behind a code block is painted by the screen (GitHub-style), so no per-character background here
        BlockType.CODE -> SpanStyle(fontFamily = FontFamily.Monospace, fontSize = (style.fontSizeSp * 0.92f).sp)
        else -> null
    }

    private fun markSpan(m: Int): SpanStyle? {
        if (m == 0) return null
        val isLink = Mark.linkId(m) != 0
        val isCode = m and Mark.CODE != 0
        val deco = buildList {
            if (m and Mark.UNDERLINE != 0 || isLink) add(TextDecoration.Underline)
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
            background = when {
                m and Mark.HIGHLIGHT != 0 -> style.highlightBg
                isCode -> style.inlineCodeBg
                else -> Color.Unspecified
            },
            color = if (isLink) style.linkColor else Color.Unspecified,
            fontFamily = if (isCode) FontFamily.Monospace else null,
        )
    }

    companion object {
        /** Height of the line a divider is drawn into. */
        const val HR_HEIGHT_DP = 28f

        /** One table row (the overlay table uses the same number) + vertical breathing room. */
        const val TABLE_ROW_DP = 40f
        const val TABLE_PAD_DP = 8f

        fun tableHeightDp(rows: Int): Float = rows.coerceAtLeast(1) * TABLE_ROW_DP + 2 * TABLE_PAD_DP
    }

    /** Caret mapping (real text ⇄ shown text), used to keep the caret on screen. */
    val offsetMapping: OffsetMapping get() = mapping

    private val mapping = object : OffsetMapping {
        override fun originalToTransformed(offset: Int): Int = layout.originalToTransformed(offset)
        override fun transformedToOriginal(offset: Int): Int = layout.transformedToOriginal(offset)
    }
}
