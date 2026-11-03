package spa.core.math

import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext

/**
 * The hypergeometric distribution: draws without replacement from a population that holds some
 * successes, such as lands in a deck. Every result is computed exactly in integers, as
 * C(K,i)·C(N-K,n-i)/C(N,n) summed over the outcomes asked for, and rounded to a [Double] once at
 * the end.
 *
 * Parameters everywhere: `populationSize` N >= 0 (cards in the deck), `successes` K in 0..N
 * (successes in the deck), `draws` n in 0..N (cards seen), and `k` the number of successes asked
 * about. Out-of-range N, K, or n throw [IllegalArgumentException].
 */
object Hypergeometric {
    private val precision = MathContext.DECIMAL128

    /** P(X = k): the chance of exactly [k] successes. Zero for any impossible [k]. */
    fun pmf(populationSize: Int, successes: Int, draws: Int, k: Int): Double {
        validate(populationSize, successes, draws)
        return ratio(ways(populationSize, successes, draws, k), total(populationSize, draws))
    }

    /** P(X >= k): the chance of at least [k] successes. One for [k] <= 0. */
    fun atLeast(populationSize: Int, successes: Int, draws: Int, k: Int): Double {
        validate(populationSize, successes, draws)
        val ways =
            (maxOf(k, 0)..minOf(draws, successes)).fold(BigInteger.ZERO) { sum, i ->
                sum + ways(populationSize, successes, draws, i)
            }
        return ratio(ways, total(populationSize, draws))
    }

    private fun validate(populationSize: Int, successes: Int, draws: Int) {
        require(populationSize >= 0) { "populationSize must be >= 0, got $populationSize" }
        require(successes in 0..populationSize) {
            "successes must be in 0..$populationSize, got $successes"
        }
        require(draws in 0..populationSize) { "draws must be in 0..$populationSize, got $draws" }
    }

    private fun ways(populationSize: Int, successes: Int, draws: Int, k: Int): BigInteger =
        binomial(successes, k) * binomial(populationSize - successes, draws - k)

    private fun total(populationSize: Int, draws: Int): BigInteger = binomial(populationSize, draws)

    private fun ratio(numerator: BigInteger, denominator: BigInteger): Double =
        BigDecimal(numerator).divide(BigDecimal(denominator), precision).toDouble()

    /** C(n, k), zero when k is outside 0..n. */
    private fun binomial(n: Int, k: Int): BigInteger {
        if (k !in 0..n) return BigInteger.ZERO
        val smaller = minOf(k, n - k)
        var result = BigInteger.ONE
        for (i in 1..smaller) {
            result =
                result * BigInteger.valueOf((n - smaller + i).toLong()) /
                    BigInteger.valueOf(i.toLong())
        }
        return result
    }
}
