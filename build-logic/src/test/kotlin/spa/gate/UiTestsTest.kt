package spa.gate

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UiTestsTest {
    private val root = "D:\\repo"

    private val source =
        GateUnitTestsTask.ResultSource(
            File("app/build/test-results/testDebugUnitTest"),
            listOf("app/src/test/kotlin", "app/src/test/java"),
            "./gradlew :app:testDebugUnitTest -Proborazzi.test.verify=true",
        )

    /**
     * Shape copied from a real Robolectric run: a Compose UI test and a Roborazzi verify failure.
     */
    private val xml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <testsuite name="spa.app.ShellSnapshotTest" tests="3" skipped="0" failures="2" errors="0">
          <testcase name="showsTheAppName" classname="spa.app.PlaceholderScreenTest" time="1.2">
            <failure message="java.lang.AssertionError: Failed: assertExists." type="java.lang.AssertionError">java.lang.AssertionError: Failed: assertExists.
        	at androidx.compose.ui.test.SemanticsNodeInteraction.fetchSemanticsNode(SemanticsNodeInteraction.kt:145)
        	at spa.app.PlaceholderScreenTest.showsTheAppName(PlaceholderScreenTest.kt:21)
        </failure>
          </testcase>
          <testcase name="shellPlaceholder" classname="spa.app.ShellSnapshotTest" time="2.0">
            <failure message="java.lang.AssertionError: Roborazzi: D:\repo\app\src\test\snapshots\shell_placeholder.png is changed.&#10;See the compare image at D:\repo\app\build\outputs\roborazzi\shell_placeholder_compare.png" type="java.lang.AssertionError">java.lang.AssertionError: Roborazzi: D:\repo\app\src\test\snapshots\shell_placeholder.png is changed.
        	at com.github.takahirom.roborazzi.RoborazziKt.processOutputImageAndReport(Roborazzi.kt:1)
        	at spa.app.ShellSnapshotTest.shellPlaceholder(ShellSnapshotTest.kt:21)
        </failure>
          </testcase>
          <testcase name="fillsThe411By914DpScreen" classname="spa.app.PlaceholderScreenTest" time="0.1"/>
        </testsuite>
        """
            .trimIndent()

    @Test
    fun `the pass note names how many UI tests and snapshots ran`() {
        assertEquals("7 UI tests and 1 snapshot ran", UiTests.note(7, 1))
        assertEquals("1 UI test and 2 snapshots ran", UiTests.note(1, 2))
    }

    @Test
    fun `counts snapshots from the Roborazzi results summary`() {
        val summary =
            """{"summary":{"total":1,"recorded":0,"added":0,"changed":0,"unchanged":1},"results":[]}"""
        assertEquals(1, UiTests.snapshotCount(summary))
        assertEquals(3, JUnitResults.testCount(xml))
    }

    @Test
    fun `a failing Compose UI test is named with its file and line`() {
        val failures = UiTests.failures(JUnitResults.parse(xml), root, source) { true }
        assertEquals(
            GateFailure(
                "ui-tests",
                "app/src/test/kotlin/spa/app/PlaceholderScreenTest.kt",
                21,
                "showsTheAppName: java.lang.AssertionError: Failed: assertExists.",
                "./gradlew :app:testDebugUnitTest -Proborazzi.test.verify=true " +
                    "--tests spa.app.PlaceholderScreenTest",
            ),
            failures[0],
        )
    }

    @Test
    fun `a snapshot that differs from its baseline is named, with the baseline as the file`() {
        val failure = UiTests.failures(JUnitResults.parse(xml), root, source) { true }[1]
        assertEquals("ui-tests", failure.step)
        assertEquals("app/src/test/snapshots/shell_placeholder.png", failure.file)
        assertNull(failure.line)
        assertEquals(
            "Snapshot shell_placeholder differs from its stored baseline " +
                "app/src/test/snapshots/shell_placeholder.png (test shellPlaceholder). The gate " +
                "never records baselines; if the change is intended, the ticket must say so.",
            failure.message,
        )
    }

    @Test
    fun `a snapshot with no baseline is named as missing`() {
        val message =
            "java.lang.AssertionError: Roborazzi: The original file(D:\\repo\\app\\src\\test\\" +
                "snapshots\\shell_placeholder.png) was not found.\nSee the actual image at x.png"
        assertEquals(
            SnapshotProblem(
                "shell_placeholder",
                "D:\\repo\\app\\src\\test\\snapshots\\shell_placeholder.png",
                missing = true,
            ),
            UiTests.snapshotProblem(message),
        )
        assertNull(UiTests.snapshotProblem("java.lang.AssertionError: Failed: assertExists."))
    }
}
