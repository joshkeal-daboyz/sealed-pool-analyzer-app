package spa.core.baseline

import java.nio.file.Path
import spa.core.brief.BriefLoader
import spa.core.brief.FormatBrief
import spa.core.cards.CardIndex
import spa.core.cards.ScryfallCache
import spa.core.metrics.MetricEngine
import spa.core.sim.KitSampler

/**
 * Builds a set's [Baseline] by sampling kits from [brief]'s kit template over [index]. The brief is
 * read only through [KitSampler], plus its set code and version for the baseline's header; never
 * its archetypes or card names.
 *
 * @param cacheFetchedAt written into the baseline as given; [load] sets it from the caches.
 */
class BaselineBuilder(
    index: CardIndex,
    private val brief: FormatBrief,
    private val cacheFetchedAt: String = "",
) {
    private val sampler = KitSampler(index, brief.kitTemplate)

    /**
     * Samples [kits] kits with seeds [seed] to `seed + kits - 1`, flattens each kit's metrics, and
     * aggregates every pointer seen in any kit. The same arguments give an equal baseline.
     *
     * @throws IllegalArgumentException when [kits] is below [MIN_KITS].
     */
    fun build(seed: Long, kits: Int): Baseline {
        require(kits >= MIN_KITS) { "A baseline needs at least $MIN_KITS kits, got $kits." }
        val flattened =
            (0 until kits).map {
                MetricFlattener.flatten(MetricEngine.compute(sampler.sample(seed + it)))
            }
        return Baseline(
            setCode = brief.setCode,
            briefVersion = brief.version,
            cacheFetchedAt = cacheFetchedAt,
            seed = seed,
            kits = kits,
            stats = aggregate(flattened),
        )
    }

    companion object {
        /** The fewest kits a baseline samples. */
        const val MIN_KITS = 100

        /**
         * The builder for [setCode]: its index from the caches under [scryfallDir], its brief
         * `<setCode>.json` from [briefsDir], and the latest fetch time of the caches it loaded.
         */
        fun load(scryfallDir: Path, briefsDir: Path, setCode: String): BaselineBuilder {
            val index = CardIndex.load(scryfallDir, setCode)
            val fetchedAt = index.setCodes.maxOf { ScryfallCache.load(scryfallDir, it).fetchedAt }
            val brief = BriefLoader.load(briefsDir.resolve("$setCode.json"))
            return BaselineBuilder(index, brief, fetchedAt.toString())
        }

        /**
         * One [Stat] per pointer in any of [flattened], sorted by pointer. A kit without a pointer
         * counts as 0 for it.
         */
        internal fun aggregate(flattened: List<Map<String, Double>>): Map<String, Stat> =
            flattened
                .flatMapTo(sortedSetOf()) { it.keys }
                .associateWith { pointer -> Stat.of(flattened.map { it[pointer] ?: 0.0 }) }
    }
}
