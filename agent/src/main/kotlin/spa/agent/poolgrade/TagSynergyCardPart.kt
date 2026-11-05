package spa.agent.poolgrade

import spa.agent.poolgrade.PartText.decimal
import spa.agent.poolgrade.PartText.signed
import spa.agent.synergy.SynergyPartner
import spa.agent.synergy.SynergyTable
import spa.agent.synergy.SynergyWeight
import spa.agent.synergy.TagSynergy
import spa.core.cards.Card
import spa.core.cards.Printing

/**
 * What the tag synergy part reads: the set's pair [table] and its sealed-pool [printings]
 * ([spa.agent.grader.GradingPool.printings]), whose ids are the tag file's.
 */
class TagInputs(val table: SynergyTable, val printings: List<Printing>)

/**
 * The tag synergy part: the card's [syn] with the rest of the pool against [expected] in a typical
 * pool, and its strongest [partners] in the pool (SPA-048).
 */
data class TagSynergyPart(
    override val value: Double,
    val syn: Int,
    val expected: Double,
    val partners: List<SynergyPartner>,
) : Part {
    override val line: String?
        get() =
            if (value == 0.0) null
            else
                "tag synergy ${signed(value)}: $syn with the pool, ${decimal(expected)} typical (" +
                    (if (partners.isEmpty()) "no partners in the pool"
                    else partners.joinToString(", ") { it.name }) +
                    ")"
}

/**
 * SPA-048's tag synergy part, `weight × clamp((syn − e) / max(e, 1), −1, 1)` ([TagSynergy]). The
 * tags are read only when the weight is above 0.
 */
object TagSynergyCardPart : CardPart {
    const val KEY = "tagSynergy"

    override val key = KEY

    override fun score(card: Card, context: PairContext, weight: Double): Part {
        if (weight == 0.0) return TagSynergyPart(0.0, 0, 0.0, emptyList())
        val pool =
            context.remember(PoolKey(weight)) {
                val synergy =
                    context.inputs.remember(GraderKey(weight)) {
                        val tags = context.inputs.tagInputs
                        TagSynergy(tags.table, tags.printings, SynergyWeight(weight))
                    }
                synergy.score(context.pool, context.pair)
            }
        val own = pool.cards.first { it.printing.card.oracleId == card.oracleId }
        return TagSynergyPart(own.value, own.syn, own.expected, own.partners)
    }

    private data class GraderKey(val weight: Double)

    private data class PoolKey(val weight: Double)
}
