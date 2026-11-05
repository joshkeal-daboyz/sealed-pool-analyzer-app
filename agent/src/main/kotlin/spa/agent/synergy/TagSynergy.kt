package spa.agent.synergy

import kotlin.math.max
import spa.agent.grader.GradingPool
import spa.core.cards.Card
import spa.core.cards.Pool
import spa.core.cards.Printing
import spa.core.metrics.ColorPair

/**
 * One partner of a card in a pool: [copies] copies of the card [name] (for the card itself, its
 * other copies), each scoring [score] with it.
 */
data class SynergyPartner(val name: String, val copies: Int, val score: Int) {
    /** What this partner adds to the card's syn: copies times score. */
    val contribution: Int
        get() = copies * score
}

/**
 * The tag synergy part of one distinct candidate card: [quantity] copies of [printing], its [syn]
 * with the rest of the pool, the [expected] syn in a typical pool, the part's [value], and its
 * strongest [partners] in the pool (at most [TagSynergy.MAX_PARTNERS]).
 */
data class CardSynergy(
    val printing: Printing,
    val quantity: Int,
    val syn: Int,
    val expected: Double,
    val value: Double,
    val partners: List<SynergyPartner>,
) {
    /** The part's reason line: its strongest partners in the pool, or that it has none. */
    val reason: String
        get() =
            if (partners.isEmpty()) "no partners in the pool"
            else
                "partners: " +
                    partners.joinToString(", ") {
                        "${it.name} (${it.copies} x ${it.score} = ${it.contribution})"
                    }
}

/**
 * A pool's tag synergy in one [pair]: [copies] candidate copies, [fitting] set cards that fit the
 * pair, and each distinct candidate's part, highest value first, ties by name.
 */
data class PoolSynergy(
    val pair: ColorPair,
    val copies: Int,
    val fitting: Int,
    val cards: List<CardSynergy>,
)

/**
 * The tag synergy part of a pool card (G-005 deliverable 7; SPA-048's Background): its synergy with
 * the rest of the pool in a colour pair, against a typical pool. Model-free.
 *
 * The candidates are the pool's nonland copies whose colours are within the pair (N copies). For a
 * candidate a with n(a) copies, `syn(a)` is Σ over the other distinct candidates b of
 * `n(b)·score(a,b)`, plus `(n(a) − 1)·score(a,a)`; `e(a)` is the mean of `score(a,b)` over the
 * set's fitting cards b other than a, times N − 1; and the part is `weight × clamp((syn − e) /
 * max(e, 1), −1, 1)`.
 *
 * @param table the set's pair table.
 * @param setCards the set's sealed-pool printings ([GradingPool.printings]), whose packet ids are
 *   the tag file's.
 * @param weight the part's weight.
 */
class TagSynergy(
    private val table: SynergyTable,
    private val setCards: List<Printing>,
    private val weight: SynergyWeight,
) {
    /** Tag file ids by oracle id, so any printing of a set card finds its tags. */
    private val ids: Map<String, String> = setCards.associate {
        it.card.oracleId to GradingPool.id(it)
    }

    /** One distinct candidate: its first printing, its copies, and its tag id (null: none). */
    private class Candidate(val printing: Printing, val copies: Int, val id: String?) {
        val name: String
            get() = printing.card.name
    }

    /** Each distinct candidate of [pool] in [pair] with its part and reason. */
    fun score(pool: Pool, pair: ColorPair): PoolSynergy {
        val entries = pool.entries.filter { it.count > 0 && fits(it.printing.card, pair) }
        val copies = entries.sumOf { it.count }
        val candidates =
            entries
                .groupBy { it.printing.card.oracleId }
                .map { (oracleId, same) ->
                    Candidate(same.first().printing, same.sumOf { it.count }, ids[oracleId])
                }
        val fitting = setCards.filter { fits(it.card, pair) }
        val cards =
            candidates
                .map { card(it, candidates, copies, fitting) }
                .sortedWith(
                    compareByDescending<CardSynergy> { it.value }.thenBy { it.printing.card.name }
                )
        return PoolSynergy(pair, copies, fitting.size, cards)
    }

    private fun card(
        card: Candidate,
        candidates: List<Candidate>,
        copies: Int,
        fitting: List<Printing>,
    ): CardSynergy {
        val partners =
            candidates
                .map { other ->
                    val count = if (other === card) card.copies - 1 else other.copies
                    SynergyPartner(other.name, count, score(card.id, other.id))
                }
                .filter { it.contribution != 0 }
        val syn = partners.sumOf { it.contribution }
        val others = fitting.filter { it.card.oracleId != card.printing.card.oracleId }
        val mean =
            if (others.isEmpty()) 0.0
            else others.sumOf { score(card.id, GradingPool.id(it)) }.toDouble() / others.size
        val expected = mean * (copies - 1)
        val strongest =
            partners
                .sortedWith(
                    compareByDescending<SynergyPartner> { it.contribution }.thenBy { it.name }
                )
                .take(MAX_PARTNERS)
        return CardSynergy(
            card.printing,
            card.copies,
            syn,
            expected,
            part(syn, expected, weight.weight),
            strongest,
        )
    }

    private fun score(a: String?, b: String?): Int =
        if (a == null || b == null) 0 else table.score(a, b)

    companion object {
        /** How many partners a reason line names. */
        const val MAX_PARTNERS = 3

        /**
         * A nonland card castable in [pair], as [spa.agent.poolgrade.PoolGrader] picks them: its
         * colours ([Card.colors]) are a subset of the pair's, colourless included.
         */
        fun fits(card: Card, pair: ColorPair): Boolean =
            !card.isLand && pair.colors.containsAll(card.colors)

        /** `weight × clamp((syn − e) / max(e, 1), −1, 1)`; weight 0 gives 0. */
        fun part(syn: Int, expected: Double, weight: Double): Double {
            val ratio = ((syn - expected) / max(expected, 1.0)).coerceIn(-1.0, 1.0)
            // Adding 0.0 turns a negative zero into 0.0, so weight 0 never prints as -0.000.
            return weight * ratio + 0.0
        }
    }
}
