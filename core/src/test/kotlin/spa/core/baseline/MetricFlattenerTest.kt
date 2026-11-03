package spa.core.baseline

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import spa.core.intake.synIndex
import spa.core.metrics.ColorBucket
import spa.core.metrics.ColorPair
import spa.core.metrics.Curve
import spa.core.metrics.MetricEngine
import spa.core.sim.KitSampler
import spa.core.sim.synKit
import spa.core.sim.trkIndex
import spa.core.sim.trkKit

class MetricFlattenerTest {
    @Test
    fun `numeric leaves are keyed by JSON pointer and name-bearing lists keep only their length`() {
        val json =
            Json.parseToJsonElement(
                """
                {
                  "a": 1,
                  "b": { "c/d": 2.5, "e~f": 3 },
                  "s": "x",
                  "q": "7",
                  "t": true,
                  "n": null,
                  "nums": [1, 2],
                  "objs": [{ "x": 1 }, { "x": 2 }],
                  "named": [{ "name": "Swamp", "count": 2 }],
                  "strs": ["a", "b"],
                  "empty": [],
                  "emptyObject": {}
                }
                """
            )
        assertEquals(
            mapOf(
                "/a" to 1.0,
                "/b/c~1d" to 2.5,
                "/b/e~0f" to 3.0,
                "/nums/0" to 1.0,
                "/nums/1" to 2.0,
                "/objs/0/x" to 1.0,
                "/objs/1/x" to 2.0,
                "/named/count" to 1.0,
                "/strs/count" to 2.0,
                "/empty/count" to 0.0,
            ),
            MetricFlattener.flatten(json),
        )
    }

    @Test
    fun `escape follows RFC 6901`() {
        assertEquals("a~0b~1c", MetricFlattener.escape("a~b/c"))
        assertEquals("~01", MetricFlattener.escape("~1"))
    }

    @Test
    fun `a sampled syn kit flattens to the values of its metrics`() {
        val metrics = MetricEngine.compute(KitSampler(synIndex, synKit).sample(1))
        val flat = MetricFlattener.flatten(metrics)
        val wu = metrics.pairs.getValue(ColorPair.WU)
        val red = metrics.byColor.getValue(ColorBucket.R)
        assertEquals(metrics.totalCards.toDouble(), flat["/totalCards"])
        assertEquals(metrics.basicLands.toDouble(), flat["/basicLands"])
        assertEquals(red.cards.common.toDouble(), flat["/byColor/R/cards/common"])
        assertEquals(red.playables.total.toDouble(), flat["/byColor/R/playables/total"])
        assertEquals(
            wu.curve.buckets[2].creatures.toDouble(),
            flat["/pairs/WU/curve/buckets/2/creatures"],
        )
        assertEquals(wu.cards.toDouble(), flat["/pairs/WU/cards"])
        assertEquals(metrics.fixingSources.size.toDouble(), flat["/fixingSources/count"])
        ColorPair.entries.forEach { pair ->
            val pairMetrics = metrics.pairs.getValue(pair)
            assertEquals(
                pairMetrics.splashCandidates.size.toDouble(),
                flat["/pairs/$pair/splashCandidates/count"],
            )
            pairMetrics.castProbability.forEach { (color, chance) ->
                assertEquals(chance, flat["/pairs/$pair/castProbability/$color"])
            }
            pairMetrics.landRecommendation.forEach { (color, lands) ->
                assertEquals(lands.toDouble(), flat["/pairs/$pair/landRecommendation/$color"])
            }
        }
    }

    @Test
    fun `every pair, color bucket, and curve bucket is flattened`() {
        val flat =
            MetricFlattener.flatten(MetricEngine.compute(KitSampler(synIndex, synKit).sample(2)))
        ColorPair.entries.forEach { pair ->
            (0..Curve.TOP).forEach { at ->
                assertTrue("/pairs/$pair/curve/buckets/$at/creatures" in flat)
                assertTrue("/pairs/$pair/curve/buckets/$at/noncreatures" in flat)
                assertEquals(at.toDouble(), flat["/pairs/$pair/curve/buckets/$at/manaValue"])
            }
        }
        ColorBucket.entries.forEach { assertTrue("/byColor/$it/cards/total" in flat) }
    }

    @Test
    fun `no card name reaches a pointer and named lists are not indexed`() {
        val metrics = MetricEngine.compute(KitSampler(trkIndex, trkKit).sample(7))
        val names =
            metrics.fixingSources.map { it.name } +
                metrics.pairs.values.flatMap { pair -> pair.splashCandidates.map { it.name } }
        assertTrue(names.isNotEmpty(), "seed 7 should hold a fixing source or splash candidate")
        val pointers = MetricFlattener.flatten(metrics).keys
        names.forEach { name -> assertFalse(pointers.any { name in it }, name) }
        assertFalse(pointers.any { it.startsWith("/fixingSources/0") })
        assertFalse(pointers.any { "/splashCandidates/0" in it })
    }
}
