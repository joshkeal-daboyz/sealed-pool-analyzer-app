package spa.agent.deckpick

import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import spa.agent.deckpick.DeckPickTestCards.dawnCleric
import spa.agent.deckpick.DeckPickTestCards.ironGolem
import spa.agent.deckpick.DeckPickTestCards.resolve
import spa.agent.deckpick.DeckPickTestCards.skyMarshal
import spa.agent.deckpick.DeckPickTestCards.sunKnight
import spa.agent.deckpick.DeckPickTestCards.tideCaller

/** Criterion 1: overlap, with ties broken by expectation, worked by hand. */
class OverlapTest {
    private val eps = 1e-12

    private fun deck(score: Double) = RankedCopy(score, inDeck = true)

    private fun side(score: Double) = RankedCopy(score, inDeck = false)

    @Test
    fun `with no ties the overlap is the deck copies in the top N over N`() {
        // Top 2: 5.0 deck, 4.0 sideboard -> 1 / 2.
        val copies = listOf(side(1.0), deck(5.0), deck(2.0), side(4.0))

        assertEquals(0.5, Overlap.of(copies, 2), eps)
    }

    @Test
    fun `a tie group straddling N counts its deck copies times places left over its size`() {
        // N = 3. 5.0 deck takes one place; the 3.0 group (deck, deck, sideboard, sideboard) has
        // 2 places left of its 4: 1 + 2 * 2/4 = 2, over 3.
        val copies = listOf(deck(3.0), side(3.0), deck(5.0), deck(3.0), side(3.0), side(1.0))

        assertEquals(2.0 / 3, Overlap.of(copies, 3), eps)
    }

    @Test
    fun `a tie group inside the top N counts in full and one just past it not at all`() {
        // N = 3: the 4.0 pair and the 2.0 deck copy fill it; the 1.0 group starts at place 4.
        val copies = listOf(deck(4.0), side(4.0), deck(2.0), deck(1.0), deck(1.0))

        assertEquals(2.0 / 3, Overlap.of(copies, 3), eps)
    }

    @Test
    fun `a build's copies are ranked by score with ties shared`() {
        // d1/0 of small-sealed.csv, N = 5. Scores: Sky Marshal 4.0 (deck); Sun Knight 3.0 x2
        // (deck) and Dawn Cleric 3.0 (sideboard), a group of 3 with 2 deck copies; Tide Caller
        // and Iron Golem 2.5, each one deck and one sideboard copy, a group of 4 with 2 deck
        // copies. 1 + 2 = 3 deck copies in 4 places; 1 place left of the 4: 3 + 2 * 1/4 = 3.5,
        // over 5 = 0.7.
        val build = small().builds.first()
        val scores =
            mapOf(
                skyMarshal to 4.0,
                sunKnight to 3.0,
                dawnCleric to 3.0,
                tideCaller to 2.5,
                ironGolem to 2.5,
            )

        assertEquals(0.7, Overlap.of(build) { scores.getValue(it.printing.card) }, eps)
    }

    @Test
    fun `a random ranking's overlap is N over the candidate copies`() {
        val (first, second) = small().builds

        assertEquals(5.0 / 8, Overlap.random(first), eps)
        assertEquals(4.0 / 6, Overlap.random(second), eps)
    }

    private fun small(): BuildSelection {
        val stream =
            requireNotNull(javaClass.getResourceAsStream("/spa/agent/deckpick/small-sealed.csv"))
        return stream.use { SealedBuilds.read(ByteArrayInputStream(it.readBytes()), resolve) }
    }
}
