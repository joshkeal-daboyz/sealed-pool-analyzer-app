package spa.agent.review

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import spa.core.baseline.Stat
import spa.core.brief.FormatBrief
import spa.core.cards.Face
import spa.core.cards.Pool
import spa.core.cards.Printing
import spa.core.cards.Rarity
import spa.core.metrics.MetricEngine
import spa.core.text.Color

class PoolBundleTest {
    private val dawnScout =
        ReviewTestCards.doubleFaced(
            Face(
                "Dawn Scout",
                "{1}{W}",
                2.0,
                setOf(Color.W),
                "Creature — Human",
                "At the beginning of your upkeep, you may transform Dawn Scout.",
                "2",
                "2",
            ),
            Face(
                "Dusk Raider",
                "",
                2.0,
                setOf(Color.B),
                "Creature — Werewolf",
                "Menace",
                "3",
                "3",
            ),
        )
    private val plains =
        ReviewTestCards.card(
            "Plains",
            cost = "",
            mv = 0.0,
            type = "Basic Land — Plains",
            text = "({T}: Add {W}.)",
            basic = true,
        )
    private val ghost = ReviewTestCards.card("Ghost Card", setOf(Color.U), "{U}", 1.0)
    private val twin = ReviewTestCards.card("Twin Printed", setOf(Color.R), "{R}", 1.0)
    private val lineBreaker =
        ReviewTestCards.card(
            "Line Breaker",
            setOf(Color.G),
            "{2}{G}",
            3.0,
            text = "Flying\nWhen it dies, draw a card.",
        )

    private val pool =
        Pool(
            listOf(
                ReviewTestCards.entry(twin, 1, Rarity.UNCOMMON, "twin-a"),
                ReviewTestCards.entry(dawnScout, 2, Rarity.RARE),
                ReviewTestCards.entry(plains, 4),
                ReviewTestCards.entry(ghost, 1, Rarity.MYTHIC),
                ReviewTestCards.entry(twin, 2, Rarity.COMMON, "twin-b"),
                ReviewTestCards.entry(lineBreaker, 1),
            )
        )

    private val grades: (Printing) -> Double? = { printing ->
        when (printing.card.name) {
            dawnScout.name -> 3.5
            "Plains" -> 0.5
            "Twin Printed" -> 4.0
            "Line Breaker" -> 5.0
            else -> null
        }
    }

    private fun input(
        brief: FormatBrief = ReviewTestCards.brief,
        pool: Pool = this.pool,
        withNumbers: Boolean = false,
        kit: Boolean = true,
    ) =
        ReviewInput(
            set = "xyz",
            pool = pool,
            metrics = if (withNumbers) ReviewTestCards.metrics() else MetricEngine.compute(pool),
            baseline =
                if (withNumbers) {
                    ReviewTestCards.baseline(mapOf("/totalCards" to Stat(15.0, 12.0, 18.0, 15.0)))
                } else {
                    null
                },
            brief = brief,
            gradeOf = grades,
            kitWarnings = if (kit) listOf(ReviewTestCards.kitWarning) else emptyList(),
        )

    private val expected =
        listOf(
                "# Pool bundle",
                "Set: xyz (Xyzzy), format speed: medium, brief version 3",
                "Speed notes: Games end around turn 8.",
                "Grades are 0.0 to 5.0 in half steps, relative to this set; 5.0 is the best.",
                "",
                "## Set mechanics",
                "- Dock: Attach to a ship.",
                "- Warp: Cast from exile.",
                "",
                "## Designed archetypes",
                "- WU Fliers: Win in the air. Signposts: Sky Marshal; Cloud Sage. " +
                    "Key commons: Wisp; Gull.",
                "- BR Sacrifice: Trade then drain. Signposts: Pyre Priest; Grave Imp.",
                "",
                "## Generic archetypes",
                "- Artifacts: Many artifacts.",
                "",
                "## Pool: 11 cards, 5 distinct",
                "2x Dawn Scout // Dusk Raider | {1}{W} // - | " +
                    "Creature — Human // Creature — Werewolf | rare | " +
                    "grade 3.5 (B+) | " +
                    "At the beginning of your upkeep, you may transform Dawn Scout. // Menace",
                "1x Ghost Card | {U} | Creature — Bear | mythic | grade none | ",
                "1x Line Breaker | {2}{G} | Creature — Bear | common | grade 5.0 (A+) | " +
                    "Flying / When it dies, draw a card.",
                "4x Plains | - | Basic Land — Plains | common | grade 0.5 (D−) | " +
                    "({T}: Add {W}.)",
                "3x Twin Printed | {R} | Creature — Bear | uncommon | grade 4.0 (A−) | ",
                "",
                "## Kit warnings",
                "- The pool has 80 cards; a kit holds 90.",
            )
            .joinToString("\n") + "\n"

    @Test
    fun `the bundle has the exact layout`() {
        assertEquals(expected, PoolBundle.text(input()))
    }

    @Test
    fun `empty sections print None and the text is the same each time`() {
        val brief =
            ReviewTestCards.brief.copy(mechanics = emptyList(), genericArchetypes = emptyList())
        val input = input(brief = brief, kit = false)
        val text = PoolBundle.text(input)
        assertEquals(text, PoolBundle.text(input))
        val lines = text.lines()
        assertEquals("None.", lines[lines.indexOf("## Set mechanics") + 1])
        assertEquals("None.", lines[lines.indexOf("## Generic archetypes") + 1])
        assertEquals("None.", lines[lines.indexOf("## Kit warnings") + 1])
        assertEquals("", lines.last())
        assertFalse(text.endsWith("\n\n"))
    }

    @Test
    fun `metrics and baseline do not change the bundle`() {
        val plain = PoolBundle.text(input())
        val loaded = PoolBundle.text(input(withNumbers = true))
        assertEquals(plain, loaded)
        assertFalse("/pairs/" in loaded)
        assertFalse("typical" in loaded)
    }

    @Test
    fun `an empty pool prints None in the pool section`() {
        val text = PoolBundle.text(input(pool = Pool(emptyList())))
        assertEquals(true, "## Pool: 0 cards, 0 distinct\nNone.\n" in text)
    }
}
