package spa.agent.otags

import kotlinx.serialization.Serializable
import spa.core.cards.Printing

/** One card of a set's otags file: its packet [id] and its oracle tags, ancestors included. */
@Serializable data class OtagCard(val id: String, val otags: List<String>)

/** A set's oracle tags file, `fixtures/otags/<code>.json` (SPA-054). */
@Serializable
data class OtagFile(
    val set: String,
    val source: String,
    val updatedAt: String,
    val attribution: String,
    val cards: List<OtagCard>,
) {
    /** Each card's otags by packet id. */
    fun byId(): Map<String, List<String>> = emptyMap()

    /** The number of cards with at least one tag. */
    val tagged: Int
        get() = 0

    /** The number of tags over all cards. */
    val total: Int
        get() = 0

    /** The number of distinct tags. */
    val distinct: Int
        get() = 0

    /** The file's text. */
    fun encode(): String = ""

    companion object {
        /** Scryfall's attribution, carried by every otags file. */
        const val ATTRIBUTION = "Card tags from Scryfall Tagger (tagger.scryfall.com)"

        /** The otags file of [set] for the sealed-pool [printings], from [bulk]. */
        fun build(
            set: String,
            printings: List<Printing>,
            bulk: OtagBulk,
            source: String,
            updatedAt: String,
        ): OtagFile = OtagFile(set, source, updatedAt, ATTRIBUTION, emptyList())

        /** The otags file in [text]. */
        fun parse(text: String): OtagFile = OtagFile("", "", "", "", emptyList())
    }
}
