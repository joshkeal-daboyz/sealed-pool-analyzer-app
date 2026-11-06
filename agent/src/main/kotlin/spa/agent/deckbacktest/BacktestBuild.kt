package spa.agent.deckbacktest

import spa.agent.build.BuildConfig
import spa.agent.deckpick.BuildCard
import spa.core.cards.Pool
import spa.core.cards.PoolEntry
import spa.core.text.Color

/**
 * One build of a strong player, a `(draft_id, build_index)` of a 17Lands Sealed file: its [main]
 * and [splash] colours and every card with a deck or sideboard count above 0, in the file column
 * order.
 */
data class BacktestBuild(
    val draftId: String,
    val buildIndex: String,
    val main: Set<Color>,
    val splash: Set<Color>,
    val cards: List<BuildCard>,
) {
    /** The build deck shape; null for one main colour or five. */
    val shape: DeckShape?
        get() = DeckShape.of(main, splash)

    /** What the player registered, as the measures compare it. */
    val player: Player
        get() =
            Player(
                main,
                splash,
                cards
                    .filter { !it.printing.card.isLand && it.deck > 0 }
                    .groupBy { it.printing.card.oracleId }
                    .mapValues { (_, copies) -> copies.sumOf { it.deck } },
                cards.filter { it.printing.card.isLand }.sumOf { it.deck },
            )

    /**
     * The player configuration, [main] and [splash]; null when they cannot be one (a splash colour
     * that is a main colour too, or more than [BuildConfig.MAX_SPLASH] splash colours).
     */
    val config: BuildConfig?
        get() =
            if (splash.size <= BuildConfig.MAX_SPLASH && splash.none { it in main }) {
                BuildConfig(main, splash)
            } else {
                null
            }

    /** True when some nonland card with a copy fits inside the player colours. */
    val hasChoice: Boolean
        get() = cards.any {
            val card = it.printing.card
            !card.isLand && (main + splash).containsAll(card.colors)
        }

    /** The build pool: every deck and sideboard copy that is not a basic land. */
    fun pool(): Pool =
        Pool(
            cards
                .filterNot { it.printing.card.isBasicLand }
                .map { PoolEntry(it.printing, it.deck + it.sideboard) }
        )
}
