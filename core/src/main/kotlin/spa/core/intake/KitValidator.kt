package spa.core.intake

import spa.core.brief.KitTemplate
import spa.core.brief.SlotKind
import spa.core.cards.Pool
import spa.core.cards.Rarity

/** What kind of mismatch a [KitWarning] reports, with the [code] reports show. */
enum class KitWarningCode(val code: String) {
    /** The pool's card count is outside the range the kit allows. */
    CARD_COUNT("card-count"),

    /** The pool's rare-plus-mythic count is outside what a kit can hold. */
    RARE_COUNT("rare-count"),

    /** One printing appears more often than a kit plausibly holds. */
    HIGH_COUNT("high-count"),

    /** The pool has no cards. */
    EMPTY("empty"),
}

/**
 * A way a pool does not look like the kit it came from. [expected] is the allowed range and
 * [actual] the pool's value; [message] says it in a sentence. A warning, never an error.
 */
data class KitWarning(
    val code: KitWarningCode,
    val expected: IntRange,
    val actual: Int,
    val message: String,
)

/** Checks a pool against a prerelease kit template and returns warnings; it never throws. */
object KitValidator {
    /** The most copies of one printing a pool holds before it looks wrong. */
    const val MAX_COPIES = 4

    /** Rare-plus-mythic counts may fall this far below the booster count. */
    const val RARES_BELOW = 1

    /** Rare-plus-mythic counts may rise this far above the booster count. */
    const val RARES_ABOVE = 3

    private val rareOrMythic = setOf(Rarity.RARE, Rarity.MYTHIC)

    fun validate(pool: Pool, kitTemplate: KitTemplate): List<KitWarning> {
        val total = kitTotal(kitTemplate)
        if (pool.totalCount == 0) {
            return listOf(KitWarning(KitWarningCode.EMPTY, total..total, 0, "The pool is empty."))
        }
        return listOfNotNull(
            cardCount(pool, kitTemplate),
            rareCount(pool, kitTemplate),
        ) + highCounts(pool)
    }

    /** Every card in the kit: each booster's cards plus the promo, if there is one. */
    fun kitTotal(kit: KitTemplate): Int =
        kit.boosters * kit.cardsPerBooster + (if (kit.promo != null) 1 else 0)

    /** How many booster slots across the kit can hold a basic land. */
    fun boosterBasicSlots(kit: KitTemplate): Int =
        kit.boosters *
            kit.slots.filter { (it.weights[SlotKind.BASIC_LAND] ?: 0.0) > 0.0 }.sumOf { it.count }

    /**
     * The card count a pool may have. When the event supplies basic lands, the pool's non-basic
     * cards are counted, and boosters may have put up to [boosterBasicSlots] basics in place of
     * them. Otherwise every card is counted and the kit total is exact.
     */
    fun expectedCardCount(kit: KitTemplate): IntRange {
        val total = kitTotal(kit)
        return if (kit.basicLandsSupplied) (total - boosterBasicSlots(kit))..total else total..total
    }

    private fun cardCount(pool: Pool, kit: KitTemplate): KitWarning? {
        val counted =
            pool.entries.filterNot { kit.basicLandsSupplied && it.printing.card.isBasicLand }
        val actual = counted.sumOf { it.count }
        val expected = expectedCardCount(kit)
        val what = if (kit.basicLandsSupplied) "non-basic-land cards" else "cards"
        val holds =
            if (expected.first == expected.last) "${expected.first}"
            else "${expected.first} to ${expected.last}"
        return if (actual in expected) null
        else
            KitWarning(
                KitWarningCode.CARD_COUNT,
                expected,
                actual,
                "The pool has $actual $what; the kit holds $holds.",
            )
    }

    private fun rareCount(pool: Pool, kit: KitTemplate): KitWarning? {
        val allowed = (kit.boosters - RARES_BELOW)..(kit.boosters + RARES_ABOVE)
        val actual = pool.entries.filter { it.printing.rarity in rareOrMythic }.sumOf { it.count }
        return if (actual in allowed) null
        else
            KitWarning(
                KitWarningCode.RARE_COUNT,
                allowed,
                actual,
                "The pool has $actual rares and mythics; a kit of ${kit.boosters} boosters " +
                    "holds ${allowed.first} to ${allowed.last}.",
            )
    }

    private fun highCounts(pool: Pool): List<KitWarning> =
        pool.entries
            .filter { it.count > MAX_COPIES }
            .map {
                val printing = it.printing
                KitWarning(
                    KitWarningCode.HIGH_COUNT,
                    1..MAX_COPIES,
                    it.count,
                    "The pool has ${it.count} copies of ${printing.card.name} " +
                        "(${printing.setCode} ${printing.collectorNumber}); a kit rarely holds more " +
                        "than $MAX_COPIES.",
                )
            }
}
