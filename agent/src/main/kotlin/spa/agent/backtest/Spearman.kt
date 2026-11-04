package spa.agent.backtest

/** Spearman's rank correlation: the Pearson correlation of two lists' ranks. */
object Spearman {
    /**
     * The 1-based ranks of [values], smallest first. Tied values share the average of the ranks
     * they span, so `[2, 1, 2, 3]` ranks `[2.5, 1, 2.5, 4]`.
     */
    fun ranks(values: List<Double>): List<Double> = TODO("SPA-032")

    /**
     * Spearman's rho of the pairs `(x[i], y[i])`, from -1 to 1. Null when it is undefined: fewer
     * than two pairs, or either list constant. The lists must be the same length.
     */
    fun rho(x: List<Double>, y: List<Double>): Double? = TODO("SPA-032")
}
