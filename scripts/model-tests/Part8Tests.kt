import com.inkwell.notes.editor.*

var fails = 0
fun check(name: String, ok: Boolean, extra: String = "") { if (!ok) { fails++; println("FAIL $name $extra") } else println("ok   $name") }

fun words(s: String) = s.trim().let { if (it.isEmpty()) 0 else it.split(Regex("\\s+")).size }

fun main() {
    // toolbar tools
    val keys = ToolbarTools.all.map { it.key }
    check("unique keys", keys.size == keys.toSet().size)
    check("tool count", keys.size == 32, keys.size.toString())
    check("visible none hidden", ToolbarTools.visible(emptySet()).size == 8)
    val v = ToolbarTools.visible(setOf("undo", "redo"))
    check("empty group dropped", v.size == 7 && v.none { g -> g.any { it.key == "undo" } })
    check("partial group kept", ToolbarTools.visible(setOf("bold")).any { g -> g.any { it.key == "italic" } && g.none { it.key == "bold" } })
    check("hide all", ToolbarTools.visible(keys.toSet()).isEmpty())
    val enc = ToolbarTools.encode(setOf("table", "bold"))
    check("encode", enc == "[\"bold\",\"table\"]", enc)
    check("decode", ToolbarTools.decode(enc) == setOf("bold", "table"))
    check("decode web json", ToolbarTools.decode("[\"hr\",\"nope\"]") == setOf("hr"))
    check("decode junk", ToolbarTools.decode("{{") .isEmpty() && ToolbarTools.decode(null).isEmpty() && ToolbarTools.decode("").isEmpty())
    check("roundtrip empty", ToolbarTools.decode(ToolbarTools.encode(emptySet())).isEmpty() && ToolbarTools.encode(emptySet()) == "[]")

    // streak
    fun key(i: Int) = "d" + i
    check("streak none", EditorStats.streak(emptySet(), ::key) == 0)
    check("streak today only", EditorStats.streak(setOf("d0"), ::key) == 1)
    check("streak 3", EditorStats.streak(setOf("d0", "d1", "d2", "d4"), ::key) == 3)
    check("streak today empty continues", EditorStats.streak(setOf("d1", "d2"), ::key) == 2)
    check("streak gap", EditorStats.streak(setOf("d0", "d2"), ::key) == 1)
    check("streak cap", EditorStats.streak((0 until 400).map { key(it) }.toSet(), ::key) == 365)

    // selected words
    check("caret", EditorStats.selectedWords("hello world", 3, 3, ::words) == 0)
    check("sel", EditorStats.selectedWords("hello big world", 0, 9, ::words) == 2)
    check("reversed", EditorStats.selectedWords("hello big world", 9, 0, ::words) == 2)
    check("out of range", EditorStats.selectedWords("hi", 0, 99, ::words) == 1)
    check("obj ignored", EditorStats.selectedWords("a${OBJ}b", 0, 3, ::words) == 2)
    println(if (fails == 0) "ALL OK" else "$fails FAILED")
}
