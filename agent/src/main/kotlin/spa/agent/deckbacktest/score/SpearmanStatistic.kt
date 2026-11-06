package spa.agent.deckbacktest.score

import kotlin.math.sqrt
import spa.agent.backtest.Spearman

/**
 * The rank correlation of score and bucket, with `se`, its rough standard error `1 / sqrt(n - 1)`.
 * Both are `-` below [ScoreStatistics.MIN_BUILDS] builds; the correlation alone is `-` when the
 * scores or the buckets are all equal.
 */
object SpearmanStatistic : ScoreStatistic {
    override val key = "spearman"

    override fun cell(builds: List<ScoredBuild>): String {
        if (builds.size < ScoreStatistics.MIN_BUILDS) return "spearman - se -"
        val rho = Spearman.rho(builds.map { it.score }, builds.map { it.bucket })
        val se = 1.0 / sqrt((builds.size - 1).toDouble())
        return "spearman ${ScoreStatistics.number(rho)} se ${ScoreStatistics.number(se)}"
    }
}
