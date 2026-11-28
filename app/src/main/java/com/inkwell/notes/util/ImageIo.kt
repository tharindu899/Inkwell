package com.inkwell.notes.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import android.util.LruCache
import java.io.ByteArrayOutputStream

/**
 * Picture plumbing for the editor: picked photos → `data:image/…` URLs (what the web app stores inside the
 * note's HTML), and data URLs → bitmaps for display. Everything here is blocking: call it off the main thread.
 */
object ImageIo {
    /** Longest side kept for a picked photo — plenty for a phone note, keeps the saved note small. */
    private const val MAX_SIDE = 1600
    private const val JPEG_QUALITY = 85

    /** Decoded bitmaps by source string; sized in KB, so a handful of big pictures at most. */
    private val cache = object : LruCache<String, Bitmap>(24 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    /**
     * Reads [uri], fixes the camera rotation, scales it down to [MAX_SIDE] and returns it as a data URL
     * (PNG for PNG sources so transparency survives, JPEG otherwise). null = unreadable / not an image.
     */
    fun uriToDataUrl(context: Context, uri: Uri): String? = runCatching {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri).orEmpty().lowercase()

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        var bmp = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: return@runCatching null

        val orientation = resolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
        }
        val longest = maxOf(bmp.width, bmp.height)
        if (longest > MAX_SIDE) {
            val s = MAX_SIDE.toFloat() / longest
            m.postScale(s, s)
        }
        if (!m.isIdentity) bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)

        encode(bmp, png = mime == "image/png" || mime == "image/webp" || mime == "image/gif")
    }.getOrNull()

    /** [bmp] → `data:image/png;base64,…` (drawings) or JPEG. */
    fun encode(bmp: Bitmap, png: Boolean): String {
        val out = ByteArrayOutputStream()
        if (png) bmp.compress(Bitmap.CompressFormat.PNG, 100, out) else bmp.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        val b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        return (if (png) "data:image/png;base64," else "data:image/jpeg;base64,") + b64
    }

    /** Bitmap of a `data:image/…;base64,…` source, down-sampled to about [targetWidthPx]. Cached. null = broken data. */
    fun decodeDataUrl(src: String, targetWidthPx: Int): Bitmap? {
        val key = "${src.hashCode()}:${src.length}:${targetWidthPx / 64}"
        cache.get(key)?.let { return it }
        return runCatching {
            val comma = src.indexOf(',')
            if (comma < 0) return@runCatching null
            val bytes = Base64.decode(src.substring(comma + 1), Base64.DEFAULT)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0) return@runCatching null
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= targetWidthPx.coerceAtLeast(64)) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        }.getOrNull()?.also { cache.put(key, it) }
    }
}
