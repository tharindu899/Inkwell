import com.inkwell.notes.editor.*

var fails = 0
fun check(name: String, ok: Boolean, extra: String = "") { if (!ok) { fails++; println("FAIL $name $extra") } else println("ok   $name") }

fun main() {
    val png = "data:image/png;base64,iVBORw0KGgo="
    val html = "<p>Hi</p><figure class=\"editor-figure photo-figure\" contenteditable=\"false\" data-media=\"image\" style=\"width:220px;margin-left:12px;margin-top:5px\"><img src=\"$png\" alt=\"a &quot;b&quot;.jpg\" class=\"editor-inline-image\" loading=\"lazy\"></figure><p>tail</p>"
    val d = HtmlCodec.parse(html)
    check("figure parses", d != null)
    d!!
    check("3 paras", d.paraCount == 3, "${d.paraCount}")
    val im = d.paras[1].image
    check("image para", d.paras[1].type == BlockType.IMAGE && im != null)
    check("fields", im!!.src == png && im.width == 220 && im.x == 12 && im.y == 5 && !im.drawing && im.alt == "a \"b\".jpg", "$im")
    val back = HtmlCodec.toHtml(d)
    check("html round trip", back == html, back)
    check("reparse equal", HtmlCodec.parse(back)!!.paras[1].image == im)

    val dr = HtmlCodec.parse("<figure class=\"editor-figure drawing-figure\" contenteditable=\"false\" data-media=\"drawing\"><img src=\"$png\" alt=\"Drawing\"></figure>")!!
    check("drawing default width", dr.paras[0].image!!.drawing && dr.paras[0].image!!.width == 190)
    check("trailing paragraph kept", dr.paras.last().type == BlockType.P && dr.paraCount == 2)

    val loose = HtmlCodec.parse("<p>x</p><img src=\"https://e.com/a.png\" alt=\"pic\">")!!
    check("loose img", loose.paras[1].image?.src == "https://e.com/a.png" && loose.paras[1].image!!.width == 180)
    check("relative src unsupported", HtmlCodec.parse("<p><img src=\"./a.png\"></p>") == null)
    check("img in list unsupported", HtmlCodec.parse("<ul><li>a<img src=\"$png\"></li></ul>") == null)
    check("lenient keeps http img", HtmlCodec.parseLenient("<div><img src=\"https://e.com/a.png\"></div>")?.paras?.any { it.type == BlockType.IMAGE } == true)

    check("markdown", HtmlCodec.toMarkdown(d).contains("![a \"b\".jpg]($png)"))
    val md = MarkdownCodec.parse("intro\n\n![logo](https://e.com/l.png)\n\n[![b](https://e.com/b.png)](https://x.com)\n\n![x](./rel.png)")
    check("md image lines", md.paras.count { it.type == BlockType.IMAGE } == 2, "${md.paras.map { it.type }}")
    check("md relative stays text", md.text.contains("x"))

    // editing
    val doc = RichDoc.plain("ab")
    val (nd, caret) = doc.insertObject(2, Para(type = BlockType.IMAGE, image = ImageData(png)))
    check("insert image below", nd.paras[1].type == BlockType.IMAGE && nd.paras.last().type == BlockType.P, "${nd.paras.map { it.type }}")
    check("caret in tail", nd.paraAt(caret).type == BlockType.P)
    val resized = nd.setImage(1, nd.paras[1].image!!.withWidth(5000))
    check("width clamped", resized.paras[1].image!!.width == 900)
    check("width min", nd.paras[1].image!!.withWidth(1).width == 90)
    check("offset clamp", nd.paras[1].image!!.withOffset(-4, 999).let { it.x == 0 && it.y == 80 })
    val del = nd.removeParaAt(1)
    check("delete image", del.paras.none { it.type == BlockType.IMAGE })
    check("counts ignore object", nd.countText.trim() == "ab" && nd.charCount == nd.text.length - 1)
    val l = RichLayout(nd) { 6 }
    check("layout lines", l.prefixes[1] == "\n".repeat(5))
    val ph = HtmlCodec.parse("<p>Image note</p><div class=\"image-placeholder\"><i class=\"fa-solid fa-image\"></i><span>Add image here</span></div>")
    check("web placeholder dropped", ph != null && ph.text == "Image note", "${ph?.text}")
    println(if (fails == 0) "ALL OK" else "$fails FAILED")
}
