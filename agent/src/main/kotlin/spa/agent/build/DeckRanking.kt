package spa.agent.build

/**
 * A built deck in a ranking (SPA-045): the [config] it was built for, its [setup] and [scored]
 * deck, and the [sameAs] configurations that gave the same 40 cards and ranked lower.
 */
class RankedDeck(
    val config: BuildConfig,
    val setup: BuildSetup,
    val scored: ScoredDeck,
    val sameAs: List<BuildConfig>,
)

/**
 * Builds a deck for each configuration and ranks them (G-005 deliverable 3): by total score,
 * highest first, ties by the configuration's text. A configuration with too few candidates is left
 * out. Configurations that give the same 40 cards are one deck, the higher ranked one's.
 */
class DeckRanking(private val builder: DeckBuilder) {
    private class Built(val config: BuildConfig, val setup: BuildSetup, val scored: ScoredDeck)

    /** The ranked decks of [configs]; [setupFor] gives the [BuildSetup] of each. */
    fun rank(configs: List<BuildConfig>, setupFor: (BuildConfig) -> BuildSetup): List<RankedDeck> {
        val built =
            configs
                .distinct()
                .mapNotNull { config ->
                    val setup = setupFor(config)
                    builder.build(setup)?.let { Built(config, setup, it) }
                }
                .sortedWith(
                    compareByDescending<Built> { it.scored.total }.thenBy { it.config.toString() }
                )
        return built
            .groupBy { cards(it.scored.deck) }
            .values
            .map { same ->
                val best = same.first()
                RankedDeck(best.config, best.setup, best.scored, same.drop(1).map { it.config })
            }
    }

    /** The deck's 40 cards as text: the same copies of the same cards give the same text. */
    private fun cards(deck: Deck): String =
        listOf(
                deck.spells.map { it.id }.sorted(),
                deck.nonbasics.map { it.id }.sorted(),
                deck.basics.filterValues { it > 0 }.map { "${it.key}${it.value}" }.sorted(),
            )
            .joinToString("|")
}
