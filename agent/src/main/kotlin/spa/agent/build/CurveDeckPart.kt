package spa.agent.build

import spa.agent.poolgrade.PartText.plural

/**
 * The curve part: minus the weight times, over mana value buckets 1 to 6 (6 is 6 or more), the
 * spells short of the bucket's target `target<b>`.
 */
object CurveDeckPart : DeckPart {
    const val KEY = "curve"

    override val key = KEY

    override val parameters = (1..DeckCard.TOP_BUCKET).map { "target$it" }

    override fun score(deck: Deck, weight: Double, params: PartParams): DeckPartScore {
        if (weight == 0.0) return DeckPartScore.off(KEY)
        val gaps =
            (1..DeckCard.TOP_BUCKET).associateWith {
                maxOf(0.0, params["target$it"] - deck.bucketCount(it))
            }
        val short = gaps.filterValues { it > 0.0 }
        val reason =
            if (short.isEmpty()) "every mana value meets its target"
            else
                "short at " +
                    short.entries.joinToString(", ") { (bucket, gap) ->
                        val name =
                            if (bucket == DeckCard.TOP_BUCKET) "$bucket or more" else "$bucket"
                        "mana value $name by ${gap.toInt()}"
                    } +
                    " (${plural(gaps.values.sum().toInt(), "gap")})"
        return DeckPartScore(KEY, -weight * gaps.values.sum(), reason)
    }
}
