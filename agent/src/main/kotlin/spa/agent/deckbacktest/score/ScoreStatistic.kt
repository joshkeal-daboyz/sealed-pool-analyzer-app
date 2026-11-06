package spa.agent.deckbacktest.score

import java.util.Locale

/**
 * One statistic `spa score-backtest` reports for a list of scored builds (SPA-061), each its own
 * small object in its own file. Adding a statistic adds a file and one entry to
 * [ScoreStatistics.ALL].
 */
interface ScoreStatistic {
    /** The name of the statistic. */
    val key: String

    /** The cell for [builds], in file order: its labels and numbers as text, `-` for no number. */
    fun cell(builds: List<ScoredBuild>): String
}

/** The statistics the score backtest reports, in the order of a report line. */
object ScoreStatistics {
    /** The fewest builds a statistic prints numbers for. */
    const val MIN_BUILDS = 4

    val ALL: List<ScoreStatistic> = listOf(SpearmanStatistic, QuarterStatistic)

    /** [value] with three decimals, `-` when it is null; never a negative zero. */
    fun number(value: Double?): String {
        if (value == null) return "-"
        val text = String.format(Locale.ROOT, "%.3f", value)
        return if (text == "-0.000") "0.000" else text
    }
}
