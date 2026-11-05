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

    private val positions: Map<String, Int> =
        tags.cards.withIndex().associate { it.value.id to it.index }

    /** Every score, row by row in tag file order, filled on first use. */
    private val scores: IntArray by lazy {
        val n = cards.size
        val table = IntArray(n * n)
        for (i in 0 until n) {
            for (j in i until n) {
                val score = terms.sumOf { it.score(cards[i], cards[j]) }
                table[i * n + j] = score
                table[j * n + i] = score
            }
        }
        table
    }

    /** The card with [id], or null when the tag file does not hold it. */
    fun card(id: String): TagCard? = positions[id]?.let { cards[it] }

    /** Each term's part of the score of the cards with ids [a] and [b]; all 0 for an unknown id. */
    fun parts(a: String, b: String): List<Int> {
        val first = card(a)
        val second = card(b)
        return if (first == null || second == null) terms.map { 0 }
        else terms.map { it.score(first, second) }
    }

    /** The score of the cards with ids [a] and [b]; 0 when either is unknown. */
    fun score(a: String, b: String): Int {
        val i = positions[a]
        val j = positions[b]
        return if (i == null || j == null) 0 else scores[i * cards.size + j]
    }

    /** Every pair, the diagonal included, once each, in tag file order of first then second. */
    fun pairs(): List<PairScore> =
        cards.indices.flatMap { i ->
            (i until cards.size).map { j ->
                PairScore(cards[i], cards[j], terms.map { it.score(cards[i], cards[j]) })
            }
        }
}
