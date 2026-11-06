package spa.agent.deckbacktest

import java.util.Locale
import kotlin.math.min

/**
 * How many of the player registered nonland cards the built deck also plays: the sum over cards of
 * the smaller of the two copy counts. Its cell is the mean overlap, then the mean number of nonland
 * cards the players registered, over the builds that have a built deck.
 */
object MainDeckOverlap : Measure {
    override val key = "overlap"

    override fun value(outcome: BuildOutcome): Double? =
        outcome.built
            ?.spells
            ?.entries
            ?.sumOf { (card, copies) -> min(copies, outcome.player.spells[card] ?: 0) }
            ?.toDouble()

    override fun cell(group: List<BuildOutcome>): String {
        val built = group.filter { it.built != null }
        if (built.isEmpty()) return "$key - of -"
        val overlap = built.mapNotNull(::value).average()
        val registered = built.map { it.player.spells.values.sum() }.average()
        return "$key ${decimals(overlap)} of ${decimals(registered)}"
    }

    private fun decimals(value: Double): String = String.format(Locale.ROOT, "%.2f", value)
}
