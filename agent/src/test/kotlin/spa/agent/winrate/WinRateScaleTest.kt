package spa.agent.winrate

import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Criterion 2: the mapping from GIH WR to the grade scale, worked by hand. */
class WinRateScaleTest {
    /**
     * The set: two cards with 1,000 games at 0.50 and 0.60, and one at 0.90 with 499 games, which
     * is under the 500-game cutoff and so left out of the scale.
     *
     * m = (0.50 + 0.60) / 2 = 0.55. s = sqrt(((0.50 - 0.55)^2 + (0.60 - 0.55)^2) / 2) =
     * sqrt((0.0025 + 0.0025) / 2) = 0.05 (population, dividing by n = 2, not n - 1).
     */
    private val set =
        listOf(
            CardWinRate("Low", games = 1000, wins = 500),
            CardWinRate("High", games = 1000, wins = 600),
            CardWinRate("Few games", games = 499, wins = 449),
        )

    private val scale = requireNotNull(WinRateScale.of(set))

    @Test
    fun `the scale is the mean and population standard deviation over cards with 500+ games`() {
        assertEquals(0.55, scale.mean, 1e-12)
        assertEquals(0.05, scale.sd, 1e-12)
    }

    @Test
    fun `a card at m scores 2_5`() {
        // 2.5 + (0.55 - 0.55) / 0.05 = 2.5 + 0 = 2.5
        assertEquals(2.5, scale.score(0.55))
    }

    @Test
    fun `a card at m + s scores 3_5`() {
        // 2.5 + (0.60 - 0.55) / 0.05 = 2.5 + 1 = 3.5
        assertEquals(3.5, scale.score(0.60))
    }

    @Test
    fun `a card at m - 3s is clamped to 0`() {
        // 2.5 + (0.40 - 0.55) / 0.05 = 2.5 - 3 = -0.5, clamped to 0.0
        assertEquals(0.0, scale.score(0.40))
    }

    @Test
    fun `a card at m + 0_74s rounds down to 3_0`() {
        // 2.5 + (0.587 - 0.55) / 0.05 = 2.5 + 0.74 = 3.24, nearest 0.5 is 3.0
        assertEquals(3.0, scale.score(0.587))
    }

    @Test
    fun `a card far above m is clamped to 5`() {
        // 2.5 + (0.90 - 0.55) / 0.05 = 2.5 + 7 = 9.5, clamped to 5.0
        assertEquals(5.0, scale.score(0.90))
    }

    @Test
    fun `a card under 500 games is left out of the scale`() {
        // With the 499-game card at 0.90 counted, m would be 0.6667, not 0.55.
        assertEquals(WinRateScale.of(set.take(2)), scale)
    }

    @Test
    fun `a set with no card at 500 games has no scale`() {
        assertNull(WinRateScale.of(listOf(CardWinRate("Few games", 499, 449))))
    }

    @Test
    fun `confidence is games over games plus 2000, to 3 places`() {
        // 1000 / 3000 = 0.3333 -> 0.333; 500 / 2500 = 0.2; 2000 / 4000 = 0.5;
        // 30000 / 32000 = 0.9375 -> 0.938 (half rounds up)
        assertEquals(0.333, WinRateScale.confidence(1000))
        assertEquals(0.2, WinRateScale.confidence(500))
        assertEquals(0.5, WinRateScale.confidence(2000))
        assertEquals(0.938, WinRateScale.confidence(30000))
    }

    @Test
    fun `cards all at one win rate score 2_5`() {
        val flat = requireNotNull(WinRateScale.of(listOf(CardWinRate("A", 600, 300))))
        assertEquals(0.0, flat.sd)
        assertEquals(2.5, flat.score(0.5))
    }

    @Test
    fun `the standard deviation divides by n`() {
        // Three cards at 0.4, 0.5, 0.6: m = 0.5, s = sqrt((0.01 + 0 + 0.01) / 3) = 0.08165
        val cards =
            listOf(
                CardWinRate("A", 500, 200),
                CardWinRate("B", 500, 250),
                CardWinRate("C", 500, 300),
            )
        val three = requireNotNull(WinRateScale.of(cards))
        assertEquals(sqrt(0.02 / 3), three.sd, 1e-12)
    }
}
