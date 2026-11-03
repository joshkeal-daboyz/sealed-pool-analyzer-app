package spa.core.math

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HypergeometricTest {
    private val tolerance = 0.0005

    @Test
    fun `pinned values from criterion 6`() {
        assertEquals(0.8948, Hypergeometric.atLeast(40, 17, 7, 2), tolerance)
        assertEquals(0.6493, Hypergeometric.atLeast(40, 17, 7, 3), tolerance)
        assertEquals(0.8974, Hypergeometric.atLeast(40, 8, 9, 1), tolerance)
        for (draws in 0..40) {
            assertEquals(0.0, Hypergeometric.atLeast(40, 0, draws, 1), "draws=$draws")
        }
    }

    @Test
    fun `values are exact ratios of binomial counts`() {
        // C(17,2)·C(23,5)/C(40,7) = 136·33649/18643560.
        assertEquals(136.0 * 33649 / 18643560, Hypergeometric.pmf(40, 17, 7, 2), 1e-15)
        // 1 - C(32,9)/C(40,9) = 1 - 28048800/273438880.
        assertEquals(1 - 28048800.0 / 273438880, Hypergeometric.atLeast(40, 8, 9, 1), 1e-15)
    }

    @Test
    fun `pmf sums to one and atLeast is the tail of pmf`() {
        val pmf = (0..7).map { Hypergeometric.pmf(40, 17, 7, it) }
        assertEquals(1.0, pmf.sum(), 1e-12)
        for (k in 0..8) {
            assertEquals(pmf.drop(k).sum(), Hypergeometric.atLeast(40, 17, 7, k), 1e-12, "k=$k")
        }
    }

    @Test
    fun `impossible outcomes are zero and trivial ones are one`() {
        assertEquals(0.0, Hypergeometric.pmf(40, 17, 7, -1))
        assertEquals(0.0, Hypergeometric.pmf(40, 17, 7, 8))
        assertEquals(0.0, Hypergeometric.pmf(40, 3, 7, 4)) // only 3 successes exist
        assertEquals(0.0, Hypergeometric.pmf(10, 8, 5, 2)) // at least 3 must be successes
        assertEquals(1.0, Hypergeometric.atLeast(40, 17, 7, 0))
        assertEquals(1.0, Hypergeometric.atLeast(40, 17, 7, -3))
        assertEquals(1.0, Hypergeometric.atLeast(40, 40, 7, 7))
        assertEquals(1.0, Hypergeometric.pmf(0, 0, 0, 0))
    }

    @Test
    fun `out-of-range arguments are rejected`() {
        assertFailsWith<IllegalArgumentException> { Hypergeometric.atLeast(-1, 0, 0, 0) }
        assertFailsWith<IllegalArgumentException> { Hypergeometric.atLeast(40, 41, 7, 1) }
        assertFailsWith<IllegalArgumentException> { Hypergeometric.atLeast(40, -1, 7, 1) }
        assertFailsWith<IllegalArgumentException> { Hypergeometric.pmf(40, 17, 41, 1) }
        assertFailsWith<IllegalArgumentException> { Hypergeometric.pmf(40, 17, -1, 1) }
    }
}
