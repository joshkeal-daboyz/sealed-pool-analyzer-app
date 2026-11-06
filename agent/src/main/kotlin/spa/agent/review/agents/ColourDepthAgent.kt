package spa.agent.review.agents

import java.util.Locale
import spa.agent.review.AnalysisAgent
import spa.agent.review.NumberTable
import spa.agent.review.NumberTables
import spa.agent.review.ReviewInput
import spa.core.metrics.ColorBucket

/** Which colours are deep enough to play, and where one is thin, against the set baseline. */
object ColourDepthAgent : AnalysisAgent {
    private const val BEST = 5
    private val PATTERNS = listOf("/totalCards", "/nonlandCards", "/byColor/")

    override val id: String = "colour-depth"
    override val title: String = "Colour depth"

    override fun numbers(input: ReviewInput): NumberTable =
        NumberTables.fromMetrics(input, PATTERNS)

    /** The best [BEST] graded nonland cards of each colour bucket, one line per bucket. */
    override fun facts(input: ReviewInput): String {
        val graded =
            input.pool.entries
                .filter { it.count > 0 && !it.printing.card.isLand }
                .mapNotNull { entry ->
                    input.gradeOf(entry.printing)?.let { entry.printing.card to it }
                }
                .distinctBy { (card, grade) -> card.name to grade }
        val lines =
            ColorBucket.entries.map { bucket ->
                val best =
                    graded
                        .filter { (card, _) -> bucket.holds(card.colors) }
                        .sortedWith(compareBy({ -it.second }, { it.first.name }))
                        .take(BEST)
                val text =
                    if (best.isEmpty()) {
                        "none"
                    } else {
                        best.joinToString(", ") { (card, grade) ->
                            "${card.name} ${String.format(Locale.ROOT, "%.1f", grade)}"
                        }
                    }
                "${bucket.name}: $text"
            }
        return (listOf("Best cards by colour (grade):") + lines).joinToString("\n")
    }

    override fun unavailable(input: ReviewInput): String? = null
}
