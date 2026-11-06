package spa.agent.review.agents

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import spa.agent.review.NumberEntry
import spa.agent.review.NumberTables
import spa.agent.review.ReviewInput
import spa.agent.review.ReviewTestCards
import spa.agent.review.ReviewTestCards.card
import spa.agent.review.ReviewTestCards.entry
import spa.core.baseline.Baseline
import spa.core.baseline.Stat
import spa.core.cards.Pool
import spa.core.metrics.MetricEngine
import spa.core.text.Color

class ColourDepthAgentTest {
    private val red = setOf(Color.R)
    private val white = setOf(Color.W)

    private fun creature(name: String, colors: Set<Color>) =
        card(name, colors, "{1}{${colors.first().symbol}}")

    private val gleam = card("Gleam", setOf(Color.W, Color.R), "{W}{R}")
    private val bolt = card("Bolt", red, "{R}", type = "Sorcery", text = "Destroy target creature.")
    private val mountain = card("Mountain", cost = "", type = "Basic Land — Mountain")

    // 14 cards: 10 nonland (Gleam 1, Aegis 2, Nogr 1, Ash 1, Bolt 2, Cinder 1, Dust 1, Ember 1)
    // and 4 Mountains. Red holds Gleam 1 + Ash 1 + Bolt 2 + Cinder 1 + Dust 1 + Ember 1 = 7 cards,
    // of which 5 are creatures (Bolt is a sorcery).
    private val pool =
        Pool(
            listOf(
                entry(gleam, 1),
                entry(creature("Aegis", white), 2),
                entry(creature("Nogr", white), 1),
                entry(creature("Ash", red), 1),
                entry(bolt, 2),
                entry(creature("Cinder", red), 1),
                entry(creature("Dust", red), 1),
                entry(creature("Ember", red), 1),
                entry(mountain, 4),
            )
        )

    private val grades =
        mapOf(
            "Gleam" to 4.5,
            "Aegis" to 3.5,
            "Ash" to 4.0,
            "Bolt" to 4.0,
            "Cinder" to 3.0,
            "Dust" to 3.0,
            "Ember" to 3.0,
            "Mountain" to 5.0,
        )

    private val totalStat = Stat(median = 40.0, p10 = 38.0, p90 = 42.0, mean = 40.0)
    private val redStat = Stat(median = 6.0, p10 = 4.0, p90 = 8.0, mean = 6.0)
    private val baseline =
        ReviewTestCards.baseline(
            mapOf("/totalCards" to totalStat, "/byColor/R/cards/total" to redStat)
        )

    private fun input(baseline: Baseline? = this.baseline) =
        ReviewInput(
            "xyz",
            pool,
            MetricEngine.compute(pool),
            baseline,
            ReviewTestCards.brief,
            { grades[it.card.name] },
            emptyList(),
        )

    @Test
    fun `the agent has its id and title and always runs`() {
        assertEquals("colour-depth", ColourDepthAgent.id)
        assertEquals("Colour depth", ColourDepthAgent.title)
        assertNull(ColourDepthAgent.unavailable(input()))
        assertNull(ColourDepthAgent.unavailable(input(null)))
    }

    @Test
    fun `the numbers are the metrics for its three patterns with the baseline stats`() {
        val table = ColourDepthAgent.numbers(input())

        val byKey = table.entries.associateBy { it.key }
        assertEquals(NumberEntry("/totalCards", 14.0, totalStat), byKey["/totalCards"])
        assertEquals(NumberEntry("/nonlandCards", 10.0, null), byKey["/nonlandCards"])
        assertEquals(
            NumberEntry("/byColor/R/cards/total", 7.0, redStat),
            byKey["/byColor/R/cards/total"],
        )
        assertEquals(NumberEntry("/byColor/R/creatures", 5.0, null), byKey["/byColor/R/creatures"])
        assertEquals(
            NumberTables.fromMetrics(input(), listOf("/totalCards", "/nonlandCards", "/byColor/")),
            table,
        )
    }

    @Test
    fun `the facts list the best five graded cards of each bucket`() {
        val expected =
            """
            Best cards by colour (grade):
            W: Gleam 4.5, Aegis 3.5
            U: none
            B: none
            R: Gleam 4.5, Ash 4.0, Bolt 4.0, Cinder 3.0, Dust 3.0
            G: none
            MULTI: Gleam 4.5
            COLORLESS: none
            """
                .trimIndent()

        assertEquals(expected, ColourDepthAgent.facts(input()))
    }
}
