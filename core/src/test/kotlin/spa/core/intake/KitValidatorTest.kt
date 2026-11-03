package spa.core.intake

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
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
     * A pool that matches the kit: five rares once each and twenty commons and uncommons four times
     * each (85 non-basic cards), plus three Forests that the count leaves out.
     */
    private fun kitPool(): MutableMap<Printing, Int> {
        val pool = linkedMapOf<Printing, Int>()
        rares.forEach { pool[syn(it)] = 1 }
        others.forEach { pool[syn(it)] = 4 }
        variants.forEach { pool[it] = 4 }
        pool[syn("25")] = 3
        return pool
    }

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
        val trk = BriefLoader.load(briefFixturesDir.resolve("trk.json")).kitTemplate
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
    fun `too few rares is a rare-count warning`() {
        val pool =
            kitPool().apply {
                remove(syn("20"))
                this[syn("6").copy(scryfallId = "test-extra-6", collectorNumber = "6v")] = 1
            }

        val warnings = validate(pool)

        assertEquals(listOf(KitWarningCode.RARE_COUNT), warnings.map { it.code })
        assertEquals("rare-count", warnings[0].code.code)
        assertEquals(5..9, warnings[0].expected)
        assertEquals(4, warnings[0].actual)
        assertEquals(
            "The pool has 4 rares and mythics; a kit of 6 boosters holds 5 to 9.",
            warnings[0].message,
        )
    }

    @Test
    fun `too many rares is a rare-count warning and nine is still fine`() {
        val nine =
            kitPool().apply {
                this[syn("12")] = 3
                this[syn("13")] = 2
                this[syn("14")] = 2
                listOf("1", "2", "3", "4").forEach { this[syn(it)] = 3 }
            }
        assertEquals(emptyList(), validate(nine))

        val ten = nine.apply {
            this[syn("15")] = 2
            this[syn("5")] = 3
        }
        val warnings = validate(ten)

        assertEquals(listOf(KitWarningCode.RARE_COUNT), warnings.map { it.code })
        assertEquals(10, warnings[0].actual)
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
