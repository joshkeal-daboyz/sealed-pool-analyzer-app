package spa.core.sim

import spa.core.brief.SlotKind
import spa.core.cards.CardIndex
import spa.core.cards.Printing
import spa.core.cards.Rarity
import spa.core.intake.PoolLines

/**
 * The cards each [SlotKind] draws from, over one index. Each card is held as the printing with the
 * lowest collector number in its set, and its rarity is that printing's.
 * - [SlotKind.COMMON], [SlotKind.UNCOMMON], [SlotKind.RARE], [SlotKind.MYTHIC]: the target set's
 *   cards of that rarity, basic lands excluded.
 * - [SlotKind.BASIC_LAND]: the target set's basic lands.
 * - [SlotKind.COMMON_LAND]: the target set's common nonbasic lands.
 * - [SlotKind.BONUS_SHEET]: every card of the companion sets.
 */
class RarityPools(index: CardIndex) {
    private val pools: Map<SlotKind, List<Printing>>

    init {
        val target = lowestPrintings(index.printings(index.setCodes.first()))
        val nonBasic = target.filterNot { it.card.isBasicLand }
        fun ofRarity(rarity: Rarity) = nonBasic.filter { it.rarity == rarity }
        pools =
            mapOf(
                SlotKind.COMMON to ofRarity(Rarity.COMMON),
                SlotKind.UNCOMMON to ofRarity(Rarity.UNCOMMON),
                SlotKind.RARE to ofRarity(Rarity.RARE),
                SlotKind.MYTHIC to ofRarity(Rarity.MYTHIC),
                SlotKind.BASIC_LAND to target.filter { it.card.isBasicLand },
                SlotKind.COMMON_LAND to ofRarity(Rarity.COMMON).filter { it.card.isLand },
                SlotKind.BONUS_SHEET to
                    index.setCodes
                        .drop(1)
                        .flatMap { lowestPrintings(index.printings(it)) }
                        .distinctBy { it.card.oracleId },
            )
    }

    /** The printings [kind] draws from, in collector number order; empty when it has none. */
    fun pool(kind: SlotKind): List<Printing> = pools.getValue(kind)

    private companion object {
        /** One printing per card, the one with the lowest collector number, in that order. */
        fun lowestPrintings(printings: List<Printing>): List<Printing> =
            printings
                .sortedWith(compareBy(PoolLines.collectorOrder) { it.collectorNumber })
                .distinctBy { it.card.oracleId }
    }
}
