package spa.agent.poolgrade

import kotlin.math.floor
import spa.agent.poolgrade.PartText.decimal
import spa.agent.poolgrade.PartText.plural
import spa.agent.poolgrade.PartText.ratio
import spa.agent.poolgrade.PartText.signed
import spa.core.cards.Card
import spa.core.metrics.Curve

/**
 * The curve part: the pair has [count] cards in the card's mana value bucket [manaValue] (0 to 6,
 * or 7 for 7 or more), against [typical] in the set's baseline.
 */
data class CurvePart(
    override val value: Double,
    val manaValue: Int,
    val count: Int,
    val typical: Double,
) : Part {
    override val line: String?
        get() {
            if (value == 0.0) return null
            val bucket = if (manaValue == Curve.TOP) "$manaValue or more" else "$manaValue"
            return "curve ${signed(value)}: ${plural(count, "card")} at mana value " +
                "$bucket, ${decimal(typical)} typical"
        }
}

/** The pair's cards at the card's mana value against the baseline's typical count (SPA-038). */
object CurveCardPart : CardPart {
    const val KEY = "curve"

    override val key = KEY

    override fun score(card: Card, context: PairContext, weight: Double): Part {
        val pair = context.pair
        val bucket = floor(card.manaValue).toInt().coerceIn(0, Curve.TOP)
        val count = context.metrics.pairs.getValue(pair).curve.buckets[bucket].total
        val prefix = "/pairs/${pair.name}/curve/buckets/$bucket"
        val typical =
            context.inputs.mean("$prefix/creatures") + context.inputs.mean("$prefix/noncreatures")
        val value = weight * ratio(typical - count, typical)
        return CurvePart(value, bucket, count, typical)
    }
}
