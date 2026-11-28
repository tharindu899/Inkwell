package com.inkwell.notes.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.inkwell.notes.R
import com.inkwell.notes.editor.ImageData
import com.inkwell.notes.ui.components.FaIcon
import com.inkwell.notes.ui.components.tap
import com.inkwell.notes.ui.theme.Ink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The picture itself: a decoded data URL (photos, drawings) or a remote URL (pasted markdown), width-filling. */
@Composable
fun InkImage(src: String, widthPx: Int, modifier: Modifier = Modifier) {
    if (src.startsWith("data:", ignoreCase = true)) {
        val bmp by produceState<android.graphics.Bitmap?>(null, src, widthPx / 64) {
            value = withContext(Dispatchers.Default) { com.inkwell.notes.util.ImageIo.decodeDataUrl(src, widthPx) }
        }
        val b = bmp
        if (b != null) {
            Image(
                bitmap = b.asImageBitmap(),
                contentDescription = null,
                modifier = modifier.fillMaxWidth(),
                contentScale = ContentScale.FillWidth,
            )
        } else {
            Box(modifier.fillMaxWidth().heightIn(min = 64.dp).background(Ink.colors.bgCard))
        }
    } else {
        AsyncImage(
            model = src,
            contentDescription = null,
            modifier = modifier.fillMaxWidth().heightIn(min = 48.dp),
            contentScale = ContentScale.FillWidth,
        )
    }
}

/**
 * An image paragraph, drawn over the blank lines the text field reserves for it (like [TableOverlay]).
 * Tap = select. A selected picture shows three round handles (web `.media-*` buttons): delete (top right),
 * move (top left — drags the picture's left / top margin), resize (bottom right). Changes are committed on release.
 */
@Composable
fun ImageOverlay(
    image: ImageData,
    selected: Boolean,
    editable: Boolean,
    onSelect: () -> Unit,
    onCommit: (ImageData) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Ink.colors
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val maxW = maxWidth.value.coerceAtLeast(ImageData.MIN_WIDTH.toFloat())
        // Live values while a handle is dragged; reset whenever the saved picture changes.
        var w by remember(image) { mutableFloatStateOf(image.width.toFloat()) }
        var ox by remember(image) { mutableFloatStateOf(image.x.toFloat()) }
        var oy by remember(image) { mutableFloatStateOf(image.y.toFloat()) }

        val shownW = w.coerceIn(ImageData.MIN_WIDTH.toFloat(), maxW - 8f)
        val maxX = (maxW - shownW - 8f).coerceAtLeast(0f)
        val left = ox.coerceIn(0f, maxX)

        val latest = rememberUpdatedState(image)
        val commitCb = rememberUpdatedState(onCommit)
        fun commit() = commitCb.value(latest.value.withWidth(w.toInt()).withOffset(ox.toInt(), oy.toInt()))

        Box(Modifier.padding(start = left.dp, top = oy.coerceIn(0f, ImageData.MAX_Y.toFloat()).dp).width(shownW.dp)) {
            val shape = RoundedCornerShape(10.dp)
            Box(
                Modifier
                    .clip(shape)
                    .then(if (selected) Modifier.border(2.dp, c.accent, shape) else Modifier)
                    .then(if (editable) Modifier.tap(onSelect) else Modifier),
            ) {
                InkImage(image.src, with(density) { shownW.dp.roundToPx() })
            }

            if (selected && editable) {
                // delete
                Handle(Modifier.align(Alignment.TopEnd).offset(x = (-6).dp, y = 6.dp).tap(onDelete), Color.Black.copy(alpha = 0.62f)) {
                    FaIcon(R.drawable.ic_fa_trash_can, tint = Color.White, size = 13.dp)
                }
                // move
                Handle(
                    Modifier
                        .align(Alignment.TopStart)
                        .offset(x = 6.dp, y = 6.dp)
                        .pointerInput(image, maxX) {
                            detectDragGestures(
                                onDragEnd = { commit() },
                                onDragCancel = { commit() },
                            ) { change, drag ->
                                change.consume()
                                ox = (ox + drag.x / density.density).coerceIn(0f, maxX)
                                oy = (oy + drag.y / density.density).coerceIn(0f, ImageData.MAX_Y.toFloat())
                            }
                        },
                    Color.Black.copy(alpha = 0.62f),
                ) { MoveGlyph() }
                // resize
                Handle(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = (-6).dp, y = (-6).dp)
                        .pointerInput(image, maxW) {
                            detectDragGestures(
                                onDragEnd = { commit() },
                                onDragCancel = { commit() },
                            ) { change, drag ->
                                change.consume()
                                w = (w + drag.x / density.density).coerceIn(ImageData.MIN_WIDTH.toFloat(), (maxW - 8f - ox).coerceAtLeast(ImageData.MIN_WIDTH.toFloat()))
                            }
                        },
                    c.accent,
                ) { ResizeGlyph() }
            }
        }
    }
}

@Composable
private fun Handle(modifier: Modifier, bg: Color, content: @Composable () -> Unit) {
    Box(modifier.size(30.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) { content() }
}

/** Four-way arrow. */
@Composable
private fun MoveGlyph() {
    Canvas(Modifier.size(14.dp)) {
        val w = size.width
        val sw = 1.6.dp.toPx()
        val m = w / 2f
        drawLine(Color.White, Offset(m, 1f), Offset(m, w - 1f), sw, StrokeCap.Round)
        drawLine(Color.White, Offset(1f, m), Offset(w - 1f, m), sw, StrokeCap.Round)
        val a = w * 0.22f
        for ((dx, dy) in listOf(0f to -1f, 0f to 1f, -1f to 0f, 1f to 0f)) {
            val tip = Offset(m + dx * (m - 1f), m + dy * (m - 1f))
            val back = Offset(tip.x - dx * a, tip.y - dy * a)
            val side = Offset(-dy * a, dx * a)
            drawLine(Color.White, tip, Offset(back.x + side.x, back.y + side.y), sw, StrokeCap.Round)
            drawLine(Color.White, tip, Offset(back.x - side.x, back.y - side.y), sw, StrokeCap.Round)
        }
    }
}

/** Diagonal two-way arrow. */
@Composable
private fun ResizeGlyph() {
    Canvas(Modifier.size(13.dp)) {
        val w = size.width
        val sw = 1.7.dp.toPx()
        val tint = Color.White
        drawLine(tint, Offset(1f, w - 1f), Offset(w - 1f, 1f), sw, StrokeCap.Round)
        drawLine(tint, Offset(w - 1f, 1f), Offset(w - 1f - w * 0.38f, 1f), sw, StrokeCap.Round)
        drawLine(tint, Offset(w - 1f, 1f), Offset(w - 1f, 1f + w * 0.38f), sw, StrokeCap.Round)
        drawLine(tint, Offset(1f, w - 1f), Offset(1f + w * 0.38f, w - 1f), sw, StrokeCap.Round)
        drawLine(tint, Offset(1f, w - 1f), Offset(1f, w - 1f - w * 0.38f), sw, StrokeCap.Round)
    }
}
