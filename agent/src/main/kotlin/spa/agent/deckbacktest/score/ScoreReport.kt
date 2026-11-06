package spa.agent.deckbacktest.score

import spa.agent.deckbacktest.BacktestSelection
import spa.agent.deckbacktest.BacktestSkip

/** The report lines of the score backtest. */
object ScoreReport {
    /**
     * The `read` line: the builds with a bucket, then each reader skip of [selection] and each of
     * [skips] by reason, in the order the checks run.
     */
    fun readLine(selection: BacktestSelection, skips: Map<ScoreSkip, Int>): String =
        "read ${selection.strong} builds with a bucket; skipped " +
            (BacktestSkip.entries.map { "${it.label} ${selection.skipped[it] ?: 0}" } +
                    ScoreSkip.entries.map { "${it.label} ${skips[it] ?: 0}" })
                .joinToString(", ")

    /** `<name>: ` and the cell of each of [statistics] for [builds], in order. */
    fun line(
        name: String,
        builds: List<ScoredBuild>,
        statistics: List<ScoreStatistic> = ScoreStatistics.ALL,
    ): String = "$name: " + statistics.joinToString(" ") { it.cell(builds) }
}
