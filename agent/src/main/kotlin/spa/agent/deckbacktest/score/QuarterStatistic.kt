package spa.agent.deckbacktest.score

import kotlin.math.max

/**
 * The mean bucket of the top and bottom quarter of builds by score, and their difference. Sorted by
 * score ascending with ties in file order, with `q = max(1, n / 4)`, the bottom quarter is the
 * first `q` builds and the top quarter the last `q`. All `-` below [ScoreStatistics.MIN_BUILDS]
 * builds.
 */
object QuarterStatistic : ScoreStatistic {
    override val key = "top-quarter"

    /** The builds in each quarter of [n] builds. */
    fun quarter(n: Int): Int = max(1, n / 4)

    override fun cell(builds: List<ScoredBuild>): String {
        if (builds.size < ScoreStatistics.MIN_BUILDS) {
            return "top-quarter - bottom-quarter - gap -"
        }
        val sorted = builds.sortedBy { it.score }
        val q = quarter(builds.size)
        val bottom = sorted.take(q).map { it.bucket }.average()
        val top = sorted.takeLast(q).map { it.bucket }.average()
        val number = ScoreStatistics::number
        return "top-quarter ${number(top)} bottom-quarter ${number(bottom)} gap ${number(top - bottom)}"
    }
}
