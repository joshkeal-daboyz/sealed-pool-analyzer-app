package spa.agent.grader

import kotlin.test.Test
import kotlin.test.assertEquals
import spa.agent.grader.GraderTestCards.card
import spa.agent.grader.GraderTestCards.printing
import spa.core.cards.CardIndex
import spa.core.cards.Rarity

/** Criterion 1: the sealed pool the packet holds, one entry per distinct card, in order. */
class GradingPoolTest {
    private val alpha = card("Alpha")
    private val beta = card("Beta")
    private val gamma = card("Gamma")
    private val reprint = card("Reprint")
    private val forest = card("Forest", basic = true)

    @Test
    fun `a showcase printing of a card already in the pool is left out`() {
        val index =
            CardIndex(
                listOf("xyz"),
                listOf(
                    printing(alpha, "xyz", "1"),
                    printing(beta, "xyz", "2"),
                    printing(alpha, "xyz", "301", promoTypes = listOf("showcase")),
                ),
            )

        assertEquals(listOf("xyz/1", "xyz/2"), GradingPool.printings(index).map(GradingPool::id))
    }

    @Test
    fun `a variant with a lower number than the loaded order still loses to collector order`() {
        // Loaded showcase first; collector order puts 1 first, so 1 represents the card.
        val index =
            CardIndex(
                listOf("xyz"),
                listOf(printing(alpha, "xyz", "301"), printing(alpha, "xyz", "1")),
            )

        assertEquals(listOf("xyz/1"), GradingPool.printings(index).map(GradingPool::id))
    }

    @Test
    fun `basic lands are excluded`() {
        val index =
            CardIndex(
                listOf("xyz"),
                listOf(printing(alpha, "xyz", "1"), printing(forest, "xyz", "2")),
            )

        assertEquals(listOf("xyz/1"), GradingPool.printings(index).map(GradingPool::id))
    }

    @Test
    fun `cards are in collector order within each set, the main set before companions`() {
        val index =
            CardIndex(
                listOf("xyz", "cmp"),
                listOf(
                    printing(gamma, "xyz", "10"),
                    printing(beta, "xyz", "9"),
                    printing(reprint, "cmp", "1"),
                    printing(alpha, "xyz", "101b"),
                    printing(card("Delta"), "xyz", "101"),
                    // A companion's printing of a main-set card is the same card: left out.
                    printing(beta, "cmp", "2"),
                ),
            )

        assertEquals(
            listOf("xyz/9", "xyz/10", "xyz/101", "xyz/101b", "cmp/1"),
            GradingPool.printings(index).map(GradingPool::id),
        )
    }

    @Test
    fun `the representative printing keeps its own rarity`() {
        val index =
            CardIndex(listOf("xyz"), listOf(printing(alpha, "xyz", "1", rarity = Rarity.RARE)))

        assertEquals(Rarity.RARE, GradingPool.printings(index).single().rarity)
    }

    @Test
    fun `collector order compares the leading number, then the rest`() {
        val sorted =
            listOf("10", "9", "1701D", "101b", "101", "1", "A5", "1701")
                .sortedWith(GradingPool.collectorOrder)

        assertEquals(listOf("1", "9", "10", "101", "101b", "1701", "1701D", "A5"), sorted)
    }
}
