package spa.agent.otags

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import spa.agent.grader.GradingPool
import spa.core.cards.Printing

/** One card of a set's otags file: its packet [id] and its oracle tags, ancestors included. */
@Serializable data class OtagCard(val id: String, val otags: List<String>)

/**
 * A set's oracle tags file, `fixtures/otags/<code>.json` (SPA-054): the [set], the bulk file it
 * came from ([source], [updatedAt]), Scryfall's [attribution], and every sealed-pool card's otags
 * in pool (collector) order.
 */
@Serializable
data class OtagFile(
    val set: String,
    val source: String,
    val updatedAt: String,
    val attribution: String,
    val cards: List<OtagCard>,
) {
    /** Each card's otags by packet id. */
    fun byId(): Map<String, List<String>> = cards.associate { it.id to it.otags }

    /** The number of cards with at least one tag. */
    val tagged: Int
        get() = cards.count { it.otags.isNotEmpty() }

    /** The number of tags over all cards, ancestors included. */
    val total: Int
        get() = cards.sumOf { it.otags.size }

    /** The number of distinct tags. */
    val distinct: Int
        get() = cards.flatMap { it.otags }.toSet().size

    /** The file's text: two-space indents, one card a line, a final line feed. */
    fun encode(): String = buildString {
        appendLine("{")
        appendLine("  \"set\": ${quote(set)},")
        appendLine("  \"source\": ${quote(source)},")
        appendLine("  \"updatedAt\": ${quote(updatedAt)},")
        appendLine("  \"attribution\": ${quote(attribution)},")
        appendLine("  \"cards\": [")
        cards.forEachIndexed { i, card ->
            val otags = card.otags.joinToString(", ", "[", "]") { quote(it) }
            val comma = if (i < cards.size - 1) "," else ""
            appendLine("    { \"id\": ${quote(card.id)}, \"otags\": $otags }$comma")
        }
        appendLine("  ]")
        appendLine("}")
    }

    companion object {
        /** Scryfall's attribution, carried by every otags file. */
        const val ATTRIBUTION = "Card tags from Scryfall Tagger (tagger.scryfall.com)"

        /**
         * The otags file of [set] for the sealed-pool [printings] ([GradingPool.printings]), each
         * card's tags read from [bulk] by its oracle id.
         */
        fun build(
            set: String,
            printings: List<Printing>,
            bulk: OtagBulk,
            source: String,
            updatedAt: String,
        ): OtagFile =
            OtagFile(
                set,
                source,
                updatedAt,
                ATTRIBUTION,
                printings.map { OtagCard(GradingPool.id(it), bulk.otags(it.card.oracleId)) },
            )

        /** The otags file in [text]; anything else is an [IllegalArgumentException]. */
        fun parse(text: String): OtagFile =
            try {
                Json.decodeFromString(serializer(), text)
            } catch (e: SerializationException) {
                throw IllegalArgumentException(e.message, e)
            }

        private fun quote(text: String): String = Json.encodeToString(String.serializer(), text)
    }
}
