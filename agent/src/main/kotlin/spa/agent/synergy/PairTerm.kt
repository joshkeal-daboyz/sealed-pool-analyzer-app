package spa.agent.synergy

import spa.agent.tags.TagCard
import spa.agent.tags.TagFile

/**
 * One term of the synergy score of two cards of a set's tag file (G-005 deliverable 7). A pair's
 * score is the sum of every term in the list; [score] must be symmetric in its two cards, and is
 * also asked for a card with itself.
 *
 * Without the Java-compatibility `DefaultImpls` bridge: every caller is Kotlin, and the bridge
 * would be uncovered code.
 */
@JvmDefaultWithoutCompatibility
interface PairTerm {
    /** The term's name, printed beside its part of each pair's score. */
    val name: String

    /** This term's part of the score of [a] with [b], where [a] and [b] may be the same card. */
    fun score(a: TagCard, b: TagCard): Int

    /**
     * The term as a [SynergyTable] of [tags] uses it, given each card's [facts] by tag id: this
     * term by default; a term that needs the table's data returns one holding it, and a term that
     * adds nothing to [tags] returns null, so it is not printed.
     */
    fun forTable(tags: TagFile, facts: Map<String, Set<String>>): PairTerm? = this

    /**
     * [forTable], also given each card's cares tags by tag id ([cares]: fact to strength, SPA-054).
     * By default the cares tags are not read.
     */
    fun forTable(
        tags: TagFile,
        facts: Map<String, Set<String>>,
        cares: Map<String, Map<String, Int>>,
    ): PairTerm? = forTable(tags, facts)

    /** False for a term whose part `spa synergy-table` prints only when it is not 0. */
    val printsZero: Boolean
        get() = true
}

/** The terms a pair's score sums, in print order. A new term is one file and one entry here. */
object PairTerms {
    val ALL: List<PairTerm> = listOf(AxisTerm, PartnerTerm, FactSupplyTerm, CaresTerm)
}
