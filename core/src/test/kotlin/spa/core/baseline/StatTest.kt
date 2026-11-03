package spa.core.baseline

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StatTest {
    private fun values(range: IntRange) = range.map { it.toDouble() }

    @Test
    fun `percentiles use the nearest rank`() {
        assertEquals(Stat(median = 5.0, p10 = 1.0, p90 = 9.0, mean = 5.5), Stat.of(values(1..10)))
        assertEquals(
            Stat(median = 50.0, p10 = 10.0, p90 = 90.0, mean = 50.5),
            Stat.of(values(1..100)),
        )
        // n = 7: ranks ceil(0.7) = 1, ceil(3.5) = 4, ceil(6.3) = 7.
        assertEquals(Stat(median = 4.0, p10 = 1.0, p90 = 7.0, mean = 4.0), Stat.of(values(1..7)))
    }

    @Test
    fun `input order does not matter`() {
        assertEquals(Stat.of(values(1..10)), Stat.of(values(1..10).reversed()))
    }

    @Test
    fun `one value is every statistic`() {
        assertEquals(Stat(3.0, 3.0, 3.0, 3.0), Stat.of(listOf(3.0)))
    }

    @Test
    fun `values are rounded to 4 decimals`() {
        assertEquals(0.3333, Stat.of(listOf(0.0, 0.0, 1.0)).mean)
        assertEquals(0.1235, Stat.of(listOf(0.123456)).median)
    }

    @Test
    fun `an empty list is rejected`() {
        assertFailsWith<IllegalArgumentException> { Stat.of(emptyList()) }
    }
}
