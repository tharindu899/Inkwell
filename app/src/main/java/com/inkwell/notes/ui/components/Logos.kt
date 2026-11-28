package com.inkwell.notes.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Simple 4-colour "G" drawn with Canvas (no image file needed). */
@Composable
fun GoogleLogo(logoSize: Dp = 20.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(logoSize)) {
        val s = size.minDimension
        val stroke = s * 0.2f
        val topLeft = Offset(stroke / 2, stroke / 2)
        val arcSize = Size(s - stroke, s - stroke)
        fun arc(color: Color, start: Float, sweep: Float) =
            drawArc(color, start, sweep, false, topLeft, arcSize, style = Stroke(stroke))
        arc(Color(0xFFEA4335), 225f, 90f)
        arc(Color(0xFFFBBC05), 135f, 90f)
        arc(Color(0xFF34A853), 45f, 90f)
        arc(Color(0xFF4285F4), 350f, 55f)
        drawLine(Color(0xFF4285F4), Offset(s / 2, s / 2), Offset(s - stroke / 2, s / 2), strokeWidth = stroke)
    }
}

/** Ink drop mark for the login screen, drawn as a Canvas path. */
@Composable
fun InkDropLogo(modifier: Modifier, color: Color) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val drop = Path().apply {
            moveTo(w * 0.5f, h * 0.08f)
            cubicTo(w * 0.5f, h * 0.08f, w * 0.18f, h * 0.45f, w * 0.18f, h * 0.64f)
            cubicTo(w * 0.18f, h * 0.82f, w * 0.33f, h * 0.92f, w * 0.5f, h * 0.92f)
            cubicTo(w * 0.67f, h * 0.92f, w * 0.82f, h * 0.82f, w * 0.82f, h * 0.64f)
            cubicTo(w * 0.82f, h * 0.45f, w * 0.5f, h * 0.08f, w * 0.5f, h * 0.08f)
            close()
        }
        drawPath(drop, color)
    }
}
