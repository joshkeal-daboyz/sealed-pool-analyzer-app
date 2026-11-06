package spa.agent.build

import spa.agent.poolgrade.PartText.decimal
import spa.agent.poolgrade.PartText.plural

/** The grades part: the sum of the spells' set grades, times the weight. */
object GradesDeckPart : DeckPart {
    const val KEY = "grades"

    override val key = KEY

    override fun score(deck: Deck, weight: Double, params: PartParams): DeckPartScore {
        if (weight == 0.0) return DeckPartScore.off(KEY)
        val sum = deck.spells.sumOf { it.grade }
        val mean = if (deck.spells.isEmpty()) 0.0 else sum / deck.spells.size
        return DeckPartScore(
            KEY,
            weight * sum,
            "${plural(deck.spells.size, "spell")} with a mean grade of ${decimal(mean)}",
        )
    }
}
