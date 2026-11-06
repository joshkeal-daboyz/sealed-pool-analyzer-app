package spa.agent.review.agents

import java.util.Locale
import spa.agent.review.AnalysisAgent
import spa.agent.review.NumberEntry
import spa.agent.review.NumberTable
import spa.agent.review.NumberTables
import spa.agent.review.ReviewInput
import spa.core.cards.Rarity
import spa.core.metrics.ColorPair
import spa.core.text.Color

/** Which cards win games alone, and whether they justify a colour commitment or a splash. */
object BombAgent : AnalysisAgent {
    private const val MAX_CANDIDATES = 25
    private const val BOMB_GRADE = 4.0
    private val SPLASH_PATTERN = listOf("/pairs/*/splashCandidates/count")

    override val id: String = "bombs"
    override val title: String = "Bombs and splashes"

    /** A pool card worth naming: its [name], [grade], printing [rarity] and [colors]. */
    private data class Candidate(
        val name: String,
        val grade: Double,
        val rarity: Rarity,
        val colors: Set<Color>,
    )

    override fun numbers(input: ReviewInput): NumberTable {
        val grades =
            candidates(input).map { NumberEntry("/grades/${escape(it.name)}", it.grade, null) }
        return NumberTable(grades + NumberTables.fromMetrics(input, SPLASH_PATTERN).entries)
    }

    override fun facts(input: ReviewInput): String {
        val candidateLines =
            candidates(input).map { candidate ->
                val colours =
                    Color.entries
                        .filter { it in candidate.colors }
                        .joinToString("") { it.symbol.toString() }
                        .ifEmpty { "colourless" }
                "${candidate.name} (${candidate.rarity.scryfallName}, colours $colours)"
            }
        val splashLines =
            ColorPair.entries.mapNotNull { pair ->
                val splashes = input.metrics.pairs.getValue(pair).splashCandidates
                if (splashes.isEmpty()) {
                    null
                } else {
                    "Splash candidates ${pair.name}: " +
                        splashes.joinToString(", ") {
                            "${it.name} (${it.color.symbol}, " +
                                "${it.reason.name.lowercase(Locale.ROOT).replace('_', ' ')})"
                        }
                }
            }
        return (listOf("Candidates:") + candidateLines + splashLines).joinToString("\n")
    }

    override fun unavailable(input: ReviewInput): String? = null

    /**
     * The distinct nonland cards with a grade that are rare or mythic, or graded [BOMB_GRADE] or
     * more, best grade first then by name, at most [MAX_CANDIDATES]. A card in several printings
     * counts once, with its best qualifying grade.
     */
    private fun candidates(input: ReviewInput): List<Candidate> =
        input.pool.entries
            .filter { it.count > 0 && !it.printing.card.isLand }
            .mapNotNull { entry ->
                val printing = entry.printing
                val grade = input.gradeOf(printing) ?: return@mapNotNull null
                val strong = printing.rarity == Rarity.RARE || printing.rarity == Rarity.MYTHIC
                if (strong || grade >= BOMB_GRADE) {
                    Candidate(printing.card.name, grade, printing.rarity, printing.card.colors)
                } else {
                    null
                }
            }
            .groupBy { it.name }
            .values
            .map { same -> same.maxBy { it.grade } }
            .sortedWith(compareBy({ -it.grade }, { it.name }))
            .take(MAX_CANDIDATES)

    /** [name] as one JSON pointer segment: `~` as `~0`, `/` as `~1`. */
    private fun escape(name: String): String = name.replace("~", "~0").replace("/", "~1")
}
