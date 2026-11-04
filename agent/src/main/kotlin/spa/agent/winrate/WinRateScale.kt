package spa.agent.winrate

/**
 * Maps GIH WR onto the grade scale within one set: [mean] and [sd] are the mean and population
 * standard deviation of GIH WR over the set's cards with at least [MIN_GAMES] games.
 */
data class WinRateScale(val mean: Double, val sd: Double) {
    /** `2.5 + (gihwr - mean) / sd`, clamped to 0 to 5 and rounded to the nearest 0.5. */
    fun score(gihwr: Double): Double = TODO()

    companion object {
        /** A card with fewer games gets no grade. */
        const val MIN_GAMES = 500

        /** The scale of [cards], from those with at least [MIN_GAMES] games; null if none has. */
        fun of(cards: List<CardWinRate>): WinRateScale? = TODO()

        /** `games / (games + 2000)`, rounded to 3 places. */
        fun confidence(games: Int): Double = TODO()
    }
}
