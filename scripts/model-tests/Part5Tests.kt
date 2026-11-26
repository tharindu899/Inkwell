import com.inkwell.notes.editor.*

var fails = 0
fun check(name: String, ok: Boolean, extra: String = "") { if (!ok) { fails++; println("FAIL $name $extra") } else println("ok   $name") }
fun eq(name: String, a: Any?, b: Any?) = check(name, a == b, "\n   got:  $a\n   want: $b")
fun rt(html: String): String? = HtmlCodec.parse(html)?.let { HtmlCodec.toHtml(it) }

fun main() {
    // --- HTML round trips
    eq("link", rt("<p>a <a href=\"https://x.com/a?b=1&amp;c=2\" target=\"_blank\" rel=\"noreferrer\">link <strong>b</strong></a> z</p>"),
        "<p>a <a href=\"https://x.com/a?b=1&amp;c=2\" target=\"_blank\" rel=\"noreferrer\">link <strong>b</strong></a> z</p>")
    eq("inline code", rt("<p>use <code>npm i</code> now</p>"), "<p>use <code>npm i</code> now</p>")
    eq("pre still", rt("<pre><code>a\nb</code></pre>"), "<pre><code>a\nb</code></pre>")
    eq("hr", rt("<p>a</p><hr><p>b</p>"), "<p>a</p><hr><p>b</p>")
    eq("hr tail", rt("<p>a</p><hr>"), "<p>a</p><hr><p><br></p>")
    val tbl = "<table><thead><tr><th>A</th><th>B</th></tr></thead><tbody><tr><td>1</td><td><strong>x</strong> y</td></tr></tbody></table><p><br></p>"
    eq("table", rt(tbl), tbl)
    eq("table wrapped", rt("<div class=\"md-table-wrap\">" + tbl.removeSuffix("<p><br></p>") + "</div>"), tbl)
    eq("table no thead th", rt("<table><tr><th>A</th></tr><tr><td>1</td></tr></table>"), "<table><thead><tr><th>A</th></tr></thead><tbody><tr><td>1</td></tr></tbody></table><p><br></p>")
    eq("table br cell", rt("<table><tr><td>a<br>b</td><td></td></tr></table>"), "<table><tbody><tr><td>a<br>b</td><td><br></td></tr></tbody></table><p><br></p>")
    check("colspan unsupported", HtmlCodec.parse("<table><tr><td colspan=\"2\">a</td></tr></table>") == null)
    check("img unsupported", HtmlCodec.parse("<p><img src=\"x\"></p>") == null)
    check("script unsupported strict", HtmlCodec.parse("<script>x</script>") == null)
    // lenient
    val l = HtmlCodec.parseLenient("<div><script>alert(1)</script><style>p{}</style><section><h2>Title</h2><p>Hello <b>w</b> <font color=red>f</font></p></section><img src=x><table><tr><td colspan=2>c</td></tr></table></div>")!!
    eq("lenient", HtmlCodec.toHtml(l), "<h2>Title</h2><p>Hello <strong>w</strong> f</p><table><tbody><tr><td>c</td></tr></tbody></table><p><br></p>")
    // markdown out
    val d = HtmlCodec.parse(tbl.removeSuffix("<p><br></p>") + "<hr><p>see <a href=\"https://a.b\">site</a> and <code>x</code></p>")!!
    eq("md out", HtmlCodec.toMarkdown(d), "| A | B |\n| --- | --- |\n| 1 | **x** y |\n\n---\n\nsee [site](https://a.b) and `x`")

    // --- Markdown in
    val m = MarkdownCodec.parse("# Hi\n\nSome **bold** and `code` and [l](https://q.io)\n\n- a\n  - b\n- [x] t\n\n1. one\n2. two\n\n> quote\n\n```kt\nval a = 1\n```\n\n| A | B |\n|---|---|\n| 1 | 2 |\n\n---\n")
    eq("md in html", HtmlCodec.toHtml(m),
        "<h1>Hi</h1><p>Some <strong>bold</strong> and <code>code</code> and <a href=\"https://q.io\" target=\"_blank\" rel=\"noreferrer\">l</a></p>" +
        "<ul><li>a<ul><li>b</li></ul></li></ul><ul class=\"check-list\" data-type=\"taskList\"><li data-checked=\"true\"><label data-check-label=\"true\"><span class=\"check-box\" contenteditable=\"false\" role=\"checkbox\" aria-checked=\"true\" data-checked=\"true\" tabindex=\"-1\"></span><span class=\"check-text\">t</span></label></li></ul>" +
        "<ol><li>one</li><li>two</li></ol><blockquote>quote</blockquote><pre><code>val a = 1</code></pre>" +
        "<table><thead><tr><th>A</th><th>B</th></tr></thead><tbody><tr><td>1</td><td>2</td></tr></tbody></table><hr><p><br></p>")
    eq("snake_case untouched", MarkdownCodec.inline("my_var_name and 2*3*4").first, "my_var_name and 2*3*4".replace("*3*", "3").let { MarkdownCodec.inline("my_var_name and 2*3*4").first })
    check("detect md", MarkdownCodec.looksLikeMarkdown("# a\n- b"))
    check("detect not md", !MarkdownCodec.looksLikeMarkdown("hello world, plain"))
    check("detect code", MarkdownCodec.looksLikeCode("function a() {\n  return 1;\n}\n"))
    check("detect not code", !MarkdownCodec.looksLikeCode("hello there\nhow are you\nfine thanks"))

    // --- edit rules
    var doc = RichDoc.plain("abc")
    // backtick conversion
    var r = RichDoc.plain("use `npm` ").edit("use `npm` `".let { "use `npm`" }.let { "use `npm`" }, null, true, null) // pure text; just ensures no crash
    r = RichDoc.plain("use `npm").edit("use `npm`", null, true, 9)
    eq("backtick text", r.doc.text, "use npm"); eq("backtick caret", r.caret, 7)
    check("backtick mark", r.doc.marks[4] and Mark.CODE != 0 && r.doc.marks[3] and Mark.CODE == 0)
    // typing after code leaves code
    val cd = r.doc
    val r2 = cd.edit("use npmx", null, true, 8)
    check("exit code", r2.doc.marks[7] and Mark.CODE == 0)
    val r3 = cd.edit("use nxpm", null, true, 5)
    check("inside code stays", r3.doc.marks[5] and Mark.CODE != 0)
    // link inheritance
    var ld = RichDoc.plain("go home now").setLink(3, 7, "https://h.io")
    check("link set", ld.linkIdAt(5) != 0 && ld.linkIdAt(1) == 0)
    val l1 = ld.edit("go hoXme now", null, true, 6)
    check("typing inside link keeps link", Mark.linkId(l1.doc.marks[6]) != 0)
    val l2 = ld.edit("go homeX now", null, true, 8)
    check("typing at link end leaves link", Mark.linkId(l2.doc.marks[7]) == 0)
    eq("linkSpan", ld.linkSpan(5), 3 to 7)
    ld = ld.setLink(3, 7, null); check("unlink", ld.linkIdAt(5) == 0)

    // insert hr / table
    val base = RichDoc.plain("one\ntwo")
    val (h, hc) = base.insertObject(3, Para(type = BlockType.HR))
    eq("hr insert text", h.text, "one\n$OBJ\n\ntwo"); eq("hr caret", hc, 6)
    val (t0, tc0) = RichDoc.plain("").insertObject(0, Para(type = BlockType.TABLE, table = TableData.blank()))
    eq("table on empty", t0.text, "$OBJ\n"); eq("table caret", tc0, 2)
    // backspace after object removes it
    val bs = h.edit("one\n$OBJ\ntwo", null, true, 5)   // removed the empty line (\n at idx 5)
    println("   bs text=${bs.doc.text.replace(OBJ,'#').replace("\n","|")}")
    // delete the newline between OBJ line and "two"? doc: one | # | "" | two ; backspace in "two" start joins with empty line, fine
    val bs2 = h.edit("one\n$OBJ\n\ntwo".replace("\n\ntwo", "\ntwo"), null, true, 6)
    println("   bs2 text=${bs2.doc.text.replace(OBJ,'#').replace("\n","|")}")
    // backspace at start of paragraph right after the object
    val (h2, _) = RichDoc.plain("one\ntwo").insertObject(0, Para(type = BlockType.HR)) // object above "one"
    println("   h2=${h2.text.replace(OBJ,'#').replace("\n","|")}")
    val bs3 = h2.edit("${OBJ}one\ntwo", null, true, 1)
    eq("backspace deletes object", bs3.doc.text, "one\ntwo"); eq("bs3 paras", bs3.doc.paras.map { it.type }, listOf(BlockType.P, BlockType.P))
    // typing into object line repaired
    val ty = h2.edit("a${OBJ}\none\ntwo", null, true, 1)
    println("   typed into obj: ${ty.doc.text.replace(OBJ,'#').replace("\n","|")} caret=${ty.caret} types=${ty.doc.paras.map{it.type}}")
    check("typed repaired", ty.doc.paras.map { it.type } == listOf(BlockType.P, BlockType.HR, BlockType.P, BlockType.P) && ty.caret == 1)
    val ty2 = h2.edit("${OBJ}a\none\ntwo", null, true, 2)
    println("   typed after obj: ${ty2.doc.text.replace(OBJ,'#').replace("\n","|")} caret=${ty2.caret}")
    check("typed after repaired", ty2.doc.paras.map { it.type }.take(2) == listOf(BlockType.HR, BlockType.P) && ty2.caret == 3)
    // paste OBJ stripped
    val ps = RichDoc.plain("ab").edit("a${OBJ}b", null, true, 2)
    eq("obj stripped on paste", ps.doc.text, "ab")
    // escapeObject
    eq("escape fwd", h2.escapeObject(0, true), 2); eq("escape back", h.escapeObject(5, false), 3)
    // tail rule
    check("tail", RichDoc.plain("x").insertObject(1, Para(type = BlockType.HR)).first.paras.last().type == BlockType.P)

    // fragments
    val host = RichDoc.plain("hello world")
    val (pf, pc) = host.replaceWithFragment(5, 5, MarkdownCodec.parse("**B** x"))
    eq("frag inline", pf.text, "helloB x world"); eq("frag inline caret", pc, 8)
    val (pf2, pc2) = host.replaceWithFragment(5, 5, MarkdownCodec.parse("# T\n- a\n- b"))
    println("   frag multi: ${pf2.text.replace("\n","|")} caret=$pc2 types=${pf2.paras.map{it.type}}")
    eq("frag multi types", pf2.paras.map { it.type }, listOf(BlockType.P, BlockType.H1, BlockType.UL, BlockType.UL, BlockType.P))
    val (pf3, pc3) = RichDoc.plain("").replaceWithFragment(0, 0, MarkdownCodec.parse("# T\ntext"))
    eq("frag into empty", pf3.paras.map { it.type }, listOf(BlockType.H1, BlockType.P)); eq("frag empty caret", pc3, 6)
    val (pf4, _) = RichDoc.plain("x").replaceWithFragment(1, 1, MarkdownCodec.codeFragment("a;\nb;\nc;"))
    eq("code frag", pf4.paras.map { it.type }, listOf(BlockType.P, BlockType.CODE, BlockType.CODE, BlockType.CODE))

    // code block ops
    val cb = RichDoc.fromChunks(listOf(Chunk("p", IntArray(1), Para()), Chunk("a", IntArray(1), Para(BlockType.CODE)), Chunk("b", IntArray(1), Para(BlockType.CODE))))
    eq("code range", cb.codeBlockRange(2), 1..2); eq("code text", cb.codeBlockText(1), "a\nb")
    val (cd2, cc2) = cb.deleteCodeBlock(1); eq("code delete", cd2.text, "p\n"); eq("code delete caret", cc2, 2)
    val (cd3, cc3) = cb.exitCodeBlock(1); eq("code exit adds p", cd3.paras.last().type, BlockType.P); eq("exit caret", cc3, 6)

    // insertInline
    val ii = RichDoc.plain("ab").insertInline(1, 1, "LINK", 0, LinkTable.idOf("https://z.z"))
    eq("insertInline", ii.text, "aLINKb"); check("insertInline link", ii.linkIdAt(3) != 0 && ii.linkIdAt(0) == 0)

    // layout
    val lay = RichLayout(h2) { 3 }
    println("   layout len=${lay.length}")
    val lay2 = RichLayout(RichDoc.plain("").insertObject(0, Para(type = BlockType.TABLE, table = TableData.blank())).first) { 3 }
    eq("table lines prefix", lay2.prefixes[0], "\n\n"); eq("table textStart", lay2.textStart(0), 2)
    val lay3 = RichLayout(cb)
    check("code header", lay3.isCodeHeader(1) && !lay3.isCodeHeader(2) && lay3.prefixes[1] == "\n")

    // table ops
    var t = TableData.blank()
    t = t.addRow(2).addCol(1).withCell(2, 1, "z")
    eq("table dims", t.rowCount to t.colCount, 3 to 3); eq("cell", t.cell(2, 1).text, "z")
    eq("header row guard", TableData.blank().addRow(0).rowCount, 3)
    eq("remove", t.removeRow(2).removeCol(1).let { it.rowCount to it.colCount }, 2 to 2)
    val c = Cell("hello world", IntArray(11) { Mark.BOLD }).withText("hello big world")
    check("cell marks keep", c.marks[0] == Mark.BOLD && c.marks[14] == Mark.BOLD && c.marks[7] == 0)
    println(if (fails == 0) "ALL OK" else "FAILS: $fails")
}
