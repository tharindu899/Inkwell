package com.inkwell.notes.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inkwell.notes.R
import com.inkwell.notes.editor.Align
import com.inkwell.notes.editor.BlockType
import com.inkwell.notes.editor.Mark
import com.inkwell.notes.ui.components.FaIcon
import com.inkwell.notes.ui.components.tap
import com.inkwell.notes.ui.theme.Ink

/** What the toolbar needs to know about the caret / selection (computed by [EditorController]). */
class ToolbarState(
    val marks: Int = 0,
    val block: BlockType = BlockType.P,
    val align: Align = Align.Start,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
)

/** Which glyph a button draws. */
private sealed interface Glyph {
    class Label(val text: String, val weight: FontWeight = FontWeight.Bold, val size: Int = 12) : Glyph
    data object Undo : Glyph
    data object Redo : Glyph
    data object Bold : Glyph
    data object Italic : Glyph
    data object Underline : Glyph
    data object Strike : Glyph
    data object Sup : Glyph
    data object Sub : Glyph
    data object Highlight : Glyph
    data object Clear : Glyph
    data object Bullets : Glyph
    data object Numbers : Glyph
    data object Quote : Glyph
    data class AlignLines(val align: Align) : Glyph
    data class IndentArrow(val forward: Boolean) : Glyph
}

/**
 * Port of `.editor-toolbar` (Part 2 tools): history · inline · blocks · lists · align · extras · clear.
 * Tables, links, images, code, date/time and the toolbar customiser arrive in later parts.
 *
 * Buttons are plain taps (no focus), so the text field keeps its focus and selection while you format.
 */
@Composable
fun EditorToolbar(
    state: ToolbarState,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onMark: (Int) -> Unit,
    onBlock: (BlockType) -> Unit,
    onAlign: (Align) -> Unit,
    onIndent: (Int) -> Unit,
    onClear: () -> Unit,
    scroll: ScrollState = rememberScrollState(),
) {
    val c = Ink.colors
    Column(Modifier.fillMaxWidth().background(c.bgCard)) {
        // Docked at the bottom (above the keyboard): the divider goes on top.
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(scroll)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            ToolButton(Glyph.Undo, enabled = state.canUndo, onClick = onUndo)
            ToolButton(Glyph.Redo, enabled = state.canRedo, onClick = onRedo)
            Sep()
            ToolButton(Glyph.Bold, on = state.marks and Mark.BOLD != 0) { onMark(Mark.BOLD) }
            ToolButton(Glyph.Italic, on = state.marks and Mark.ITALIC != 0) { onMark(Mark.ITALIC) }
            ToolButton(Glyph.Underline, on = state.marks and Mark.UNDERLINE != 0) { onMark(Mark.UNDERLINE) }
            ToolButton(Glyph.Strike, on = state.marks and Mark.STRIKE != 0) { onMark(Mark.STRIKE) }
            Sep()
            ToolButton(Glyph.Label("H1"), on = state.block == BlockType.H1) { onBlock(BlockType.H1) }
            ToolButton(Glyph.Label("H2"), on = state.block == BlockType.H2) { onBlock(BlockType.H2) }
            ToolButton(Glyph.Label("H3"), on = state.block == BlockType.H3) { onBlock(BlockType.H3) }
            ToolButton(Glyph.Label("¶", FontWeight.Normal, 15), on = state.block == BlockType.P) { onBlock(BlockType.P) }
            Sep()
            ToolButton(Glyph.Bullets, on = state.block == BlockType.UL) { onBlock(BlockType.UL) }
            ToolButton(Glyph.Numbers, on = state.block == BlockType.OL) { onBlock(BlockType.OL) }
            ToolButton(Glyph.Quote, on = state.block == BlockType.QUOTE) { onBlock(BlockType.QUOTE) }
            Sep()
            ToolButton(Glyph.AlignLines(Align.Start), on = state.align == Align.Start) { onAlign(Align.Start) }
            ToolButton(Glyph.AlignLines(Align.Center), on = state.align == Align.Center) { onAlign(Align.Center) }
            ToolButton(Glyph.AlignLines(Align.End), on = state.align == Align.End) { onAlign(Align.End) }
            ToolButton(Glyph.IndentArrow(true)) { onIndent(1) }
            ToolButton(Glyph.IndentArrow(false)) { onIndent(-1) }
            Sep()
            ToolButton(Glyph.Highlight, on = state.marks and Mark.HIGHLIGHT != 0) { onMark(Mark.HIGHLIGHT) }
            ToolButton(Glyph.Sup, on = state.marks and Mark.SUP != 0) { onMark(Mark.SUP) }
            ToolButton(Glyph.Sub, on = state.marks and Mark.SUB != 0) { onMark(Mark.SUB) }
            Sep()
            ToolButton(Glyph.Clear, onClick = onClear)
        }
    }
}

/** `.tb-sep` */
@Composable
private fun Sep() {
    Box(
        Modifier
            .padding(horizontal = 3.dp)
            .width(1.dp)
            .height(22.dp)
            .background(Ink.colors.borderHi),
    )
}

/** `.tb-btn` (+ `.on`): 32dp, accent tint while the format is active at the caret. */
@Composable
private fun ToolButton(
    glyph: Glyph,
    on: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val c = Ink.colors
    val tint = if (on) c.accent else c.text2
    Box(
        Modifier
            .size(32.dp)
            .alpha(if (enabled) 1f else 0.35f)
            .clip(RoundedCornerShape(9.dp))
            .background(if (on) c.accentDim else Color.Transparent)
            .tap { if (enabled) onClick() },
        contentAlignment = Alignment.Center,
    ) {
        GlyphView(glyph, tint)
    }
}

@Composable
private fun GlyphView(glyph: Glyph, tint: Color) {
    val c = Ink.colors
    when (glyph) {
        is Glyph.Label -> Txt(glyph.text, tint, glyph.size, glyph.weight)
        Glyph.Undo -> FaIcon(R.drawable.ic_fa_rotate_left, tint = tint, size = 14.dp)
        Glyph.Redo -> FaIcon(
            R.drawable.ic_fa_rotate_left,
            tint = tint,
            size = 14.dp,
            modifier = Modifier.graphicsLayer(scaleX = -1f),
        )
        Glyph.Bold -> Txt("B", tint, 15, FontWeight.ExtraBold)
        Glyph.Italic -> Txt("I", tint, 15, FontWeight.Normal, italic = true)
        Glyph.Underline -> Txt("U", tint, 14, FontWeight.Bold, deco = TextDecoration.Underline)
        Glyph.Strike -> Txt("S", tint, 14, FontWeight.Bold, deco = TextDecoration.LineThrough)
        Glyph.Quote -> Txt("“", tint, 22, FontWeight.Bold)
        Glyph.Highlight -> Box(
            Modifier.clip(RoundedCornerShape(3.dp)).background(Color(0xFFFFE49A).copy(alpha = 0.75f)).padding(horizontal = 4.dp),
        ) { Txt("A", Color(0xFF3A2E10), 13, FontWeight.Bold) }
        Glyph.Sup -> ScriptGlyph(tint, sup = true)
        Glyph.Sub -> ScriptGlyph(tint, sup = false)
        Glyph.Clear -> ClearGlyph(tint)
        Glyph.Bullets -> Canvas(Modifier.size(16.dp)) {
            val w = size.width
            val sw = 1.8.dp.toPx()
            for (k in 0..2) {
                val y = size.height * (0.2f + 0.3f * k)
                drawCircle(tint, radius = 1.4.dp.toPx(), center = Offset(w * 0.12f, y))
                drawLine(tint, Offset(w * 0.36f, y), Offset(w * 0.96f, y), sw, StrokeCap.Round)
            }
        }
        Glyph.Numbers -> Canvas(Modifier.size(16.dp)) {
            val w = size.width
            val sw = 1.8.dp.toPx()
            for (k in 0..2) {
                val y = size.height * (0.2f + 0.3f * k)
                drawLine(tint, Offset(w * 0.04f, y), Offset(w * 0.22f, y), sw * 1.3f, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.4f, y), Offset(w * 0.96f, y), sw, StrokeCap.Round)
            }
        }
        is Glyph.AlignLines -> Canvas(Modifier.size(16.dp)) {
            val w = size.width
            val sw = 1.8.dp.toPx()
            val lens = floatArrayOf(1f, 0.62f, 1f, 0.62f)
            for (k in 0..3) {
                val y = size.height * (0.14f + 0.24f * k)
                val len = w * lens[k]
                val x0 = when (glyph.align) {
                    Align.Start -> 0f
                    Align.Center -> (w - len) / 2f
                    Align.End -> w - len
                }
                drawLine(tint, Offset(x0 + sw / 2, y), Offset(x0 + len - sw / 2, y), sw, StrokeCap.Round)
            }
        }
        is Glyph.IndentArrow -> Canvas(Modifier.size(16.dp)) {
            val w = size.width
            val h = size.height
            val sw = 1.8.dp.toPx()
            drawLine(tint, Offset(sw / 2, h * 0.12f), Offset(w - sw / 2, h * 0.12f), sw, StrokeCap.Round)
            drawLine(tint, Offset(sw / 2, h * 0.88f), Offset(w - sw / 2, h * 0.88f), sw, StrokeCap.Round)
            drawLine(tint, Offset(w * 0.5f, h * 0.37f), Offset(w - sw / 2, h * 0.37f), sw, StrokeCap.Round)
            drawLine(tint, Offset(w * 0.5f, h * 0.63f), Offset(w - sw / 2, h * 0.63f), sw, StrokeCap.Round)
            val tri = Path().apply {
                if (glyph.forward) {
                    moveTo(w * 0.04f, h * 0.30f); lineTo(w * 0.36f, h * 0.5f); lineTo(w * 0.04f, h * 0.70f)
                } else {
                    moveTo(w * 0.36f, h * 0.30f); lineTo(w * 0.04f, h * 0.5f); lineTo(w * 0.36f, h * 0.70f)
                }
                close()
            }
            drawPath(tri, tint)
        }
    }
}

@Composable
private fun Txt(
    text: String,
    color: Color,
    size: Int,
    weight: FontWeight,
    italic: Boolean = false,
    deco: TextDecoration? = null,
) {
    BasicText(
        text,
        style = TextStyle(
            color = color,
            fontSize = size.sp,
            fontWeight = weight,
            fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
            textDecoration = deco,
            fontFamily = if (italic || text == "“") Ink.serif else Ink.sans,
        ),
    )
}

@Composable
private fun ScriptGlyph(tint: Color, sup: Boolean) {
    BasicText(
        buildAnnotatedString {
            append("x")
            withStyle(
                SpanStyle(
                    fontSize = 9.sp,
                    baselineShift = if (sup) BaselineShift.Superscript else BaselineShift.Subscript,
                ),
            ) { append("2") }
        },
        style = TextStyle(color = tint, fontSize = 14.sp, fontWeight = FontWeight.Bold),
    )
}

@Composable
private fun ClearGlyph(tint: Color) {
    BasicText(
        buildAnnotatedString {
            append("T")
            withStyle(SpanStyle(fontSize = 9.sp, baselineShift = BaselineShift.Subscript)) { append("x") }
        },
        style = TextStyle(color = tint, fontSize = 14.sp, fontWeight = FontWeight.Bold),
    )
}
