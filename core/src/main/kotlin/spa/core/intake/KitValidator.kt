package spa.core.intake

import spa.core.brief.KitTemplate
import spa.core.cards.Pool
import spa.core.cards.Rarity

/** What kind of mismatch a [KitWarning] reports, with the [code] reports show. */
enum class KitWarningCode(val code: String) {
    /** The pool's card count is not the kit's. */
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
        val expectedCards =
            kitTemplate.boosters * kitTemplate.cardsPerBooster +
                (if (kitTemplate.promo != null) 1 else 0)
        if (pool.totalCount == 0) {
            return listOf(
                KitWarning(
                    KitWarningCode.EMPTY,
                    expectedCards..expectedCards,
                    0,
                    "The pool is empty.",
                )
            )
        }
        return listOfNotNull(
            cardCount(pool, kitTemplate, expectedCards),
            rareCount(pool, kitTemplate),
        ) + highCounts(pool)
    }

    private fun cardCount(pool: Pool, kit: KitTemplate, expected: Int): KitWarning? {
        val counted =
            pool.entries.filterNot { kit.basicLandsSupplied && it.printing.card.isBasicLand }
        val actual = counted.sumOf { it.count }
        val what = if (kit.basicLandsSupplied) "non-basic-land cards" else "cards"
        return if (actual == expected) null
        else
            KitWarning(
                KitWarningCode.CARD_COUNT,
                expected..expected,
                actual,
                "The pool has $actual $what; the kit holds $expected.",
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
