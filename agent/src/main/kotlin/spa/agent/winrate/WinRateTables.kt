package spa.agent.winrate

import spa.agent.grade.GradeFile
import spa.core.cards.CardIndex

/**
 * A set's win-rate grade [file], the [scale] it used (null when no card had enough games), the
 * 17Lands names that match no card in the set ([unmatched]), and the matched cards with too few
 * games to grade ([ungraded]).
 */
data class WinRateTable(
    val file: GradeFile,
    val scale: WinRateScale?,
    val unmatched: List<String>,
    val ungraded: List<CardWinRate>,
)

/** Builds win-rate grade files from game data. */
object WinRateTables {
    /**
     * The table of [setCode] from [data]: each 17Lands name is matched to a card of [index] by name
     * (a double-faced card by its front face), the scale is taken over the matched cards with at
     * least [WinRateScale.MIN_GAMES] games, and each of those gets one `WIN_RATE` grade carrying
     * its GIH WR and games.
     */
    fun build(
        setCode: String,
        data: GameDataSummary,
        index: CardIndex,
        provenance: String,
        createdAt: String,
    ): WinRateTable = TODO()
}
