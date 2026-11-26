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
    /** Text colour of a ticked checklist item. */
    val doneColor: Color,
    /** Fill of the code-block card (drawn behind the lines by the screen). */
    val codeBg: Color,
    /** Text colour inside code blocks. */
    val codeFg: Color,
    /** Inline `code`: background + text colour. */
    val inlineCodeBg: Color,
    val inlineCodeFg: Color,
    /** Links (accent, underlined). */
    val linkColor: Color,
) {
    /** Inline look of one character's mark Int (flags + link id); null = plain. Also used for table cells. */
    fun markSpan(m: Int): SpanStyle? {
        if (m == 0) return null
        val flags = Mark.flags(m)
        val isLink = Mark.linkId(m) != 0
        val isCode = flags and Mark.CODE != 0
        val deco = buildList {
            if (flags and Mark.UNDERLINE != 0 || isLink) add(TextDecoration.Underline)
            if (flags and Mark.STRIKE != 0) add(TextDecoration.LineThrough)
        }
        val baseline = when {
            flags and Mark.SUP != 0 -> BaselineShift.Superscript
            flags and Mark.SUB != 0 -> BaselineShift.Subscript
            else -> null
        }
        return SpanStyle(
            color = when {
                isCode -> inlineCodeFg
                isLink -> linkColor
                else -> Color.Unspecified
            },
            fontWeight = if (flags and Mark.BOLD != 0) FontWeight.Bold else null,
            fontStyle = if (flags and Mark.ITALIC != 0) FontStyle.Italic else null,
            fontFamily = if (isCode) FontFamily.Monospace else null,
            textDecoration = if (deco.isEmpty()) null else TextDecoration.combine(deco),
            baselineShift = baseline,
            fontSize = when {
                baseline != null -> (fontSizeSp * 0.75f).sp
                isCode -> (fontSizeSp * 0.92f).sp
                else -> TextUnit.Unspecified
            },
            background = when {
                isCode -> inlineCodeBg
                flags and Mark.HIGHLIGHT != 0 -> highlightBg
                else -> Color.Unspecified
            },
        )
    }
}

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
    /** Lines of blank space reserved for table paragraph k (the screen measures the table and feeds this back). */
    objectLines: (Int) -> Int = { 1 },
) : VisualTransformation {

    /** Prefix / hidden-paragraph geometry; also used by the screen to hit-test checkbox and chevron taps. */
    val layout = RichLayout(doc, objectLines)

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
            val needsParagraphStyle = align != null || headingScale > 0f
            if (needsParagraphStyle) {
                val lh: TextUnit = if (headingScale > 0f) {
                    (style.fontSizeSp * headingScale * 1.4f).sp
                } else TextUnit.Unspecified
                pushStyle(ParagraphStyle(textAlign = align ?: TextAlign.Unspecified, lineHeight = lh))
            }

            val prefix = layout.prefixes[i]
            if (prefix.isNotEmpty()) {
                pushStyle(SpanStyle(color = style.accent, fontWeight = FontWeight.Bold))
                append(prefix)
                pop()
            }

            val block = blockSpan(p)
            if (block != null) pushStyle(block)
            if (p.type.isObject) {
                // The placeholder is invisible: the screen draws the divider / lays the table over these lines.
                pushStyle(SpanStyle(color = Color.Transparent))
                append(OBJ)
                pop()
            } else if (p.type == BlockType.CODE) {
                append(doc.text.substring(s, e))
            } else {
                var a = s
                while (a < e) {
                    var b = a + 1
                    while (b < e && doc.marks[b] == doc.marks[a]) b++
                    val span = style.markSpan(doc.marks[a])
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
        BlockType.CODE -> SpanStyle(fontFamily = FontFamily.Monospace, fontSize = (style.fontSizeSp * 0.92f).sp, color = style.codeFg)
        else -> null
    }

    /** Caret mapping (real text ⇄ shown text), used to keep the caret on screen. */
    val offsetMapping: OffsetMapping get() = mapping

    private val mapping = object : OffsetMapping {
        override fun originalToTransformed(offset: Int): Int = layout.originalToTransformed(offset)
        override fun transformedToOriginal(offset: Int): Int = layout.transformedToOriginal(offset)
    }
}
