package spa.core.intake

import spa.core.cards.Card
import spa.core.cards.CardIndex
import spa.core.cards.Printing

/**
 * Reads one card name per line: `N Name`, `Nx Name`, `N x Name`, `Name xN`, or `Name` for one copy,
 * each with an optional trailing `(SET) 123` or `[SET]` that is ignored. Names resolve through
 * [CardIndex.byName]; lines starting with `#` or `//` are comments.
 */
object TextListParser {
    private val leadingCount = Regex("""^(\d+)(?:\s*[xX])?\s+(.+)$""")
    private val trailingCount = Regex("""^(.+?)\s+[xX]\s*(\d+)$""")
    private val setSuffix =
        Regex("""\s*(?:\(\s*[A-Za-z0-9]+\s*\)(?:\s*\S+)?|\[\s*[A-Za-z0-9]+\s*])\s*$""")

    fun parse(text: String, index: CardIndex): ParseResult {
        val builder = PoolBuilder(PoolFormat.TEXT, index)
        PoolLines.split(text).forEachIndexed { at, raw ->
            val lineNumber = at + 1
            if (raw.isBlank() || PoolLines.isComment(raw)) {
                builder.skip()
                return@forEachIndexed
            }
            val (name, countText) = nameAndCount(raw.trim().replace(setSuffix, ""))
            val count = PoolLines.positiveCount(countText)
            val card = index.byName(name)
            when {
                count == null -> builder.unresolved(lineNumber, raw, UnresolvedReason.BAD_QUANTITY)
                card == null -> builder.unresolved(lineNumber, raw, UnresolvedReason.UNKNOWN_NAME)
                else -> builder.add(lineNumber, printingOf(card, index), count)
            }
        }
        return builder.build()
    }

    private fun nameAndCount(line: String): Pair<String, String> =
        leadingCount.matchEntire(line)?.let { it.groupValues[2] to it.groupValues[1] }
            ?: trailingCount.matchEntire(line)?.let { it.groupValues[1] to it.groupValues[2] }
            ?: (line to "1")

    /**
     * The printing a name's count attaches to: the target set's printing of [card] with the lowest
     * collector number, else the first companion set's that has one.
     */
    internal fun printingOf(card: Card, index: CardIndex): Printing =
        index.setCodes.firstNotNullOf { setCode ->
            index
                .printings(setCode)
                .filter { it.card.oracleId == card.oracleId }
                .minWithOrNull(compareBy(PoolLines.collectorOrder) { it.collectorNumber })
        }
}
