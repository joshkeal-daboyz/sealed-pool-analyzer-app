package spa.agent.synergy

import spa.agent.tags.TagCard
import spa.agent.tags.TagFile

/**
 * The cares term (SPA-054): one card's facts times the other card's cares strengths for them. For
 * cards a and b, Σ over a's facts f of `cares(b, f)` plus Σ over b's facts f of `cares(a, f)`,
 * where `cares(c, f)` is c's `cares:<f>` strength ([spa.agent.otags.CaresTable]), or 0. A fact
 * counts once, at strength 1. The diagonal uses the same formula, so it counts twice.
 *
 * It needs the table's facts and cares tags, so [forTable] gives the working term; when no card has
 * a cares tag (a set with no otags file) it gives null, and the table scores and prints exactly as
 * before. This object on its own scores 0. Its part is printed only when it is not 0. The cares
 * tags are the cares table's joined with the Fable cares file's ([merged], SPA-055).
 */
object CaresTerm : PairTerm {
    override val name = "cares"

    override val printsZero = false

    override fun score(a: TagCard, b: TagCard): Int = 0

    /**
     * The cares tags of [scryfall] (from the cares table) joined with those of [fable] (the Fable
     * cares file, SPA-055), both by card id: each card has every fact either gives it, at the
     * larger strength when both do. With no [fable] cares it is [scryfall] itself.
     */
    fun merged(
        scryfall: Map<String, Map<String, Int>>,
        fable: Map<String, Map<String, Int>>,
    ): Map<String, Map<String, Int>> {
        if (fable.isEmpty()) return scryfall
        val joined = scryfall.mapValues { it.value.toMutableMap() }.toMutableMap()
        for ((id, facts) in fable) {
            val card = joined.getOrPut(id) { mutableMapOf() }
            for ((fact, strength) in facts) card.merge(fact, strength, ::maxOf)
        }
        return joined
    }

    /** Without cares tags there is no cares term. */
    override fun forTable(tags: TagFile, facts: Map<String, Set<String>>): PairTerm? = null

    override fun forTable(
        tags: TagFile,
        facts: Map<String, Set<String>>,
        cares: Map<String, Map<String, Int>>,
    ): PairTerm? {
        val held = cares.filterValues { it.isNotEmpty() }
        return if (held.isEmpty()) null else Bound(facts, held)
    }

    /** The term for one table: facts and nonempty cares tags by card id. */
    private class Bound(
        private val facts: Map<String, Set<String>>,
        private val cares: Map<String, Map<String, Int>>,
    ) : PairTerm {
        override val name = CaresTerm.name

        override val printsZero = CaresTerm.printsZero

        override fun score(a: TagCard, b: TagCard): Int = caredFor(a, b) + caredFor(b, a)

        /** Σ over [card]'s facts of [carer]'s cares strength for them. */
        private fun caredFor(card: TagCard, carer: TagCard): Int {
            val strengths = cares[carer.id] ?: return 0
            return facts[card.id].orEmpty().sumOf { strengths[it] ?: 0 }
        }
    }
}
