import com.inkwell.notes.editor.*

var fails = 0
fun check(name: String, ok: Boolean, extra: String = "") { if (!ok) { fails++; println("FAIL: $name $extra") } else println("ok   $name") }
fun eq(name: String, a: Any?, b: Any?) = check(name, a == b, "\n   got:      $a\n   expected: $b")

fun rt(html: String): String? = HtmlCodec.parse(html)?.let { HtmlCodec.toHtml(it) }

fun main() {
    // ── HTML round trips
    eq("plain p", rt("<p>a</p><p><br></p><p>b</p>"), "<p>a</p><p><br></p><p>b</p>")
    eq("empty", rt(""), "")
    eq("heading+bold", rt("<h1>Title</h1><p>x <strong>bold</strong> y</p>"), "<h1>Title</h1><p>x <strong>bold</strong> y</p>")
    eq("b/i tags normalised", rt("<p><b>a</b><i>b</i></p>"), "<p><strong>a</strong><em>b</em></p>")
    eq("nested fmt", rt("<p><strong><em>ab</em></strong></p>"), "<p><strong><em>ab</em></strong></p>")
    eq("ul", rt("<ul><li>a</li><li>b</li></ul>"), "<ul><li>a</li><li>b</li></ul>")
    eq("ol", rt("<ol><li>a</li><li>b</li></ol>"), "<ol><li>a</li><li>b</li></ol>")
    eq("nested ul", rt("<ul><li>a<ul><li>b</li></ul></li><li>c</li></ul>"), "<ul><li>a<ul><li>b</li></ul></li><li>c</li></ul>")
    eq("quote", rt("<blockquote>q</blockquote>"), "<blockquote>q</blockquote>")
    eq("align", rt("<p style=\"text-align: center;\">c</p>"), "<p style=\"text-align: center;\">c</p>")
    eq("center tag", rt("<center>c</center>"), "<p style=\"text-align: center;\">c</p>")
    eq("indent chrome", rt("<blockquote style=\"margin: 0 0 0 40px; border: none; padding: 0px;\"><p>x</p></blockquote>"),
        "<blockquote style=\"margin: 0 0 0 40px; border: none; padding: 0px;\"><p>x</p></blockquote>")
    eq("div lines", rt("<div>a</div><div>b</div>"), "<p>a</p><p>b</p>")
    eq("bare text + br", rt("a<br>b"), "<p>a</p><p>b</p>")
    eq("entities", rt("<p>a &amp; b &lt;c&gt; &nbsp;d</p>"), "<p>a &amp; b &lt;c&gt;  d</p>")
    eq("mark", rt("<p><mark>hi</mark></p>"), "<p><mark>hi</mark></p>")
    eq("span bg", rt("<p><span style=\"background-color: rgba(201, 169, 110, 0.33);\">hi</span></p>"), "<p><mark>hi</mark></p>")
    eq("sup sub", rt("<p>x<sup>2</sup> H<sub>2</sub>O</p>"), "<p>x<sup>2</sup> H<sub>2</sub>O</p>")
    eq("trailing br", rt("<p>abc<br></p>"), "<p>abc</p>")
    // unsupported → null
    for ((n, h) in listOf("table" to "<table><tr><td>a</td></tr></table>", "img" to "<p><img src=x></p>", "pre" to "<pre>a</pre>",
        "link" to "<p><a href=\"http://x\">a</a></p>", "code" to "<p><code>a</code></p>", "hr" to "<hr>",
        "check" to "<ul class=\"check-list\"><li>a</li></ul>", "fig" to "<figure><img src=x></figure>", "h4" to "<h4>a</h4>"))
        check("unsupported $n", HtmlCodec.parse(h) == null)

    // ── markdown
    val d1 = HtmlCodec.parse("<h1>T</h1><p>a <strong>b</strong> <em>c</em></p><ul><li>x</li><li>y</li></ul><ol><li>1</li><li>2</li></ol><blockquote>q</blockquote>")!!
    eq("markdown", HtmlCodec.toMarkdown(d1), "# T\n\na **b** *c*\n\n- x\n- y\n\n1. 1\n2. 2\n\n> q")

    // ── editing: typing
    var d = Doc.plain("hello world")
    d = DocOps.toggleSpan(d, SpanKind.BOLD, 0, 5)
    eq("bold range", d.spans, listOf(Span(SpanKind.BOLD, 0, 5)))
    // type at end of bold -> inherits
    var d2 = DocOps.applyTextEdit(d, "hellox world", 6, emptyMap())
    eq("inherit at end", d2.spans, listOf(Span(SpanKind.BOLD, 0, 6)))
    // type at start of bold -> no inherit, span shifts
    d2 = DocOps.applyTextEdit(d, "xhello world", 1, emptyMap())
    eq("insert before", d2.spans, listOf(Span(SpanKind.BOLD, 1, 6)))
    // pending off at end of bold
    d2 = DocOps.applyTextEdit(d, "hellox world", 6, mapOf(SpanKind.BOLD to false))
    eq("pending off", d2.spans, listOf(Span(SpanKind.BOLD, 0, 5)))
    // pending on in plain text
    d2 = DocOps.applyTextEdit(Doc.plain("ab"), "abc", 3, mapOf(SpanKind.ITALIC to true))
    eq("pending on", d2.spans, listOf(Span(SpanKind.ITALIC, 2, 3)))
    // delete inside
    d2 = DocOps.applyTextEdit(d, "hllo world", 1, emptyMap())
    eq("delete inside", d2.spans, listOf(Span(SpanKind.BOLD, 0, 4)))
    // delete whole span
    d2 = DocOps.applyTextEdit(d, " world", 0, emptyMap())
    eq("delete whole", d2.spans, emptyList<Span>())
    // repeated chars disambiguation: "aaa" bold on last 'a' ; type 'a' at index 1
    val da = DocOps.toggleSpan(Doc.plain("aaa"), SpanKind.BOLD, 2, 3)
    d2 = DocOps.applyTextEdit(da, "aaaa", 2, emptyMap())
    eq("repeated chars", d2.spans, listOf(Span(SpanKind.BOLD, 3, 4)))
    // autocorrect replace first word, caret at end: tail spans survive
    val db = DocOps.toggleSpan(Doc.plain("foo bar baz"), SpanKind.BOLD, 8, 11)
    d2 = DocOps.applyTextEdit(db, "Foo bar baz", 11, emptyMap())
    eq("autocorrect keeps tail", d2.spans, listOf(Span(SpanKind.BOLD, 8, 11)))

    // ── enter / backspace
    var h = DocOps.setBlockType(Doc.plain("Title"), 0, 0, BlockType.H1)
    var h2 = DocOps.applyTextEdit(h, "Title\n", 6, emptyMap())
    eq("enter after heading -> P", h2.blocks, listOf(BlockFmt(BlockType.H1), BlockFmt(BlockType.P)))
    h2 = DocOps.applyTextEdit(h, "Ti\ntle", 3, emptyMap())
    eq("enter mid heading keeps H1", h2.blocks, listOf(BlockFmt(BlockType.H1), BlockFmt(BlockType.H1)))
    val l = DocOps.setBlockType(Doc.plain("a"), 0, 0, BlockType.UL)
    var l2 = DocOps.applyTextEdit(l, "a\n", 2, emptyMap())
    eq("enter in list -> new item", l2.blocks, listOf(BlockFmt(BlockType.UL), BlockFmt(BlockType.UL)))
    // merge lines with backspace
    val m = Doc.of("ab\ncd", listOf(BlockFmt(BlockType.H1), BlockFmt()), emptyList())
    val m2 = DocOps.applyTextEdit(m, "abcd", 2, emptyMap())
    eq("merge lines", m2.blocks, listOf(BlockFmt(BlockType.H1)))
    // multi-line paste
    val pst = DocOps.applyTextEdit(Doc.plain("x"), "x\ny\nz", 5, emptyMap())
    eq("paste lines", pst.blocks.size, 3)
    check("invariant", pst.blocks.size == pst.text.count { it == '\n' } + 1)

    // ── toggles
    var t = Doc.plain("a\nb\nc")
    t = DocOps.setBlockType(t, 0, 5, BlockType.UL)
    eq("list all lines", t.blocks.map { it.type }, listOf(BlockType.UL, BlockType.UL, BlockType.UL))
    t = DocOps.setBlockType(t, 0, 5, BlockType.UL)
    eq("list toggle off", t.blocks.map { it.type }, listOf(BlockType.P, BlockType.P, BlockType.P))
    t = DocOps.setBlockType(t, 2, 2, BlockType.OL)
    eq("single line", t.blocks.map { it.type }, listOf(BlockType.P, BlockType.OL, BlockType.P))
    eq("sel ends at line start excludes it", Doc.plain("a\nb").lineRange(0, 2), 0..0)
    t = DocOps.setAlign(Doc.plain("a"), 0, 0, Align.CENTER)
    eq("align", t.blocks[0].align, Align.CENTER)
    t = DocOps.setAlign(t, 0, 0, Align.CENTER)
    eq("align toggle", t.blocks[0].align, Align.LEFT)
    t = DocOps.indent(Doc.plain("a"), 0, 0, 1)
    eq("indent", t.blocks[0].indent, 1)
    t = DocOps.indent(t, 0, 0, -3)
    eq("outdent clamp", t.blocks[0].indent, 0)
    var cf = DocOps.toggleSpan(Doc.plain("abc"), SpanKind.BOLD, 0, 3)
    cf = DocOps.setBlockType(cf, 0, 0, BlockType.H2)
    cf = DocOps.clearFormat(cf, 1, 2)
    eq("clear format", cf.spans, listOf(Span(SpanKind.BOLD, 0, 1), Span(SpanKind.BOLD, 2, 3)))
    eq("clear format block", cf.blocks[0], BlockFmt())
    var sp = DocOps.toggleSpan(Doc.plain("abc"), SpanKind.SUB, 0, 3)
    sp = DocOps.toggleSpan(sp, SpanKind.SUP, 0, 3)
    eq("sup replaces sub", sp.spans, listOf(Span(SpanKind.SUP, 0, 3)))

    // ── full pipeline: html → edit → html
    val rich = HtmlCodec.parse("<h1>Hi</h1><p>one <strong>two</strong></p>")!!
    val ed = DocOps.applyTextEdit(rich, "Hi\none twoo", 11, emptyMap())
    eq("edit rich", HtmlCodec.toHtml(ed), "<h1>Hi</h1><p>one <strong>twoo</strong></p>")

    println(if (fails == 0) "\nALL PASS" else "\n$fails FAILED")
}
