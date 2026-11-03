package spa.core.metrics

import kotlinx.serialization.Serializable

/**
 * Every card-level number for one pool, counted by copies. Computed by [MetricEngine.compute].
 *
 * @property totalCards every copy in the pool, lands included.
 * @property nonlandCards copies that are not lands.
 * @property basicLands copies of basic lands.
 * @property nonbasicLands copies of other lands.
 * @property byColor the nonland cards per [ColorBucket]; every bucket is a key.
 * @property fixingSources the pool's fixing sources, by name.
 * @property removalTotal nonland copies tagged [spa.core.text.Tag.REMOVAL].
 * @property interactionTotal nonland copies with at least one interaction tag.
 * @property pairs the numbers per [ColorPair]; all ten pairs are keys.
 */
@Serializable
data class PoolMetrics(
    val totalCards: Int,
    val nonlandCards: Int,
    val basicLands: Int,
    val nonbasicLands: Int,
    val byColor: Map<ColorBucket, ColorMetrics>,
    val fixingSources: List<FixingSource>,
    val removalTotal: Int,
    val interactionTotal: Int,
    val pairs: Map<ColorPair, ColorPairMetrics>,
)
