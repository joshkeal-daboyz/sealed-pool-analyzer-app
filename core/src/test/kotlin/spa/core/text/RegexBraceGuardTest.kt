package spa.core.text

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Android's ICU regex engine rejects an unescaped `}` that the JVM accepts, so a pattern that
 * passes every JVM test can still fail to compile on the device. This guard scans every
 * `Regex(...)` and `"...".toRegex()` string literal under `core/src/main/kotlin` and names the file
 * and line of any `}` that is outside a character class and does not close a `{n}`, `{n,}` or
 * `{n,m}` quantifier.
 */
class RegexBraceGuardTest {
    private val sources: Path = Path.of("src", "main", "kotlin")

    @Test
    fun `no regex literal in core's main sources holds an unescaped closing brace`() {
        assertTrue(Files.isDirectory(sources), "missing ${sources.toAbsolutePath()}")
        val files =
            Files.walk(sources).use { paths ->
                paths.filter { it.isRegularFile() && it.extension == "kt" }.sorted().toList()
            }
        val scans = files.map {
            val name =
                "core/src/main/kotlin/" + sources.relativize(it).invariantSeparatorsPathString
            RegexBraceGuard.scan(name, it.readText())
        }

        assertTrue(scans.sumOf { it.literals } >= MIN_LITERALS, "scanned too few regex literals")
        val violations = scans.flatMap { it.violations }
        assertTrue(violations.isEmpty(), violations.joinToString("\n"))
    }

    @Test
    fun `a scratch unescaped closing brace is named by file and line`() {
        val source = "package x\n\nprivate val symbol = Regex(\"\\\\{([^{}]*)}\")\n"

        val scan = RegexBraceGuard.scan("Scratch.kt", source)

        assertEquals(1, scan.literals)
        assertEquals(listOf("Scratch.kt:3: unescaped '}' in regex \\{([^{}]*)}"), scan.violations)
    }

    @Test
    fun `escaped braces, braces in classes, and quantifiers pass`() {
        val source =
            """
            val a = Regex("\\{([^{}]*)\\}")
            val b = Regex("\\{(?:½|H[^}]*)\\}")
            val c = Regex(""${'"'}^\d{2,}x{3}y{1,4}[}]$""${'"'})
            val d = "[a-z]{2}".toRegex()
            """
                .trimIndent()

        val scan = RegexBraceGuard.scan("Fine.kt", source)

        assertEquals(4, scan.literals)
        assertEquals(emptyList(), scan.violations)
    }

    @Test
    fun `raw strings, concatenation, toRegex, and later lines are scanned`() {
        val source =
            """
            val a =
                Regex(
                    ""${'"'}^(\d+)""${'"'} +
                        ""${'"'}x}""${'"'}
                )
            val b = "a}b".toRegex()
            """
                .trimIndent()

        val scan = RegexBraceGuard.scan("Raw.kt", source)

        assertEquals(2, scan.literals)
        assertEquals(
            listOf(
                "Raw.kt:4: unescaped '}' in regex ^(\\d+)x}",
                "Raw.kt:6: unescaped '}' in regex a}b",
            ),
            scan.violations,
        )
    }

    @Test
    fun `a template's closing brace is not part of the pattern`() {
        val source = "val a = Regex(\"\${PREFIX}x\\\\b\")\nval b = Regex(pattern)\n"

        val scan = RegexBraceGuard.scan("Template.kt", source)

        assertEquals(1, scan.literals)
        assertEquals(emptyList(), scan.violations)
    }

    private companion object {
        const val MIN_LITERALS = 10
    }
}

/** Finds unescaped closing braces in the regex string literals of one Kotlin source file. */
object RegexBraceGuard {
    class Scan(val literals: Int, val violations: List<String>)

    /** One pattern character and the offset in the source it came from. */
    private class Ch(val c: Char, val at: Int)

    private val regexCall = Regex("""\bRegex\(""")
    private val toRegexCall = Regex("""\.toRegex\(\)""")

    fun scan(file: String, source: String): Scan {
        val patterns = mutableListOf<List<Ch>>()
        regexCall.findAll(source).forEach { call ->
            literalsFrom(source, call.range.last + 1)?.let { patterns += it }
        }
        toRegexCall.findAll(source).forEach { call ->
            literalBefore(source, call.range.first)?.let { patterns += it }
        }
        val violations = patterns.flatMap { pattern ->
            unescapedClosers(pattern).map { at ->
                val line = source.substring(0, at).count { it == '\n' } + 1
                "$file:$line: unescaped '}' in regex ${pattern.map { it.c }.joinToString("")}"
            }
        }
        return Scan(patterns.size, violations)
    }

    /**
     * The string literals joined by `+` starting at [from], or null if the argument is no literal.
     */
    private fun literalsFrom(source: String, from: Int): List<Ch>? {
        val out = mutableListOf<Ch>()
        var i = skipSpace(source, from)
        while (i < source.length && source[i] == '"') {
            i = readLiteral(source, i, out)
            val next = skipSpace(source, i)
            if (next < source.length && source[next] == '+') {
                i = skipSpace(source, next + 1)
            } else {
                break
            }
        }
        return out.takeIf { i > skipSpace(source, from) }
    }

    /** The literal on the same line that ends just before [end], or null if there is none. */
    private fun literalBefore(source: String, end: Int): List<Ch>? {
        var i = source.lastIndexOf('\n', end - 1) + 1
        while (i < end) {
            if (source[i] != '"') {
                i++
                continue
            }
            val out = mutableListOf<Ch>()
            i = readLiteral(source, i, out)
            if (i == end) return out
        }
        return null
    }

    /** Reads the literal opening at [start] into [out] and returns the offset just past it. */
    private fun readLiteral(source: String, start: Int, out: MutableList<Ch>): Int {
        val raw = source.startsWith("\"\"\"", start)
        val quote = if (raw) "\"\"\"" else "\""
        var i = start + quote.length
        while (i < source.length && !source.startsWith(quote, i)) {
            val c = source[i]
            when {
                c == '$' && source.startsWith("{", i + 1) -> i = skipTemplate(source, i + 2)
                !raw && c == '\\' -> {
                    out += Ch(unescape(source[i + 1]), i)
                    i += 2
                }
                else -> {
                    out += Ch(c, i)
                    i++
                }
            }
        }
        return minOf(i + quote.length, source.length)
    }

    private fun unescape(c: Char): Char =
        when (c) {
            'n' -> '\n'
            't' -> '\t'
            'r' -> '\r'
            'b' -> '\b'
            else -> c
        }

    private fun skipTemplate(source: String, from: Int): Int {
        var depth = 1
        var i = from
        while (i < source.length && depth > 0) {
            when (source[i]) {
                '{' -> depth++
                '}' -> depth--
            }
            i++
        }
        return i
    }

    private fun skipSpace(source: String, from: Int): Int {
        var i = from
        while (i < source.length && source[i].isWhitespace()) i++
        return i
    }

    private val quantifier = Regex("""\{\d+(?:,\d*)?\}""")

    /**
     * Source offsets of each `}` outside a class that neither is escaped nor closes a quantifier.
     */
    private fun unescapedClosers(pattern: List<Ch>): List<Int> {
        val text = pattern.map { it.c }.joinToString("")
        val found = mutableListOf<Int>()
        var classDepth = 0
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == '\\' -> i++
                classDepth > 0 && c == ']' -> classDepth--
                c == '[' -> {
                    classDepth++
                    if (text.startsWith("[^]", i)) i += 2 else if (text.startsWith("[]", i)) i++
                }
                classDepth > 0 -> Unit
                c == '{' -> quantifier.matchAt(text, i)?.let { i = it.range.last }
                c == '}' -> found += pattern[i].at
            }
            i++
        }
        return found
    }
}
