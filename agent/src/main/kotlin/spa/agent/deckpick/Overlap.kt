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
    fun of(copies: List<RankedCopy>, n: Int): Double {
        require(n > 0) { "N must be above 0, got $n" }
        // + 0.0 makes -0.0 the same score as 0.0.
        val groups = copies.groupBy { it.score + 0.0 }.toSortedMap(reverseOrder()).values
        var taken = 0
        var deck = 0.0
        for (group in groups) {
            val left = n - taken
            if (left <= 0) break
            val inDeck = group.count { it.inDeck }
            deck +=
                if (group.size <= left) inDeck.toDouble() else inDeck.toDouble() * left / group.size
            taken += group.size
        }
        return deck / n
    }

    /** [of] for [build]'s candidate copies, each scored by [score]. */
    fun of(build: Build, score: (BuildCard) -> Double): Double =
        of(
            build.candidates.flatMap { card ->
                val value = score(card)
                List(card.deck) { RankedCopy(value, true) } +
                    List(card.sideboard) { RankedCopy(value, false) }
            },
            build.n,
        )

    /** A random ranking's expected overlap: N over the candidate copies. */
    fun random(build: Build): Double = build.n.toDouble() / build.candidateCopies
}
