import com.inkwell.notes.editor.*

var fails = 0
fun check(name: String, ok: Boolean, extra: String = "") { if (!ok) { fails++; println("FAIL $name $extra") } else println("ok   $name") }

fun main() {
    check("safe name", NoteExport.safeFileName("  a/b:c*d?  e  ") == "a-b-c-d- e", NoteExport.safeFileName("  a/b:c*d?  e  "))
    check("safe empty", NoteExport.safeFileName("   ") == "note")
    check("safe long", NoteExport.safeFileName("x".repeat(200)).length == 80)

    val doc = HtmlCodec.parse("<h1>Head</h1><p>para</p><ul><li>one</li><li>two</li></ul><ol><li>a</li><li>b</li></ol><hr><p>end</p>")!!
    val plain = NoteExport.plainText(doc)
    check("plain keeps text", plain.contains("Head") && plain.contains("para") && plain.contains("end"), plain)
    check("bullets", plain.contains("• one") && plain.contains("• two"), plain)
    check("numbers", plain.contains("1. a") && plain.contains("2. b"), plain)
    check("hr", plain.contains("----------"), plain)
    check("no OBJ", !plain.contains(OBJ), plain)

    val png = "data:image/png;base64,iVBORw0KGgo="
    val img = HtmlCodec.parse("<figure class=\"editor-figure\" data-media=\"image\"><img src=\"$png\" alt=\"cat\"></figure>")!!
    check("image placeholder", NoteExport.plainText(img).contains("[Image: cat]"), NoteExport.plainText(img))

    check("txt", NoteExport.text("Title", "body") == "Title\n─────\n\nbody\n")
    check("md", NoteExport.markdown("T", "x\n") == "# T\n\nx\n")
    val h = NoteExport.html("A <b>", "<p>x</p>")
    check("html", h.contains("<title>A &lt;b&gt;</title>") && h.contains("<p>x</p>") && h.startsWith("<!doctype html>"))
    val x = NoteExport.xls("T&", "2026", 3, "a\nb")
    check("xls", x.contains("<td>T&amp;</td>") && x.contains("a<br>b") && x.contains("<td>3</td>"))
    println(if (fails == 0) "ALL OK" else "$fails FAILED")
}
