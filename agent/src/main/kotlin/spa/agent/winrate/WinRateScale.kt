package spa.agent.winrate

import kotlin.math.sqrt

/**
 * Maps GIH WR onto the grade scale within one set: [mean] and [sd] are the mean and population
 * standard deviation of GIH WR over the set's cards with at least [MIN_GAMES] games.
 */
data class WinRateScale(val mean: Double, val sd: Double) {
    /**
     * `2.5 + (gihwr - mean) / sd`, clamped to 0 to 5 and rounded to the nearest 0.5 (a half step
     * rounds up). When every card has the same win rate ([sd] 0), every card scores 2.5.
     */
    fun score(gihwr: Double): Double {
        val raw = if (sd == 0.0) MIDDLE else MIDDLE + (gihwr - mean) / sd
        val clamped = raw.coerceIn(0.0, MAX)
        return Math.round(clamped / STEP) * STEP
    }

    companion object {
        /** A card with fewer games gets no grade. */
        const val MIN_GAMES = 500

        private const val MIDDLE = 2.5
        private const val MAX = 5.0
        private const val STEP = 0.5
        private const val PRIOR_GAMES = 2000
        private const val PLACES = 1000.0

        /** The scale of [cards], from those with at least [MIN_GAMES] games; null if none has. */
        fun of(cards: List<CardWinRate>): WinRateScale? {
            val rates = cards.filter { it.games >= MIN_GAMES }.map { it.gihwr }
            if (rates.isEmpty()) return null
            val mean = rates.average()
            val variance = rates.sumOf { (it - mean) * (it - mean) } / rates.size
            return WinRateScale(mean, sqrt(variance))
        }

        /** `games / (games + 2000)`, rounded to 3 places. */
        fun confidence(games: Int): Double =
            Math.round(games.toDouble() / (games + PRIOR_GAMES) * PLACES) / PLACES
    }
}
