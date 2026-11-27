import com.inkwell.notes.editor.*

var fails = 0
fun check(name: String, ok: Boolean, extra: String = "") { if (!ok) { fails++; println("FAIL $name $extra") } else println("ok   $name") }

fun main() {
    val d = HtmlCodec.parse("<h1>Title</h1><p>some <b>bold</b> text</p><ul><li>one</li><li>two</li></ul>")!!
    val t = d.text
    // a word inside the heading: no "# "
    val a = t.indexOf("itl")
    check("word in heading", HtmlCodec.toMarkdown(d.slice(a, a + 3)).trim() == "itl", HtmlCodec.toMarkdown(d.slice(a, a + 3)))
    // bold part
    val b = t.indexOf("some")
    val md = HtmlCodec.toMarkdown(d.slice(b, b + 9)).trim()
    check("inline bold kept", md == "some **bold**", md)
    // across heading + paragraph
    val all = HtmlCodec.toMarkdown(d.slice(0, t.length)).trim()
    check("all", all.startsWith("# Title") && all.contains("**bold**") && all.contains("- one") && all.contains("- two"), all)
    // list items across
    val o = t.indexOf("one")
    val two = HtmlCodec.toMarkdown(d.slice(o, t.length)).trim()
    check("list items", two.contains("- one") && two.contains("- two"), two)
    check("empty", d.slice(3, 3).text.isEmpty())
    println(if (fails == 0) "ALL OK" else "$fails FAILED")
}
