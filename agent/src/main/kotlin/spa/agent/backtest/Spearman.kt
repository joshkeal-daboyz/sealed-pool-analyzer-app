package spa.agent.backtest

import kotlin.math.sqrt

/** Spearman's rank correlation: the Pearson correlation of two lists' ranks. */
object Spearman {
    /**
     * The 1-based ranks of [values], smallest first. Tied values share the average of the ranks
     * they span, so `[2, 1, 2, 3]` ranks `[2.5, 1, 2.5, 4]`.
     */
    fun ranks(values: List<Double>): List<Double> {
        val order = values.indices.sortedBy { values[it] }
        val ranks = DoubleArray(values.size)
        var start = 0
        while (start < order.size) {
            var end = start
            while (end + 1 < order.size && values[order[end + 1]] == values[order[start]]) end++
            // Positions start..end hold one tied value; their 1-based ranks average to this.
            val average = (start + end) / 2.0 + 1
            for (position in start..end) ranks[order[position]] = average
            start = end + 1
        }
        return ranks.toList()
    }

    /**
     * Spearman's rho of the pairs `(x[i], y[i])`, from -1 to 1. Null when it is undefined: fewer
     * than two pairs, or either list constant. The lists must be the same length.
     */
    fun rho(x: List<Double>, y: List<Double>): Double? {
        require(x.size == y.size) { "x has ${x.size} values and y has ${y.size}" }
        if (x.size < 2) return null
        return pearson(ranks(x), ranks(y))
    }

    /** Pearson's correlation of [x] and [y], or null when either has no spread. */
    private fun pearson(x: List<Double>, y: List<Double>): Double? {
        val meanX = x.average()
        val meanY = y.average()
        var products = 0.0
        var squaresX = 0.0
        var squaresY = 0.0
        for (i in x.indices) {
            val dx = x[i] - meanX
            val dy = y[i] - meanY
            products += dx * dy
            squaresX += dx * dx
            squaresY += dy * dy
        }
        if (squaresX == 0.0 || squaresY == 0.0) return null
        return products / sqrt(squaresX * squaresY)
    }
}
