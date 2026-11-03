package spa.core.sim

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import spa.core.brief.BoosterSlot
import spa.core.brief.KitTemplate
import spa.core.brief.SlotKind
import spa.core.cards.Rarity
import spa.core.intake.synIndex

class KitSamplerTest {
    private val sampler = KitSampler(synIndex, synKit)
    private val pools = RarityPools(synIndex)
    private val seeds = 1L..50L

    @Test
    fun `a syn kit holds boosters times cards per booster plus the promo`() {
        assertEquals(6, synKit.boosters)
        assertEquals(14, synKit.cardsPerBooster)
        seeds.forEach { seed ->
            val kit = sampler.sampleKit(seed)
            assertEquals(85, sampler.sample(seed).totalCount)
            assertEquals(6, kit.boosters.size)
            assertTrue(kit.boosters.all { it.size == 14 })
            assertTrue(kit.promo != null)
        }
    }

    @Test
    fun `every slot's count is honored and each card comes from a kind its slot draws`() {
        // Each booster's cards are in slot order, so slot i owns the next `count` cards.
        val slotOfPosition = synKit.slots.flatMap { slot -> List(slot.count) { slot } }
        assertEquals(synKit.cardsPerBooster, slotOfPosition.size)
        val boosters = seeds.flatMap { sampler.sampleKit(it).boosters }
        boosters.forEach { booster -> assertEquals(slotOfPosition.size, booster.size) }
        boosters
            .flatMap { slotOfPosition.zip(it) }
            .forEach { (slot, card) ->
                assertTrue((slot.weights[card.kind] ?: 0.0) > 0, "${slot.name}: $card")
                assertTrue(card.printing in pools.pool(card.kind), "$card")
                assertEquals(slot.foil, card.foil)
            }
    }

    @Test
    fun `no booster holds the same card twice`() {
        seeds.forEach { seed ->
            sampler.sampleKit(seed).boosters.forEach { booster ->
                val ids = booster.map { it.printing.card.oracleId }
                assertEquals(ids.size, ids.toSet().size, "seed $seed")
            }
        }
    }

    @Test
    fun `basic lands come only from the basic land pool`() {
        val basics = pools.pool(SlotKind.BASIC_LAND).toSet()
        var seen = 0
        seeds.forEach { seed ->
            sampler.sampleKit(seed).cards.forEach { card ->
                assertEquals(card.printing.card.isBasicLand, card.kind == SlotKind.BASIC_LAND)
                if (card.printing.card.isBasicLand) {
                    assertTrue(card.printing in basics)
                    seen++
                }
            }
        }
        assertTrue(seen > 0)
    }

    @Test
    fun `companion-set cards come only from the bonus sheet`() {
        // syn has no companion, so its bonus sheet is empty and never draws.
        seeds.forEach { seed ->
            sampler.sampleKit(seed).cards.forEach { card ->
                assertEquals("syn", card.printing.setCode)
                assertNotEquals(SlotKind.BONUS_SHEET, card.kind)
            }
        }
        // trk's bonus sheet is sds; every sds card in a trk kit is a bonus sheet draw.
        val trk = KitSampler(trkIndex, trkKit)
        val cards = (1L..100L).flatMap { trk.sampleKit(it).cards }
        cards.forEach { card ->
            assertEquals(card.printing.setCode == "sds", card.kind == SlotKind.BONUS_SHEET)
        }
        assertTrue(cards.any { it.kind == SlotKind.BONUS_SHEET })
    }

    @Test
    fun `the same seed gives an equal pool and different seeds differ`() {
        assertEquals(sampler.sample(1), sampler.sample(1))
        assertEquals(sampler.sample(1), KitSampler(synIndex, synKit).sample(1))
        assertEquals(sampler.sampleKit(7), sampler.sampleKit(7))
        assertNotEquals(sampler.sample(1), sampler.sample(2))
    }

    @Test
    fun `the template's expected rare and mythic count follows from its weights`() {
        // Per booster: the rare slot is always rare or mythic; each wildcard is 7 in 68. Plus the
        // promo, which always is.
        assertEquals(6 * (1 + 2 * 7.0 / 68) + 1, KitSampler.expectedRaresAndMythics(synKit), 1e-9)
    }

    @Test
    fun `200 kits average within 10 percent of the expected rare and mythic count`() {
        assertAverageRares(sampler, KitSampler.expectedRaresAndMythics(synKit))
        assertAverageRares(KitSampler(trkIndex, trkKit), KitSampler.expectedRaresAndMythics(trkKit))
    }

    private fun assertAverageRares(sampler: KitSampler, expected: Double) {
        val counts =
            (1L..200L).map { seed ->
                sampler
                    .sample(seed)
                    .entries
                    .filter {
                        it.printing.rarity == Rarity.RARE || it.printing.rarity == Rarity.MYTHIC
                    }
                    .sumOf { it.count }
            }
        val mean = counts.average()
        assertTrue(abs(mean - expected) <= 0.1 * expected, "mean $mean, expected $expected")
    }

    @Test
    fun `a kit without a promo holds only its boosters`() {
        val noPromo = synKit.copy(promo = null)
        val kit = KitSampler(synIndex, noPromo).sampleKit(3)
        assertNull(kit.promo)
        assertEquals(84, kit.pool.totalCount)
        assertEquals(0.0, KitSampler.expectedRaresAndMythics(template(slot(SlotKind.COMMON, 1))))
    }

    @Test
    fun `a slot that runs out of new cards draws the ones left, then fails`() {
        // syn has exactly four rares: four rare draws in one booster must take each once.
        val fourRares = KitSampler(synIndex, template(slot(SlotKind.RARE, 4)))
        repeat(10) { seed ->
            val booster = fourRares.sampleKit(seed.toLong()).boosters.single()
            assertEquals(pools.pool(SlotKind.RARE).toSet(), booster.map { it.printing }.toSet())
        }
        val fiveRares = KitSampler(synIndex, template(slot(SlotKind.RARE, 5)))
        val error = assertFailsWith<IllegalStateException> { fiveRares.sample(1) }
        assertEquals("Slot 'Only RARE' has no card left to draw.", error.message)
        // A kind whose pool is empty never draws, whatever its weight.
        val bonusOnly = KitSampler(synIndex, template(slot(SlotKind.BONUS_SHEET, 1)))
        assertFailsWith<IllegalStateException> { bonusOnly.sample(1) }
    }

    @Test
    fun `foil counts record the foil slot's cards`() {
        val kit = sampler.sampleKit(11)
        val foils = kit.cards.filter { it.foil }
        assertEquals(6, foils.size)
        assertEquals(6, kit.foilCounts().values.sum())
        foils.forEach { assertTrue(kit.foilCounts().getValue(it.printing.scryfallId) > 0) }
    }

    private fun slot(kind: SlotKind, count: Int) =
        BoosterSlot(name = "Only $kind", count = count, weights = mapOf(kind to 1.0))

    private fun template(slot: BoosterSlot) =
        KitTemplate(
            boosters = 1,
            cardsPerBooster = slot.count,
            slots = listOf(slot),
            promo = null,
            basicLandsSupplied = false,
        )
}
