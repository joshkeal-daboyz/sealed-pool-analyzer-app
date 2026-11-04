package spa.core.baseline

import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** The trk rebuild's time budget, from SPA-008's criterion 4: under 60 seconds on CI. */
val trkRebuildBudget: Duration = 60.seconds

/**
 * Asserts that [took] is under [budget] only when [ci], the value of the `CI` environment variable,
 * is exactly `true`. Anywhere else, such as a busy developer PC, any duration passes.
 */
fun assertRebuildBudget(took: Duration, ci: String?, budget: Duration = trkRebuildBudget) {
    if (ci == "true") {
        assertTrue(took < budget, "trk rebuild took $took, budget $budget on CI")
    }
}
