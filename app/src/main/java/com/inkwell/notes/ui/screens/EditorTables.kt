package com.inkwell.notes.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inkwell.notes.editor.Cell
import com.inkwell.notes.editor.RichStyle
import com.inkwell.notes.editor.TableData
import com.inkwell.notes.ui.components.tap
import com.inkwell.notes.ui.theme.Ink

/**
 * A table paragraph, drawn over the blank lines the text field reserves for it (see RichLayout / EditorScreen).
 * Columns are at least 120dp wide; wider tables scroll sideways inside their own frame (web `.md-table-wrap`).
 * Tapping a cell opens the cell sheet ([onCell]); in Reading mode [editable] is false and taps do nothing.
 */
@Composable
fun TableOverlay(
    table: TableData,
    style: RichStyle,
    editable: Boolean,
    onCell: (row: Int, col: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Ink.colors
    val shape = RoundedCornerShape(10.dp)
    BoxWithConstraints(modifier.fillMaxWidth().clip(shape).border(1.dp, c.border, shape)) {
        val colW = maxOf(120.dp, maxWidth / table.colCount)
        Column(Modifier.horizontalScroll(rememberScrollState())) {
            table.rows.forEachIndexed { r, row ->
                val head = table.header && r == 0
                Row(Modifier.height(IntrinsicSize.Min)) {
                    row.forEachIndexed { ci, cell ->
                        androidx.compose.foundation.layout.Box(
                            Modifier
                                .width(colW)
                                .fillMaxHeight()
                                .background(if (head) c.bgElevated else c.bgCard)
                                .border(0.5.dp, c.border)
                                .then(if (editable) Modifier.tap { onCell(r, ci) } else Modifier)
                                .padding(horizontal = 10.dp, vertical = 9.dp)
                                .heightIn(min = 22.dp),
                        ) {
                            BasicText(
                                cellString(cell, style),
                                style = TextStyle(
                                    color = c.text1,
                                    fontSize = (style.fontSizeSp * 0.92f).sp,
                                    lineHeight = (style.fontSizeSp * 0.92f * 1.4f).sp,
                                    fontWeight = if (head) FontWeight.Bold else FontWeight.Normal,
                                    fontFamily = Ink.sans,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A cell's text with its inline marks (bold, links, inline code …). */
private fun cellString(cell: Cell, style: RichStyle): AnnotatedString = buildAnnotatedString {
    val t = cell.text
    var a = 0
    while (a < t.length) {
        var b = a + 1
        while (b < t.length && cell.marks[b] == cell.marks[a]) b++
        val sp = style.markSpan(cell.marks[a])
        if (sp != null) pushStyle(sp)
        append(t.substring(a, b))
        if (sp != null) pop()
        a = b
    }
}
