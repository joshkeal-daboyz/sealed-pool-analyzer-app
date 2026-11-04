package spa.gate

/**
 * Collects what each step did during one gate run and turns it into a verdict.
 *
 * Only the first failing step, in contract order, is reported: steps after it are `not_run` even if
 * something recorded an outcome for them. That keeps the verdict faithful to "stop at the first
 * failing step" whatever order Gradle happened to finish tasks in.
 */
class GateRun {
    private val passed = mutableMapOf<GateStep, String?>()
    private val failed = mutableMapOf<GateStep, MutableList<GateFailure>>()

    @Synchronized
    fun pass(step: GateStep, note: String? = null) {
        passed[step] = note ?: passed[step]
    }

    @Synchronized
    fun fail(step: GateStep, failures: List<GateFailure>) {
        require(failures.isNotEmpty()) { "a failing step needs at least one failure" }
        require(failures.all { it.step == step.id }) { "failures must belong to step ${step.id}" }
        failed.getOrPut(step) { mutableListOf() }.addAll(failures)
    }

    @Synchronized fun hasFailures(step: GateStep): Boolean = step in failed

    /** The first step that has neither passed nor failed: where an unexplained stop happened. */
    @Synchronized
    fun firstOpenStep(): GateStep =
        GateStep.entries.firstOrNull { it !in passed && it !in failed } ?: GateStep.entries.last()

    @Synchronized
    fun verdict(ticket: String, commit: String): Verdict {
        val firstFailed = GateStep.entries.firstOrNull { it in failed }
        val stopped = firstFailed ?: GateStep.entries.firstOrNull { it !in passed }
        val failures =
            when {
                firstFailed != null -> failed.getValue(firstFailed).toList()
                stopped != null -> listOf(stoppedFailure(stopped))
                else -> emptyList()
            }
        val steps =
            GateStep.entries.map { step ->
                val result =
                    when {
                        stopped == null -> VerdictJson.PASS
                        // A step before the stop that never finished (its tools were still waiting
                        // on an earlier task) did not pass.
                        step.ordinal < stopped.ordinal ->
                            if (step in passed) VerdictJson.PASS else VerdictJson.NOT_RUN
                        step == stopped -> VerdictJson.FAIL
                        else -> VerdictJson.NOT_RUN
                    }
                StepReport(step.id, result, passed[step].takeIf { result == VerdictJson.PASS })
            }
        val result = if (stopped == null) VerdictJson.PASS else VerdictJson.FAIL
        return Verdict(ticket, commit, result, failures, steps)
    }

    private fun stoppedFailure(step: GateStep) =
        GateFailure(
            step = step.id,
            file = null,
            line = null,
            message = "The build stopped before this step finished; read the Gradle output.",
            reproduce = "./gradlew check",
        )
}
