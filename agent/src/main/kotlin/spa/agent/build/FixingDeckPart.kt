package spa.agent.build

import spa.agent.poolgrade.PartText.plural

/**
 * The fixing part (owner, 2026-11-05): the weight times, over the deck's fixing cards (lands and
 * spells), the colours past two the deck needs, `k - 2`, times the share of the deck's `k` colours
 * the card can supply. It is 0 in a two colour deck and rises with the colours.
 */
object FixingDeckPart : DeckPart {
    const val KEY = "fixing"

    private const val TWO_COLOURS = 2

    override val key = KEY

    override fun score(deck: Deck, weight: Double, params: PartParams): DeckPartScore {
        if (weight == 0.0) return DeckPartScore.off(KEY)
        val k = deck.config.colours.size
        val needed = maxOf(0, k - TWO_COLOURS)
        val share =
            deck.fixingCards.sumOf { card ->
                val supplied = card.fixing?.colours?.count { it in deck.config.colours } ?: 0
                supplied.toDouble() / k
            }
        val reason =
            if (needed == 0) "no bonus in a deck of $k colours or fewer"
            else "${plural(deck.fixingCards.size, "fixing card")} in $k colours"
        return DeckPartScore(KEY, weight * needed * share, reason)
    }
}
