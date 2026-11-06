package spa.agent.review

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import spa.core.baseline.Baseline
import spa.core.baseline.Stat

class NumberTablesTest {
    private val totalStat = Stat(median = 15.0, p10 = 12.0, p90 = 18.0, mean = 15.0)
    private val wuCreaturesStat = Stat(median = 5.5, p10 = 4.0, p90 = 7.0, mean = 5.5)
    private val baseline =
        ReviewTestCards.baseline(
            mapOf("/totalCards" to totalStat, "/pairs/WU/creatures" to wuCreaturesStat)
        )

    private fun input(baseline: Baseline?) =
        ReviewInput(
            set = "xyz",
            pool = ReviewTestCards.metricsPool(),
            metrics = ReviewTestCards.metrics(),
            baseline = baseline,
            brief = ReviewTestCards.brief,
            gradeOf = { null },
            kitWarnings = emptyList(),
        )

    private fun table(vararg patterns: String, baseline: Baseline? = this.baseline) =
        NumberTables.fromMetrics(input(baseline), patterns.toList())

    private fun NumberTable.keys() = entries.map { it.key }

    private fun NumberTable.value(key: String) = entries.single { it.key == key }.value

    @Test
    fun `an exact pattern selects one entry`() {
        val table = table("/totalCards")
        assertEquals(listOf("/totalCards"), table.keys())
    }

    @Test
    fun `a trailing slash selects every pointer below it`() {
        val table = table("/pairs/WU/curve/buckets/2/")
        val prefix = "/pairs/WU/curve/buckets/2"
        assertEquals(
            listOf("$prefix/creatures", "$prefix/manaValue", "$prefix/noncreatures"),
            table.keys(),
        )
    }

    @Test
    fun `a star matches exactly one segment`() {
        val table = table("/pairs/*/creatures")
        assertEquals(10, table.entries.size)
        assertEquals(true, table.keys().all { Regex("/pairs/[WUBRG]{2}/creatures").matches(it) })
        assertEquals(false, "/pairs/WU/curve/buckets/2/creatures" in table.keys())
    }

    @Test
    fun `a pattern that matches nothing selects nothing`() {
        assertEquals(emptyList(), table("/nothing").keys())
        assertEquals(emptyList(), table("/nothing/").keys())
    }

    @Test
    fun `entries are sorted by pointer and each pointer appears once`() {
        val table = table("/totalCards", "/removalTotal", "/pairs/WU/creatures", "/totalCards")
        assertEquals(listOf("/pairs/WU/creatures", "/removalTotal", "/totalCards"), table.keys())
    }

    @Test
    fun `typical comes from the baseline when it has the pointer`() {
        val table = table("/totalCards", "/removalTotal", "/pairs/WU/creatures")
        assertEquals(
            wuCreaturesStat,
            table.entries.single { it.key == "/pairs/WU/creatures" }.typical,
        )
        assertEquals(totalStat, table.entries.single { it.key == "/totalCards" }.typical)
        assertNull(table.entries.single { it.key == "/removalTotal" }.typical)
    }

    @Test
    fun `typical is null for every entry when there is no baseline`() {
        val table = table("/totalCards", "/pairs/WU/creatures", baseline = null)
        assertEquals(2, table.entries.size)
        assertEquals(true, table.entries.all { it.typical == null })
    }

    @Test
    fun `values equal the numbers worked out by hand from the pool`() {
        // Pool: Bear x3, Smite x2, Drifter x1, Envoy x1, Brute x2, Plains x5.
        val table = table("/totalCards", "/removalTotal", "/pairs/WU/creatures")
        // 3 + 2 + 1 + 1 + 2 + 5 copies.
        assertEquals(14.0, table.value("/totalCards"))
        // Smite is the only card with removal text: 2 copies.
        assertEquals(2.0, table.value("/removalTotal"))
        // Creatures castable in WU: Bear 3 + Drifter 1 + Envoy 1; Brute (red) is not.
        assertEquals(5.0, table.value("/pairs/WU/creatures"))
    }

    @Test
    fun `values in a curve bucket equal the numbers worked out by hand`() {
        // Every spell costs two mana: WU creatures 5 and noncreatures (Smite) 2, in bucket 2.
        val table = table("/pairs/WU/curve/buckets/2/")
        assertEquals(5.0, table.value("/pairs/WU/curve/buckets/2/creatures"))
        assertEquals(2.0, table.value("/pairs/WU/curve/buckets/2/noncreatures"))
    }
}
