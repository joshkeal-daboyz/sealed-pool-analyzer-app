package spa.agent.synergy

import kotlin.math.max
import spa.agent.tags.RoleNameCheck
import spa.agent.tags.TagCard
import spa.agent.tags.TagFile

/**
 * The fact supply term (SPA-051): an axis's `suppliedBy` facts count as enabler strength for every
 * card that has them. For card c and axis x, `F(c,x)` is the largest `suppliedBy` strength on x
 * among c's facts, or 0, and the effective enabler strength is `Ê(c,x) = max(E(c,x), F(c,x))`. The
 * term adds, over the axes, `(Ê(a,x) − E(a,x))·P(b,x) + (Ê(b,x) − E(b,x))·P(a,x)`, so that with
 * [AxisTerm] the total is `Σ Ê(a,x)·P(b,x) + Ê(b,x)·P(a,x)`. The diagonal uses the same formula.
 *
 * It needs the table's facts, so [forTable] gives the working term; for a tag file whose axes have
 * no `suppliedBy` fact it gives null, and such a file scores and prints exactly as before. This
 * object on its own scores 0.
 */
object FactSupplyTerm : PairTerm {
    override val name = "fact"

    override fun score(a: TagCard, b: TagCard): Int = 0

    override fun forTable(tags: TagFile, facts: Map<String, Set<String>>): PairTerm? {
        val supplied = tags.axes.filter { !it.suppliedBy.isNullOrEmpty() }
        if (supplied.isEmpty()) return null
        val supply =
            tags.cards.associate { card ->
                val have = facts[card.id].orEmpty()
                card.id to
                    supplied
                        .associate { axis ->
                            axis.id to
                                (axis.suppliedBy!!
                                    .filter { it.fact in have }
                                    .maxOfOrNull { it.strength } ?: 0)
                        }
                        .filterValues { it > 0 }
            }
        return Bound(supply)
    }

    /** The term for one table: `F(c, x)` by card id and axis id, only where it is above 0. */
    private class Bound(private val supply: Map<String, Map<String, Int>>) : PairTerm {
        override val name = FactSupplyTerm.name

        override fun score(a: TagCard, b: TagCard): Int = extra(a, b) + extra(b, a)

        /** Σ over the axes of ([enabler]'s Ê − E)·[payoff]'s P. */
        private fun extra(enabler: TagCard, payoff: TagCard): Int =
            supply[enabler.id].orEmpty().entries.sumOf { (axis, f) ->
                val e = strength(enabler, axis, RoleNameCheck.ENABLER)
                (max(e, f) - e) * strength(payoff, axis, RoleNameCheck.PAYOFF)
            }

        private fun strength(card: TagCard, axis: String, role: String): Int =
            card.roles.filter { it.axis == axis && it.role == role }.sumOf { it.strength }
    }
}
