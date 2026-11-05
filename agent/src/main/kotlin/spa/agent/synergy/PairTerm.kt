package spa.agent.synergy

import spa.agent.tags.TagCard

/**
 * One term of the synergy score of two cards of a set's tag file (G-005 deliverable 7). A pair's
 * score is the sum of every term in the list; [score] must be symmetric in its two cards, and is
 * also asked for a card with itself.
 */
interface PairTerm {
    /** The term's name, printed beside its part of each pair's score. */
    val name: String

    /** This term's part of the score of [a] with [b], where [a] and [b] may be the same card. */
    fun score(a: TagCard, b: TagCard): Int
}

/** The terms a pair's score sums, in print order. A new term is one file and one entry here. */
object PairTerms {
    val ALL: List<PairTerm> = listOf(AxisTerm, PartnerTerm)
}
