package spa.agent.build

import spa.core.math.LandMath

/**
 * The search over one [setup] for one land count: a greedy fill, then local swaps (SPA-044).
 * Everything is in collector order, so the same inputs give the same deck.
 */
internal class DeckSearch(private val builder: DeckBuilder, private val setup: BuildSetup) {
    /**
     * The deck of `40 - landCount` spells that no single swap of one copy for another improves by
     * more than [EPSILON] under [objective], or null when the setup has too few legal copies.
     */
    fun run(landCount: Int, objective: (ScoredDeck) -> Double = { it.total }): ScoredDeck? {
        val size = LandMath.DEFAULT_DECK_SIZE - landCount
        val start = fill(size, landCount) ?: return null
        return improve(start, landCount, objective)
    }

    /** Adds, size times, the candidate copy of the largest score, ties to the earlier card. */
    private fun fill(size: Int, landCount: Int): List<DeckCard>? {
        val spells = mutableListOf<DeckCard>()
        while (spells.size < size) {
            var best: DeckCard? = null
            var bestScore = Double.NEGATIVE_INFINITY
            for (candidate in setup.spells) {
                if (!canAdd(spells, candidate)) continue
                val score = builder.evaluate(setup, spells + candidate.card, landCount).total
                if (score > bestScore) {
                    best = candidate.card
                    bestScore = score
                }
            }
            spells += best ?: return null
        }
        return spells
    }

    private fun canAdd(spells: List<DeckCard>, candidate: Candidate): Boolean =
        spells.count { it === candidate.card } < candidate.quantity &&
            (!candidate.card.isSplash(setup.config) ||
                spells.count { it.isSplash(setup.config) } < setup.splash.maxCards)

    /** Makes the best swap while one improves the objective by more than [EPSILON]. */
    private fun improve(
        start: List<DeckCard>,
        landCount: Int,
        objective: (ScoredDeck) -> Double,
    ): ScoredDeck {
        var scored = builder.evaluate(setup, start, landCount)
        var value = objective(scored)
        while (true) {
            val (next, nextValue) = bestSwap(scored, objective, value + EPSILON) ?: return scored
            scored = next
            value = nextValue
        }
    }

    /** The swap of one copy for another with the largest objective above [floor], or null. */
    private fun bestSwap(
        scored: ScoredDeck,
        objective: (ScoredDeck) -> Double,
        floor: Double,
    ): Pair<ScoredDeck, Double>? {
        val spells = scored.deck.spells
        var best: Pair<ScoredDeck, Double>? = null
        for (out in spells.distinct()) {
            val without = spells.toMutableList().also { it.remove(out) }
            val ins = setup.spells.filter { it.card !== out && canAdd(without, it) }
            for (candidate in ins) {
                val next = builder.evaluate(setup, without + candidate.card, scored.deck.landCount)
                val value = objective(next)
                if (value > (best?.second ?: floor)) best = next to value
            }
        }
        return best
    }

    private companion object {
        const val EPSILON = 1e-9
    }
}
