package spa.agent.build

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import spa.agent.build.BuildTestCards.R
import spa.agent.build.BuildTestCards.U
import spa.agent.build.BuildTestCards.W
import spa.core.math.Hypergeometric
import spa.core.text.Color

/** SPA-044 criterion 4: each part's formula, pinned on hand-built decks. */
class DeckPartsTest {
    private val weights = BuildTestCards.weights()

    private fun score(part: DeckPart, deck: Deck, weight: Double): DeckPartScore =
        part.score(deck, weight, weights.params(part))

    private fun near(expected: Double, actual: Double) =
        assertTrue(abs(expected - actual) < 1e-9, "expected $expected, got $actual")

    private fun copies(card: DeckCard, count: Int) = List(count) { card }

    private fun deckOf(
        spells: List<DeckCard>,
        config: String = "W",
        pairs: PairScorer = PairScorer.NONE,
    ): Deck {
        val setup =
            BuildTestCards.setup(
                BuildTestCards.candidates(spells.distinct(), quantity = 10),
                config,
                pairs = pairs,
            )
        return BuildTestCards.deck(setup, spells)
    }

    private fun w(name: String, mv: Int, grade: Double = 3.0, type: String = "Creature — Test") =
        BuildTestCards.spell(name, W, "{${mv - 1}}{W}", mv, grade, type)

    @Test
    fun `grades sums the spells' grades times the weight`() {
        val part = score(GradesDeckPart, deckOf(listOf(w("A", 2, 4.0), w("B", 3, 2.5))), 2.0)

        near(13.0, part.value)
        assertEquals("grades", part.key)
        assertTrue(part.reason!!.isNotBlank())
    }

    @Test
    fun `curve counts the gaps against the targets and the 6 or more bucket`() {
        // Targets 1:1 2:5 3:4 4:3 5:2 6:1. Counts: 1:1 (gap 0), 2:3 (2), 3:0 (4), 4:2 (1), 5:0
        // (2), 6 or more: a 7-drop (0). Gaps 9.
        val spells =
            listOf(w("A", 1)) + copies(w("B", 2), 3) + copies(w("C", 4), 2) + listOf(w("D", 7))

        near(-4.5, score(CurveDeckPart, deckOf(spells), 0.5).value)
    }

    @Test
    fun `curve has a gap in the 6 or more bucket when no card costs 6`() {
        // 2:5 + 3:4 + 4:3 + 5:2 + 6 or more:1; the 1-drop meets its target.
        near(-15.0, score(CurveDeckPart, deckOf(listOf(w("A", 1))), 1.0).value)
    }

    @Test
    fun `creatures takes off the weight per creature below the minimum`() {
        val creatures = (1..10).map { w("C$it", 2) }
        val sorcery = w("Spell", 2, type = "Sorcery")

        near(-6.0, score(CreaturesDeckPart, deckOf(creatures + sorcery), 1.5).value)
        near(0.0, score(CreaturesDeckPart, deckOf((1..14).map { w("D$it", 2) }), 1.5).value)
    }

    @Test
    fun `removal counts removal spells up to the cap`() {
        val removal =
            (1..8).map {
                BuildTestCards.spell(
                    "Bolt $it",
                    W,
                    "{W}",
                    1,
                    type = "Instant",
                    text = "Destroy target creature.",
                )
            }

        near(12.0, score(RemovalDeckPart, deckOf(removal), 2.0).value)
        near(6.0, score(RemovalDeckPart, deckOf(removal.take(3)), 2.0).value)
    }

    @Test
    fun `castability on a two colour deck weighs each spell by its grade and its odds`() {
        val white = copies(w("White", 2, grade = 4.0), 12)
        val blue = copies(BuildTestCards.spell("Blue", U, "{2}{U}", 3, grade = 2.0), 11)
        val deck = deckOf(white + blue, config = "WU")

        // 17 lands split by pips 12 to 11: 9 Plains and 8 Islands.
        assertEquals(mapOf(Color.W to 9, Color.U to 8), deck.basics)
        val pWhite = Hypergeometric.atLeast(40, 9, 6 + 2, 1)
        val pBlue = Hypergeometric.atLeast(40, 8, 6 + 3, 1)
        val expected = 12 * 4.0 * (1 - pWhite) + 11 * 2.0 * (1 - pBlue)

        near(-1.5 * expected, score(CastabilityDeckPart, deck, 1.5).value)
        near(pWhite, deck.castable(white.first()))
    }

    @Test
    fun `castability of a splash card counts its few sources`() {
        val base =
            copies(w("White", 2), 11) + copies(BuildTestCards.spell("Blue", U, "{2}{U}", 3), 11)
        val splash = BuildTestCards.spell("Splash", R, "{3}{R}", 4, grade = 4.0)
        val fixer =
            BuildTestCards.spell("Fixer", emptySet(), "{2}", 2, fixes = setOf(Color.R, Color.U))
        val deck = deckOf(base + splash + fixer, config = "WU/R")

        // Pips W 11, U 11, R 1 over 17 lands: 8, 8 and 1. One fixing spell adds floor(0.5) = 0.
        assertEquals(mapOf(Color.W to 8, Color.U to 8, Color.R to 1), deck.basics)
        assertEquals(1, deck.sources(Color.R))
        val p = Hypergeometric.atLeast(40, 1, 6 + 4, 1)
        near(p, deck.castable(splash))
        assertTrue(p < 0.5)
    }

    @Test
    fun `fixing spells add half a source each, rounded down`() {
        val fixers =
            (1..4).map {
                BuildTestCards.spell("F$it", emptySet(), "{2}", 2, fixes = setOf(Color.U))
            }
        val deck =
            deckOf(
                fixers + copies(w("W", 2), 5) + copies(BuildTestCards.spell("U", U, "{U}", 1), 5),
                "WU",
            )

        assertEquals(deck.basics.getValue(Color.U) + 2, deck.sources(Color.U))
    }

    private fun fixingDeck(config: String): Deck {
        val land = BuildTestCards.land("Any Land", Color.entries.toSet())
        val spell =
            BuildTestCards.spell("Two", emptySet(), "{2}", 2, fixes = setOf(Color.W, Color.U))
        val setup = BuildTestCards.setup(listOf(Candidate(land, 1), Candidate(spell, 1)), config)
        return BuildTestCards.deck(setup, listOf(spell))
    }

    @Test
    fun `fixing is 0 at two colours and larger at four than three`() {
        near(0.0, score(FixingDeckPart, fixingDeck("WU"), 1.0).value)
        // Three colours: the land supplies 3 of 3 (1 * 3/3), the spell 2 of 3 (1 * 2/3).
        near(1.0 + 2.0 / 3.0, score(FixingDeckPart, fixingDeck("WUB"), 1.0).value)
        // Four colours: the land 2 * 4/4, the spell 2 * 2/4.
        near(3.0, score(FixingDeckPart, fixingDeck("WUB/R"), 1.0).value)
        near(6.0, score(FixingDeckPart, fixingDeck("WUB/R"), 2.0).value)
    }

    @Test
    fun `fixing at weight 0 is off`() {
        val part = score(FixingDeckPart, fixingDeck("WUB"), 0.0)

        near(0.0, part.value)
        assertNull(part.reason)
    }

    @Test
    fun `synergy counts each pair of copies and the diagonal for a second copy`() {
        val a = w("A", 2)
        val b = w("B", 2)
        val deck = deckOf(listOf(a, a, b), pairs = { x, y -> if (x === y) 5 else 3 })

        // The pair of A's is the diagonal, 5; A with B twice, 3 each: 11, over 3 spells.
        near(0.6 * 11.0 / 3.0, score(SynergyDeckPart, deck, 0.6).value)
        assertEquals(11, deck.pairSum)
    }

    @Test
    fun `the land rule has three cases`() {
        val rule = LandRule(2.6, 3.4, 16, 17, 18)

        assertEquals(16, rule.count(2.6, 2))
        assertEquals(16, rule.count(2.0, 4))
        assertEquals(18, rule.count(3.4, 3))
        assertEquals(17, rule.count(3.4, 2))
        assertEquals(17, rule.count(3.0, 4))
        assertEquals(17, rule.count(2.61, 3))
    }
}
