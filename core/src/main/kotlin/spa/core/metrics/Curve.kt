package spa.core.metrics

import kotlinx.serialization.Serializable

/**
 * One mana value's worth of a curve.
 *
 * @property manaValue the bucket's mana value; the last bucket, [Curve.TOP], holds that value and
 *   everything above it.
 */
@Serializable
data class CurveBucket(val manaValue: Int, val creatures: Int, val noncreatures: Int) {
    /** Every card in the bucket. */
    val total: Int
        get() = creatures + noncreatures
}

/**
 * A mana curve: always eight [buckets], for mana value 0 to 6 and then 7 or more, in that order. A
 * card's bucket is its mana value rounded down.
 */
@Serializable
data class Curve(val buckets: List<CurveBucket>) {
    companion object {
        /** The mana value of the last bucket, which holds that value and everything above it. */
        const val TOP = 7
    }
}
