package spa.agent.build

/**
 * One part's score of a deck: its [key], its [value] (already weighted, in grade points) and the
 * [reason] it gives for it, which is null only when the part is off.
 */
class DeckPartScore(val key: String, val value: Double, val reason: String?) {
    companion object {
        /** The score of a part whose weight is 0: nothing, with no reason. */
        fun off(key: String) = DeckPartScore(key, 0.0, null)
    }
}

/** The numbers a part reads from its section of the weights file, by name. */
class PartParams(private val values: Map<String, Double>) {
    /** The parameter [name]; an [IllegalArgumentException] when the file did not give it. */
    operator fun get(name: String): Double =
        requireNotNull(values[name]) { "the weights file gives no '$name'" }
}

/**
 * One rule of the deck score (G-005 Constraints: each its own class behind this interface, its own
 * file). [DeckBuilder] sums the scores of its list of parts, in order, and the printer prints each
 * part's own reason. Adding, removing or reordering a part touches the list and the part's file,
 * nothing else.
 *
 * Without the Java-compatibility `DefaultImpls` bridge: every caller is Kotlin.
 */
@JvmDefaultWithoutCompatibility
interface DeckPart {
    /** The part's key in the weights file. */
    val key: String

    /** The names of the numbers the part reads from its `params` section of the weights file. */
    val parameters: List<String>
        get() = emptyList()

    /**
     * The part's score of [deck] at [weight] with [params]. A [weight] of 0 switches the part off:
     * the result is [DeckPartScore.off].
     */
    fun score(deck: Deck, weight: Double, params: PartParams): DeckPartScore
}

/** The parts the builder uses by default, in the order they are summed and printed. */
object DeckParts {
    val ALL: List<DeckPart> =
        listOf(
            GradesDeckPart,
            CurveDeckPart,
            CreaturesDeckPart,
            RemovalDeckPart,
            CastabilityDeckPart,
            FixingDeckPart,
            SynergyDeckPart,
        )
}

/** A pair of cards' synergy score; symmetric, and asked for a card with itself. */
fun interface PairScorer {
    fun score(a: DeckCard, b: DeckCard): Int

    companion object {
        /** No synergy between any two cards. */
        val NONE = PairScorer { _, _ -> 0 }
    }
}
