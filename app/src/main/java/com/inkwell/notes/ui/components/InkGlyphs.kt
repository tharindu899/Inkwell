package com.inkwell.notes.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp

/** The 18 notebook icons (keys like "fa-rocket"), drawn with Canvas. */
@Composable
fun NotebookGlyph(key: String, tint: Color, modifier: Modifier = Modifier.size(24.dp)) {
    Canvas(modifier) {
        val s = size.minDimension / 24f
        scale(s, s, pivot = Offset.Zero) {
            val sw = 2f
            val st = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
            fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
                drawLine(tint, Offset(x1, y1), Offset(x2, y2), sw, StrokeCap.Round)
            fun poly(vararg p: Float, close: Boolean = false) {
                val path = Path()
                path.moveTo(p[0], p[1])
                var i = 2
                while (i < p.size) { path.lineTo(p[i], p[i + 1]); i += 2 }
                if (close) path.close()
                drawPath(path, tint, style = st)
            }
            fun shape(build: Path.() -> Unit) = drawPath(Path().apply(build), tint, style = st)
            fun ring(x: Float, y: Float, r: Float) = drawCircle(tint, r, Offset(x, y), style = st)
            fun dot(x: Float, y: Float, r: Float) = drawCircle(tint, r, Offset(x, y))

            when (key) {
                "fa-user" -> {
                    ring(12f, 8f, 4f)
                    shape { moveTo(4f, 21f); cubicTo(5f, 17f, 8f, 15f, 12f, 15f); cubicTo(16f, 15f, 19f, 17f, 20f, 21f) }
                }
                "fa-briefcase" -> {
                    poly(3f, 8f, 21f, 8f, 21f, 19f, 3f, 19f, close = true)
                    poly(9f, 8f, 9f, 5f, 15f, 5f, 15f, 8f)
                    line(3f, 13f, 21f, 13f)
                }
                "fa-lightbulb" -> {
                    ring(12f, 10f, 5.5f)
                    line(10f, 17f, 14f, 17f)
                    line(10.5f, 20f, 13.5f, 20f)
                }
                "fa-heart" -> shape {
                    moveTo(12f, 20f)
                    cubicTo(3f, 14f, 3f, 6f, 8f, 5.5f)
                    cubicTo(10.5f, 5.3f, 12f, 7f, 12f, 8.5f)
                    cubicTo(12f, 7f, 13.5f, 5.3f, 16f, 5.5f)
                    cubicTo(21f, 6f, 21f, 14f, 12f, 20f)
                    close()
                }
                "fa-star" -> poly(12f, 3f, 14.6f, 9f, 21f, 9.5f, 16.2f, 13.8f, 17.6f, 20.5f, 12f, 17f, 6.4f, 20.5f, 7.8f, 13.8f, 3f, 9.5f, 9.4f, 9f, close = true)
                "fa-music" -> {
                    ring(8f, 18f, 3f)
                    ring(16f, 16f, 3f)
                    line(11f, 18f, 11f, 5f)
                    line(19f, 16f, 19f, 3f)
                    line(11f, 5f, 19f, 3f)
                }
                "fa-camera" -> {
                    poly(3f, 8f, 8f, 8f, 9.5f, 5f, 14.5f, 5f, 16f, 8f, 21f, 8f, 21f, 19f, 3f, 19f, close = true)
                    ring(12f, 13.5f, 3.5f)
                }
                "fa-globe" -> {
                    ring(12f, 12f, 9f)
                    drawOval(tint, Offset(8f, 3f), Size(8f, 18f), style = st)
                    line(3f, 12f, 21f, 12f)
                }
                "fa-code" -> {
                    poly(8f, 7f, 3f, 12f, 8f, 17f)
                    poly(16f, 7f, 21f, 12f, 16f, 17f)
                    line(14f, 5f, 10f, 19f)
                }
                "fa-plane" -> {
                    poly(21f, 3f, 3f, 10f, 10f, 14f, 14f, 21f, close = true)
                    line(10f, 14f, 21f, 3f)
                }
                "fa-coffee" -> {
                    poly(4f, 8f, 16f, 8f, 16f, 17f, 4f, 17f, close = true)
                    shape { moveTo(16f, 10f); cubicTo(21f, 10f, 21f, 15f, 16f, 15f) }
                    line(8f, 3f, 8f, 5f)
                    line(12f, 3f, 12f, 5f)
                }
                "fa-rocket" -> {
                    poly(12f, 3f, 16f, 9f, 16f, 16f, 8f, 16f, 8f, 9f, close = true)
                    poly(8f, 13f, 5f, 17f, 8f, 16f)
                    poly(16f, 13f, 19f, 17f, 16f, 16f)
                    ring(12f, 10f, 1.5f)
                    line(12f, 17f, 12f, 21f)
                }
                "fa-leaf" -> {
                    shape {
                        moveTo(5f, 19f)
                        cubicTo(5f, 9f, 11f, 4f, 20f, 4f)
                        cubicTo(20f, 13f, 15f, 19f, 5f, 19f)
                        close()
                    }
                    line(5f, 19f, 13f, 11f)
                }
                "fa-fire" -> shape {
                    moveTo(12f, 3f)
                    cubicTo(13f, 8f, 19f, 10f, 18f, 16f)
                    cubicTo(17.5f, 20f, 6.5f, 20f, 6f, 16f)
                    cubicTo(5.5f, 13f, 8f, 12f, 8.5f, 9f)
                    cubicTo(10f, 10f, 11.5f, 8f, 12f, 3f)
                    close()
                }
                "fa-flask" -> {
                    poly(10f, 3f, 10f, 10f, 5f, 19f, 19f, 19f, 14f, 10f, 14f, 3f)
                    line(9f, 3f, 15f, 3f)
                }
                "fa-palette" -> {
                    ring(12f, 12f, 9f)
                    dot(8f, 10f, 1.3f)
                    dot(12f, 7f, 1.3f)
                    dot(16f, 10f, 1.3f)
                }
                "fa-graduation-cap" -> {
                    poly(2f, 9f, 12f, 4f, 22f, 9f, 12f, 14f, close = true)
                    poly(6f, 11f, 6f, 16f, 12f, 19f, 18f, 16f, 18f, 11f)
                    line(22f, 9f, 22f, 15f)
                }
                else -> { // fa-book and anything unknown
                    shape {
                        moveTo(5f, 4f); lineTo(18f, 4f); lineTo(18f, 20f); lineTo(7f, 20f)
                        quadraticTo(5f, 20f, 5f, 18f); close()
                    }
                    line(9f, 8f, 14f, 8f)
                }
            }
        }
    }
}
