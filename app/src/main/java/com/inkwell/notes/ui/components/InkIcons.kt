package com.inkwell.notes.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp

enum class InkIcon { Home, Tag, Plus, Book, User, Search, Pin, Trash, Close, Check, Pen }

/** Tiny stroke icons drawn on a 24x24 grid — no image files, any colour. */
@Composable
fun InkIconView(
    icon: InkIcon,
    tint: Color,
    modifier: Modifier = Modifier.size(24.dp),
    strokeWidth: Float = 2.2f,
) {
    Canvas(modifier) {
        val s = size.minDimension / 24f
        scale(s, s, pivot = Offset.Zero) {
            val st = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
            fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
                drawLine(tint, Offset(x1, y1), Offset(x2, y2), strokeWidth, StrokeCap.Round)
            fun poly(vararg p: Float, close: Boolean = false) {
                val path = Path()
                path.moveTo(p[0], p[1])
                var i = 2
                while (i < p.size) { path.lineTo(p[i], p[i + 1]); i += 2 }
                if (close) path.close()
                drawPath(path, tint, style = st)
            }
            when (icon) {
                InkIcon.Home -> poly(4f, 11f, 12f, 4f, 20f, 11f, 20f, 20f, 4f, 20f, close = true)
                InkIcon.Tag -> {
                    poly(3f, 3f, 12f, 3f, 21f, 12f, 12f, 21f, 3f, 12f, close = true)
                    drawCircle(tint, 1.6f, Offset(8f, 8f))
                }
                InkIcon.Plus -> { line(12f, 5f, 12f, 19f); line(5f, 12f, 19f, 12f) }
                InkIcon.Book -> {
                    val p = Path()
                    p.moveTo(5f, 4f); p.lineTo(18f, 4f); p.lineTo(18f, 20f); p.lineTo(7f, 20f)
                    p.quadraticTo(5f, 20f, 5f, 18f); p.close()
                    drawPath(p, tint, style = st)
                    line(9f, 8f, 14f, 8f)
                }
                InkIcon.User -> {
                    drawCircle(tint, 4f, Offset(12f, 8f), style = st)
                    val p = Path()
                    p.moveTo(4f, 21f); p.cubicTo(5f, 17f, 8f, 15f, 12f, 15f); p.cubicTo(16f, 15f, 19f, 17f, 20f, 21f)
                    drawPath(p, tint, style = st)
                }
                InkIcon.Search -> {
                    drawCircle(tint, 7f, Offset(11f, 11f), style = st)
                    line(20f, 20f, 16.2f, 16.2f)
                }
                InkIcon.Pin -> {
                    poly(8f, 3f, 16f, 3f, 15f, 10f, 18f, 14f, 6f, 14f, 9f, 10f, close = true)
                    line(12f, 14f, 12f, 21f)
                }
                InkIcon.Trash -> {
                    line(4f, 7f, 20f, 7f)
                    poly(9f, 7f, 9f, 4f, 15f, 4f, 15f, 7f)
                    poly(6f, 7f, 7f, 20f, 17f, 20f, 18f, 7f)
                }
                InkIcon.Close -> { line(6f, 6f, 18f, 18f); line(18f, 6f, 6f, 18f) }
                InkIcon.Check -> poly(5f, 12f, 10f, 17f, 19f, 7f)
                InkIcon.Pen -> poly(4f, 20f, 8f, 19f, 19f, 8f, 16f, 5f, 5f, 16f, close = true)
            }
        }
    }
}
