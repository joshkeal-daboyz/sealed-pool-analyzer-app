package spa.core.intake

import spa.core.cards.Pool

/** The three pool file formats a parser reads. */
enum class PoolFormat {
    /** A ManaBox CSV export, resolved by Scryfall id. */
    MANABOX,

    /** One card name per line, with an optional count. */
    TEXT,

    /** One collector number per line, with an optional set code and count. */
    COLLECTOR,
}

/**
 * What a pool file parsed to. [pool] holds exactly the resolved entries; [unresolved] holds every
 * other line that was not blank, a comment, or the ManaBox header. [resolvedLines] are the 1-based
 * numbers of the lines that went into [pool]. [skippedLines] counts blank and comment lines.
 * [setCode] is the index's target set. [warnings] is empty unless a kit template was given.
 */
data class ParseResult(
    val pool: Pool,
    val unresolved: List<UnresolvedLine>,
    val warnings: List<KitWarning>,
    val format: PoolFormat,
    val setCode: String,
    val skippedLines: Int,
    val resolvedLines: List<Int>,
)

/** A line that did not resolve to a printing, with its 1-based [lineNumber] and the [reason]. */
data class UnresolvedLine(val lineNumber: Int, val rawLine: String, val reason: UnresolvedReason)

/** Why a line did not resolve, with the [code] reports show. */
enum class UnresolvedReason(val code: String) {
    /** The line names a set that is not in the index. */
    WRONG_SET("wrong-set"),

    /** A ManaBox row's Scryfall id is not in the index. */
    UNKNOWN_SCRYFALL_ID("unknown-scryfall-id"),

    /** The count is not a positive integer. */
    BAD_QUANTITY("bad-quantity"),

    /** A text line's name is not a card in the index. */
    UNKNOWN_NAME("unknown-name"),

    /** A collector line's number is not in its set. */
    UNKNOWN_COLLECTOR_NUMBER("unknown-collector-number"),

    /** A collector line that does not match the collector grammar at all. */
    UNPARSED("unparsed"),
}

/** A pool file that cannot be read at all, such as a ManaBox export missing a required column. */
class PoolParseException(message: String, val missingColumns: List<String> = emptyList()) :
    IllegalArgumentException(message)
