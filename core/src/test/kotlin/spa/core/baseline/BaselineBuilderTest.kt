package spa.core.baseline

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import spa.core.brief.briefFixturesDir
import spa.core.cards.ScryfallCache
import spa.core.cards.fixturesDir
import spa.core.intake.synIndex
import spa.core.metrics.MetricEngine
import spa.core.sim.KitSampler

class BaselineBuilderTest {
    private val builder = BaselineBuilder(synIndex, synBrief)

    private fun flattenedSynKits(seed: Long, kits: Int): List<Map<String, Double>> {
        val sampler = KitSampler(synIndex, synBrief.kitTemplate)
        return (0 until kits).map {
            MetricFlattener.flatten(MetricEngine.compute(sampler.sample(seed + it)))
        }
    }

    @Test
    fun `fewer than 100 kits is rejected`() {
        assertFailsWith<IllegalArgumentException> { builder.build(seed = 1, kits = 99) }
        assertFailsWith<IllegalArgumentException> { builder.build(seed = 1, kits = 0) }
    }

    @Test
    fun `the header comes from the brief and the arguments`() {
        val baseline = builder.build(seed = 11, kits = 100)
        assertEquals("syn", baseline.setCode)
        assertEquals(synBrief.version, baseline.briefVersion)
        assertEquals("", baseline.cacheFetchedAt)
        assertEquals(11, baseline.seed)
        assertEquals(100, baseline.kits)
    }

    @Test
    fun `the same seed gives an equal baseline and another seed different stats`() {
        val first = builder.build(seed = 1, kits = 100)
        val again = BaselineBuilder(synIndex, synBrief).build(seed = 1, kits = 100)
        assertEquals(first, again)
        assertEquals(first.encode(), again.encode())
        assertNotEquals(first.stats, builder.build(seed = 2, kits = 100).stats)
    }

    @Test
    fun `stats aggregate the kits with seeds seed to seed + kits - 1`() {
        val baseline = builder.build(seed = 40, kits = 100)
        val flattened = flattenedSynKits(seed = 40, kits = 100)
        assertEquals(flattened.flatMap { it.keys }.toSet(), baseline.stats.keys)
        assertEquals(BaselineBuilder.aggregate(flattened), baseline.stats)
        val totals = flattened.map { it.getValue("/pairs/WU/cards") }
        assertEquals(Stat.of(totals), baseline.stats["/pairs/WU/cards"])
    }

    @Test
    fun `a pointer present in only some syn kits counts 0 where it is missing`() {
        val flattened = flattenedSynKits(seed = 1, kits = 100)
        val partial =
            flattened
                .flatMap { it.keys }
                .toSortedSet()
                .first { pointer ->
                    flattened.any { pointer in it } && flattened.any { pointer !in it }
                }
        val values = flattened.map { it[partial] ?: 0.0 }
        assertEquals(Stat.of(values), builder.build(seed = 1, kits = 100).stats[partial])
    }

    @Test
    fun `aggregate fills missing pointers with 0 and sorts by pointer`() {
        val kits =
            listOf(mapOf("/b" to 2.0, "/a" to 4.0), mapOf("/a" to 6.0)) +
                List(8) { mapOf("/a" to 5.0) }
        val stats = BaselineBuilder.aggregate(kits)
        assertEquals(listOf("/a", "/b"), stats.keys.toList())
        assertEquals(Stat(median = 0.0, p10 = 0.0, p90 = 0.0, mean = 0.2), stats["/b"])
        assertEquals(Stat(median = 5.0, p10 = 4.0, p90 = 5.0, mean = 5.0), stats["/a"])
    }

    @Test
    fun `load reads the index, brief, and latest cache fetch time`() {
        val baseline = BaselineBuilder.load(fixturesDir, briefFixturesDir, "trk").build(1, 100)
        val latest = listOf("trk", "sds").maxOf { ScryfallCache.load(fixturesDir, it).fetchedAt }
        assertEquals(latest.toString(), baseline.cacheFetchedAt)
        assertEquals("trk", baseline.setCode)
        assertTrue(baseline.stats.isNotEmpty())
    }
}
