package spa.agent.synergy

import spa.agent.tags.TagCard

/**
 * The partner term: for two different cards, the strength with which a names b as a partner plus
 * the strength with which b names a; for a card with itself, the strength with which it names
 * itself. A card that names no partner adds 0.
 */
object PartnerTerm : PairTerm {
    override val name = "partner"

    override fun score(a: TagCard, b: TagCard): Int =
        if (a.id == b.id) names(a, a) else names(a, b) + names(b, a)

    /** The strength with which [card] names [other], 0 when it does not. */
    private fun names(card: TagCard, other: TagCard): Int =
        card.partners.filter { it.id == other.id }.sumOf { it.strength }
}
