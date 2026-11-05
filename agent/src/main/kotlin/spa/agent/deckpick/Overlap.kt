package spa.agent.deckpick

/** One candidate copy of a build: its ranking [score], and whether it is [inDeck]. */
data class RankedCopy(val score: Double, val inDeck: Boolean)

/** How many of a build's deck copies a ranking puts in its top N. */
object Overlap {
    /**
     * The deck copies among the top [n] of [copies], highest score first, over [n]. A group of
     * equal scores that straddles position [n] counts its deck copies times (places left / group
     * size), the expectation over its orders.
     */
    fun of(copies: List<RankedCopy>, n: Int): Double = TODO()

    /** [of] for [build]'s candidate copies, each scored by [score]. */
    fun of(build: Build, score: (BuildCard) -> Double): Double = TODO()

    /** A random ranking's expected overlap: N over the candidate copies. */
    fun random(build: Build): Double = TODO()
}
