package spa.core.intake

import spa.core.cards.CardIndex
import spa.core.cards.Pool
import spa.core.cards.PoolEntry
import spa.core.cards.Printing

/**
 * Collects one parse: resolved lines merged by printing in first-seen order, unresolved lines, and
 * the count of skipped lines.
 */
internal class PoolBuilder(private val format: PoolFormat, private val index: CardIndex) {
    private val counts = linkedMapOf<String, PoolEntry>()
    private val unresolved = mutableListOf<UnresolvedLine>()
    private val resolvedLines = mutableListOf<Int>()
    private var skipped = 0

    fun add(lineNumber: Int, printing: Printing, count: Int) {
        val previous = counts[printing.scryfallId]
        counts[printing.scryfallId] = PoolEntry(printing, count + (previous?.count ?: 0))
        resolvedLines += lineNumber
    }

    fun unresolved(lineNumber: Int, rawLine: String, reason: UnresolvedReason) {
        unresolved += UnresolvedLine(lineNumber, rawLine, reason)
    }

    fun skip() {
        skipped++
    }

    fun build(): ParseResult =
        ParseResult(
            pool = Pool(counts.values.toList()),
            unresolved = unresolved.toList(),
            warnings = emptyList(),
            format = format,
            setCode = index.setCodes.first(),
            skippedLines = skipped,
            resolvedLines = resolvedLines.toList(),
        )
}

/** Shared line handling for the line-based formats. */
internal object PoolLines {
    /** The byte-order mark U+FEFF, which an export may start with. */
    val BOM: String = Char(BOM_CODE_POINT).toString()

    private const val BOM_CODE_POINT = 0xFEFF

    /**
     * [text]'s lines without a leading byte-order mark, split on CRLF, LF, or CR. A final line
     * terminator does not make an extra empty line.
     */
    fun split(text: String): List<String> {
        val lines = text.removePrefix(BOM).lines()
        return if (lines.size > 1 && lines.last().isEmpty()) lines.dropLast(1) else lines
    }

    /** True for a line starting with `#` or `//` after leading whitespace. */
    fun isComment(line: String): Boolean =
        line.trimStart().let { it.startsWith("#") || it.startsWith("//") }

    /** A count that is a positive integer, or null. */
    fun positiveCount(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it > 0 }

    /**
     * Collector numbers in printed order: by leading digits as a number, then by the whole string,
     * so `7` < `7s` < `30` < `103`.
     */
    val collectorOrder: Comparator<String> =
        compareBy<String> { number ->
                number.takeWhile { it.isDigit() }.toIntOrNull() ?: Int.MAX_VALUE
            }
            .thenBy { it }
}
