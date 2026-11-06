package spa.agent.build

/**
 * The synergy part: the weight times the sum of the pair score over every pair of spell copies (a
 * second copy of a card pairs with the first on the diagonal), divided by the spells.
 */
object SynergyDeckPart : DeckPart {
    const val KEY = "synergy"

    override val key = KEY

    override fun score(deck: Deck, weight: Double, params: PartParams): DeckPartScore {
        if (weight == 0.0) return DeckPartScore.off(KEY)
        val spells = deck.spells.size
        val mean = if (spells == 0) 0.0 else deck.pairSum.toDouble() / spells
        return DeckPartScore(
            KEY,
            weight * mean,
            "pair scores total ${deck.pairSum} over $spells spells",
        )
    }
}
