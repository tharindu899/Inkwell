package com.inkwell.notes.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inkwell.notes.R
import com.inkwell.notes.editor.Align
import com.inkwell.notes.editor.BlockType
import com.inkwell.notes.editor.SpanKind
import com.inkwell.notes.ui.components.FaIcon
import com.inkwell.notes.ui.components.InkText
import com.inkwell.notes.ui.theme.Ink

/**
 * Editor Part 2 — the formatting toolbar (`.toolbar` in Editor.jsx).
 *
 * One horizontally scrolling row, tools grouped with separators, the active tools highlighted.
 * Taps use a pointer handler instead of `clickable`, so the text field keeps focus (and its
 * selection) while a tool is applied — the Compose version of "selection preserve".
 *
 * Each tool has a stable [Tool.key] (same ids as the web `TOOLBAR_GROUPS`) so Part 8's
 * "Manage toolbar" can show / hide them.
 */
private class Tool(
    val key: String,
    val active: Boolean = false,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
    val content: @Composable (Color) -> Unit,
)

private enum class LinesKind { AlignLeft, AlignCenter, AlignRight, Indent, Outdent }

@Composable
fun FormatToolbar(ctl: EditorController, modifier: Modifier = Modifier) {
    val c = Ink.colors

    val groups: List<List<Tool>> = listOf(
        listOf(
            Tool("undo", enabled = ctl.canUndo, onClick = ctl::undo) { tint ->
                FaIcon(R.drawable.ic_fa_rotate_left, tint = tint, size = 14.dp)
            },
            Tool("redo", enabled = ctl.canRedo, onClick = ctl::redo) { tint ->
                FaIcon(R.drawable.ic_fa_rotate_left, tint = tint, size = 14.dp, modifier = Modifier.graphicsLayer { scaleX = -1f })
            },
        ),
        listOf(
            Tool("bold", ctl.isInlineActive(SpanKind.BOLD), onClick = { ctl.toggleInline(SpanKind.BOLD) }) { tint ->
                InkText("B", color = tint, size = 15.sp, weight = FontWeight.ExtraBold)
            },
            Tool("italic", ctl.isInlineActive(SpanKind.ITALIC), onClick = { ctl.toggleInline(SpanKind.ITALIC) }) { tint ->
                ToolText("I", tint, italic = true)
            },
            Tool("underline", ctl.isInlineActive(SpanKind.UNDERLINE), onClick = { ctl.toggleInline(SpanKind.UNDERLINE) }) { tint ->
                ToolText("U", tint, decoration = TextDecoration.Underline)
            },
            Tool("strike", ctl.isInlineActive(SpanKind.STRIKE), onClick = { ctl.toggleInline(SpanKind.STRIKE) }) { tint ->
                ToolText("S", tint, decoration = TextDecoration.LineThrough)
            },
        ),
        listOf(
            Tool("h1", ctl.isBlockActive(BlockType.H1), onClick = { ctl.setBlock(BlockType.H1) }) { tint -> ToolText("H1", tint, size = 12.sp) },
            Tool("h2", ctl.isBlockActive(BlockType.H2), onClick = { ctl.setBlock(BlockType.H2) }) { tint -> ToolText("H2", tint, size = 12.sp) },
            Tool("h3", ctl.isBlockActive(BlockType.H3), onClick = { ctl.setBlock(BlockType.H3) }) { tint -> ToolText("H3", tint, size = 12.sp) },
            Tool("p", ctl.isBlockActive(BlockType.P), onClick = { ctl.setBlock(BlockType.P) }) { tint ->
                InkText("\u00B6", color = tint, size = 16.sp, weight = FontWeight.Medium)
            },
        ),
        listOf(
            Tool("ul", ctl.isBlockActive(BlockType.UL), onClick = { ctl.setBlock(BlockType.UL) }) { tint -> ListIcon(tint, numbered = false) },
            Tool("ol", ctl.isBlockActive(BlockType.OL), onClick = { ctl.setBlock(BlockType.OL) }) { tint -> ListIcon(tint, numbered = true) },
            Tool("bq", ctl.isBlockActive(BlockType.QUOTE), onClick = { ctl.setBlock(BlockType.QUOTE) }) { tint ->
                InkText("\u201C", color = tint, size = 22.sp, weight = FontWeight.Bold)
            },
        ),
        listOf(
            Tool("left", ctl.isAlignActive(Align.LEFT), onClick = { ctl.setAlign(Align.LEFT) }) { tint -> LinesIcon(LinesKind.AlignLeft, tint) },
            Tool("center", ctl.isAlignActive(Align.CENTER), onClick = { ctl.setAlign(Align.CENTER) }) { tint -> LinesIcon(LinesKind.AlignCenter, tint) },
            Tool("right", ctl.isAlignActive(Align.RIGHT), onClick = { ctl.setAlign(Align.RIGHT) }) { tint -> LinesIcon(LinesKind.AlignRight, tint) },
            Tool("indent", onClick = { ctl.indent(1) }) { tint -> LinesIcon(LinesKind.Indent, tint) },
            Tool("outdent", onClick = { ctl.indent(-1) }) { tint -> LinesIcon(LinesKind.Outdent, tint) },
        ),
        listOf(
            Tool("highlight", ctl.isInlineActive(SpanKind.HIGHLIGHT), onClick = { ctl.toggleInline(SpanKind.HIGHLIGHT) }) { tint ->
                Box(Modifier.clip(RoundedCornerShape(3.dp)).background(Color(0x55C9A96E)).padding(horizontal = 3.dp)) {
                    InkText("ab", color = tint, size = 13.sp, weight = FontWeight.Bold)
                }
            },
            Tool("sup", ctl.isInlineActive(SpanKind.SUP), onClick = { ctl.toggleInline(SpanKind.SUP) }) { tint ->
                ScriptText("x", "2", BaselineShift.Superscript, tint)
            },
            Tool("sub", ctl.isInlineActive(SpanKind.SUB), onClick = { ctl.toggleInline(SpanKind.SUB) }) { tint ->
                ScriptText("x", "2", BaselineShift.Subscript, tint)
            },
            Tool("clear", onClick = ctl::clearFormat) { tint ->
                InkText(
                    buildAnnotatedString {
                        withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append("T") }
                        withStyle(SpanStyle(fontSize = 9.sp, baselineShift = BaselineShift.Subscript)) { append("x") }
                    },
                    color = tint,
                    size = 15.sp,
                    weight = FontWeight.Bold,
                )
            },
        ),
    )

    Column(modifier.fillMaxWidth().background(c.bg)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            groups.forEachIndexed { gi, group ->
                if (gi > 0) Box(Modifier.padding(horizontal = 4.dp).width(1.dp).height(20.dp).background(c.borderHi))
                group.forEach { ToolButton(it) }
            }
        }
    }
}

/** No `clickable` here on purpose: it would pull focus (and the selection) out of the text field. */
private fun Modifier.tapKeepFocus(enabled: Boolean, onClick: () -> Unit): Modifier = composed {
    val cb by rememberUpdatedState(onClick)
    val on by rememberUpdatedState(enabled)
    pointerInput(Unit) { detectTapGestures(onTap = { if (on) cb() }) }
}

@Composable
private fun ToolButton(tool: Tool) {
    val c = Ink.colors
    val shape = RoundedCornerShape(9.dp)
    val tint = if (tool.active) c.accent else c.text2
    Box(
        Modifier
            .height(34.dp)
            .widthIn(min = 34.dp)
            .alpha(if (tool.enabled) 1f else 0.35f)
            .clip(shape)
            .background(if (tool.active) c.accentDim else Color.Transparent)
            .border(1.dp, if (tool.active) c.accent else Color.Transparent, shape)
            .tapKeepFocus(tool.enabled, tool.onClick)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) { tool.content(tint) }
}

@Composable
private fun ToolText(
    label: String,
    tint: Color,
    italic: Boolean = false,
    decoration: TextDecoration? = null,
    size: androidx.compose.ui.unit.TextUnit = 15.sp,
) {
    androidx.compose.foundation.text.BasicText(
        label,
        style = androidx.compose.ui.text.TextStyle(
            color = tint,
            fontSize = size,
            fontWeight = FontWeight.Bold,
            fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
            textDecoration = decoration,
            fontFamily = Ink.sans,
        ),
    )
}

@Composable
private fun ScriptText(base: String, script: String, shift: BaselineShift, tint: Color) {
    InkText(
        buildAnnotatedString {
            append(base)
            withStyle(SpanStyle(baselineShift = shift, fontSize = 9.sp)) { append(script) }
        },
        color = tint,
        size = 15.sp,
        weight = FontWeight.Bold,
    )
}

@Composable
private fun ListIcon(tint: Color, numbered: Boolean) {
    Canvas(Modifier.size(16.dp)) {
        val sw = 1.6.dp.toPx()
        val ys = listOf(size.height * 0.2f, size.height * 0.5f, size.height * 0.8f)
        for (y in ys) {
            if (numbered) {
                // tiny tick instead of a dot: reads as "1 / 2 / 3"
                drawLine(tint, Offset(0.5f, y), Offset(size.width * 0.18f, y), strokeWidth = sw * 1.4f, cap = StrokeCap.Butt)
            } else {
                drawCircle(tint, radius = sw * 0.9f, center = Offset(sw, y))
            }
            drawLine(tint, Offset(size.width * 0.38f, y), Offset(size.width - sw / 2f, y), strokeWidth = sw, cap = StrokeCap.Round)
        }
    }
}

@Composable
private fun LinesIcon(kind: LinesKind, tint: Color) {
    Canvas(Modifier.size(16.dp)) {
        val w = size.width
        val h = size.height
        val sw = 1.6.dp.toPx()
        val pad = sw / 2f
        val ys = listOf(h * 0.15f, h * 0.38f, h * 0.62f, h * 0.85f)
        fun line(y: Float, x1: Float, x2: Float) =
            drawLine(tint, Offset(x1, y), Offset(x2, y), strokeWidth = sw, cap = StrokeCap.Round)

        when (kind) {
            LinesKind.AlignLeft, LinesKind.AlignCenter, LinesKind.AlignRight -> {
                val lens = listOf(w, w * 0.6f, w, w * 0.6f)
                for (i in ys.indices) {
                    val len = lens[i] - sw
                    val x1 = when (kind) {
                        LinesKind.AlignLeft -> pad
                        LinesKind.AlignCenter -> (w - len) / 2f
                        else -> w - pad - len
                    }
                    line(ys[i], x1, x1 + len)
                }
            }
            LinesKind.Indent, LinesKind.Outdent -> {
                line(ys[0], pad, w - pad)
                line(ys[3], pad, w - pad)
                line(ys[1], w * 0.5f, w - pad)
                line(ys[2], w * 0.5f, w - pad)
                val tri = Path().apply {
                    if (kind == LinesKind.Indent) {
                        moveTo(pad, h * 0.34f); lineTo(w * 0.32f, h * 0.5f); lineTo(pad, h * 0.66f)
                    } else {
                        moveTo(w * 0.32f, h * 0.34f); lineTo(pad, h * 0.5f); lineTo(w * 0.32f, h * 0.66f)
                    }
                    close()
                }
                drawPath(tri, tint)
            }
        }
    }
}
