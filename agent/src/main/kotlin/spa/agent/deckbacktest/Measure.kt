package spa.agent.deckbacktest

import java.util.Locale

/**
 * One number the deck backtest reports for a group of builds (G-005 deliverable 6), each its own
 * small object in its own file. Adding a measure adds a file and one entry to [Measures.ALL].
 */
interface Measure {
    /** The name that heads the measure cell in a report line. */
    val key: String

    /** [outcome] value, or null when the measure does not apply to it (the build is left out). */
    fun value(outcome: BuildOutcome): Double?

    /** The measure cell for [group]: its [key] and value as text, with dashes for no value. */
    fun cell(group: List<BuildOutcome>): String
}

/** A measure of a yes or no: its value is 1 for a hit and 0 for a miss, its cell a percentage. */
abstract class RateMeasure(final override val key: String) : Measure {
    /** Whether [outcome] is a hit; null when the measure does not apply to it. */
    abstract fun hit(outcome: BuildOutcome): Boolean?

    final override fun value(outcome: BuildOutcome): Double? =
        hit(outcome)?.let { if (it) 1.0 else 0.0 }

    override fun cell(group: List<BuildOutcome>): String {
        val values = group.mapNotNull(::value)
        return if (values.isEmpty()) "$key -" else "$key ${percent(values.average())}"
    }

    private fun percent(share: Double): String = String.format(Locale.ROOT, "%.1f", share * PERCENT)

    private companion object {
        const val PERCENT = 100.0
    }
}

/** The measures the backtest reports, in the order of a report line. */
object Measures {
    val ALL: List<Measure> =
        listOf(ColourAgreement, TopThree, SplashAgreement, MainDeckOverlap, LandCount)
}
