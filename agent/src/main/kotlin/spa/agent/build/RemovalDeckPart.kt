package spa.agent.build

import spa.agent.poolgrade.PartText.plural

/** The removal part: the weight per removal spell, up to `cap`. */
object RemovalDeckPart : DeckPart {
    const val KEY = "removal"

    override val key = KEY

    override val parameters = listOf("cap")

    override fun score(deck: Deck, weight: Double, params: PartParams): DeckPartScore {
        if (weight == 0.0) return DeckPartScore.off(KEY)
        val removal = deck.spells.count { it.isRemoval }
        val counted = minOf(removal.toDouble(), params["cap"])
        return DeckPartScore(
            KEY,
            weight * counted,
            "${plural(removal, "removal spell")}, up to ${params["cap"].toInt()} count",
        )
    }
}
