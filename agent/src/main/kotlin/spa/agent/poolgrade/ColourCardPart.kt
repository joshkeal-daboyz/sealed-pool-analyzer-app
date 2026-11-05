package spa.agent.poolgrade

import spa.agent.poolgrade.PartText.decimal
import spa.agent.poolgrade.PartText.ratio
import spa.agent.poolgrade.PartText.signed
import spa.core.cards.Card
import spa.core.metrics.ColorBucket
import spa.core.text.Color

/**
 * The colour part: the card's shallowest colour [colour] has [playables] playables in the pool,
 * against [typical] in the baseline. A colourless card has a null [colour] and value 0.
 */
data class ColourPart(
    override val value: Double,
    val colour: Color?,
    val playables: Int,
    val typical: Double,
) : Part {
    override val line: String?
        get() =
            if (value == 0.0) null
            else
                "colour ${signed(value)}: ${NAMES.getValue(requireNotNull(colour))} playables " +
                    "$playables, typical ${decimal(typical)}"

    private companion object {
        val NAMES =
            mapOf(
                Color.W to "White",
                Color.U to "Blue",
                Color.B to "Black",
                Color.R to "Red",
                Color.G to "Green",
            )
    }
}

/**
 * The pool's playables of each of the card's colours against the baseline's typical count; the
 * weight times the smallest (SPA-038).
 */
object ColourCardPart : CardPart {
    const val KEY = "colour"

    override val key = KEY

    override fun score(card: Card, context: PairContext, weight: Double): Part {
        val depths =
            Color.entries
                .filter { it in card.colors }
                .map { colour ->
                    val bucket = context.metrics.byColor.getValue(ColorBucket.valueOf(colour.name))
                    val typical = context.inputs.mean("/byColor/${colour.name}/playables/total")
                    ColourPart(
                        weight * ratio(bucket.playables.total - typical, typical),
                        colour,
                        bucket.playables.total,
                        typical,
                    )
                }
        // The weight is not negative, so the smallest part is the smallest r(x).
        return depths.minByOrNull { it.value } ?: ColourPart(0.0, null, 0, 0.0)
    }
}
