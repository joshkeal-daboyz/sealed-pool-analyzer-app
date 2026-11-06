package spa.agent.build

import spa.agent.poolgrade.PartText.decimal

/**
 * The castability part: minus the weight times, over the spells, grade times the chance the card is
 * not castable on curve ([Deck.castable]).
 */
object CastabilityDeckPart : DeckPart {
    const val KEY = "castability"

    /** A spell with a chance below this is named in the reason. */
    private const val NAMED_BELOW = 0.9

    override val key = KEY

    override fun score(deck: Deck, weight: Double, params: PartParams): DeckPartScore {
        if (weight == 0.0) return DeckPartScore.off(KEY)
        val loss = deck.spells.sumOf { it.grade * (1.0 - deck.castable(it)) }
        val poor = deck.spells.distinct().filter { deck.castable(it) < NAMED_BELOW }
        val reason =
            if (poor.isEmpty()) "every spell is castable on curve at 90% or better"
            else
                "${poor.size} below 90%: " +
                    poor.take(NAMED).joinToString(", ") {
                        "${it.name} ${decimal(deck.castable(it) * PERCENT)}%"
                    }
        return DeckPartScore(KEY, -weight * loss, reason)
    }

    private const val NAMED = 3
    private const val PERCENT = 100.0
}
