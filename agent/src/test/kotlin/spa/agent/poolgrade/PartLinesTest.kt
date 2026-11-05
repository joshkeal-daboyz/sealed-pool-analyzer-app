package spa.agent.poolgrade

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test
import spa.agent.poolgrade.PoolGradeTestCards.baseline
import spa.agent.synergy.SynergyPartner
import spa.agent.synergy.SynergyTable
import spa.agent.synergy.SynergyTestCards
import spa.agent.themes.ThemeCard
import spa.core.cards.Rarity
import spa.core.metrics.ColorPair
import spa.core.metrics.Curve
import spa.core.text.Color

/**
 * SPA-043: each part writes its own reason line (the text SPA-038 printed, and the tag synergy line
 * of SPA-043's Background), and the tag synergy part on SPA-048's hand-built set `xyz`.
 */
class PartLinesTest {
    private val eps = 1e-9

    @Test
    fun `G-004's three parts write SPA-038's reason lines`() {
        assertEquals(
            "synergy +0.16: 8 partners in the pool, 6.07 expected (Spacecraft, Station)",
            SynergyPart(0.159, 8, 6.069, listOf("Spacecraft", "Station")).line,
        )
        assertEquals(
            "synergy −0.50: 1 partner in the pool, 1.66 expected (Spacecraft)",
            SynergyPart(-0.5, 1, 1.655, listOf("Spacecraft")).line,
        )
        assertEquals(
            "curve −0.14: 7 cards at mana value 4, 4.48 typical",
            CurvePart(-0.141, 4, 7, 4.478).line,
        )
        assertEquals(
            "curve +0.25: 1 card at mana value 7 or more, 2.00 typical",
            CurvePart(0.25, Curve.TOP, 1, 2.0).line,
        )
        assertEquals(
            "colour +0.12: Blue playables 20, typical 15.30",
            ColourPart(0.117, Color.U, 20, 15.3).line,
        )
        listOf(Color.W to "White", Color.B to "Black", Color.R to "Red", Color.G to "Green")
            .forEach { (colour, name) ->
                assertEquals(
                    "colour −0.10: $name playables 3, typical 4.00",
                    ColourPart(-0.1, colour, 3, 4.0).line,
                )
            }
    }

    @Test
    fun `a part at value 0 writes no line`() {
        assertNull(SynergyPart(0.0, 0, 0.0, emptyList()).line)
        assertNull(CurvePart(0.0, 2, 3, 3.0).line)
        assertNull(ColourPart(0.0, null, 0, 0.0).line)
        assertNull(TagSynergyPart(0.0, 0, 0.0, emptyList()).line)
    }

    @Test
    fun `the tag synergy line names the partners, or that there are none`() {
        assertEquals(
            "tag synergy +0.13: 26 with the pool, 20.69 typical (Hemmer, Spanner Jockey)",
            TagSynergyPart(
                    0.128333,
                    26,
                    20.69,
                    listOf(SynergyPartner("Hemmer", 1, 6), SynergyPartner("Spanner Jockey", 3, 2)),
                )
                .line,
        )
        assertEquals(
            "tag synergy −0.50: 0 with the pool, 3.03 typical (no partners in the pool)",
            TagSynergyPart(-0.5, 0, 3.034, emptyList()).line,
        )
    }

    @Test
    fun `the tag synergy part is SPA-048's part, read from the tags once`() {
        var loads = 0
        val tags = lazy {
            loads++
            TagInputs(SynergyTable(SynergyTestCards.TAGS), SynergyTestCards.SET)
        }
        val setCards = SynergyTestCards.SET.map { ThemeCard(it.card, Rarity.COMMON, emptyList()) }
        val weights = Weights.parse("""{ "tagSynergy": 0.5, "cap": 1 }""")
        val grader = PoolGrader(setCards, emptyList(), emptyMap(), baseline(), weights, tags)
        val pool = SynergyTestCards.pool("Twin" to 2, "Maker" to 1)

        val wu = grader.grade(pool, ColorPair.WU) { 2.0 }
        grader.grade(pool, ColorPair.WU) { 2.0 }
        val twin = wu.cards.single { it.printing.card.name == "Twin" }
        val part = twin.parts.filterIsInstance<TagSynergyPart>().single()

        // TagSynergyTest: Twin's syn 8, e 4.5, part 0.5 x (8 - 4.5) / 4.5.
        assertEquals(1, loads)
        assertEquals(8, part.syn)
        assertEquals(4.5, part.expected, eps)
        assertEquals(0.5 * (8 - 4.5) / 4.5, part.value, eps)
        assertEquals(2.0 + part.value, twin.adjusted, eps)
        assertEquals(listOf("Twin", "Maker"), part.partners.map { it.name })
        assertEquals("tag synergy +0.39: 8 with the pool, 4.50 typical (Twin, Maker)", part.line)
    }
}
