package spa.core.sim

import kotlin.random.Random
import spa.core.brief.KitTemplate
import spa.core.brief.SlotKind
import spa.core.cards.CardIndex
import spa.core.cards.Pool
import spa.core.cards.PoolEntry
import spa.core.cards.Printing

/** One card a kit holds: its [printing], the [kind] of card it was drawn as, and whether foil. */
data class SampledCard(val printing: Printing, val kind: SlotKind, val foil: Boolean)

/** One sampled kit: its [boosters] in order, each card in slot order, and the [promo] if any. */
data class SampledKit(val boosters: List<List<SampledCard>>, val promo: SampledCard?) {
    /** Every card: the boosters in order, then the promo. */
    val cards: List<SampledCard>
        get() = boosters.flatten() + listOfNotNull(promo)

    /** The kit as a pool, one entry per printing in first-drawn order. */
    val pool: Pool
        get() = Pool(mergeCounts(cards).map { (printing, count) -> PoolEntry(printing, count) })

    /** Foil copies per Scryfall id; a printing with no foil copy is absent. */
    fun foilCounts(): Map<String, Int> =
        mergeCounts(cards.filter { it.foil }).entries.associate { it.key.scryfallId to it.value }

    private fun mergeCounts(cards: List<SampledCard>): Map<Printing, Int> {
        val counts = linkedMapOf<Printing, Int>()
        cards.forEach { counts.merge(it.printing, 1, Int::plus) }
        return counts
    }
}

/**
 * Samples prerelease kits from [kitTemplate] over [index]. A slot draws a kind of card by its
 * weights, then a card uniformly from that kind's [RarityPools] pool. A booster never holds the
 * same card twice: a slot that draws a card already in its booster draws again from that kind. The
 * sampler reads only the template, never a brief's archetypes or card names.
 */
class KitSampler(index: CardIndex, private val kitTemplate: KitTemplate) {
    private val pools = RarityPools(index)

    /** The kit for [seed] as a pool. The same seed, index, and template give an equal pool. */
    fun sample(seed: Long): Pool = sampleKit(seed).pool

    /** The kit for [seed], booster by booster. */
    fun sampleKit(seed: Long): SampledKit {
        val random = Random(seed)
        val boosters = List(kitTemplate.boosters) { booster(random) }
        val promo =
            kitTemplate.promo?.let { draw("Promo", it.weights, emptySet(), foil = false, random) }
        return SampledKit(boosters, promo)
    }

    private fun booster(random: Random): List<SampledCard> {
        val cards = mutableListOf<SampledCard>()
        kitTemplate.slots.forEach { slot ->
            repeat(slot.count) {
                val inBooster = cards.mapTo(mutableSetOf()) { it.printing.card.oracleId }
                cards += draw(slot.name, slot.weights, inBooster, slot.foil, random)
            }
        }
        return cards
    }

    /**
     * Draws one card not in [exclude]: a kind by its weight, then a card uniformly from that kind's
     * pool. A drawn card already in the booster is drawn again from the same kind, so each kind
     * keeps its weight's chance; only a kind with no card left, or an empty pool, is passed over.
     */
    private fun draw(
        slotName: String,
        weights: Map<SlotKind, Double>,
        exclude: Set<String>,
        foil: Boolean,
        random: Random,
    ): SampledCard {
        val open =
            weights.entries
                .filter { it.value > 0 }
                .map { (kind, weight) ->
                    OpenKind(
                        kind,
                        weight,
                        pools.pool(kind).filterNot { it.card.oracleId in exclude },
                    )
                }
                .filter { it.cards.isNotEmpty() }
        check(open.isNotEmpty()) { "Slot '$slotName' has no card left to draw." }
        var point = random.nextDouble() * open.sumOf { it.weight }
        val drawn =
            open.firstOrNull {
                point -= it.weight
                point < 0
            } ?: open.last()
        return SampledCard(drawn.cards[random.nextInt(drawn.cards.size)], drawn.kind, foil)
    }

    private class OpenKind(val kind: SlotKind, val weight: Double, val cards: List<Printing>)

    companion object {
        /** The cards of a kit drawn from [kitTemplate]'s weights, as rare or mythic, on average. */
        fun expectedRaresAndMythics(kitTemplate: KitTemplate): Double {
            val perBooster = kitTemplate.slots.sumOf { it.count * rareShare(it.weights) }
            return kitTemplate.boosters * perBooster +
                (kitTemplate.promo?.let { rareShare(it.weights) } ?: 0.0)
        }

        private fun rareShare(weights: Map<SlotKind, Double>): Double {
            val total = weights.values.sum()
            val rare = (weights[SlotKind.RARE] ?: 0.0) + (weights[SlotKind.MYTHIC] ?: 0.0)
            return if (total > 0) rare / total else 0.0
        }
    }
}
