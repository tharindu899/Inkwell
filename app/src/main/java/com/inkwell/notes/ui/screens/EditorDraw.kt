package com.inkwell.notes.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import com.inkwell.notes.ui.components.InkText
import com.inkwell.notes.ui.components.onAccent
import com.inkwell.notes.ui.components.tap
import com.inkwell.notes.ui.theme.Ink
import com.inkwell.notes.util.ImageIo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One finished (or in-progress) pen stroke, in canvas pixels. A single point = a tap = a dot. */
private class PenStroke(val color: Color, val widthDp: Float, val points: List<Offset>)

private val PAPER = Color(0xFFF7F0DF)
private val SWATCHES = listOf(0xFFF28C40, 0xFFF8F8F8, 0xFF7DD3FC, 0xFF86EFAC, 0xFFFDA4AF).map { Color(it) }

/**
 * Sketch dialog (web `drawModal`): colour swatches, brush 1..10, paper-coloured canvas, Undo (last stroke),
 * Clear, Cancel, Insert. Insert renders the strokes to a PNG and hands back its data URL.
 */
@Composable
fun DrawModal(onInsert: (dataUrl: String) -> Unit, onClose: () -> Unit) {
    val c = Ink.colors
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val strokes = remember { mutableStateListOf<PenStroke>() }
    var live by remember { mutableStateOf<PenStroke?>(null) }
    var color by remember { mutableStateOf(SWATCHES[0]) }
    var brush by remember { mutableIntStateOf(3) }
    var canvasW by remember { mutableIntStateOf(0) }
    var canvasH by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }

    val colorNow = rememberUpdatedState(color)
    val brushNow = rememberUpdatedState(brush)
    val cardShape = RoundedCornerShape(22.dp)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.44f))
            .tap(onClose)
            .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.navigationBars).union(WindowInsets.ime))
            .padding(horizontal = 13.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .clip(cardShape)
                .background(c.bg)
                .border(1.dp, c.border, cardShape)
                .tap { /* swallow taps */ }
                .padding(18.dp),
        ) {
            InkText("Draw", family = Ink.serif, size = 20.sp, weight = FontWeight.Bold, lineHeight = 24.sp)
            Spacer(Modifier.height(4.dp))
            InkText("Sketch something, then insert it into your note.", size = 12.sp, color = c.text2, lineHeight = 17.sp)
            Spacer(Modifier.height(12.dp))

            // tools: colours + brush
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SWATCHES.forEach { sw ->
                    Box(
                        Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(sw)
                            .border(if (sw == color) 3.dp else 1.dp, if (sw == color) c.accent else c.borderHi, CircleShape)
                            .tap { color = sw },
                    )
                }
                InkText("Brush", size = 11.sp, weight = FontWeight.ExtraBold, color = c.text3, modifier = Modifier.padding(start = 4.dp))
                BrushSlider(brush, { brush = it }, Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))

            // canvas
            val canvasShape = RoundedCornerShape(16.dp)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .clip(canvasShape)
                    .border(1.dp, c.borderHi, canvasShape)
                    .background(PAPER)
                    .onSizeChanged { canvasW = it.width; canvasH = it.height }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Main)
                            down.consume()
                            val pts = ArrayList<Offset>()
                            pts.add(down.position)
                            val col = colorNow.value
                            val wDp = brushNow.value.toFloat()
                            live = PenStroke(col, wDp, pts.toList())
                            while (true) {
                                val ev = awaitPointerEvent(PointerEventPass.Main)
                                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                ch.consume()
                                if (!ch.pressed) break
                                pts.add(ch.position)
                                live = PenStroke(col, wDp, pts.toList())
                            }
                            strokes.add(PenStroke(col, wDp, pts.toList()))
                            live = null
                        }
                    },
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    for (s in strokes + listOfNotNull(live)) drawPen(s, density.density)
                }
            }
            Spacer(Modifier.height(14.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DrawButton("Cancel", c.bgCard, c.text2, c.border, Modifier.weight(1f), true, onClose)
                DrawButton("Undo", c.bgCard, c.text1, c.border, Modifier.weight(1f), strokes.isNotEmpty()) {
                    if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex)
                }
                DrawButton("Clear", c.bgCard, c.text1, c.border, Modifier.weight(1f), strokes.isNotEmpty()) { strokes.clear() }
                DrawButton("Insert", c.accent, onAccent(), c.accent, Modifier.weight(1f), strokes.isNotEmpty() && !busy) {
                    busy = true
                    val snapshot = strokes.toList()
                    val w = canvasW.coerceAtLeast(64)
                    val h = canvasH.coerceAtLeast(64)
                    val d = density.density
                    scope.launch {
                        val url = withContext(Dispatchers.Default) { renderStrokes(snapshot, w, h, d) }
                        busy = false
                        onInsert(url)
                    }
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPen(s: PenStroke, density: Float) {
    val w = s.widthDp * density
    if (s.points.size == 1) {
        drawCircle(s.color, radius = maxOf(1f, s.widthDp) * density, center = s.points[0])
        return
    }
    val p = Path()
    p.moveTo(s.points[0].x, s.points[0].y)
    for (i in 1 until s.points.size) p.lineTo(s.points[i].x, s.points[i].y)
    drawPath(p, s.color, style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** Paints the strokes on a paper-coloured bitmap and returns it as a PNG data URL. */
private fun renderStrokes(strokes: List<PenStroke>, w: Int, h: Int, density: Float): String {
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val cv = android.graphics.Canvas(bmp)
    cv.drawColor(PAPER.toArgb())
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = android.graphics.Paint.Cap.ROUND
        strokeJoin = android.graphics.Paint.Join.ROUND
    }
    for (s in strokes) {
        paint.color = s.color.toArgb()
        if (s.points.size == 1) {
            paint.style = android.graphics.Paint.Style.FILL
            cv.drawCircle(s.points[0].x, s.points[0].y, maxOf(1f, s.widthDp) * density, paint)
        } else {
            paint.style = android.graphics.Paint.Style.STROKE
            paint.strokeWidth = s.widthDp * density
            val p = android.graphics.Path()
            p.moveTo(s.points[0].x, s.points[0].y)
            for (i in 1 until s.points.size) p.lineTo(s.points[i].x, s.points[i].y)
            cv.drawPath(p, paint)
        }
    }
    return ImageIo.encode(bmp, png = true)
}

@Composable
private fun DrawButton(label: String, bg: Color, fg: Color, border: Color, modifier: Modifier, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .alpha(if (enabled) 1f else 0.4f)
            .heightIn(min = 42.dp)
            .clip(shape)
            .background(bg)
            .border(1.dp, border, shape)
            .tap { if (enabled) onClick() },
        contentAlignment = Alignment.Center,
    ) {
        InkText(label, size = 13.sp, weight = FontWeight.Black, color = fg, maxLines = 1)
    }
}

/** Brush size 1..10 (web `<input type=range>`): tap or drag along the track. */
@Composable
private fun BrushSlider(value: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = Ink.colors
    var widthPx by remember { mutableIntStateOf(1) }
    fun at(x: Float): Int = (1 + (x / widthPx.coerceAtLeast(1) * 9f).roundToInt()).coerceIn(1, 10)
    Box(
        modifier
            .height(32.dp)
            .onSizeChanged { widthPx = it.width }
            .pointerInput(Unit) { detectTapGestures { onChange(at(it.x)) } }
            .pointerInput(Unit) { detectDragGestures { change, _ -> change.consume(); onChange(at(change.position.x)) } },
        contentAlignment = Alignment.CenterStart,
    ) {
        Canvas(Modifier.fillMaxWidth().height(32.dp)) {
            val y = size.height / 2f
            drawLine(c.borderHi, Offset(8f, y), Offset(size.width - 8f, y), 3.dp.toPx(), StrokeCap.Round)
            val frac = (value - 1) / 9f
            val x = 8f + (size.width - 16f) * frac
            drawLine(c.accent, Offset(8f, y), Offset(x, y), 3.dp.toPx(), StrokeCap.Round)
            drawCircle(c.accent, radius = (5 + value).dp.toPx() / 2f + 3.dp.toPx(), center = Offset(x, y))
        }
    }
}
