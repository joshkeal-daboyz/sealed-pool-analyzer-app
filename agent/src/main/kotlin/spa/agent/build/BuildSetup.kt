package spa.agent.build

import java.util.IdentityHashMap

/**
 * One build's inputs (SPA-044): the [config], the [splash] limits, and the pool's cards as
 * [Candidate]s (nonbasic lands included, basics left out), with the synergy [pairs] scorer.
 *
 * A nonland card is a legal [spells] candidate when its colours fit inside the deck's, and, if it
 * has a splash colour, it has at most [SplashRule.maxPips] pips of splash colours. The others are
 * the [rest]. A nonbasic land is a [lands] candidate when it can supply two or more of the deck's
 * colours; the others are [unplayedLands].
 */
class BuildSetup(
    val config: BuildConfig,
    val splash: SplashRule,
    pool: List<Candidate>,
    private val pairs: PairScorer = PairScorer.NONE,
) {
    private val byCard = Comparator<Candidate> { a, b -> DeckCard.ORDER.compare(a.card, b.card) }

    /** The legal spells, in collector order. */
    val spells: List<Candidate> =
        pool.filter { !it.card.card.isLand && legal(it.card) }.sortedWith(byCard)

    /** The nonland cards that cannot be in this deck, in collector order. */
    val rest: List<Candidate> =
        pool.filter { !it.card.card.isLand && !legal(it.card) }.sortedWith(byCard)

    /** The nonbasic lands that can supply two or more of the deck's colours, in play order. */
    val lands: List<Candidate> =
        pool
            .filter { it.card.card.isLand && supply(it.card) >= MIN_SUPPLY }
            .sortedWith(compareByDescending<Candidate> { supply(it.card) }.then(byCard))

    /** The other nonbasic lands. */
    val unplayedLands: List<Candidate> =
        pool.filter { it.card.card.isLand && supply(it.card) < MIN_SUPPLY }.sortedWith(byCard)

    /**
     * The most spell copies a deck can play: every copy of the legal spells with no splash colour,
     * and at most [SplashRule.maxCards] of the splash ones.
     */
    val capacity: Int =
        spells.filter { !it.card.isSplash(config) }.sumOf { it.quantity } +
            minOf(splash.maxCards, spells.filter { it.card.isSplash(config) }.sumOf { it.quantity })
    /** Every copy of [lands], in play order. */
    val landCopies: List<DeckCard> = lands.flatMap { c -> List(c.quantity) { c.card } }

    private val slots = IdentityHashMap<DeckCard, Int>()
    private val matrix: IntArray

    init {
        spells.forEachIndexed { i, c -> slots[c.card] = i }
        val n = spells.size
        matrix = IntArray(n * n)
        for (i in 0 until n) {
            for (j in i until n) {
                val score = pairs.score(spells[i].card, spells[j].card)
                matrix[i * n + j] = score
                matrix[j * n + i] = score
            }
        }
    }

    /** The synergy score of [a] with [b] (the same card for the diagonal). */
    fun pair(a: DeckCard, b: DeckCard): Int {
        val i = slots[a]
        val j = slots[b]
        return if (i == null || j == null) pairs.score(a, b) else matrix[i * spells.size + j]
    }

    /** How many of the deck's colours [card] can supply. */
    private fun supply(card: DeckCard): Int =
        card.fixing?.colours?.count { it in config.colours } ?: 0

    private fun legal(card: DeckCard): Boolean =
        card.card.colors.all { it in config.colours } &&
            (!card.isSplash(config) || card.splashPips(config) <= splash.maxPips)

    private companion object {
        const val MIN_SUPPLY = 2
    }
}
