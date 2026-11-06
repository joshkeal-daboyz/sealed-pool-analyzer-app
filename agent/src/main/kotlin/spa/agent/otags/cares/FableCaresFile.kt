package spa.agent.otags.cares

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

private const val MAX_STRENGTH = 3

/** One cares tag Fable added: the [fact], its [strength] (1 to 3) and a one-line [reason]. */
@Serializable
data class FableCare(val fact: String, val strength: Int, val reason: String) {
    init {
        require(strength in 1..MAX_STRENGTH) { "strength $strength of '$fact' is not 1 to 3" }
    }
}

/** One card's additions: its packet [id] and the cares tags Fable gave it. */
@Serializable data class FableCard(val id: String, val cares: List<FableCare>)

/**
 * A set's Fable cares file, `fixtures/otags/<code>.fable.json` (SPA-055): the cares tags Fable
 * added where Scryfall's tags leave a gap, for the [cards] that got any, in collector order. It
 * joins the cares tags the cares table gives ([spa.agent.synergy.CaresTerm.merged]).
 */
@Serializable
data class FableCaresFile(val set: String, val provenance: String, val cards: List<FableCard>) {
    /** Each card's cares tags by packet id: fact to strength. */
    fun byId(): Map<String, Map<String, Int>> = cards.associate { card ->
        card.id to card.cares.associate { it.fact to it.strength }
    }

    /** The file's text: two-space indents and a final line feed. */
    fun encode(): String = json.encodeToString(serializer(), this) + "\n"

    companion object {
        private val json = Json {
            prettyPrint = true
            prettyPrintIndent = "  "
        }

        /** The Fable cares file in [text]; anything else is an [IllegalArgumentException]. */
        fun parse(text: String): FableCaresFile =
            try {
                json.decodeFromString(serializer(), text)
            } catch (e: SerializationException) {
                throw IllegalArgumentException(e.message, e)
            }
    }
}
