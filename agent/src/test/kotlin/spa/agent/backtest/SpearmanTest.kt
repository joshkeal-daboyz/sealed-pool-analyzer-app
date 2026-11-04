package spa.agent.backtest

import kotlin.math.sqrt
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

/** Criterion 1: Spearman's rho on pinned, hand-computed cases. */
class SpearmanTest {
    private fun rho(x: List<Double>, y: List<Double>): Double =
        requireNotNull(Spearman.rho(x, y)) { "rho is defined for $x and $y" }

    @Test
    fun `identical rankings give 1`() {
        val x = listOf(0.5, 1.0, 2.5, 3.0, 4.5)
        assertEquals(1.0, rho(x, x), 1e-12)
        // Same order, different values: rho reads only the order.
        assertEquals(1.0, rho(x, listOf(1.0, 2.0, 3.0, 4.0, 5.0)), 1e-12)
    }

    @Test
    fun `reversed rankings give -1`() {
        val x = listOf(1.0, 2.0, 3.0, 4.0, 5.0)
        assertEquals(-1.0, rho(x, x.reversed()), 1e-12)
    }

    @Test
    fun `ties take the average of the ranks they span`() {
        assertEquals(listOf(2.5, 1.0, 2.5, 4.0), Spearman.ranks(listOf(2.0, 1.0, 2.0, 3.0)))
        assertEquals(listOf(2.0, 2.0, 2.0), Spearman.ranks(listOf(3.5, 3.5, 3.5)))
        assertEquals(listOf(3.0, 1.0, 2.0), Spearman.ranks(listOf(5.0, 0.0, 2.5)))
        assertEquals(emptyList(), Spearman.ranks(emptyList()))
    }

    @Test
    fun `a case with ties is the Pearson correlation of the average ranks`() {
        // x = [1, 2, 2, 3] ranks [1, 2.5, 2.5, 4]; y = [1, 3, 2, 4] ranks [1, 3, 2, 4].
        // Both rank lists have mean 2.5. Deviations: x [-1.5, 0, 0, 1.5], y [-1.5, 0.5, -0.5, 1.5].
        // Sum of products 2.25 + 0 + 0 + 2.25 = 4.5; sum of squares x 4.5, y 5.
        // rho = 4.5 / sqrt(4.5 * 5) = sqrt(0.9) = 0.9487. The no-ties shortcut
        // 1 - 6 * sum(d^2) / (n (n^2 - 1)) would give 1 - 6 * 0.5 / 60 = 0.95 instead.
        val x = listOf(1.0, 2.0, 2.0, 3.0)
        val y = listOf(1.0, 3.0, 2.0, 4.0)
        assertEquals(sqrt(0.9), rho(x, y), 1e-12)
        assertEquals(sqrt(0.9), rho(y, x), 1e-12)
    }

    @Test
    fun `a case with ties on both sides`() {
        // x = [1, 1, 2, 3, 3] ranks [1.5, 1.5, 3, 4.5, 4.5]; y = [2, 1, 1, 3, 4] ranks
        // [3, 1.5, 1.5, 4, 5]. Means 3. Deviations x [-1.5, -1.5, 0, 1.5, 1.5],
        // y [0, -1.5, -1.5, 1, 2]. Products 0 + 2.25 + 0 + 1.5 + 3 = 6.75.
        // Squares x 9, y 0 + 2.25 + 2.25 + 1 + 4 = 9.5. rho = 6.75 / sqrt(85.5).
        val x = listOf(1.0, 1.0, 2.0, 3.0, 3.0)
        val y = listOf(2.0, 1.0, 1.0, 3.0, 4.0)
        assertEquals(6.75 / sqrt(85.5), rho(x, y), 1e-12)
    }

    @Test
    fun `a constant list has no rho`() {
        assertNull(Spearman.rho(listOf(2.5, 2.5, 2.5, 2.5), listOf(1.0, 2.0, 3.0, 4.0)))
        assertNull(Spearman.rho(listOf(1.0, 2.0, 3.0, 4.0), listOf(3.0, 3.0, 3.0, 3.0)))
    }

    @Test
    fun `fewer than two pairs have no rho`() {
        assertNull(Spearman.rho(emptyList(), emptyList()))
        assertNull(Spearman.rho(listOf(1.0), listOf(2.0)))
    }

    @Test
    fun `lists of different lengths are refused`() {
        assertFailsWith<IllegalArgumentException> {
            Spearman.rho(listOf(1.0, 2.0), listOf(1.0, 2.0, 3.0))
        }
    }
}
