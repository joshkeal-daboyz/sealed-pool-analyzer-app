package spa.core.intake

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import spa.core.brief.BoosterSlot
import spa.core.brief.BriefLoader
import spa.core.brief.KitTemplate
import spa.core.brief.SlotKind
import spa.core.brief.briefFixturesDir
import spa.core.brief.synBriefPath
import spa.core.cards.Pool
import spa.core.cards.PoolEntry
import spa.core.cards.Printing

class KitValidatorTest {
    /** syn.json's kit: 6 boosters of 14 cards, a promo, basic lands supplied; 85 cards. */
    private val kit = BriefLoader.load(synBriefPath).kitTemplate

    /**
     * syn's rares and mythics: Huntmaster, Agadeem's Awakening, Bonecrusher, Murderous Rider,
     * Temple.
     */
    private val rares = listOf("12", "13", "14", "15", "20")

    /** syn's non-basic commons and uncommons. */
    private val others = (1..11).map { "$it" } + (16..19).map { "$it" }

    /** Test-only second printings of syn 1 to 5, so a kit-sized pool fits four copies each. */
    private val variants =
        (1..5).map { syn("$it").copy(scryfallId = "test-variant-$it", collectorNumber = "${it}v") }

    /**
     * A pool that matches the kit: seven rares and mythics (two each of syn 12 and 13, one each of
     * the rest), the twenty commons and uncommons four times each except syn 11 twice (85 non-basic
     * cards), plus three Forests that the count leaves out.
     */
    private fun kitPool(): MutableMap<Printing, Int> {
        val pool = linkedMapOf<Printing, Int>()
        rares.forEach { pool[syn(it)] = 1 }
        pool[syn("12")] = 2
        pool[syn("13")] = 2
        others.forEach { pool[syn(it)] = 4 }
        pool[syn("11")] = 2
        variants.forEach { pool[it] = 4 }
        pool[syn("25")] = 3
        return pool
    }

    /**
     * An 85-card pool with exactly [rareCount] rares and mythics, spread over syn's five rares, and
     * the rest commons and uncommons at most four copies each, so only the rare count can warn.
     */
    private fun rarePool(rareCount: Int): Map<Printing, Int> {
        val pool = linkedMapOf<Printing, Int>()
        (0 until rareCount).forEach {
            val rare = syn(rares[it % rares.size])
            pool[rare] = (pool[rare] ?: 0) + 1
        }
        var left = 85 - rareCount
        (others.map { syn(it) } + variants).forEach {
            val copies = minOf(KitValidator.MAX_COPIES, left)
            if (copies > 0) pool[it] = copies
            left -= copies
        }
        assertEquals(0, left)
        return pool
    }

    private val trk = BriefLoader.load(briefFixturesDir.resolve("trk.json")).kitTemplate

    private fun validate(pool: Map<Printing, Int>, template: KitTemplate = kit) =
        KitValidator.validate(
            Pool(pool.map { (printing, count) -> PoolEntry(printing, count) }),
            template,
        )

    @Test
    fun `the syn kit holds 85 cards`() {
        assertEquals(6, kit.boosters)
        assertEquals(14, kit.cardsPerBooster)
        assertTrue(kit.promo != null)
        assertTrue(kit.basicLandsSupplied)
    }

    @Test
    fun `a kit-sized pool has no warnings`() {
        val pool = kitPool()

        assertEquals(85, pool.entries.filterNot { it.key.card.isBasicLand }.sumOf { it.value })
        assertEquals(emptyList(), validate(pool))
    }

    /** syn's basic lands, 21 to 25, then a test-only second printing of each. */
    private val basics =
        (21..25).map { syn("$it") } +
            (21..25).map {
                syn("$it").copy(scryfallId = "test-basic-$it", collectorNumber = "${it}v")
            }

    /** A test-only second printing of syn 6, a common, for pools past 85 non-basic cards. */
    private val extra = syn("6").copy(scryfallId = "test-extra-6", collectorNumber = "6v")

    /**
     * The kit pool cut or padded to [nonbasic] non-basic cards, with [basicCount] basic lands
     * spread over [basics] at most three copies each, so only the card count can warn.
     */
    private fun pool(nonbasic: Int, basicCount: Int): Map<Printing, Int> {
        val pool = kitPool().apply { remove(syn("25")) }
        others.take((85 - nonbasic).coerceAtLeast(0)).forEach { pool[syn(it)] = 3 }
        if (nonbasic > 85) pool[extra] = nonbasic - 85
        (0 until basicCount).forEach {
            val basic = basics[it % basics.size]
            pool[basic] = (pool[basic] ?: 0) + 1
        }
        assertEquals(
            nonbasic,
            pool.entries.filterNot { it.key.card.isBasicLand }.sumOf { it.value },
        )
        assertEquals(basicCount, pool.entries.filter { it.key.card.isBasicLand }.sumOf { it.value })
        return pool
    }

    private fun cardCount(warnings: List<KitWarning>) = warnings.single {
        it.code == KitWarningCode.CARD_COUNT
    }

    @Test
    fun `the syn and trk kits hold 85 cards, 6 of them booster basic slots`() {
        listOf(kit, trk).forEach {
            assertEquals(85, KitValidator.kitTotal(it))
            assertEquals(6, KitValidator.boosterBasicSlots(it))
            assertEquals(79..85, KitValidator.expectedCardCount(it))
        }
        val notSupplied = kit.copy(basicLandsSupplied = false)
        assertEquals(85..85, KitValidator.expectedCardCount(notSupplied))
    }

    @Test
    fun `slots without a basic-land weight, or with a zero one, hold no booster basics`() {
        val land = kit.slots.single { SlotKind.BASIC_LAND in it.weights }
        val zero = land.copy(weights = land.weights + (SlotKind.BASIC_LAND to 0.0))
        val twoLands = land.copy(count = 2)

        assertEquals(0, KitValidator.boosterBasicSlots(kit.copy(slots = kit.slots - land)))
        assertEquals(0, KitValidator.boosterBasicSlots(kit.copy(slots = kit.slots - land + zero)))
        assertEquals(
            12,
            KitValidator.boosterBasicSlots(kit.copy(slots = kit.slots - land + twoLands)),
        )
    }

    @Test
    fun `basics supplied, 79 non-basic cards and 6 booster basics is no warning`() {
        assertEquals(emptyList(), validate(pool(nonbasic = 79, basicCount = 6)))
    }

    @Test
    fun `basics supplied, 85 non-basic cards and no basics is no warning`() {
        assertEquals(emptyList(), validate(pool(nonbasic = 85, basicCount = 0)))
    }

    @Test
    fun `basics supplied, 81 non-basic cards and 30 event basics is no warning`() {
        assertEquals(emptyList(), validate(pool(nonbasic = 81, basicCount = 30)))
    }

    @Test
    fun `basics supplied, 78 non-basic cards is a card-count warning`() {
        val warnings = validate(pool(nonbasic = 78, basicCount = 6))

        assertEquals(listOf(KitWarningCode.CARD_COUNT), warnings.map { it.code })
        assertEquals("card-count", warnings[0].code.code)
        assertEquals(79..85, warnings[0].expected)
        assertEquals(78, warnings[0].actual)
        assertEquals(
            "The pool has 78 non-basic-land cards; the kit holds 79 to 85.",
            warnings[0].message,
        )
    }

    @Test
    fun `basics supplied, 86 non-basic cards is a card-count warning`() {
        val warning = cardCount(validate(pool(nonbasic = 86, basicCount = 0)))

        assertEquals(79..85, warning.expected)
        assertEquals(86, warning.actual)
        assertEquals(
            "The pool has 86 non-basic-land cards; the kit holds 79 to 85.",
            warning.message,
        )
    }

    @Test
    fun `basics not supplied, 85 cards including basics is no warning`() {
        val notSupplied = kit.copy(basicLandsSupplied = false)

        assertEquals(emptyList(), validate(pool(nonbasic = 82, basicCount = 3), notSupplied))
    }

    @Test
    fun `basics not supplied, 84 cards including basics is a card-count warning`() {
        val notSupplied = kit.copy(basicLandsSupplied = false)

        val warnings = validate(pool(nonbasic = 81, basicCount = 3), notSupplied)

        assertEquals(listOf(KitWarningCode.CARD_COUNT), warnings.map { it.code })
        assertEquals(85..85, warnings[0].expected)
        assertEquals(84, warnings[0].actual)
        assertEquals("The pool has 84 cards; the kit holds 85.", warnings[0].message)
    }

    @Test
    fun `when basic lands are not supplied they count, and no promo means one card fewer`() {
        val noBasics = kit.copy(basicLandsSupplied = false)
        val warnings = validate(kitPool(), noBasics)

        assertEquals(listOf(KitWarningCode.CARD_COUNT), warnings.map { it.code })
        assertEquals(88, warnings[0].actual)
        assertEquals("The pool has 88 cards; the kit holds 85.", warnings[0].message)

        val noPromo = validate(kitPool(), kit.copy(promo = null))
        assertEquals(78..84, noPromo.single().expected)
        assertEquals(85, noPromo.single().actual)
    }

    @Test
    fun `the trk and syn kits hold 7 to 13 rares and mythics`() {
        listOf(trk, kit).forEach {
            assertEquals(7, KitRareCounts.guaranteedRares(it))
            assertEquals(13, KitRareCounts.rareCountMax(it))
            assertEquals(7..13, KitValidator.expectedRareCount(it))
        }
    }

    @Test
    fun `the trk rare-count distribution matches the exact values`() {
        val dist = KitRareCounts.rareCountDistribution(trk)
        val atMost = { k: Int -> dist.take(k + 1).sum() }

        assertEquals(1.0, dist.sum(), 1e-9)
        assertEquals(0.0, dist.take(7).sum(), 1e-12)
        assertEquals(0.24707, dist[7], 0.00001)
        assertEquals(0.99889, atMost(12), 0.00001)
        assertEquals(0.99987, atMost(13), 0.00001)
    }

    @Test
    fun `each kit position's rare chance counts rare, mythic and bonus-sheet weights`() {
        val chances = KitRareCounts.rareChances(trk)

        assertEquals(85, chances.size)
        assertEquals(7, chances.count { it == 1.0 })
        assertEquals(6, chances.count { it == 1.0 / 64 })
        assertEquals(12, chances.count { it == 7.0 / 68 })
        assertEquals(60, chances.count { it == 0.0 })
    }

    @Test
    fun `a single rare slot, one booster and no promo derives 1 to 1`() {
        val single =
            kit.copy(
                boosters = 1,
                cardsPerBooster = 1,
                slots = listOf(BoosterSlot("Rare", 1, weights = mapOf(SlotKind.RARE to 1.0))),
                promo = null,
            )

        assertEquals(1..1, KitValidator.expectedRareCount(single))
    }

    @Test
    fun `a slot with no weights never yields a rare`() {
        val empty = kit.copy(slots = listOf(BoosterSlot("Empty", 1, weights = emptyMap())))

        assertEquals(List(6) { 0.0 } + 1.0, KitRareCounts.rareChances(empty))
        assertEquals(1..1, KitValidator.expectedRareCount(empty))
    }

    @Test
    fun `syn, 7 rares and mythics is no warning`() {
        assertEquals(emptyList(), validate(rarePool(7)))
    }

    @Test
    fun `syn, 13 rares and mythics is no warning`() {
        assertEquals(emptyList(), validate(rarePool(13)))
    }

    @Test
    fun `syn, 6 rares and mythics is a rare-count warning`() {
        val warnings = validate(rarePool(6))

        assertEquals(listOf(KitWarningCode.RARE_COUNT), warnings.map { it.code })
        assertEquals("rare-count", warnings[0].code.code)
        assertEquals(7..13, warnings[0].expected)
        assertEquals(6, warnings[0].actual)
        assertEquals(
            "The pool has 6 rares and mythics; a kit of 6 boosters holds 7 to 13.",
            warnings[0].message,
        )
    }

    @Test
    fun `syn, 14 rares and mythics is a rare-count warning`() {
        val warnings = validate(rarePool(14))

        assertEquals(listOf(KitWarningCode.RARE_COUNT), warnings.map { it.code })
        assertEquals(7..13, warnings[0].expected)
        assertEquals(14, warnings[0].actual)
    }

    @Test
    fun `more than four of one printing is a high-count warning for that printing`() {
        val pool =
            kitPool().apply {
                this[syn("7")] = 5
                this[syn("8")] = 3
            }

        val warnings = validate(pool)

        assertEquals(listOf(KitWarningCode.HIGH_COUNT), warnings.map { it.code })
        assertEquals("high-count", warnings[0].code.code)
        assertEquals(1..4, warnings[0].expected)
        assertEquals(5, warnings[0].actual)
        assertEquals(
            "The pool has 5 copies of Llanowar Elves (syn 7); a kit rarely holds more than 4.",
            warnings[0].message,
        )
    }

    @Test
    fun `30 copies of one basic land printing is no high-count warning`() {
        val pool = kitPool().apply { this[syn("25")] = 30 }

        assertTrue(syn("25").card.isBasicLand)
        assertEquals(emptyList(), validate(pool))
        val notSupplied = validate(pool, kit.copy(basicLandsSupplied = false))
        assertEquals(listOf(KitWarningCode.CARD_COUNT), notSupplied.map { it.code })
    }

    @Test
    fun `3 copies each of two printings of one nonbasic card is no high-count warning`() {
        val pool =
            kitPool().apply {
                this[syn("1")] = 3
                this[variants[0]] = 3
            }

        assertEquals(syn("1").card, variants[0].card)
        assertEquals(emptyList(), validate(pool).filter { it.code == KitWarningCode.HIGH_COUNT })
    }

    @Test
    fun `an empty pool is one empty warning and nothing else`() {
        val warnings = KitValidator.validate(Pool(emptyList()), kit)

        assertEquals(listOf(KitWarningCode.EMPTY), warnings.map { it.code })
        assertEquals("empty", warnings[0].code.code)
        assertEquals(85..85, warnings[0].expected)
        assertEquals(0, warnings[0].actual)
        assertEquals("The pool is empty.", warnings[0].message)
    }

    @Test
    fun `several problems at once are all reported, in a fixed order`() {
        val pool = mapOf(syn("1") to 6, syn("2") to 5)

        assertEquals(
            listOf(
                KitWarningCode.CARD_COUNT,
                KitWarningCode.RARE_COUNT,
                KitWarningCode.HIGH_COUNT,
                KitWarningCode.HIGH_COUNT,
            ),
            validate(pool).map { it.code },
        )
    }
}
