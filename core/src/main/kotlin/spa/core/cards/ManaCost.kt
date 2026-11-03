package spa.core.cards

import spa.core.text.Color
import spa.core.text.ManaCost as RulesManaCost

/**
 * The mana-cost reads [CardNormalizer] needs, delegated to [spa.core.text.ManaCost].
 *
 * That parser rejects the half symbols of Un-sets (`{½}`, `{HW}`), which Scryfall can print, so
 * they are taken out of the cost first: each adds one half to the mana value and no color.
 */
internal object ManaCost {
    private val halfSymbol = Regex("\\{(?:½|H[^}]*)}")
    private const val HALF = 0.5

    /**
     * The mana value of [cost], as [spa.core.text.ManaCost.manaValue] plus one half per half
     * symbol.
     */
    fun manaValue(cost: String): Double =
        RulesManaCost.parse(withoutHalves(cost)).manaValue + halfSymbol.findAll(cost).count() * HALF

    /** The colors whose symbols appear in [cost], hybrid and Phyrexian halves included. */
    fun colors(cost: String): Set<Color> = RulesManaCost.parse(withoutHalves(cost)).colors

    private fun withoutHalves(cost: String): String = halfSymbol.replace(cost, "")
}
