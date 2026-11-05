package spa.agent.synergy

import spa.agent.tags.TagCard

/** Stub. */
object FactSupplyTerm : PairTerm {
    override val name = "fact"

    override fun score(a: TagCard, b: TagCard): Int = 0
}
