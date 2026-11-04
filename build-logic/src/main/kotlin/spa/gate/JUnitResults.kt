package spa.gate

import spa.gate.Xml.all
import spa.gate.Xml.children

/**
 * A failed test case from a JUnit XML report. [fileName] and [line] come from the first stack frame
 * inside the test class: the failing assertion's line.
 */
data class TestFailure(
    val className: String,
    val testName: String,
    val message: String,
    val fileName: String?,
    val line: Int?,
)

/** Reads Gradle's JUnit XML test reports for the `unit-tests` step. */
object JUnitResults {
    private val frame = Regex("^\\s*at\\s+(.+)\\(([^():]+\\.(?:kt|java)):(\\d+)\\)\\s*$")

    fun parse(xml: String): List<TestFailure> =
        Xml.parse(xml).all("testcase").flatMap { case ->
            val className = case.getAttribute("classname")
            (case.children("failure") + case.children("error")).map { problem ->
                val trace = problem.textContent.orEmpty()
                val location = locate(className, trace)
                TestFailure(
                    className = className,
                    testName = case.getAttribute("name"),
                    message =
                        problem.getAttribute("message").ifBlank {
                            trace.lineSequence().firstOrNull().orEmpty().trim()
                        },
                    fileName = location?.first,
                    line = location?.second,
                )
            }
        }

    /** How many test cases a report holds, passed or not. */
    fun testCount(xml: String): Int = Xml.parse(xml).all("testcase").size

    /** File name and line of the first stack frame whose method belongs to [className]. */
    fun locate(className: String, stackTrace: String): Pair<String, Int>? {
        val outer = className.substringBefore('$')
        return stackTrace
            .lineSequence()
            .mapNotNull { frame.find(it) }
            .firstOrNull { match ->
                val method = match.groupValues[1]
                method.startsWith("$outer.") || method.startsWith("$outer$")
            }
            ?.let { match ->
                val (_, file, line) = match.destructured
                file to line.toInt()
            }
    }

    /**
     * The repository-relative source path of a failure: the package directory of the class under
     * the first of [sourceRoots] where [exists] finds it, else under the first root.
     */
    fun sourcePath(
        failure: TestFailure,
        sourceRoots: List<String>,
        exists: (String) -> Boolean,
    ): String? {
        val fileName = failure.fileName ?: return null
        val packageDir = failure.className.substringBeforeLast('.', "").replace('.', '/')
        val relative = if (packageDir.isEmpty()) fileName else "$packageDir/$fileName"
        val candidates = sourceRoots.map { it.trimEnd('/') + "/" + relative }
        return candidates.firstOrNull(exists) ?: candidates.firstOrNull()
    }

    /** The class to name in `--tests`: the top-level class, so nested classes run too. */
    fun testClassFilter(className: String): String = className.substringBefore('$')
}
