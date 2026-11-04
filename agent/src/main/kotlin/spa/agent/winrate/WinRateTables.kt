package spa.agent.winrate

import spa.agent.grade.GradeEntry
import spa.agent.grade.GradeFile
import spa.agent.grade.GradeFileHeader
import spa.agent.grade.GradeSource
import spa.core.cards.CardIndex
import spa.core.cards.Printing

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
    /** GIH WR is kept to 6 places in the file, far finer than any sample supports. */
    private const val GIHWR_PLACES = 1_000_000.0

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
    ): WinRateTable {
        val matched = mutableListOf<Pair<CardWinRate, Printing>>()
        val unmatched = mutableListOf<String>()
        for (rate in data.cards) {
            val printing = printing(index, setCode, rate.name)
            if (printing == null) unmatched += rate.name else matched += rate to printing
        }
        val scale = WinRateScale.of(matched.map { it.first })
        val (graded, ungraded) = matched.partition { it.first.games >= WinRateScale.MIN_GAMES }
        val grades =
            if (scale == null) emptyList()
            else graded.map { (rate, printing) -> entry(rate, printing, scale) }
        val header = GradeFileHeader(setCode, GradeSource.WIN_RATE, provenance, createdAt)
        return WinRateTable(GradeFile(header, grades), scale, unmatched, ungraded.map { it.first })
    }

    /** The main set's first printing of the card named [name], else its first printing at all. */
    private fun printing(index: CardIndex, setCode: String, name: String): Printing? {
        val card = index.byName(name) ?: return null
        return index.printings(setCode).firstOrNull { it.card.oracleId == card.oracleId }
            ?: index.printings.firstOrNull { it.card.oracleId == card.oracleId }
    }

    private fun entry(rate: CardWinRate, printing: Printing, scale: WinRateScale) =
        GradeEntry(
            name = printing.card.name,
            collectorNumber = printing.collectorNumber,
            setCode = printing.setCode,
            score = scale.score(rate.gihwr),
            confidence = WinRateScale.confidence(rate.games),
            gihwr = Math.round(rate.gihwr * GIHWR_PLACES) / GIHWR_PLACES,
            games = rate.games,
        )
}
