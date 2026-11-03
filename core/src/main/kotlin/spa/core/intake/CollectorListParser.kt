package spa.core.intake

import java.util.Locale
import spa.core.cards.CardIndex

/**
 * Reads one collector number per line: `123`, `123 x2`, `2 123`, `2x 123`, `SET 123`, or `SET 123
 * x2`. Numbers may carry letters (`123a`, `7s`). A line with no set code means the index's target
 * set. Lines starting with `#` or `//` are comments.
 */
object CollectorListParser {
    private val grammar =
        Regex(
            """^(?:(\d+)(?:\s*[xX])?\s+)?(?:([A-Za-z0-9]*[A-Za-z][A-Za-z0-9]*)\s+)?""" +
                """(\d+[A-Za-z★†]*)(?:\s+[xX]\s*(\d+))?$"""
        )

    /** True when [line], trimmed, matches the collector grammar. */
    fun matches(line: String): Boolean = grammar.matches(line.trim())

    fun parse(text: String, index: CardIndex): ParseResult {
        val builder = PoolBuilder(PoolFormat.COLLECTOR, index)
        PoolLines.split(text).forEachIndexed { at, raw ->
            if (raw.isBlank() || PoolLines.isComment(raw)) builder.skip()
            else line(at + 1, raw, index, builder)
        }
        return builder.build()
    }

    private fun line(lineNumber: Int, raw: String, index: CardIndex, builder: PoolBuilder) {
        val match = grammar.matchEntire(raw.trim())
        if (match == null) {
            builder.unresolved(lineNumber, raw, UnresolvedReason.UNPARSED)
            return
        }
        val (before, setText, number, after) = match.destructured
        val setCode = setText.ifEmpty { index.setCodes.first() }.lowercase(Locale.ROOT)
        val count =
            if (before.isNotEmpty() && after.isNotEmpty()) null
            else PoolLines.positiveCount(before.ifEmpty { after.ifEmpty { "1" } })
        val printing =
            index.byCollectorNumber(setCode, number)
                ?: index.byCollectorNumber(setCode, number.lowercase(Locale.ROOT))
        when {
            index.printings(setCode).isEmpty() ->
                builder.unresolved(lineNumber, raw, UnresolvedReason.WRONG_SET)
            printing == null ->
                builder.unresolved(lineNumber, raw, UnresolvedReason.UNKNOWN_COLLECTOR_NUMBER)
            count == null -> builder.unresolved(lineNumber, raw, UnresolvedReason.BAD_QUANTITY)
            else -> builder.add(lineNumber, printing, count)
        }
    }
}
