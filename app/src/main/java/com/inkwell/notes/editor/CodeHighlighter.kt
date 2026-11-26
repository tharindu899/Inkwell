package com.inkwell.notes.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString

/** GitHub's code-block colours (dark = "Dark default", light = "Light default"). */
class CodePalette(
    val bg: Color,
    val border: Color,
    val text: Color,
    val hint: Color,
    val comment: Color,
    val keyword: Color,
    val string: Color,
    val constant: Color,
    val function: Color,
    val type: Color,
    val tag: Color,
) {
    companion object {
        val Dark = CodePalette(
            bg = Color(0xFF161B22),
            border = Color(0xFF30363D),
            text = Color(0xFFE6EDF3),
            hint = Color(0xFF8B949E),
            comment = Color(0xFF8B949E),
            keyword = Color(0xFFFF7B72),
            string = Color(0xFFA5D6FF),
            constant = Color(0xFF79C0FF),
            function = Color(0xFFD2A8FF),
            type = Color(0xFFFFA657),
            tag = Color(0xFF7EE787),
        )
        val Light = CodePalette(
            bg = Color(0xFFF6F8FA),
            border = Color(0xFFD0D7DE),
            text = Color(0xFF1F2328),
            hint = Color(0xFF6E7781),
            comment = Color(0xFF6E7781),
            keyword = Color(0xFFCF222E),
            string = Color(0xFF0A3069),
            constant = Color(0xFF0550AE),
            function = Color(0xFF8250DF),
            type = Color(0xFF953800),
            tag = Color(0xFF116329),
        )

        fun of(dark: Boolean): CodePalette = if (dark) Dark else Light
    }
}

object CodeHighlighter {
    /** Same length as [src] (so it can be used as a visual transformation with an identity offset mapping). */
    fun highlight(src: String, p: CodePalette): AnnotatedString = buildAnnotatedString {
        append(src)
        if (src.isEmpty()) return@buildAnnotatedString
        addStyle(SpanStyle(color = p.text), 0, src.length)
        for (t in CodeTokenizer.tokenize(src)) {
            val c = when (t.kind) {
                Tok.COMMENT -> p.comment
                Tok.KEYWORD -> p.keyword
                Tok.STRING -> p.string
                Tok.CONSTANT -> p.constant
                Tok.FUNCTION -> p.function
                Tok.TYPE -> p.type
                Tok.TAG -> p.tag
            }
            addStyle(SpanStyle(color = c), t.start, t.end)
        }
    }
}
