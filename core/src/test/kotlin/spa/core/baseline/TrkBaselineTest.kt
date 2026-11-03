package spa.core.baseline

import java.nio.file.Files
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import spa.core.metrics.ColorBucket
import spa.core.metrics.ColorPair
import spa.core.metrics.Curve
import spa.core.metrics.MetricEngine
import spa.core.sim.KitSampler
import spa.core.sim.trkIndex
import spa.core.sim.trkKit

/** Sanity values pinned on the committed `fixtures/baselines/trk.json`. */
class TrkBaselineTest {
    private val baseline =
        Baseline.decode(Files.readString(baselineFixturesDir.resolve("trk.json")))
    private val stats = baseline.stats

    @Test
    fun `every pointer of a sampled kit is in the stats`() {
        val sampler = KitSampler(trkIndex, trkKit)
        val pointers =
            (0 until 200L).flatMapTo(mutableSetOf()) {
                MetricFlattener.flatten(MetricEngine.compute(sampler.sample(baseline.seed + it)))
                    .keys
            }
        assertEquals(emptySet(), pointers - stats.keys)
    }

    @Test
    fun `all ten pairs, seven color buckets, and every curve bucket are present`() {
        ColorPair.entries.forEach { pair ->
            assertTrue("/pairs/$pair/cards" in stats, "$pair")
            (0..Curve.TOP).forEach { at ->
                assertTrue("/pairs/$pair/curve/buckets/$at/creatures" in stats, "$pair $at")
                assertTrue("/pairs/$pair/curve/buckets/$at/noncreatures" in stats, "$pair $at")
            }
        }
        assertEquals(10, stats.keys.count { Regex("^/pairs/[A-Z]{2}/cards$").matches(it) })
        ColorBucket.entries.forEach { assertTrue("/byColor/$it/cards/total" in stats, "$it") }
        assertEquals(7, stats.keys.count { Regex("^/byColor/[A-Z]+/cards/total$").matches(it) })
    }

    @Test
    fun `the typical pool holds the kit template's card total`() {
        val templateTotal =
            trkKit.boosters * trkKit.cardsPerBooster + (if (trkKit.promo != null) 1 else 0)
        assertEquals(85, templateTotal)
        assertEquals(templateTotal.toDouble(), stats.getValue("/totalCards").median)
    }

    @Test
    fun `basic lands follow Binomial(6, 4 over 5)`() {
        // Only the Land slot yields basics, at 4 in 5, once per booster; basicLandsSupplied
        // does not change what boosters hold.
        val basics = stats.getValue("/basicLands")
        assertEquals(5.0, basics.median)
        assertEquals(4.0, basics.p10)
        assertEquals(6.0, basics.p90)
        assertTrue(abs(basics.mean - 4.8) <= 0.05, "mean ${basics.mean}")
    }

    @Test
    fun `every pair's typical card count is between 15 and 45`() {
        ColorPair.entries.forEach { pair ->
            val median = stats.getValue("/pairs/$pair/cards").median
            assertTrue(median in 15.0..45.0, "$pair median $median")
        }
    }
}
