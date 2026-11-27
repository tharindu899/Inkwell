import com.inkwell.notes.editor.*

var fails = 0
fun check(name: String, cond: Boolean, extra: String = "") { if (!cond) { fails++; println("FAIL $name $extra") } else println("ok   $name") }

fun typeAt(d: RichDoc, pos: Int, s: String): EditResult =
    d.edit(d.text.substring(0, pos) + s + d.text.substring(pos), null)

fun main() {
    // roundtrip web checklist
    val web = """<ul class="check-list" data-type="taskList"><li data-checked="false"><label data-check-label="true"><span class="check-box" contenteditable="false" role="checkbox" aria-checked="false" data-checked="false" tabindex="-1"></span><span class="check-text">Buy milk</span></label></li><li data-checked="true"><label><span class="check-box" role="checkbox" tabindex="-1" contenteditable="false" data-checked="true" aria-checked="true"></span> <span class="check-text">Done <b>it</b></span></label></li></ul>"""
    val d = HtmlCodec.parse(web)!!
    check("web checklist parse text", d.text == "Buy milk\nDone it", d.text)
    check("web checklist types", d.paras.all { it.type == BlockType.TASK } && !d.paras[0].checked && d.paras[1].checked)
    val h = HtmlCodec.toHtml(d)
    val d2 = HtmlCodec.parse(h)!!
    check("checklist roundtrip", d2.text == d.text && d2.paras == d.paras, h)
    check("checklist markdown", HtmlCodec.toMarkdown(d) == "- [ ] Buy milk\n- [x] Done **it**", HtmlCodec.toMarkdown(d))

    // nested lists
    val nested = "<ul><li>A<ul><li>A1</li><li>A2<ol><li>x</li><li>y</li></ol></li></ul></li><li data-collapsed=\"true\">B<ul><li>B1</li></ul></li></ul>"
    val n = HtmlCodec.parse(nested)!!
    check("nested text", n.text == "A\nA1\nA2\nx\ny\nB\nB1", n.text)
    check("nested indents", n.paras.map { it.indent } == listOf(0,1,1,2,2,0,1), n.paras.map { it.indent }.toString())
    check("nested types", n.paras[3].type == BlockType.OL)
    check("collapsed", n.paras[5].collapsed && n.hidden[6] && !n.hidden[1])
    val nh = HtmlCodec.toHtml(n)
    check("nested html", nh == nested.replace("<li data-collapsed=\"true\">B","<li data-collapsed=\"true\">B"), nh)
    // invalid nested ul directly in ul (Chrome)
    val bad = HtmlCodec.parse("<ul><li>a</li><ul><li>b</li></ul></ul>")!!
    check("ul-in-ul", bad.paras.map { it.indent } == listOf(0,1), bad.paras.map{it.indent}.toString())
    check("md nested", HtmlCodec.toMarkdown(n).startsWith("- A\n  - A1\n  - A2\n    1. x\n    2. y\n- B"), HtmlCodec.toMarkdown(n))

    // code block
    val code = HtmlCodec.parse("<p>hi</p><pre><div class=\"code-scroll\"><code contenteditable=\"true\" aria-readonly=\"false\">a &lt; b\n\n  c\n</code></div></pre><p>after</p>")!!
    check("code parse", code.text == "hi\na < b\n\n  c\nafter", code.text.replace("\n","|"))
    check("code types", code.paras.map { it.type } == listOf(BlockType.P, BlockType.CODE, BlockType.CODE, BlockType.CODE, BlockType.P), code.paras.map{it.type}.toString())
    check("code html", HtmlCodec.toHtml(code) == "<p>hi</p><pre><code>a &lt; b\n\n  c</code></pre><p>after</p>", HtmlCodec.toHtml(code))
    val emptyCode = HtmlCodec.parse("<pre><code></code></pre>")!!
    check("empty code", emptyCode.paras.size == 1 && emptyCode.paras[0].type == BlockType.CODE)
    check("inline code supported (Part 5)", HtmlCodec.parse("<p><code>x</code></p>")?.marks?.get(0) == Mark.CODE)
    check("unknown class rejected", HtmlCodec.parse("<p class=\"foo\">x</p>") == null)

    // shortcuts
    var doc = RichDoc.EMPTY
    var r = typeAt(doc, 0, "-"); doc = r.doc
    r = typeAt(doc, 1, " ")
    check("dash shortcut", r.doc.paras[0].type == BlockType.UL && r.doc.text == "" && r.caret == 0)
    for ((m, t) in listOf("#" to BlockType.H1, "##" to BlockType.H2, "###" to BlockType.H3, ">" to BlockType.QUOTE, "1." to BlockType.OL, "[]" to BlockType.TASK, "```" to BlockType.CODE)) {
        val x = RichDoc.plain(m)
        val rr = x.edit("$m ", null)
        check("shortcut $m", rr.doc.paras[0].type == t && rr.doc.text == "", rr.doc.paras[0].type.toString())
    }
    check("shortcut off", RichDoc.plain("-").edit("- ", null, shortcuts = false).doc.text == "- ")
    check("no shortcut mid-line", RichDoc.plain("a -").edit("a - ", null).doc.text == "a - ")
    check("shortcut keeps later lines", RichDoc.plain("-\nfoo").edit("- \nfoo", null).doc.text == "\nfoo")
    // enter /check
    val c = RichDoc.plain("/check").edit("/check\n", null)
    check("/check enter", c.doc.paras[0].type == BlockType.TASK && c.doc.text == "" && c.doc.paraCount == 1)
    val cb = RichDoc.plain("```").edit("```\n", null)
    check("``` enter", cb.doc.paras[0].type == BlockType.CODE && cb.doc.text == "")

    // enter continuation in task
    var t = RichDoc.create("task", IntArray(4), listOf(Para(BlockType.TASK, checked = true)))
    var e = t.edit("task\n", null)
    check("enter task", e.doc.paraCount == 2 && e.doc.paras[1].type == BlockType.TASK && !e.doc.paras[1].checked && e.doc.paras[0].checked)
    // enter on empty nested → outdent → exit
    var ne = RichDoc.create("a\n", IntArray(2), listOf(Para(BlockType.UL), Para(BlockType.UL, indent = 1)))
    var e1 = ne.edit("a\n\n", null)
    check("empty nested enter outdents", e1.doc.paras[1].indent == 0 && e1.doc.paras[1].type == BlockType.UL && e1.caret != null, e1.doc.paras.toString())
    var e2 = e1.doc.edit(e1.doc.text.substring(0, 2) + "\n" + e1.doc.text.substring(2), null)
    check("empty top enter exits", e2.doc.paras[1].type == BlockType.P)

    // backspace at start of nested item outdents, then paragraph
    var bs = RichDoc.create("a\nb", IntArray(3), listOf(Para(BlockType.UL), Para(BlockType.UL, indent = 1)))
    var b1 = bs.edit("ab", null)
    check("backspace nested outdent", b1.doc.paras[1].indent == 0 && b1.doc.text == "a\nb")
    var b2 = b1.doc.edit("ab", null)
    check("backspace top → P", b2.doc.paras[1].type == BlockType.P)
    // code lines merge with backspace
    val cm = RichDoc.create("x\ny", IntArray(3), listOf(Para(BlockType.CODE), Para(BlockType.CODE)))
    check("code backspace merges", cm.edit("xy", null).doc.text == "xy")
    // backspace into hidden → expands
    val col = HtmlCodec.parse("<ul><li data-collapsed=\"true\">A<ul><li>A1</li></ul></li></ul><p>after</p>")!!
    check("hidden before", col.hidden[1])
    val bh = col.edit(col.text.replace("A1\nafter", "A1after"), null)
    check("backspace after collapsed expands", !bh.doc.paras[0].collapsed && bh.doc.text == col.text && bh.caret == col.starts[2], bh.caret.toString())
    // enter at end of collapsed expands
    val ce = col.edit("A\n" + col.text.substring(1), null, true, caret = 2)
    check("enter on collapsed expands", !ce.doc.paras[0].collapsed)

    // indent
    val ind = RichDoc.create("a\nb\nc", IntArray(5), listOf(Para(BlockType.UL), Para(BlockType.UL), Para(BlockType.UL, indent = 1)))
    val i1 = ind.changeIndent(1, 2, 2) // 'b'
    check("indent b with child", i1.paras.map { it.indent } == listOf(0, 1, 2), i1.paras.map{it.indent}.toString())
    check("indent first item noop", ind.changeIndent(1, 0, 0) === ind)
    val o1 = i1.changeIndent(-1, 2, 2)
    check("outdent b with child", o1.paras.map { it.indent } == listOf(0, 0, 1))
    // setBlock
    val sb = RichDoc.plain("x").setBlock(BlockType.TASK, 0, 0)
    check("setBlock task", sb.paras[0].type == BlockType.TASK)
    check("toggleChecked", sb.toggleChecked(0).paras[0].checked)
    check("ol numbering", n.olNumbers[3] == 1 && n.olNumbers[4] == 2)
    // caret-anchored edits
    val mid = RichDoc.create("a\nb", IntArray(3), listOf(Para(BlockType.UL), Para(BlockType.P)))
    val en = mid.edit("a\n\nb", null, true, caret = 2)
    check("enter end of bullet before P", en.doc.paras.map { it.type } == listOf(BlockType.UL, BlockType.UL, BlockType.P), en.doc.paras.map{it.type}.toString())
    val mid2 = RichDoc.create("a\n\nb", IntArray(4), listOf(Para(BlockType.P), Para(BlockType.UL), Para(BlockType.P)))
    val bk = mid2.edit("a\nb", null, true, caret = 1)
    check("backspace on empty bullet", bk.doc.paras[1].type == BlockType.P && bk.doc.text == "a\n\nb" && bk.caret == 2, bk.caret.toString() + bk.doc.paras)
    println(if (fails == 0) "ALL OK" else "$fails FAILED")
}
