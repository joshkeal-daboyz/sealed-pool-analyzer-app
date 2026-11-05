package spa.agent.synergy

import spa.agent.tags.TagCard

/**
 * The axis term: over the set's axes x, `E(a,x)·P(b,x) + E(b,x)·P(a,x)`, where `E` is a card's
 * enabler strength on x and `P` its payoff strength, each 0 when the card has no such role. For a
 * card with itself this is `2·Σ E(a,x)·P(a,x)`.
 */
object AxisTerm : PairTerm {
    override val name = "axis"

    override fun score(a: TagCard, b: TagCard): Int = TODO()
}
