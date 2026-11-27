package com.inkwell.notes.util

import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.SpannableStringBuilder
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.StyleSpan
import java.io.ByteArrayOutputStream

/**
 * Note → A4 PDF with Android's own text layout, so Sinhala / Tamil / emoji render correctly
 * (the web exporter only knew Latin Helvetica). Text only, like the web version.
 */
object PdfExport {
    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 48

    fun render(title: String, body: String): ByteArray {
        val width = PAGE_W - 2 * MARGIN
        val pageH = PAGE_H - 2 * MARGIN

        val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
            textSize = 11f
            color = 0xFF1A1A1A.toInt()
        }
        val sb = SpannableStringBuilder(title.ifEmpty { "Untitled" })
        sb.setSpan(AbsoluteSizeSpan(18), 0, sb.length, 0)
        sb.setSpan(StyleSpan(Typeface.BOLD), 0, sb.length, 0)
        sb.append("\n\n").append(body)

        val layout = StaticLayout.Builder.obtain(sb, 0, sb.length, paint, width)
            .setLineSpacing(3f, 1f)
            .build()

        val pdf = PdfDocument()
        var line = 0
        var pageNo = 1
        do {
            val top = layout.getLineTop(line)
            var end = line
            while (end < layout.lineCount && layout.getLineBottom(end) - top <= pageH) end++
            if (end == line) end = line + 1
            end = end.coerceAtMost(layout.lineCount)

            val page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
            val c = page.canvas
            c.translate(MARGIN.toFloat(), MARGIN.toFloat())
            c.clipRect(0f, 0f, width.toFloat(), (layout.getLineBottom(end - 1) - top).toFloat())
            c.translate(0f, -top.toFloat())
            layout.draw(c)
            pdf.finishPage(page)

            line = end
            pageNo++
        } while (line < layout.lineCount)

        val out = ByteArrayOutputStream()
        pdf.writeTo(out)
        pdf.close()
        return out.toByteArray()
    }
}
