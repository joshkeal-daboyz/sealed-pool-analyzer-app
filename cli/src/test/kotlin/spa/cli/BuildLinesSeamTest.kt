package spa.cli

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import spa.agent.build.BuildConfig
import spa.agent.build.BuildSetup
import spa.agent.build.Candidate
import spa.agent.build.Deck
import spa.agent.build.DeckBuilder
import spa.agent.build.DeckCard
import spa.agent.build.DeckPart
import spa.agent.build.DeckPartScore
import spa.agent.build.DeckParts
import spa.agent.build.DeckWeights
import spa.agent.build.PairScorer
import spa.agent.build.PartParams
import spa.agent.build.SplashRule
import spa.core.cards.Card
import spa.core.cards.Face
import spa.core.cards.Rarity
import spa.core.text.Color

/**
 * SPA-044 criterion 5, the printed side of the seam: a test-only part's score and reason appear in
 * the printout with no change to [BuildLines].
 */
class BuildLinesSeamTest {
    private object RedBonus : DeckPart {
        override val key = "redBonus"

        override fun score(deck: Deck, weight: Double, params: PartParams): DeckPartScore {
            val red = deck.spells.count { Color.R in it.card.colors }
            return DeckPartScore(key, weight * red, "$red red spells, test only")
        }
    }

    private fun card(name: String, colour: Color, grade: Double): DeckCard {
        val colours = setOf(colour)
        val face =
            Face(name, "{2}{${colour.symbol}}", 3.0, colours, "Creature — Test", "", null, null)
        val card =
            Card(
                "o-$name",
                name,
                "normal",
                listOf(face),
                colours,
                colours,
                3.0,
                "Creature — Test",
                true,
                false,
                false,
                Rarity.COMMON,
            )
        return DeckCard("t/$name", card, grade)
    }

    @Test
    fun `a test only part appears in the printout with its reason`() {
        val white = (1..24).map { card("White %02d".format(it), Color.W, 3.0) }
        val red = (1..2).map { card("Red $it", Color.R, 2.5) }
        val pairs = PairScorer { a, b ->
            if (a !== b && a.card.name.startsWith("Red") && b.card.name.startsWith("Red")) 7 else 0
        }
        val setup =
            BuildSetup(
                BuildConfig.parse("W/R"),
                SplashRule(3, 1.0),
                (white + red).map { Candidate(it, 1) },
                pairs,
            )
        val weights =
            DeckWeights(
                mapOf("grades" to 1.0, RedBonus.key to 3.0),
                emptyMap(),
                spa.agent.build.LandRule(2.6, 3.4, 16, 17, 18),
                SplashRule(3, 1.0),
            )

        val plain = assertNotNull(DeckBuilder(weights).build(setup))
        val extended = assertNotNull(DeckBuilder(weights, DeckParts.ALL + RedBonus).build(setup))
        val plainLines = BuildLines.lines(setup, plain)
        val lines = BuildLines.lines(setup, extended)

        assertTrue(plainLines.none { "redBonus" in it })
        assertTrue(
            lines.any { it.startsWith("  redBonus +6.000: 2 red spells, test only") },
            lines.joinToString("\n"),
        )
        assertEquals("Deck W/R: score ${"%.3f".format(extended.total)}", lines.first())
        assertTrue(
            lines.any { it.contains("Red 1") && it.contains("pairs with Red 2 (7)") },
            lines.joinToString("\n"),
        )
        assertTrue(lines.any { it.startsWith("Lands (17):") })
    }
}
