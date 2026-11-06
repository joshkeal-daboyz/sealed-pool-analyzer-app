package spa.agent.build

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import spa.agent.build.BuildTestCards.R
import spa.agent.build.BuildTestCards.U
import spa.agent.build.BuildTestCards.W

/** SPA-044 criterion 3: the search finds the known best deck of hand-built pools. */
class DeckSearchTest {
    private fun builder(vararg weights: Pair<String, Double>) =
        DeckBuilder(BuildTestCards.weights(*weights))

    private fun names(deck: ScoredDeck) = deck.deck.spells.map { it.card.name }

    /** No swap of one deck copy for one candidate copy raises the score. */
    private fun assertLocalOptimum(builder: DeckBuilder, setup: BuildSetup, scored: ScoredDeck) {
        val deck = scored.deck
        val used = deck.spells.groupingBy { it }.eachCount()
        for (out in used.keys) {
            for (candidate in setup.spells) {
                val into = candidate.card
                if (into === out || used.getOrDefault(into, 0) >= candidate.quantity) continue
                val swapped = deck.spells.toMutableList()
                swapped.remove(out)
                swapped.add(into)
                val total = builder.evaluate(setup, swapped, deck.landCount).total
                assertTrue(
                    total <= scored.total + 1e-9,
                    "swap ${out.card.name} for ${into.card.name}",
                )
            }
        }
    }

    @Test
    fun `two cards that clearly beat the rest by grade are in the deck`() {
        val bombs = BuildTestCards.whites("Bomb", 2, grade = 5.0)
        val fillers = BuildTestCards.whites("Filler", 25, grade = 2.0)
        val setup = BuildTestCards.setup(BuildTestCards.candidates(fillers + bombs))
        val builder = builder("grades" to 1.0)

        val built = assertNotNull(builder.build(setup))

        assertTrue(names(built).containsAll(listOf("Bomb 01", "Bomb 02")))
        assertEquals(23, built.deck.spells.size)
        assertEquals(17, built.deck.landCount)
        assertEquals(2 * 5.0 + 21 * 2.0, built.total, 1e-9)
        assertLocalOptimum(builder, setup, built)
    }

    @Test
    fun `a synergy pair beats a slightly higher graded loner`() {
        val base = BuildTestCards.whites("Base", 21, grade = 3.5)
        val pair =
            listOf(
                BuildTestCards.spell("Pair A", W, "{2}{W}", 3, grade = 3.0),
                BuildTestCards.spell("Pair B", W, "{2}{W}", 3, grade = 3.0),
            )
        val loner = BuildTestCards.spell("Loner", W, "{2}{W}", 3, grade = 3.2)
        val pairs = PairScorer { a, b ->
            if (a !== b && a.card.name.startsWith("Pair") && b.card.name.startsWith("Pair")) 10
            else 0
        }
        val setup =
            BuildTestCards.setup(BuildTestCards.candidates(base + pair + loner), pairs = pairs)

        val withSynergy = builder("grades" to 1.0, "synergy" to 1.0)
        val built = assertNotNull(withSynergy.build(setup))
        val gradesOnly = assertNotNull(builder("grades" to 1.0).build(setup))

        assertTrue(names(built).containsAll(listOf("Pair A", "Pair B")))
        assertTrue("Loner" !in names(built))
        assertTrue("Loner" in names(gradesOnly))
        assertLocalOptimum(withSynergy, setup, built)
    }

    @Test
    fun `castability drops a heavy splash card`() {
        val base =
            BuildTestCards.whites("White", 12, grade = 3.0) +
                (1..11).map { BuildTestCards.spell("Blue $it", U, "{2}{U}", 3, grade = 3.0) }
        val heavy = BuildTestCards.spell("Heavy", R, "{5}{R}", 6, grade = 4.5)
        val setup = BuildTestCards.setup(BuildTestCards.candidates(base + heavy), "WU/R")

        val without = assertNotNull(builder("grades" to 1.0).build(setup))
        val castable = builder("grades" to 1.0, "castability" to 1.0)
        val with = assertNotNull(castable.build(setup))

        assertTrue("Heavy" in names(without))
        assertTrue("Heavy" !in names(with))
        assertLocalOptimum(castable, setup, with)
    }

    @Test
    fun `a deck never has more copies than the pool, nor more splash cards than the limit`() {
        val base = BuildTestCards.whites("Base", 22, grade = 2.0)
        val bomb = BuildTestCards.spell("Bomb", W, "{2}{W}", 3, grade = 5.0)
        val splash = (1..5).map { BuildTestCards.spell("Splash $it", R, "{2}{R}", 3, grade = 4.0) }
        val heavySplash = BuildTestCards.spell("Double", R, "{R}{R}", 2, grade = 5.0)
        val setup =
            BuildTestCards.setup(
                BuildTestCards.candidates(base + splash + heavySplash) + Candidate(bomb, 3),
                "W/R",
            )

        val built = assertNotNull(builder("grades" to 1.0).build(setup))

        assertEquals(3, built.deck.spells.count { it === bomb })
        assertEquals(3, built.deck.spells.count { R == it.card.colors })
        assertTrue("Double" !in names(built))
        assertEquals(40, built.deck.size)
    }

    @Test
    fun `too few candidates give no deck`() {
        val setup =
            BuildTestCards.setup(BuildTestCards.candidates(BuildTestCards.whites("Few", 21)))

        assertNull(builder("grades" to 1.0).build(setup))
    }

    @Test
    fun `the same inputs give the same deck`() {
        val cards = BuildTestCards.whites("Card", 30, grade = 3.0)
        val setup = BuildTestCards.setup(BuildTestCards.candidates(cards))
        val builder = builder("grades" to 1.0, "curve" to 1.0)

        val first = assertNotNull(builder.build(setup))
        val second = assertNotNull(builder.build(setup))

        assertEquals(names(first), names(second))
        assertEquals(first.total, second.total)
    }

    @Test
    fun `land counts that cycle are settled on a deck the rule agrees with`() {
        // 23 spells without the 8-drop have mean 2.43 (16 lands), but 24 spells with it have mean
        // 2.67 (17 lands). A deck of 23 that includes it has mean 2.70 and agrees with 17 lands.
        val twos = (1..13).map { BuildTestCards.spell("Two $it", W, "{1}{W}", 2) }
        val threes = (1..10).map { BuildTestCards.spell("Three $it", W, "{2}{W}", 3) }
        val eight = BuildTestCards.spell("Eight", W, "{7}{W}", 8, grade = 1.0)
        val setup = BuildTestCards.setup(BuildTestCards.candidates(twos + threes + eight))
        val builder = builder("grades" to 1.0)

        val built = assertNotNull(builder.build(setup))

        val rule = BuildTestCards.weights().lands
        assertEquals(40, built.deck.size)
        assertEquals(rule.count(built.deck.meanManaValue, 1), built.deck.landCount)
        assertTrue("Eight" in names(built))
    }

    @Test
    fun `a deck plays the pool's fixing lands and fills the rest with basics`() {
        val land = BuildTestCards.land("Dual", setOf(spa.core.text.Color.W, spa.core.text.Color.U))
        val offLand =
            BuildTestCards.land("Wrong", setOf(spa.core.text.Color.B, spa.core.text.Color.R))
        val cards =
            BuildTestCards.whites("White", 12) +
                (1..12).map { BuildTestCards.spell("Blue $it", U, "{2}{U}", 3) }
        val setup =
            BuildTestCards.setup(
                BuildTestCards.candidates(cards) + Candidate(land, 1) + Candidate(offLand, 1),
                "WU",
            )

        val built = assertNotNull(builder("grades" to 1.0).build(setup))

        assertEquals(listOf(land), built.deck.nonbasics)
        assertEquals(built.deck.landCount - 1, built.deck.basics.values.sum())
        assertEquals(40, built.deck.size)
    }
}
