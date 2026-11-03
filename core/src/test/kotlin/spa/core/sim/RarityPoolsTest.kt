package spa.core.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import spa.core.brief.SlotKind
import spa.core.cards.Rarity
import spa.core.intake.PoolLines
import spa.core.intake.synIndex
import spa.core.intake.synWithCompanion

class RarityPoolsTest {
    private val syn = RarityPools(synIndex)

    private fun numbers(pools: RarityPools, kind: SlotKind) =
        pools.pool(kind).map { "${it.setCode} ${it.collectorNumber}" }

    @Test
    fun `syn splits into its rarities with basic lands apart`() {
        // syn 1 to 25: 13 commons of which 21 to 25 are basics, 7 uncommons, 4 rares, 1 mythic.
        assertEquals(8, syn.pool(SlotKind.COMMON).size)
        assertEquals(7, syn.pool(SlotKind.UNCOMMON).size)
        assertEquals(4, syn.pool(SlotKind.RARE).size)
        assertEquals(1, syn.pool(SlotKind.MYTHIC).size)
        assertEquals(
            listOf("syn 21", "syn 22", "syn 23", "syn 24", "syn 25"),
            numbers(syn, SlotKind.BASIC_LAND),
        )
        assertEquals(listOf("Evolving Wilds"), syn.pool(SlotKind.COMMON_LAND).map { it.card.name })
        assertEquals(emptyList(), syn.pool(SlotKind.BONUS_SHEET))
    }

    @Test
    fun `each rarity pool holds only its rarity and never a basic land`() {
        val rarities =
            mapOf(
                SlotKind.COMMON to Rarity.COMMON,
                SlotKind.UNCOMMON to Rarity.UNCOMMON,
                SlotKind.RARE to Rarity.RARE,
                SlotKind.MYTHIC to Rarity.MYTHIC,
            )
        listOf(syn, RarityPools(trkIndex)).forEach { pools ->
            rarities.forEach { (kind, rarity) ->
                assertTrue(pools.pool(kind).all { it.rarity == rarity && !it.card.isBasicLand })
                assertTrue(pools.pool(kind).all { it.setCode == "syn" || it.setCode == "trk" })
            }
            assertTrue(pools.pool(SlotKind.BASIC_LAND).all { it.card.isBasicLand })
            assertTrue(
                pools.pool(SlotKind.COMMON_LAND).all {
                    it.card.isLand && !it.card.isBasicLand && it.rarity == Rarity.COMMON
                }
            )
        }
    }

    @Test
    fun `a card is held once, as its lowest collector number in its set`() {
        val pools = RarityPools(synWithCompanion)

        // Llanowar Elves is syn 7, 7s, and 103; 7 is the lowest.
        assertEquals(
            listOf("syn 7"),
            pools
                .pool(SlotKind.COMMON)
                .filter { it.card.name == "Llanowar Elves" }
                .map { "${it.setCode} ${it.collectorNumber}" },
        )
        SlotKind.entries.forEach { kind ->
            val pool = pools.pool(kind)
            assertEquals(pool.size, pool.distinctBy { it.card.oracleId }.size, "$kind")
        }
    }

    @Test
    fun `the bonus sheet is every card of the companion sets and only those`() {
        val withCompanion = RarityPools(synWithCompanion)
        // sds's 30 cards and the test-only sds 0 printing of Grizzly Bears.
        assertEquals(31, withCompanion.pool(SlotKind.BONUS_SHEET).size)
        assertTrue(withCompanion.pool(SlotKind.BONUS_SHEET).all { it.setCode == "sds" })
        assertEquals("0", withCompanion.pool(SlotKind.BONUS_SHEET).first().collectorNumber)

        val trk = RarityPools(trkIndex)
        assertEquals(30, trk.pool(SlotKind.BONUS_SHEET).size)
        assertTrue(trk.pool(SlotKind.BONUS_SHEET).all { it.setCode == "sds" })
        val trkPools = SlotKind.entries.filter { it != SlotKind.BONUS_SHEET }
        assertTrue(trkPools.flatMap { trk.pool(it) }.all { it.setCode == "trk" })
    }

    @Test
    fun `pools are in collector number order and use the lowest printing of each card`() {
        val trk = RarityPools(trkIndex)
        SlotKind.entries.forEach { kind ->
            val pool = trk.pool(kind)
            assertEquals(
                pool.map { it.collectorNumber }.sortedWith(PoolLines.collectorOrder),
                pool.map { it.collectorNumber },
            )
            pool.forEach { printing ->
                val lowest =
                    trkIndex
                        .printings(printing.setCode)
                        .filter { it.card.oracleId == printing.card.oracleId }
                        .map { it.collectorNumber }
                        .minWith(PoolLines.collectorOrder)
                assertEquals(lowest, printing.collectorNumber)
            }
        }
    }
}
