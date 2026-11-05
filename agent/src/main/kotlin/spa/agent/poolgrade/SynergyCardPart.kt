package spa.agent.poolgrade

import spa.agent.poolgrade.PartText.decimal
import spa.agent.poolgrade.PartText.plural
import spa.agent.poolgrade.PartText.ratio
import spa.agent.poolgrade.PartText.signed
import spa.agent.themes.Theme
import spa.agent.themes.ThemeCard
import spa.agent.themes.ThemeRules
import spa.core.cards.Card

/**
 * The synergy part: [partners] candidate copies are partners of the card, against [expected] in a
 * typical pool, through the card's [themes] (names, in the set's theme order). No themes is 0.
 */
data class SynergyPart(
    override val value: Double,
    val partners: Int,
    val expected: Double,
    val themes: List<String>,
) : Part {
    override val line: String?
        get() =
            if (value == 0.0) null
            else
                "synergy ${signed(value)}: ${plural(partners, "partner")} in the pool, " +
                    "${decimal(expected)} expected (${themes.joinToString(", ")})"
}

/**
 * G-004's counted synergy (SPA-038): the card's partners among the candidates other than one copy
 * of itself, against the share of partners among the fitting set cards other than itself.
 */
object SynergyCardPart : CardPart {
    const val KEY = "synergy"

    override val key = KEY

    override fun score(card: Card, context: PairContext, weight: Double): Part {
        val inputs = context.inputs
        val own = roles(inputs, card)
        if (own.isEmpty) return SynergyPart(0.0, 0, 0.0, emptyList())
        val partners =
            context.candidates.sumOf { entry ->
                val other = entry.printing.card
                val count = if (other.oracleId == card.oracleId) entry.count - 1 else entry.count
                if (own.partners(roles(inputs, other))) count else 0
            }
        val others = context.fitting.filter { it.card.oracleId != card.oracleId }
        val share =
            if (others.isEmpty()) 0.0
            else others.count { own.partners(roles(inputs, it.card)) }.toDouble() / others.size
        val expected = share * (context.copies - 1)
        val value = weight * ratio(partners - expected, expected)
        val names = inputs.themes.filter { it in own.has || it in own.refers }.map { it.name }
        return SynergyPart(value, partners, expected, names)
    }

    private fun roles(inputs: GradeInputs, card: Card): Roles =
        inputs.remember(RolesKey(card.oracleId)) {
            val themed = ThemeCard(card, card.rarity, inputs.keywords[card.oracleId].orEmpty())
            Roles(
                inputs.themes.filter { ThemeRules.has(themed, it) }.toSet(),
                inputs.themes.filter { ThemeRules.refersTo(themed, it) }.toSet(),
            )
        }

    private data class RolesKey(val oracleId: String)

    /** The kept themes a card has, and those it refers to. */
    private class Roles(val has: Set<Theme>, val refers: Set<Theme>) {
        val isEmpty: Boolean
            get() = has.isEmpty() && refers.isEmpty()

        /**
         * Whether a card with [other] roles is a partner of this one: this refers to a theme it
         * has, or this has a theme it has or refers to.
         */
        fun partners(other: Roles): Boolean =
            refers.any { it in other.has } || has.any { it in other.has || it in other.refers }
    }
}
