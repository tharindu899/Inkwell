package com.inkwell.notes.editor

/** Token classes of the code-block highlighter (GitHub's "prettylights" groups, collapsed to what a generic scanner can tell). */
enum class Tok { COMMENT, KEYWORD, STRING, CONSTANT, FUNCTION, TYPE, TAG }

/** [start, end) of one coloured token; text between tokens keeps the default colour. */
class TokSpan(val start: Int, val end: Int, val kind: Tok)

/**
 * Language-agnostic scanner (no Compose types, unit-testable). It does not know which language a block is in; it colours
 * what looks the same in almost all of them: comments (`//` `/* */` `#` `<!-- -->` `-- `), strings, numbers / hex colours,
 * keywords, types (Capitalised names), function calls (`name(`), HTML tags, JSON / YAML keys, operators and annotations.
 */
object CodeTokenizer {

    private fun words(s: String): Set<String> = s.split(' ').filter { it.isNotEmpty() }.toHashSet()

    private val KEYWORDS = words(
        "abstract as async await break case catch class const continue def del delete do elif else enum except export extends " +
            "final finally for from fun function global goto if implements import in instanceof interface is lambda let lateinit " +
            "namespace new nonlocal not or and override package pass private protected public raise return sealed static struct " +
            "super switch throw throws trait try typealias typeof val var virtual when while with yield echo fi done esac " +
            "elsif unless begin end rescue module impl pub mut mod use crate fn loop match where readonly declare type " +
            "extern inline operator suspend companion data inner internal open out vararg volatile synchronized transient " +
            "native strictfp default using",
    )

    /** Upper-case SQL words (`SELECT`, `FROM` …) — matched only when written in capitals so prose isn't painted. */
    private val SQL = words(
        "select insert update delete create table drop alter join inner outer left right on group order by having limit values " +
            "set into distinct union from where and or not null as primary key foreign references index and view with " +
            "case when then else end like between exists in is asc desc",
    )

    private val CONSTANTS = words("true false null nil none None True False undefined NaN Infinity this self cls")

    private val TYPES = words(
        "int long short byte char float double boolean bool string str number any unknown never void object " +
            "i8 i16 i32 i64 u8 u16 u32 u64 usize isize f32 f64 u128 i128 uint int8 int16 int32 int64 float32 float64 " +
            "Int Long String Boolean Double Float Char Byte Short Unit Any Nothing Array List Map Set",
    )

    private val DIRECTIVES = words("include define ifdef ifndef if else elif endif pragma undef error import region endregion warning line using")

    private const val OPS = "=+*%!&|^~?<>"

    fun tokenize(src: String): List<TokSpan> {
        val out = ArrayList<TokSpan>()
        val n = src.length
        var i = 0
        var lineStart = 0
        var inTag = false

        fun add(a: Int, b: Int, k: Tok) { if (b > a) out.add(TokSpan(a, b, k)) }
        fun eol(from: Int): Int { var j = from; while (j < n && src[j] != '\n') j++; return j }
        fun isIdent(c: Char) = c.isLetterOrDigit() || c == '_' || c == '$'
        fun prevNonSpace(from: Int): Char? {
            var j = from - 1
            while (j >= lineStart && (src[j] == ' ' || src[j] == '\t')) j--
            return if (j >= lineStart) src[j] else null
        }
        fun blankBefore(pos: Int): Boolean {
            var j = lineStart
            while (j < pos) { if (src[j] != ' ' && src[j] != '\t') return false; j++ }
            return true
        }

        while (i < n) {
            val ch = src[i]
            val next = if (i + 1 < n) src[i + 1] else '\n'
            when {
                ch == '\n' -> { i++; lineStart = i; inTag = false }
                ch.isWhitespace() -> i++

                // ── comments ──
                ch == '/' && next == '/' && !(i > 0 && src[i - 1] == ':') -> { val e = eol(i); add(i, e, Tok.COMMENT); i = e }
                ch == '/' && next == '*' -> {
                    val k = src.indexOf("*/", i + 2)
                    val e = if (k < 0) n else k + 2
                    add(i, e, Tok.COMMENT)
                    // a block comment can span lines: keep lineStart in step
                    val nl = src.lastIndexOf('\n', e - 1)
                    if (nl >= i) lineStart = nl + 1
                    i = e
                }
                ch == '<' && src.startsWith("<!--", i) -> {
                    val k = src.indexOf("-->", i + 4)
                    val e = if (k < 0) n else k + 3
                    add(i, e, Tok.COMMENT)
                    val nl = src.lastIndexOf('\n', e - 1)
                    if (nl >= i) lineStart = nl + 1
                    i = e
                }
                ch == '-' && next == '-' && i + 2 < n && src[i + 2] == ' ' && blankBefore(i) -> { val e = eol(i); add(i, e, Tok.COMMENT); i = e }

                ch == '#' -> {
                    val wEnd = run { var j = i + 1; while (j < n && src[j].isLetter()) j++; j }
                    val word = src.substring(i + 1, wEnd)
                    val lineHasBrace = src.indexOf('{', i).let { it in 0 until eol(i) }
                    when {
                        next == ' ' || next == '\t' || next == '!' || next == '#' || next == '\n' || next == '\r' -> {
                            val e = eol(i); add(i, e, Tok.COMMENT); i = e
                        }
                        blankBefore(i) && word in DIRECTIVES -> { add(i, wEnd, Tok.KEYWORD); i = wEnd }
                        blankBefore(i) && word.isNotEmpty() && !lineHasBrace && src.getOrNull(i + 1)?.isLetter() == true -> {
                            val e = eol(i); add(i, e, Tok.COMMENT); i = e
                        }
                        next.isLetterOrDigit() -> {
                            var j = i + 1
                            while (j < n && (src[j].isDigit() || src[j] in 'a'..'f' || src[j] in 'A'..'F')) j++
                            val len = j - i - 1
                            if ((len == 3 || len == 4 || len == 6 || len == 8) && (j >= n || !isIdent(src[j]))) {
                                add(i, j, Tok.CONSTANT); i = j
                            } else i++
                        }
                        else -> i++
                    }
                }

                // ── strings ──
                ch == '"' || ch == '\'' || ch == '`' -> {
                    if (ch == '\'' && i > 0 && isIdent(src[i - 1])) { i++ } // apostrophe inside a word (don't)
                    else {
                        val triple = (ch == '"' || ch == '\'') && src.startsWith("$ch$ch$ch", i)
                        val e: Int
                        if (triple) {
                            val k = src.indexOf("$ch$ch$ch", i + 3)
                            e = if (k < 0) n else k + 3
                        } else {
                            var j = i + 1
                            while (j < n) {
                                val d = src[j]
                                if (d == '\\') { j += 2; continue }
                                if (d == ch) { j++; break }
                                if (d == '\n' && ch != '`') break
                                j++
                            }
                            e = minOf(j, n)
                        }
                        // JSON-style key: "name": …  (string right after `{` `,` or at the start of a line)
                        var q = e
                        while (q < n && (src[q] == ' ' || src[q] == '\t')) q++
                        val pn = prevNonSpace(i)
                        val isKey = ch == '"' && q < n && src[q] == ':' && (pn == null || pn == '{' || pn == ',')
                        add(i, e, if (isKey) Tok.CONSTANT else Tok.STRING)
                        val nl = src.lastIndexOf('\n', e - 1)
                        if (nl >= i) lineStart = nl + 1
                        i = e
                    }
                }

                // ── numbers ──
                ch.isDigit() -> {
                    var j = i
                    if (ch == '0' && (next == 'x' || next == 'X')) {
                        j += 2
                        while (j < n && (src[j].isLetterOrDigit() || src[j] == '_')) j++
                    } else {
                        while (j < n && (src[j].isDigit() || src[j] == '_')) j++
                        if (j + 1 < n && src[j] == '.' && src[j + 1].isDigit()) {
                            j++
                            while (j < n && (src[j].isDigit() || src[j] == '_')) j++
                        }
                        if (j + 1 < n && (src[j] == 'e' || src[j] == 'E') &&
                            (src[j + 1].isDigit() || ((src[j + 1] == '+' || src[j + 1] == '-') && j + 2 < n && src[j + 2].isDigit()))
                        ) {
                            j += 2
                            while (j < n && src[j].isDigit()) j++
                        }
                        while (j < n && src[j].isLetter()) j++
                    }
                    add(i, j, Tok.CONSTANT)
                    i = j
                }

                // ── annotations / decorators ──
                ch == '@' && next.isLetter() -> {
                    var j = i + 1
                    while (j < n && (isIdent(src[j]) || src[j] == '.')) j++
                    add(i, j, Tok.TYPE)
                    i = j
                }

                // ── words ──
                ch.isLetter() || ch == '_' || ch == '$' -> {
                    var j = i + 1
                    while (j < n && isIdent(src[j])) j++
                    val w = src.substring(i, j)
                    val upper = w.length > 1 && w.all { it.isUpperCase() || it.isDigit() || it == '_' }
                    var q = j
                    while (q < n && (src[q] == ' ' || src[q] == '\t')) q++
                    val kind: Tok? = when {
                        w in CONSTANTS -> Tok.CONSTANT
                        upper && w.lowercase() in SQL && w.length > 1 -> Tok.KEYWORD
                        w in KEYWORDS -> Tok.KEYWORD
                        w in TYPES -> Tok.TYPE
                        j < n && src[j] == '(' -> Tok.FUNCTION
                        // `key:` at the start of a line (YAML / properties / TOML-ish)
                        j < n && src[j] == ':' && (j + 1 >= n || src[j + 1] == ' ' || src[j + 1] == '\n') &&
                            (blankBefore(i) || (prevNonSpace(i) == '-' && src.substring(lineStart, i).trim() == "-")) -> Tok.TAG
                        upper -> Tok.CONSTANT
                        w[0].isUpperCase() && w.length > 1 -> Tok.TYPE
                        else -> null
                    }
                    if (kind != null) add(i, j, kind)
                    i = j
                }

                // ── HTML / XML tags ──
                ch == '<' && !inTag && (next.isLetter() || (next == '/' && i + 2 < n && src[i + 2].isLetter())) &&
                    (next == '/' || i == 0 || !(isIdent(src[i - 1]) || src[i - 1] == ')')) -> {
                    var j = i + 1
                    if (src[j] == '/') j++
                    val s = j
                    while (j < n && (src[j].isLetterOrDigit() || src[j] == '-' || src[j] == ':' || src[j] == '.')) j++
                    add(s, j, Tok.TAG)
                    inTag = true
                    i = j
                }
                inTag && ch == '>' -> { inTag = false; i++ }

                // ── operators ──
                ch in OPS -> {
                    val prev = if (i > 0) src[i - 1] else ' '
                    val angle = ch == '<' || ch == '>'
                    if (inTag || (angle && !(prev.isWhitespace() || prev in "=-<>!" ))) { i++ } else {
                        var j = i
                        while (j < n && src[j] in OPS) j++
                        add(i, j, Tok.KEYWORD)
                        i = j
                    }
                }

                else -> i++
            }
        }
        return out
    }
}
