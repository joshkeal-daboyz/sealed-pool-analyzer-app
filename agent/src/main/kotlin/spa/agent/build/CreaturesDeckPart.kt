package spa.agent.build

import spa.agent.poolgrade.PartText.plural

/** The creatures part: minus the weight per creature spell below `minCreatures`. */
object CreaturesDeckPart : DeckPart {
    const val KEY = "creatures"

    override val key = KEY

    override val parameters = listOf("minCreatures")

    override fun score(deck: Deck, weight: Double, params: PartParams): DeckPartScore {
        if (weight == 0.0) return DeckPartScore.off(KEY)
        val creatures = deck.spells.count { it.card.isCreature }
        val minimum = params["minCreatures"]
        val short = maxOf(0.0, minimum - creatures)
        val reason =
            "${plural(creatures, "creature")}, " +
                if (short > 0.0) "${short.toInt()} short of ${minimum.toInt()}"
                else "the minimum of ${minimum.toInt()} met"
        return DeckPartScore(KEY, -weight * short, reason)
    }
}
