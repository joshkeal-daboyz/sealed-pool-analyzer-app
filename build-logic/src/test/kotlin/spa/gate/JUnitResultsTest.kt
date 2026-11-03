package spa.gate

import kotlin.test.Test
import kotlin.test.assertEquals

class JUnitResultsTest {
    /** Shape copied from a real Gradle 9.8 JUnit XML report of a failing kotlin.test assertion. */
    private val xml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <testsuite name="spa.core.SampleTest" tests="2" skipped="0" failures="1" errors="1" time="0.019">
          <properties/>
          <testcase name="half of four()" classname="spa.core.SampleTest" time="0.018">
            <failure message="org.opentest4j.AssertionFailedError: expected: &lt;3&gt; but was: &lt;2&gt;" type="org.opentest4j.AssertionFailedError">org.opentest4j.AssertionFailedError: expected: &lt;3&gt; but was: &lt;2&gt;
        	at org.junit.jupiter.api.Assertions.assertEquals(Assertions.java:1210)
        	at kotlin.test.junit5.JUnit5Asserter.assertEquals(JUnitSupport.kt:32)
        	at kotlin.test.AssertionsKt.assertEquals(Unknown Source)
        	at spa.core.SampleTest.half of four(SampleTest.kt:9)
        </failure>
          </testcase>
          <testcase name="passes()" classname="spa.core.SampleTest" time="0.001"/>
          <testcase name="inner()" classname="spa.core.SampleTest${'$'}Nested" time="0.001">
            <error type="java.lang.IllegalStateException">java.lang.IllegalStateException: boom
        	at spa.core.Helper.explode(Helper.kt:4)
        	at spa.core.SampleTest${'$'}Nested.inner(SampleTest.kt:15)
        </error>
          </testcase>
          <system-out><![CDATA[]]></system-out>
        </testsuite>
        """
            .trimIndent()

    @Test
    fun `reads failures and errors with the failing line inside the test class`() {
        assertEquals(
            listOf(
                TestFailure(
                    "spa.core.SampleTest",
                    "half of four()",
                    "org.opentest4j.AssertionFailedError: expected: <3> but was: <2>",
                    "SampleTest.kt",
                    9,
                ),
                TestFailure(
                    "spa.core.SampleTest\$Nested",
                    "inner()",
                    "java.lang.IllegalStateException: boom",
                    "SampleTest.kt",
                    15,
                ),
            ),
            JUnitResults.parse(xml),
        )
    }

    @Test
    fun `no frame inside the class means no location`() {
        assertEquals(
            null,
            JUnitResults.locate("spa.core.Other", "\tat spa.core.Helper.explode(Helper.kt:4)"),
        )
    }

    @Test
    fun `source path is the package directory under the root where the file exists`() {
        val failure = TestFailure("spa.core.SampleTest", "t()", "m", "SampleTest.kt", 9)
        val roots = listOf("core/src/test/kotlin", "core/src/test/java")
        assertEquals(
            "core/src/test/java/spa/core/SampleTest.kt",
            JUnitResults.sourcePath(failure, roots) { it.startsWith("core/src/test/java/") },
        )
        assertEquals(
            "core/src/test/kotlin/spa/core/SampleTest.kt",
            JUnitResults.sourcePath(failure, roots) { false },
        )
        assertEquals(null, JUnitResults.sourcePath(failure.copy(fileName = null), roots) { true })
    }

    @Test
    fun `the tests filter names the top-level class`() {
        assertEquals(
            "spa.core.SampleTest",
            JUnitResults.testClassFilter("spa.core.SampleTest\$Nested"),
        )
        assertEquals("spa.core.SmokeTest", JUnitResults.testClassFilter("spa.core.SmokeTest"))
    }
}
