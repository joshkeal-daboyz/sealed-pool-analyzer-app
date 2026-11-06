package spa.agent.review

import spa.core.baseline.Baseline
import spa.core.brief.FormatBrief
import spa.core.cards.Pool
import spa.core.cards.Printing
import spa.core.intake.KitWarning
import spa.core.metrics.PoolMetrics

/**
 * Everything the pool review reads, resolved: the [set] code, the [pool], its [metrics], the set's
 * [baseline] (null when none is built), the set's format [brief], the set grade of a printing
 * ([gradeOf], null when it has none), and the pool's [kitWarnings].
 */
data class ReviewInput(
    val set: String,
    val pool: Pool,
    val metrics: PoolMetrics,
    val baseline: Baseline?,
    val brief: FormatBrief,
    val gradeOf: (Printing) -> Double?,
    val kitWarnings: List<KitWarning>,
)
