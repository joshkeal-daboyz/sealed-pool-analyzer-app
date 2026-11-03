package spa.core.intake

import spa.core.brief.KitTemplate
import spa.core.cards.CardIndex

/** Detects a pool file's format and parses it with the matching parser. */
object PoolParser {
    /** The share of lines, in percent, that must match the collector grammar for [detect]. */
    const val COLLECTOR_THRESHOLD_PERCENT = 80

    private const val PERCENT = 100

    /**
     * [PoolFormat.MANABOX] when the first non-blank line contains `Scryfall ID`;
     * [PoolFormat.COLLECTOR] when at least [COLLECTOR_THRESHOLD_PERCENT] percent of the non-blank,
     * non-comment lines match the collector grammar; else [PoolFormat.TEXT].
     */
    fun detect(text: String): PoolFormat {
        val lines = PoolLines.split(text).filter { it.isNotBlank() }
        val content = lines.filterNot { PoolLines.isComment(it) }
        val collectorLines = content.count { CollectorListParser.matches(it) }
        return when {
            lines.firstOrNull()?.contains(ManaBoxCsvParser.SCRYFALL_ID, ignoreCase = true) ==
                true -> PoolFormat.MANABOX
            content.isNotEmpty() &&
                collectorLines * PERCENT >= content.size * COLLECTOR_THRESHOLD_PERCENT ->
                PoolFormat.COLLECTOR
            else -> PoolFormat.TEXT
        }
    }

    /**
     * Parses [text] against [index] as [format], or as the detected format when [format] is null.
     * When [kitTemplate] is given, the result's warnings are [KitValidator.validate]'s.
     */
    fun parse(
        text: String,
        index: CardIndex,
        format: PoolFormat? = null,
        kitTemplate: KitTemplate? = null,
    ): ParseResult {
        val result =
            when (format ?: detect(text)) {
                PoolFormat.MANABOX -> ManaBoxCsvParser.parse(text, index)
                PoolFormat.TEXT -> TextListParser.parse(text, index)
                PoolFormat.COLLECTOR -> CollectorListParser.parse(text, index)
            }
        return if (kitTemplate == null) result
        else result.copy(warnings = KitValidator.validate(result.pool, kitTemplate))
    }
}
