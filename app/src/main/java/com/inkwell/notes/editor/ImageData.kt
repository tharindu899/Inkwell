package com.inkwell.notes.editor

/**
 * Payload of a [BlockType.IMAGE] paragraph (the web `<figure class="editor-figure">`): a photo or a drawing.
 *
 * [src]     : `data:image/…` URL (photos picked / drawings made here) or an http(s) URL (pasted markdown).
 * [width]   : display width in dp, 90..900 (web: 180 photos / 190 drawings by default).
 * [x] / [y] : the web's "drag to move" margins (margin-left 0..900, margin-top 0..80), in dp.
 */
data class ImageData(
    val src: String,
    val alt: String = "image",
    val drawing: Boolean = false,
    val width: Int = defaultWidth(drawing),
    val x: Int = 0,
    val y: Int = 0,
) {
    fun withWidth(w: Int): ImageData = copy(width = w.coerceIn(MIN_WIDTH, MAX_WIDTH))

    fun withOffset(nx: Int, ny: Int): ImageData = copy(x = nx.coerceIn(0, MAX_X), y = ny.coerceIn(0, MAX_Y))

    companion object {
        const val MIN_WIDTH = 90
        const val MAX_WIDTH = 900
        const val MAX_X = 900
        const val MAX_Y = 80

        fun defaultWidth(drawing: Boolean): Int = if (drawing) 190 else 180

        /** Same test the web sanitizer uses for `<img src>` (minus relative paths, which can't resolve here). */
        fun isSupportedSrc(src: String): Boolean {
            val s = src.trim()
            return s.startsWith("data:image/", true) || s.startsWith("http://", true) || s.startsWith("https://", true)
        }

        /** The web decides photo vs drawing from the alt text. */
        fun looksLikeDrawing(alt: String): Boolean = alt.contains("drawing", true) || alt.contains("sketch", true)

        fun fromMarkdown(alt: String, src: String): ImageData? {
            if (!isSupportedSrc(src)) return null
            val a = alt.ifBlank { "image" }
            return ImageData(src.trim(), a, looksLikeDrawing(a))
        }
    }
}
