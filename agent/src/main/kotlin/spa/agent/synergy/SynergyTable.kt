package spa.agent.synergy

import spa.agent.tags.TagCard
import spa.agent.tags.TagFile

/**
 * One pair of the table: [first] and [second] (the same card on the diagonal), [first] no later
 * than [second] in the tag file's order, with each term's part in [parts], in the terms' order.
 */
data class PairScore(val first: TagCard, val second: TagCard, val parts: List<Int>) {
    /** The pair's score: the sum of its parts. */
    val score: Int
        get() = parts.sum()

    /** True for a card with itself. */
    val isDiagonal: Boolean
        get() = first.id == second.id
}

/**
 * The synergy score of every pair of cards in a set's tag file, each card with itself included
 * (G-005 deliverable 7): the sum of the [terms]. It is computed from the tags, never stored, and is
 * symmetric. An id the tag file does not hold scores 0 with every card.
 */
class SynergyTable(val tags: TagFile, val terms: List<PairTerm> = PairTerms.ALL) {
    /** The tag file's cards, in its (collector) order. */
    val cards: List<TagCard>
        get() = tags.cards

    /** The card with [id], or null when the tag file does not hold it. */
    fun card(id: String): TagCard? = TODO()

    /** Each term's part of the score of the cards with ids [a] and [b]; all 0 for an unknown id. */
    fun parts(a: String, b: String): List<Int> = TODO()

    /** The score of the cards with ids [a] and [b]; 0 when either is unknown. */
    fun score(a: String, b: String): Int = TODO()

    /** Every pair, the diagonal included, once each, in tag file order of first then second. */
    fun pairs(): List<PairScore> = TODO()
}
