package spa.core.baseline

import kotlin.math.ceil
import kotlinx.serialization.Serializable

/**
 * One metric's spread over the sampled kits, every value rounded to 4 decimals.
 *
 * @property median the 50th percentile.
 * @property p10 the 10th percentile.
 * @property p90 the 90th percentile.
 * @property mean the arithmetic mean.
 */
@Serializable
data class Stat(val median: Double, val p10: Double, val p90: Double, val mean: Double) {
    companion object {
        private const val ROUNDING = 10_000.0
        private const val P10 = 10
        private const val P50 = 50
        private const val P90 = 90
        private const val PERCENT = 100.0

        /** The stat of [values], percentiles by the nearest-rank method. [values] is not empty. */
        fun of(values: List<Double>): Stat {
            require(values.isNotEmpty()) { "A stat needs at least one value." }
            val sorted = values.sorted()
            return Stat(
                median = round(nearestRank(sorted, P50)),
                p10 = round(nearestRank(sorted, P10)),
                p90 = round(nearestRank(sorted, P90)),
                mean = round(sorted.sum() / sorted.size),
            )
        }

        /** The value at rank `ceil(percentile / 100 * n)` of [sorted], ranks counted from 1. */
        private fun nearestRank(sorted: List<Double>, percentile: Int): Double {
            val rank = ceil(percentile * sorted.size / PERCENT).toInt().coerceIn(1, sorted.size)
            return sorted[rank - 1]
        }

        private fun round(value: Double): Double = Math.round(value * ROUNDING) / ROUNDING
    }
}
