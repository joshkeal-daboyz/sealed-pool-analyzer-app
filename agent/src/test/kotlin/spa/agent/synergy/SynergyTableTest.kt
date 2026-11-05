package spa.agent.synergy

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import spa.agent.synergy.SynergyTestCards.TAGS
import spa.agent.tags.TagCard

/** SPA-048 criteria 1 and 6: the pair table on the hand-built set `xyz`, and its seam. */
class SynergyTableTest {
    private val table = SynergyTable(TAGS)

    private fun id(name: String) = SynergyTestCards.tag(name).id

    private fun score(a: String, b: String) = table.score(id(a), id(b))

    @Test
    fun `a pair's score is its axis part plus its partner part`() {
        assertEquals(listOf(6, 0), table.parts(id("Maker"), id("Rewarder")))
        assertEquals(6, score("Maker", "Rewarder"))
        assertEquals(listOf(0, 3), table.parts(id("Namer"), id("Named")))
        assertEquals(listOf(0, 1), table.parts(id("One Way"), id("Rewarder")))
        assertEquals(9, score("Rewarder", "Twin"))
    }

    @Test
    fun `the diagonal uses the same formula`() {
        assertEquals(listOf(4, 0), table.parts(id("Both"), id("Both")))
        assertEquals(listOf(6, 0), table.parts(id("Twin"), id("Twin")))
        assertEquals(listOf(0, 3), table.parts(id("Selfie"), id("Selfie")))
        assertEquals(0, score("Maker", "Maker"))
    }

    @Test
    fun `a card with no tags scores 0 with everything, itself included`() {
        TAGS.cards.forEach { assertEquals(0, table.score(id("Blank"), it.id), it.name) }
    }

    @Test
    fun `an id the tag file does not hold scores 0`() {
        assertNull(table.card("xyz/99"))
        assertEquals(0, table.score("xyz/99", id("Twin")))
        assertEquals(listOf(0, 0), table.parts(id("Twin"), "xyz/99"))
        assertEquals("Twin", table.card(id("Twin"))?.name)
    }

    @Test
    fun `the table is symmetric`() {
        TAGS.cards.forEach { a ->
            TAGS.cards.forEach { b ->
                assertEquals(
                    table.parts(a.id, b.id),
                    table.parts(b.id, a.id),
                    "${a.name}, ${b.name}",
                )
            }
        }
    }

    @Test
    fun `pairs lists every pair once, the diagonal included, in tag file order`() {
        val pairs = table.pairs()
        val n = TAGS.cards.size

        assertEquals(n * (n + 1) / 2, pairs.size)
        assertEquals(n, pairs.count { it.isDiagonal })
        assertEquals("Maker" to "Maker", pairs[0].first.name to pairs[0].second.name)
        assertEquals("Maker" to "Rewarder", pairs[1].first.name to pairs[1].second.name)
        assertEquals("Rewarder" to "Rewarder", pairs[n].first.name to pairs[n].second.name)
        assertEquals(
            pairs.map { it.first.id to it.second.id }.toSet().size,
            pairs.size,
            "no pair twice",
        )
        pairs.forEach {
            assertEquals(table.parts(it.first.id, it.second.id), it.parts)
            assertEquals(it.parts.sum(), it.score)
        }
    }

    @Test
    fun `a test-only term added to the list changes the score with no other change`() {
        val one =
            object : PairTerm {
                override val name = "one"

                override fun score(a: TagCard, b: TagCard) = 1
            }
        val extended = SynergyTable(TAGS, PairTerms.ALL + one)

        assertEquals(0, table.score(id("Maker"), id("Blank")))
        assertEquals(1, extended.score(id("Maker"), id("Blank")))
        assertEquals(listOf(6, 0, 1), extended.parts(id("Maker"), id("Rewarder")))
        assertEquals(7, extended.score(id("Maker"), id("Rewarder")))
        assertTrue(extended.pairs().all { it.parts.size == 3 })
    }
}
