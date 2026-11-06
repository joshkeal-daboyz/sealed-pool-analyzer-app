package spa.agent.build

import spa.core.math.LandMath

/** A [deck] with each part's score in list order, and their sum, the [total]. */
class ScoredDeck(val deck: Deck, val parts: List<DeckPartScore>) {
    val total: Double = parts.sumOf { it.value }
}

/**
 * Builds the best 40 card deck of a [BuildSetup] by search, with no model call (G-005 deliverables
 * 2 and 4). The score is the sum of [parts], each weighted from [weights]; the builder knows
 * nothing of any one part. The search is deterministic.
 */
class DeckBuilder(val weights: DeckWeights, val parts: List<DeckPart> = DeckParts.ALL) {
    /** The score of [spells] (copies) with [landCount] lands in [setup]. */
    fun evaluate(setup: BuildSetup, spells: List<DeckCard>, landCount: Int): ScoredDeck {
        val deck = Deck(setup, spells.sortedWith(DeckCard.ORDER), landCount)
        return ScoredDeck(
            deck,
            parts.map { it.score(deck, weights.weight(it.key), weights.params(it)) },
        )
    }

    /**
     * The best deck of [setup], or null when it has too few candidates ([BuildSetup.capacity] under
     * 40 less the highest land count). The land count follows the land rule: the search starts from
     * the usual count, builds `40 - count` spells by greedy fill then local swaps, and repeats with
     * the count the rule gives for the result until it is stable. A count that needs more spells
     * than the pool has is raised to what the pool can fill; that deck is the one case where the
     * land count is not the rule's. If the counts cycle, the best deck whose own mean agrees with
     * its land count is taken (swaps that would change the rule's count are not allowed), or, if
     * none does, the first count's deck.
     */
    fun build(setup: BuildSetup): ScoredDeck? {
        val fewest = LandMath.DEFAULT_DECK_SIZE - setup.capacity
        return if (fewest > weights.lands.high) null else settle(setup, fewest)
    }

    /** The deck whose land count is stable under the rule, [build]'s loop. */
    private fun settle(setup: BuildSetup, fewest: Int): ScoredDeck? {
        val rule = weights.lands
        val colours = setup.config.colours.size
        val search = DeckSearch(this, setup)
        val seen = mutableListOf<Int>()
        var landCount = maxOf(rule.normal, fewest)
        var stable: ScoredDeck? = null
        while (stable == null && landCount !in seen) {
            seen += landCount
            val deck = checkNotNull(search.run(landCount))
            val next = maxOf(rule.count(deck.deck.meanManaValue, colours), fewest)
            if (next == landCount) stable = deck else landCount = next
        }
        return stable ?: agreeing(search, colours, fewest) ?: search.run(seen.first())
    }

    /** The best deck, over each land count, whose own mean gives that land count. */
    private fun agreeing(search: DeckSearch, colours: Int, fewest: Int): ScoredDeck? {
        val rule = weights.lands
        return listOf(rule.low, rule.normal, rule.high)
            .distinct()
            .filter { it >= fewest }
            .mapNotNull { landCount ->
                search.run(landCount) { scored ->
                    val distance = rule.distance(scored.deck.meanManaValue, colours, landCount)
                    scored.total - PENALTY * distance
                }
            }
            .filter { rule.count(it.deck.meanManaValue, colours) == it.deck.landCount }
            .maxByOrNull { it.total }
    }

    private companion object {
        const val PENALTY = 1e6
    }
}
