package spa.core.math

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import spa.core.text.Color
import spa.core.text.Color.B
import spa.core.text.Color.G
import spa.core.text.Color.R
import spa.core.text.Color.U
import spa.core.text.Color.W

class LandMathTest {
    @Test
    fun `pinned splits from criterion 7`() {
        assertEquals(mapOf(R to 11, G to 6), LandMath.recommendLands(mapOf(R to 20.0, G to 10.0)))
        assertEquals(mapOf(W to 9, U to 8), LandMath.recommendLands(mapOf(W to 15.0, U to 15.0)))
        // Map insertion order does not change the tie-break: enum order does.
        assertEquals(mapOf(W to 9, U to 8), LandMath.recommendLands(mapOf(U to 15.0, W to 15.0)))
    }

    @Test
    fun `pinned source counts from criterion 7`() {
        assertEquals(9, LandMath.sourcesFor(0.90, 9))
        assertEquals(8, LandMath.sourcesFor(0.89, 9))
    }

    @Test
    fun `sourcesFor is the smallest count that meets the target`() {
        val sources = LandMath.sourcesFor(0.90, 9)
        assertTrue(Hypergeometric.atLeast(40, sources, 9, 1) >= 0.90)
        assertTrue(Hypergeometric.atLeast(40, sources - 1, 9, 1) < 0.90)
        assertEquals(0, LandMath.sourcesFor(0.0, 9))
        assertEquals(40, LandMath.sourcesFor(1.0, 1))
        assertEquals(3, LandMath.sourcesFor(0.5, 5, deckSize = 20))
    }

    @Test
    fun `sourcesFor rejects bad input and unreachable targets`() {
        assertFailsWith<IllegalArgumentException> { LandMath.sourcesFor(1.5, 9) }
        assertFailsWith<IllegalArgumentException> { LandMath.sourcesFor(-0.1, 9) }
        assertFailsWith<IllegalArgumentException> { LandMath.sourcesFor(0.9, 41) }
        assertFailsWith<IllegalArgumentException> { LandMath.sourcesFor(0.9, 5, deckSize = -1) }
        assertFailsWith<IllegalArgumentException> { LandMath.sourcesFor(0.5, 0) }
    }

    @Test
    fun `a minor color in a two-color deck is raised to the minimum from the larger share`() {
        assertEquals(mapOf(R to 11, G to 6), LandMath.recommendLands(mapOf(R to 30.0, G to 1.0)))
        assertEquals(
            mapOf(R to 13, G to 4),
            LandMath.recommendLands(mapOf(R to 30.0, G to 1.0), minPerColor = 4),
        )
        // The minimum is capped at half the lands, so the larger share never ends up smaller.
        assertEquals(
            mapOf(R to 5, G to 4),
            LandMath.recommendLands(mapOf(R to 30.0, G to 1.0), totalLands = 9),
        )
    }

    @Test
    fun `one or three colors get no minimum`() {
        assertEquals(mapOf(G to 17), LandMath.recommendLands(mapOf(G to 12.0)))
        assertEquals(
            mapOf(W to 13, B to 2, R to 2),
            LandMath.recommendLands(mapOf(W to 20.0, B to 3.0, R to 3.0)),
        )
    }

    @Test
    fun `zero-weight colors get nothing and no weight at all gives an empty split`() {
        assertEquals(
            mapOf(R to 11, G to 6),
            LandMath.recommendLands(mapOf(R to 20.0, G to 10.0, U to 0.0)),
        )
        assertEquals(emptyMap(), LandMath.recommendLands(emptyMap()))
        assertEquals(emptyMap(), LandMath.recommendLands(mapOf(W to 0.0)))
    }

    @Test
    fun `recommendLands rejects bad input`() {
        assertFailsWith<IllegalArgumentException> { LandMath.recommendLands(mapOf(W to -1.0)) }
        assertFailsWith<IllegalArgumentException> {
            LandMath.recommendLands(mapOf(W to Double.NaN))
        }
        assertFailsWith<IllegalArgumentException> {
            LandMath.recommendLands(mapOf(W to 1.0), totalLands = -1)
        }
        assertFailsWith<IllegalArgumentException> {
            LandMath.recommendLands(mapOf(W to 1.0), minPerColor = -1)
        }
    }

    @Test
    fun `property - the split always sums to totalLands over 200 random pip maps`() {
        val random = Random(20261103)
        repeat(200) { run ->
            val colors = Color.entries.shuffled(random).take(random.nextInt(1, 6))
            val pips = colors.associateWith {
                random.nextInt(1, 40) / random.nextInt(1, 4).toDouble()
            }
            val totalLands =
                if (run % 2 == 0) LandMath.DEFAULT_TOTAL_LANDS else random.nextInt(0, 25)
            val split = LandMath.recommendLands(pips, totalLands)
            assertEquals(totalLands, split.values.sum(), "run $run: $pips into $totalLands")
            assertEquals(pips.keys, split.keys, "run $run: every weighted color is in the split")
            assertTrue(split.values.all { it >= 0 }, "run $run: $split")
            if (pips.size == 2) {
                val minimum = minOf(LandMath.DEFAULT_MIN_PER_COLOR, totalLands / 2)
                assertTrue(split.values.all { it >= minimum }, "run $run: $split")
            }
        }
    }
}
