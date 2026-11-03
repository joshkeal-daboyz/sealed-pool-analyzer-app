package spa.core.metrics

import spa.core.cards.CardIndex
import spa.core.cards.Pool
import spa.core.cards.PoolEntry
import spa.core.cards.Printing
import spa.core.cards.fixturesDir

/** The `syn` index: 25 real cards, numbered 1 to 25 (see fixtures/scryfall/README.md). */
val synCards: CardIndex = CardIndex.load(fixturesDir, "syn")

/** syn collector numbers by card. */
object Syn {
    const val GRIZZLY_BEARS = "1"
    const val SERRA_ANGEL = "2"
    const val AIR_ELEMENTAL = "3"
    const val COUNTERSPELL = "4"
    const val DOOM_BLADE = "5"
    const val GRAVEDIGGER = "6"
    const val LLANOWAR_ELVES = "7"
    const val GIANT_GROWTH = "8"
    const val KITCHEN_FINKS = "9"
    const val LIGHTNING_HELIX = "10"
    const val DELVER_OF_SECRETS = "11"
    const val HUNTMASTER = "12"
    const val AGADEEMS_AWAKENING = "13"
    const val BONECRUSHER_GIANT = "14"
    const val MURDEROUS_RIDER = "15"
    const val FIRE_ICE = "16"
    const val WEAR_TEAR = "17"
    const val MIND_STONE = "18"
    const val EVOLVING_WILDS = "19"
    const val TEMPLE_OF_MYSTERY = "20"
    const val PLAINS = "21"
    const val ISLAND = "22"
    const val SWAMP = "23"
    const val MOUNTAIN = "24"
    const val FOREST = "25"
}

/** The syn printing with collector number [number]. */
fun synPrinting(number: String): Printing =
    requireNotNull(synCards.byCollectorNumber("syn", number)) { "No syn $number" }

/** [count] copies of the syn card numbered [number]. */
fun entry(number: String, count: Int = 1): PoolEntry = PoolEntry(synPrinting(number), count)

fun poolOf(vararg entries: PoolEntry): Pool = Pool(entries.toList())

/** A curve of eight buckets, zero except for [buckets]. */
fun curveOf(vararg buckets: CurveBucket): Curve {
    val given = buckets.associateBy { it.manaValue }
    return Curve((0..Curve.TOP).map { given[it] ?: CurveBucket(it, 0, 0) })
}

/** A [RarityCounts] whose total is the sum of the four named rarities. */
fun rarities(common: Int = 0, uncommon: Int = 0, rare: Int = 0, mythic: Int = 0): RarityCounts =
    RarityCounts(common, uncommon, rare, mythic, common + uncommon + rare + mythic)

val zeroColor = ColorMetrics(rarities(), rarities(), creatures = 0, removal = 0, interaction = 0)
