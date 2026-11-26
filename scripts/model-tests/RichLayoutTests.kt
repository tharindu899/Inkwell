import com.inkwell.notes.editor.*
var f = 0
fun ck(n: String, c: Boolean, x: String = "") { if (!c) { f++; println("FAIL $n $x") } else println("ok   $n") }
fun main() {
    val doc = HtmlCodec.parse("<ul><li data-collapsed=\"true\">A<ul><li>A1</li><li>A2</li></ul></li><li>B</li></ul><p>after</p>")!!
    val l = RichLayout(doc)
    val shown = "▸ •  A\n•  B\nafter"
    ck("length", l.length == shown.length, l.length.toString())
    ck("prefix A", l.prefixes[0] == "▸ •  ", l.prefixes[0])
    ck("chevron offset", l.chevronOffset(0) == 0)
    ck("hidden prefix", l.prefixStart(1) == -1)
    ck("o2t A start", l.originalToTransformed(0) == 5, l.originalToTransformed(0).toString())
    ck("o2t A end", l.originalToTransformed(1) == 6)
    ck("o2t hidden → A end", l.originalToTransformed(3) == 6 && l.originalToTransformed(6) == 6)
    ck("o2t B", l.originalToTransformed(8) == 10, l.originalToTransformed(8).toString())
    ck("t2o prefix → text start", l.transformedToOriginal(0) == 0 && l.transformedToOriginal(4) == 0)
    ck("t2o end of A", l.transformedToOriginal(6) == 1)
    ck("t2o B start", l.transformedToOriginal(7) == 8 && l.transformedToOriginal(10) == 8, l.transformedToOriginal(10).toString())
    ck("para at", l.paraAtTransformed(8) == 3 && l.paraAtTransformed(0) == 0 && l.paraAtTransformed(shown.length) == 4)
    ck("monotone", (0 until l.length).all { l.transformedToOriginal(it) <= l.transformedToOriginal(it + 1) })
    ck("monotone2", (0 until doc.text.length).all { l.originalToTransformed(it) <= l.originalToTransformed(it + 1) })
    val d2 = RichDoc.create("a\nb", IntArray(3), listOf(Para(BlockType.TASK, checked = true), Para(BlockType.OL)))
    val l2 = RichLayout(d2)
    ck("task prefix", l2.prefixes[0] == "☑  " && l2.boxOffset(0) == 0 && l2.chevronOffset(0) == -1)
    ck("ol prefix", l2.prefixes[1] == "1.  ")
    ck("len2", l2.length == 3 + 1 + 1 + 4 + 1, l2.length.toString())
    println(if (f == 0) "ALL OK" else "$f FAILED")
}
