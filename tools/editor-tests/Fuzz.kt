import com.inkwell.notes.editor.*
import kotlin.random.Random

fun invariants(d: Doc, tag: String): Boolean {
    var ok = d.blocks.size == d.text.count { it == '\n' } + 1
    for (k in SpanKind.entries) {
        var prevEnd = -1
        for (sp in d.spans.filter { it.kind == k }) {
            if (sp.start < 0 || sp.end > d.text.length || sp.end <= sp.start || sp.start <= prevEnd) ok = false
            prevEnd = sp.end
        }
    }
    if (!ok) println("INVARIANT BROKEN $tag: $d")
    return ok
}

fun main() {
    val rnd = Random(42)
    var d = Doc.plain("hello\nworld foo\nbar")
    val alphabet = "ab \n"
    var bad = 0
    repeat(20000) { n ->
        val t = d.text
        when (rnd.nextInt(6)) {
            0 -> { // insert
                val p = rnd.nextInt(t.length + 1)
                val s = (1..rnd.nextInt(1, 4)).map { alphabet[rnd.nextInt(alphabet.length)] }.joinToString("")
                val nt = t.substring(0, p) + s + t.substring(p)
                d = DocOps.applyTextEdit(d, nt, p + s.length, if (rnd.nextBoolean()) emptyMap() else mapOf(SpanKind.entries[rnd.nextInt(7)] to rnd.nextBoolean()))
            }
            1 -> { // delete
                if (t.isNotEmpty()) {
                    val a = rnd.nextInt(t.length); val b = minOf(t.length, a + rnd.nextInt(1, 5))
                    d = DocOps.applyTextEdit(d, t.removeRange(a, b), a, emptyMap())
                }
            }
            2 -> { // replace, caret end or unknown
                if (t.isNotEmpty()) {
                    val a = rnd.nextInt(t.length); val b = minOf(t.length, a + rnd.nextInt(1, 5))
                    val s = alphabet[rnd.nextInt(alphabet.length)].toString()
                    d = DocOps.applyTextEdit(d, t.replaceRange(a, b, s), if (rnd.nextBoolean()) a + 1 else -1, emptyMap())
                }
            }
            3 -> { val a = rnd.nextInt(t.length + 1); val b = rnd.nextInt(t.length + 1); d = DocOps.toggleSpan(d, SpanKind.entries[rnd.nextInt(7)], a, b) }
            4 -> { val a = rnd.nextInt(t.length + 1); val b = rnd.nextInt(t.length + 1)
                d = when (rnd.nextInt(5)) { 0 -> DocOps.setBlockType(d, a, b, BlockType.entries[rnd.nextInt(7)]); 1 -> DocOps.setAlign(d, a, b, Align.entries[rnd.nextInt(3)])
                    2 -> DocOps.indent(d, a, b, rnd.nextInt(-1, 2)); 3 -> DocOps.clearFormat(d, a, b); else -> d } }
            5 -> { // html round trip must be stable
                val h = HtmlCodec.toHtml(d)
                val back = HtmlCodec.parse(h)
                if (back == null) { println("PARSE NULL for $h"); bad++ }
                else {
                    val h2 = HtmlCodec.toHtml(back)
                    if (h != h2) { println("HTML UNSTABLE\n $h\n $h2"); bad++ }
                    if (back.text != d.text && d.text.isNotEmpty()) { println("TEXT DIFF [${d.text}] vs [${back.text}] html=$h"); bad++ }
                }
            }
        }
        if (!invariants(d, "step $n")) bad++
        if (bad > 5) { println("stopping"); return }
        if (d.text.length > 200) d = Doc.plain("reset\nx")
    }

    // offset mapping model (same math as RichTransformation)
    val doc = HtmlCodec.parse("<h1>Title</h1><ul><li>a</li><li>b<ul><li>c</li></ul></li></ul><ol><li>x</li></ol><p></p><p>end</p>")!!
    val pre = Array(doc.lineCount) { i -> when (doc.blocks[i].type) { BlockType.UL -> "• "; BlockType.OL -> "1. "; else -> "" } }
    val before = IntArray(doc.lineCount).also { var s = 0; for (i in it.indices) { it[i] = s; s += pre[i].length } }
    val tl = doc.text.length + before.last() + pre.last().length + 1
    fun o2t(o: Int): Int { val i = doc.lineIndexAt(o); return o + before[i] + pre[i].length }
    fun t2o(t: Int): Int { var lo = 0; var hi = doc.lineCount - 1
        while (lo < hi) { val m = (lo + hi + 1) ushr 1; if (doc.lineStarts[m] + before[m] <= t) lo = m else hi = m - 1 }
        val r = t - (doc.lineStarts[lo] + before[lo]); return (if (r < pre[lo].length) doc.lineStarts[lo] else doc.lineStarts[lo] + r - pre[lo].length).coerceIn(0, doc.text.length) }
    var mapOk = true
    var prev = -1
    for (o in 0..doc.text.length) { val t = o2t(o); if (t <= prev || t > tl) mapOk = false; prev = t; if (t2o(t) != o) { mapOk = false; println("map mismatch o=$o t=$t back=${t2o(t)}") } }
    for (t in 0..tl) { val o = t2o(t); if (o !in 0..doc.text.length) mapOk = false }
    println(if (mapOk && bad == 0) "FUZZ+MAP PASS" else "FUZZ/MAP FAIL bad=$bad map=$mapOk")
}
