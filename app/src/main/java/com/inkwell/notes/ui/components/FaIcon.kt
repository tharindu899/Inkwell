package com.inkwell.notes.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Font Awesome glyph (vector drawable `ic_fa_*`) tinted like CSS `color`.
 * [size] is the glyph height (≈ CSS font-size); width follows the glyph's own aspect ratio.
 */
@Composable
fun FaIcon(
    @DrawableRes id: Int,
    tint: Color,
    size: Dp = 16.dp,
    modifier: Modifier = Modifier,
) {
    val painter = painterResource(id)
    val sz = painter.intrinsicSize
    val ratio = if (sz.isSpecified && sz.height > 0f) sz.width / sz.height else 1f
    Image(
        painter = painter,
        contentDescription = null,
        modifier = modifier.height(size).aspectRatio(ratio),
        colorFilter = ColorFilter.tint(tint),
    )
}
