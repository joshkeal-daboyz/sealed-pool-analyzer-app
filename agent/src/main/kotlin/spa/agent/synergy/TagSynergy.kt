package spa.agent.synergy

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
        get() = TODO()
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
 * @param table the set's pair table.
 * @param setCards the set's sealed-pool printings ([spa.agent.grader.GradingPool.printings]), whose
 *   packet ids are the tag file's.
 * @param weight the part's weight.
 */
class TagSynergy(
    private val table: SynergyTable,
    private val setCards: List<Printing>,
    private val weight: SynergyWeight,
) {
    /** Each distinct candidate of [pool] in [pair] with its part and reason. */
    fun score(pool: Pool, pair: ColorPair): PoolSynergy = TODO()

    companion object {
        /** How many partners a reason line names. */
        const val MAX_PARTNERS = 3

        /** A nonland card castable in [pair], as [spa.agent.poolgrade.PoolGrader] picks them. */
        fun fits(card: Card, pair: ColorPair): Boolean = TODO()

        /** `weight × clamp((syn − e) / max(e, 1), −1, 1)`. */
        fun part(syn: Int, expected: Double, weight: Double): Double = TODO()
    }
}
