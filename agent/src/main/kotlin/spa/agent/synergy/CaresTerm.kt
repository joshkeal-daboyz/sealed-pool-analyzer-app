package spa.agent.synergy

import spa.agent.tags.TagCard

/** The cares term (SPA-054). */
object CaresTerm : PairTerm {
    override val name = "cares"

    override val printsZero = false

    override fun score(a: TagCard, b: TagCard): Int = 0
}
