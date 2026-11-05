package spa.agent.synergy

import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import spa.agent.synergy.SynergyTestCards.SET
import spa.agent.synergy.SynergyTestCards.TAGS
import spa.agent.synergy.SynergyTestCards.pool
import spa.core.cards.Pool
import spa.core.cards.PoolEntry
import spa.core.metrics.ColorPair
import spa.core.text.Color

/**
 * SPA-048 criterion 1: a pool card's tag synergy part on the hand-built set `xyz`, in WU, where
 * nine set cards fit (see [SynergyTestCards]).
 */
class TagSynergyTest {
    private fun synergy(weight: Double = 0.5) =
        TagSynergy(SynergyTable(TAGS), SET, SynergyWeight(weight))

    private fun PoolSynergy.of(name: String): CardSynergy = cards.single {
        it.printing.card.name == name
    }

    private fun assertNear(expected: Double, actual: Double, what: String) =
        assertTrue(abs(expected - actual) < 1e-9, "$what: expected $expected, got $actual")

    @Test
    fun `a second copy counts through the card's score with itself`() {
        val wu = synergy().score(pool("Twin" to 2, "Maker" to 1), ColorPair.WU)

        assertEquals(3, wu.copies)
        assertEquals(9, wu.fitting)
        // Twin: Maker once (score 2) plus its other copy (score with itself 6).
        assertEquals(2 + 6, wu.of("Twin").syn)
        assertEquals(2, wu.of("Twin").quantity)
        // Maker: two copies of Twin, score 2 each; its own copy count is 1, so no self term.
        assertEquals(2 * 2, wu.of("Maker").syn)
    }

    @Test
    fun `e is the mean score over the fitting set cards other than the card, times N minus 1`() {
        val wu = synergy().score(pool("Twin" to 2, "Maker" to 1), ColorPair.WU)

        // Twin with the 8 other fitting cards: Maker 2, Rewarder 9, Both 7, the rest 0. Red
        // Payoff (R) and Token Land (a land) would add 9 each, and Twin with itself 6.
        assertNear(18.0 / 8 * 2, wu.of("Twin").expected, "e(Twin)")
        // Maker: Rewarder 6, Both 4, Twin 2.
        assertNear(12.0 / 8 * 2, wu.of("Maker").expected, "e(Maker)")
        assertNear(0.5 * (8 - 4.5) / 4.5, wu.of("Twin").value, "part(Twin)")
        assertNear(0.5 * (4 - 3.0) / 3.0, wu.of("Maker").value, "part(Maker)")
    }

    @Test
    fun `cards print highest part first`() {
        val wu = synergy().score(pool("Maker" to 1, "Twin" to 2), ColorPair.WU)

        assertEquals(listOf("Twin", "Maker"), wu.cards.map { it.printing.card.name })
    }

    @Test
    fun `an e below 1 divides by 1`() {
        val wu = synergy().score(pool("One Way" to 1, "Rewarder" to 1), ColorPair.WU)

        // One Way scores 1 with Rewarder only, so q = 1/8 and e = 1/8 · 1.
        assertNear(0.125, wu.of("One Way").expected, "e")
        assertEquals(1, wu.of("One Way").syn)
        assertNear(0.5 * (1 - 0.125), wu.of("One Way").value, "part")
        // Rewarder: q = (6 + 3 + 1 + 9) / 8, e above 1.
        assertNear(19.0 / 8, wu.of("Rewarder").expected, "e(Rewarder)")
        assertNear(0.5 * (1 - 19.0 / 8) / (19.0 / 8), wu.of("Rewarder").value, "part(Rewarder)")
    }

    @Test
    fun `the ratio is clamped to plus and minus 1`() {
        // Selfie: syn 3 from its second copy, e 0, ratio 3.
        val selfie = synergy().score(pool("Selfie" to 2), ColorPair.WU).of("Selfie")
        assertEquals(3, selfie.syn)
        assertNear(0.0, selfie.expected, "e")
        assertNear(0.5, selfie.value, "part")

        // Rewarder with three Blanks: syn 0 against e = 19/8 · 3, ratio −1.
        val alone = synergy().score(pool("Rewarder" to 1, "Blank" to 3), ColorPair.WU)
        assertEquals(0, alone.of("Rewarder").syn)
        assertNear(-0.5, alone.of("Rewarder").value, "part")
        assertEquals(-0.5, TagSynergy.part(0, 10.0, 0.5))
        assertEquals(0.5, TagSynergy.part(100, 10.0, 0.5))
    }

    @Test
    fun `a card with no tags has part 0`() {
        val blank = synergy().score(pool("Rewarder" to 1, "Blank" to 3), ColorPair.WU).of("Blank")

        assertEquals(0, blank.syn)
        assertNear(0.0, blank.expected, "e")
        assertEquals(0.0, blank.value)
        assertEquals(emptyList(), blank.partners)
        assertEquals("no partners in the pool", blank.reason)
    }

    @Test
    fun `weight 0 switches the part off`() {
        val wu = synergy(weight = 0.0).score(pool("Twin" to 2, "Maker" to 1), ColorPair.WU)

        assertTrue(wu.cards.all { it.value == 0.0 && !it.value.toString().startsWith("-") })
        assertEquals(8, wu.of("Twin").syn)
        val alone = synergy(weight = 0.0).score(pool("Rewarder" to 1, "Blank" to 3), ColorPair.WU)
        assertEquals("0.0", alone.of("Rewarder").value.toString())
    }

    @Test
    fun `only nonland cards within the pair are candidates and fitting cards`() {
        val wu =
            synergy().score(pool("Twin" to 1, "Red Payoff" to 1, "Token Land" to 2), ColorPair.WU)
        assertEquals(listOf("Twin"), wu.cards.map { it.printing.card.name })
        assertEquals(1, wu.copies)

        val red = synergy().score(pool("Red Payoff" to 1, "Blank" to 1), ColorPair.WR)
        assertEquals(
            listOf("Blank", "Red Payoff"),
            red.cards.map { it.printing.card.name }.sorted(),
        )
        // In WR the fitting cards are the W ones, Blank and Red Payoff: Maker, Both, Namer,
        // Selfie, Blank, One Way, Red Payoff.
        assertEquals(7, red.fitting)

        assertTrue(TagSynergy.fits(SynergyTestCards.printing("Blank").card, ColorPair.UB))
        assertFalse(TagSynergy.fits(SynergyTestCards.printing("Token Land").card, ColorPair.WU))
        assertFalse(TagSynergy.fits(SynergyTestCards.printing("Maker").card, ColorPair.UB))
    }

    @Test
    fun `copies of one card in two printings are one candidate, matched by oracle id`() {
        val twin = SynergyTestCards.printing("Twin")
        val variant = SynergyTestCards.printing(twin.card, "9b")
        val entries =
            listOf(
                PoolEntry(twin, 1),
                PoolEntry(variant, 1),
                PoolEntry(SynergyTestCards.printing("Maker"), 1),
                PoolEntry(SynergyTestCards.printing("Both"), 0),
            )
        val wu = synergy().score(Pool(entries), ColorPair.WU)

        assertEquals(listOf("Twin", "Maker"), wu.cards.map { it.printing.card.name })
        assertEquals(2, wu.of("Twin").quantity)
        assertEquals(8, wu.of("Twin").syn)
    }

    @Test
    fun `a pool card the tag file does not hold scores 0`() {
        val stranger =
            SynergyTestCards.printing(SynergyTestCards.card("Stranger", setOf(Color.W)), "1", "abc")
        val pool =
            Pool(listOf(PoolEntry(stranger, 1), PoolEntry(SynergyTestCards.printing("Twin"), 1)))
        val wu = synergy().score(pool, ColorPair.WU)

        assertEquals(0, wu.of("Stranger").syn)
        assertNear(0.0, wu.of("Stranger").expected, "e")
        assertEquals(0, wu.of("Twin").syn)
    }

    @Test
    fun `the reason names the strongest partners, ties by name, at most three`() {
        val wu = synergy().score(pool("Twin" to 2, "Maker" to 1), ColorPair.WU)
        assertEquals("partners: Twin (1 x 6 = 6), Maker (1 x 2 = 2)", wu.of("Twin").reason)
        assertEquals("partners: Twin (2 x 2 = 4)", wu.of("Maker").reason)

        val many =
            synergy()
                .score(
                    pool("Rewarder" to 1, "Maker" to 1, "Both" to 1, "One Way" to 1, "Twin" to 1),
                    ColorPair.WU,
                )
        assertEquals(
            "partners: Twin (1 x 9 = 9), Maker (1 x 6 = 6), Both (1 x 3 = 3)",
            many.of("Rewarder").reason,
        )
        assertEquals(19, many.of("Rewarder").syn)

        val tied = synergy().score(pool("Rewarder" to 1, "One Way" to 3, "Both" to 1), ColorPair.WU)
        assertEquals(
            "partners: Both (1 x 3 = 3), One Way (3 x 1 = 3)",
            tied.of("Rewarder").reason,
        )
    }
}
