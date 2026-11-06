package spa.gate

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** A snapshot named in a Roborazzi verify failure, and whether it differed or had no baseline. */
data class SnapshotProblem(val name: String, val baselinePath: String, val missing: Boolean)

/**
 * The `ui-tests` step's verdict logic: the app's JVM Compose UI tests and snapshot verification
 * (Robolectric and Roborazzi), read from the JUnit XML and Roborazzi's results summary.
 */
object UiTests {
    private val step = GateStep.UI_TESTS.id

    private val changed = Regex("Roborazzi: (.+?\\.png) is changed")
    private val missing = Regex("Roborazzi: The original file\\((.+?\\.png)\\) was not found")

    /**
     * The command that reruns the app's JVM tests the way the gate does: the app module's debug
     * unit-test task, by project path, in Roborazzi's verify mode. A failure appends `--tests`,
     * which is an option of the Test task, so this names that task and not `verifyRoborazziDebug`.
     */
    fun reproduceCommand(appPath: String): String =
        "./gradlew $appPath:testDebugUnitTest -Proborazzi.test.verify=true"

    /** The note when there is no app module to test. */
    const val NO_APP_MODULE = "skipped: no app module"

    /** The note when the app exists but the change does not reach it. */
    const val NO_APP_CHANGES = "skipped: no app changes"

    /** Why the step is skipped, or null when it runs the app's tests. */
    fun skipNote(hasAppModule: Boolean, appInScope: Boolean): String? =
        when {
            !hasAppModule -> NO_APP_MODULE
            !appInScope -> NO_APP_CHANGES
            else -> null
        }

    /** The pass note: how many UI tests and snapshots ran. */
    fun note(tests: Int, snapshots: Int): String =
        "${plural(tests, "UI test")} and ${plural(snapshots, "snapshot")} ran"

    /** How many snapshots Roborazzi compared, from its `results-summary.json`. */
    fun snapshotCount(summaryJson: String): Int =
        Json.parseToJsonElement(summaryJson)
            .jsonObject
            .getValue("summary")
            .jsonObject
            .getValue("total")
            .jsonPrimitive
            .int

    /** The snapshot a Roborazzi verify failure names, or null for an ordinary test failure. */
    fun snapshotProblem(message: String): SnapshotProblem? {
        val differs = changed.find(message)
        val absent = missing.find(message)
        val path = (differs ?: absent)?.groupValues?.get(1) ?: return null
        val name = path.replace('\\', '/').substringAfterLast('/').removeSuffix(".png")
        return SnapshotProblem(name, path, missing = differs == null)
    }

    /**
     * One gate failure per failed test. A snapshot failure names the snapshot and its baseline; any
     * other failure names the test's file and line, as the unit-tests step does.
     */
    fun failures(
        failed: List<TestFailure>,
        rootPath: String,
        source: GateUnitTestsTask.ResultSource,
        exists: (String) -> Boolean,
    ): List<GateFailure> = failed.map { failure ->
        val reproduce =
            "${source.command} --tests ${JUnitResults.testClassFilter(failure.className)}"
        val snapshot = snapshotProblem(failure.message)
        if (snapshot == null) {
            GateFailure(
                step,
                JUnitResults.sourcePath(failure, source.sourceRoots, exists),
                failure.line,
                "${failure.testName}: ${failure.message}",
                reproduce,
            )
        } else {
            val baseline = RepoPaths.relative(rootPath, snapshot.baselinePath)
            val what =
                if (snapshot.missing) "has no stored baseline at $baseline"
                else "differs from its stored baseline $baseline"
            GateFailure(
                step,
                baseline,
                null,
                "Snapshot ${snapshot.name} $what (test ${failure.testName}). The gate never " +
                    "records baselines; if the change is intended, the ticket must say so.",
                reproduce,
            )
        }
    }

    private fun plural(count: Int, noun: String) = if (count == 1) "1 $noun" else "$count ${noun}s"
}
