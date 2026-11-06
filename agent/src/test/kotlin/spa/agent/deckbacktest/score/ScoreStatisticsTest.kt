package spa.agent.deckbacktest.score

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * SPA-061 criterion 2: the two statistics on hand-built scores and buckets, with no builder in the
 * loop.
 */
class ScoreStatisticsTest {
    private fun builds(scores: List<Double>, buckets: List<Double>): List<ScoredBuild> =
        scores.zip(buckets).map { (score, bucket) -> ScoredBuild(score, bucket) }

    private val rising = listOf(0.30, 0.40, 0.50, 0.60, 0.70)

    @Test
    fun `scores that rise with the bucket give 1_000 and the reverse gives -1_000`() {
        val up = builds(listOf(1.0, 2.0, 3.0, 4.0, 5.0), rising)
        val down = builds(listOf(5.0, 4.0, 3.0, 2.0, 1.0), rising)

        assertEquals("spearman 1.000 se 0.500", SpearmanStatistic.cell(up))
        assertEquals("spearman -1.000 se 0.500", SpearmanStatistic.cell(down))
    }

    @Test
    fun `two tied scores get the average rank`() {
        val tied = builds(listOf(1.0, 1.0, 2.0, 3.0), listOf(0.1, 0.2, 0.3, 0.4))

        assertEquals("spearman 0.949 se 0.577", SpearmanStatistic.cell(tied))
    }

    @Test
    fun `the standard error is 1 over the square root of n less 1`() {
        val eight = builds(List(8) { it.toDouble() }, List(8) { 0.3 + it / 10.0 })

        assertEquals("spearman 1.000 se 0.378", SpearmanStatistic.cell(eight))
    }

    @Test
    fun `scores that do not vary leave the correlation undefined and the error known`() {
        val flat = builds(listOf(2.0, 2.0, 2.0, 2.0), listOf(0.1, 0.2, 0.3, 0.4))

        assertEquals("spearman - se 0.577", SpearmanStatistic.cell(flat))
    }

    @Test
    fun `8 builds give quarters of 2 and 5 give quarters of 1`() {
        val eight =
            builds(
                listOf(8.0, 7.0, 6.0, 5.0, 4.0, 3.0, 2.0, 1.0),
                listOf(0.9, 0.8, 0.6, 0.5, 0.4, 0.2, 0.3, 0.1),
            )
        val five = builds(listOf(5.0, 4.0, 3.0, 2.0, 1.0), listOf(0.9, 0.6, 0.5, 0.4, 0.2))

        assertEquals(2, QuarterStatistic.quarter(8))
        assertEquals(1, QuarterStatistic.quarter(5))
        assertEquals(1, QuarterStatistic.quarter(4))
        assertEquals(
            "top-quarter 0.850 bottom-quarter 0.200 gap 0.650",
            QuarterStatistic.cell(eight),
        )
        assertEquals(
            "top-quarter 0.900 bottom-quarter 0.200 gap 0.700",
            QuarterStatistic.cell(five),
        )
    }

    @Test
    fun `tied scores stay in file order when the quarters are cut`() {
        val tied = builds(listOf(1.0, 1.0, 1.0, 1.0), listOf(0.5, 0.4, 0.6, 0.7))

        assertEquals("top-quarter 0.700 bottom-quarter 0.500 gap 0.200", QuarterStatistic.cell(tied))
    }

    @Test
    fun `fewer than 4 builds print a dash for every number`() {
        val three = builds(listOf(1.0, 2.0, 3.0), listOf(0.1, 0.2, 0.3))

        assertEquals("spearman - se -", SpearmanStatistic.cell(three))
        assertEquals("top-quarter - bottom-quarter - gap -", QuarterStatistic.cell(three))
        assertEquals("spearman - se -", SpearmanStatistic.cell(emptyList()))
    }

    @Test
    fun `the list holds both statistics in report order`() {
        assertEquals(listOf(SpearmanStatistic, QuarterStatistic), ScoreStatistics.ALL)
        assertEquals(
            "spearman 1.000 se 0.500 top-quarter 0.700 bottom-quarter 0.300 gap 0.400",
            ScoreReport.line("builder", builds(listOf(1.0, 2.0, 3.0, 4.0, 5.0), rising)),
        )
    }
}
