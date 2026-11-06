package spa.agent.deckbacktest

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import spa.core.text.Color

/**
 * SPA-046 criterion 2: each measure and its edge, on hand-built per-build data with no builder in
 * the loop. A deck is written `<main>[/<splash>]`, such as `WU` or `WU/R`.
 */
class BacktestMeasuresTest {
    private fun colours(text: String): Set<Color> = text.mapNotNull { Color.fromSymbol(it) }.toSet()

    private fun ranked(vararg decks: String): List<RankedColours> =
        decks.map { RankedColours(colours(it.substringBefore('/')), colours(it.substringAfter('/', ""))) }

    private fun player(
        deck: String,
        spells: Map<String, Int> = emptyMap(),
        lands: Int = 17,
    ): Player =
        Player(colours(deck.substringBefore('/')), colours(deck.substringAfter('/', "")), spells, lands)

    private fun outcome(
        player: String,
        vararg decks: String,
        spells: Map<String, Int> = emptyMap(),
        lands: Int = 17,
        built: BuiltDeck? = null,
    ) = BuildOutcome(player(player, spells, lands), ranked(*decks), built)

    @Test
    fun `colour agreement is set equality of the top deck's main colours`() {
        assertEquals(1.0, ColourAgreement.value(outcome("WU", "UW")))
        assertEquals(1.0, ColourAgreement.value(outcome("WU/R", "UW/B")))
        assertEquals(0.0, ColourAgreement.value(outcome("WU", "WB", "WU")))
        assertEquals(0.0, ColourAgreement.value(outcome("WU", "WUB")))
        assertEquals(0.0, ColourAgreement.value(outcome("WU")))
    }

    @Test
    fun `top three counts the player's colours at rank 3 but not at rank 4`() {
        assertEquals(1.0, TopThree.value(outcome("WU", "WB", "BR", "UW")))
        assertEquals(0.0, TopThree.value(outcome("WU", "WB", "BR", "BG", "UW")))
        assertEquals(1.0, TopThree.value(outcome("WU", "UW")))
        assertEquals(0.0, TopThree.value(outcome("WU", "WB", "WUB")))
        assertEquals(0.0, TopThree.value(outcome("WU")))
    }

    @Test
    fun `splash agreement is whether the top deck splashes exactly when the player did`() {
        assertEquals(1.0, SplashAgreement.value(outcome("WU", "WU")))
        assertEquals(1.0, SplashAgreement.value(outcome("WU/R", "WB/G")))
        assertEquals(0.0, SplashAgreement.value(outcome("WU/R", "WU")))
        assertEquals(0.0, SplashAgreement.value(outcome("WU", "WU/R")))
        assertEquals(0.0, SplashAgreement.value(outcome("WU")))
    }

    @Test
    fun `overlap sums the smaller copy counts and ignores lands`() {
        val registered = mapOf("a" to 2, "b" to 4, "d" to 5)
        val built = BuiltDeck(mapOf("a" to 3, "b" to 1, "c" to 2), lands = 17)

        val withLands = outcome("WU", "WU", spells = registered, lands = 12, built = built)
        val moreLands =
            outcome("WU", "WU", spells = registered, lands = 3, built = BuiltDeck(built.spells, 15))

        assertEquals(3.0, MainDeckOverlap.value(withLands))
        assertEquals(3.0, MainDeckOverlap.value(moreLands))
        assertEquals(0.0, MainDeckOverlap.value(outcome("WU", "WU", built = built)))
    }

    @Test
    fun `land count is within one of the player's`() {
        fun lands(player: Int, built: Int) =
            LandCount.value(outcome("WU", "WU", lands = player, built = BuiltDeck(emptyMap(), built)))

        assertEquals(1.0, lands(17, 17))
        assertEquals(1.0, lands(17, 16))
        assertEquals(1.0, lands(17, 18))
        assertEquals(0.0, lands(17, 15))
        assertEquals(0.0, lands(17, 19))
    }

    @Test
    fun `an unbuildable build is left out of overlap and land count`() {
        val unbuildable = outcome("WU", "WU", spells = mapOf("a" to 1))

        assertNull(MainDeckOverlap.value(unbuildable))
        assertNull(LandCount.value(unbuildable))
        assertEquals(1.0, ColourAgreement.value(unbuildable))
    }

    @Test
    fun `a rate is the share of the builds the measure applies to that hit`() {
        val built = BuiltDeck(emptyMap(), 17)
        val outcomes =
            listOf(
                outcome("WU", "WU", built = built),
                outcome("WU", "WB", built = built),
                outcome("WU", "WU"),
            )

        assertEquals("colour 66.7", ColourAgreement.cell(outcomes))
        assertEquals("lands 100.0", LandCount.cell(outcomes))
        assertEquals("colour -", ColourAgreement.cell(emptyList()))
        assertEquals("lands -", LandCount.cell(listOf(outcomes.last())))
    }

    @Test
    fun `the overlap cell gives the mean overlap of the buildable builds and their registered cards`() {
        val registered = mapOf("a" to 2, "b" to 3)
        val outcomes =
            listOf(
                outcome("WU", "WU", spells = registered, built = BuiltDeck(mapOf("a" to 1), 17)),
                outcome(
                    "WU",
                    "WU",
                    spells = mapOf("a" to 4),
                    built = BuiltDeck(mapOf("a" to 4, "b" to 1), 17),
                ),
                outcome("WU", "WU", spells = mapOf("z" to 30)),
            )

        assertEquals("overlap 2.50 of 4.50", MainDeckOverlap.cell(outcomes))
        assertEquals("overlap - of -", MainDeckOverlap.cell(emptyList()))
        assertEquals("overlap - of -", MainDeckOverlap.cell(listOf(outcomes.last())))
    }

    @Test
    fun `the groups' counts add up to overall`() {
        val outcomes =
            listOf(
                outcome("WU", "WU"),
                outcome("UB", "UB"),
                outcome("WU/R", "WU"),
                outcome("WUB", "WU"),
                outcome("WUBR/G", "WU"),
                outcome("UB", "WU"),
            )

        val groups = BacktestReport.groups(outcomes)

        assertEquals(listOf("overall", "two", "splash", "multi"), groups.keys.toList())
        assertEquals(listOf(6, 3, 1, 2), groups.values.map { it.size })
        assertEquals(groups.getValue("overall").size, groups.filterKeys { it != "overall" }.values.sumOf { it.size })
    }
}
