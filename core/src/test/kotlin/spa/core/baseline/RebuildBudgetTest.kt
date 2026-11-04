package spa.core.baseline

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.seconds

/** Pins the rule that the trk rebuild's 60-second budget is enforced only when `CI` is `true`. */
class RebuildBudgetTest {
    @Test
    fun `61 seconds with CI true fails`() {
        assertFailsWith<AssertionError> { assertRebuildBudget(61.seconds, "true") }
    }

    @Test
    fun `exactly 60 seconds with CI true fails, since the budget is under 60 seconds`() {
        assertFailsWith<AssertionError> { assertRebuildBudget(60.seconds, "true") }
    }

    @Test
    fun `59 seconds with CI true passes`() {
        assertRebuildBudget(59.seconds, "true")
    }

    @Test
    fun `61 seconds with CI unset passes`() {
        assertRebuildBudget(61.seconds, null)
    }

    @Test
    fun `61 seconds with CI false passes`() {
        assertRebuildBudget(61.seconds, "false")
    }
}
