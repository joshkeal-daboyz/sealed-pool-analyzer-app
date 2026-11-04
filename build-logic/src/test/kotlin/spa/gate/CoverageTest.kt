package spa.gate

import kotlin.test.Test
import kotlin.test.assertEquals

class CoverageTest {
    /** Shape copied from a real Kover 0.9.11 `koverXmlReport` of `core`. */
    private val koverXml =
        """
        <?xml version="1.0" ?>
        <!DOCTYPE report PUBLIC "-//JACOCO//DTD Report 1.1//EN" "report.dtd">
        <report name="Kover Gradle Plugin XML report for :core">
        <package name="spa/core">
        <class name="spa/core/SampleKt" sourcefilename="Sample.kt">
        <method name="half" desc="(I)I">
        <counter type="LINE" missed="1" covered="2"/>
        </method>
        <counter type="LINE" missed="1" covered="2"/>
        </class>
        <sourcefile name="Sample.kt">
        <line nr="4" mi="0" ci="2" mb="1" cb="1"/>
        <line nr="5" mi="1" ci="0" mb="0" cb="0"/>
        <line nr="7" mi="0" ci="3" mb="0" cb="0"/>
        <counter type="INSTRUCTION" missed="1" covered="5"/>
        <counter type="LINE" missed="1" covered="2"/>
        </sourcefile>
        <sourcefile name="Full.kt">
        <counter type="LINE" missed="0" covered="4"/>
        </sourcefile>
        <counter type="LINE" missed="1" covered="6"/>
        </package>
        <package name="spa/core/pool">
        <sourcefile name="Pool.kt">
        <counter type="LINE" missed="3" covered="1"/>
        </sourcefile>
        </package>
        <counter type="LINE" missed="4" covered="7"/>
        </report>
        """
            .trimIndent()

    @Test
    fun `reads line coverage per source file keyed by package path`() {
        assertEquals(
            mapOf(
                "spa/core/Sample.kt" to LineCoverage(covered = 2, missed = 1),
                "spa/core/Full.kt" to LineCoverage(covered = 4, missed = 0),
                "spa/core/pool/Pool.kt" to LineCoverage(covered = 1, missed = 3),
            ),
            Coverage.parseKoverXml(koverXml),
        )
    }

    @Test
    fun `percent is covered over total, and a file with no lines is fully covered`() {
        assertEquals(25.0, LineCoverage(covered = 1, missed = 3).percent)
        assertEquals(100.0, LineCoverage(covered = 0, missed = 0).percent)
    }

    @Test
    fun `report key follows the package declaration, not the directory`() {
        assertEquals(
            "spa/core/pool/Pool.kt",
            Coverage.reportKey("Pool.kt", "// x\npackage spa.core.pool\n"),
        )
        assertEquals("Top.kt", Coverage.reportKey("Top.kt", "fun main() {}"))
    }

    @Test
    fun `touched files are Kotlin files under core main and cli main only`() {
        val changed =
            listOf(
                "core/src/main/kotlin/spa/core/Sample.kt",
                "core\\src\\main\\kotlin\\spa\\core\\Windows.kt",
                "core/src/test/kotlin/spa/core/SampleTest.kt",
                "cli/src/main/kotlin/spa/cli/Main.kt",
                "cli/src/test/kotlin/spa/cli/MainTest.kt",
                "build-logic/src/main/kotlin/spa/gate/Coverage.kt",
                "core/src/main/kotlin/spa/core/notes.md",
                "core/src/main/kotlin/spa/core/Sample.kt",
            )
        assertEquals(
            listOf(
                "cli/src/main/kotlin/spa/cli/Main.kt",
                "core/src/main/kotlin/spa/core/Sample.kt",
                "core/src/main/kotlin/spa/core/Windows.kt",
            ),
            Coverage.touchedMainFiles(changed),
        )
    }

    @Test
    fun `each touched file is checked against its own module's Kover report`() {
        assertEquals(
            "core/build/reports/kover/report.xml",
            Coverage.reportPath("core/src/main/kotlin/spa/core/Sample.kt"),
        )
        assertEquals(
            "cli/build/reports/kover/report.xml",
            Coverage.reportPath("cli/src/main/kotlin/spa/cli/Main.kt"),
        )
    }

    @Test
    fun `app sources are covered, against the app's debug-variant Kover report`() {
        assertEquals(
            listOf("app/src/main/kotlin/spa/app/MainActivity.kt"),
            Coverage.touchedMainFiles(
                listOf("app/src/main/kotlin/spa/app/MainActivity.kt", "app/src/test/kotlin/X.kt")
            ),
        )
        assertEquals(
            "app/build/reports/kover/reportDebug.xml",
            Coverage.reportPath("app/src/main/kotlin/spa/app/PlaceholderScreen.kt"),
        )
    }

    @Test
    fun `agent sources are covered, against the agent's own Kover report`() {
        assertEquals(
            listOf("agent/src/main/kotlin/spa/agent/ApiKey.kt"),
            Coverage.touchedMainFiles(
                listOf(
                    "agent/src/main/kotlin/spa/agent/ApiKey.kt",
                    "agent/src/test/kotlin/spa/agent/ApiKeyTest.kt",
                    "agent/src/main/resources/spa/agent/grades.schema.json",
                )
            ),
        )
        assertEquals(
            "agent/build/reports/kover/report.xml",
            Coverage.reportPath("agent/src/main/kotlin/spa/agent/ApiKey.kt"),
        )
    }

    @Test
    fun `a touched app file below the threshold fails, naming it`() {
        val failure =
            Coverage.failures(
                    listOf(
                        TouchedSource(
                            "app/src/main/kotlin/spa/app/MainActivity.kt",
                            "spa/app/MainActivity.kt",
                        )
                    ),
                    mapOf("spa/app/MainActivity.kt" to LineCoverage(covered = 1, missed = 3)),
                    70.0,
                    "r",
                )
                .single()
        assertEquals("app/src/main/kotlin/spa/app/MainActivity.kt", failure.file)
        assertEquals("Line coverage 25% is below the ticket threshold 70%.", failure.message)
    }

    @Test
    fun `one failure per touched file below the threshold, giving both percentages`() {
        val coverage = Coverage.parseKoverXml(koverXml)
        val touched =
            listOf(
                TouchedSource("core/src/main/kotlin/spa/core/Sample.kt", "spa/core/Sample.kt"),
                TouchedSource("core/src/main/kotlin/spa/core/Full.kt", "spa/core/Full.kt"),
                TouchedSource(
                    "core/src/main/kotlin/spa/core/pool/Pool.kt",
                    "spa/core/pool/Pool.kt",
                ),
            )
        assertEquals(
            listOf(
                GateFailure(
                    "coverage",
                    "core/src/main/kotlin/spa/core/Sample.kt",
                    null,
                    "Line coverage 66.7% is below the ticket threshold 80%.",
                    "r",
                ),
                GateFailure(
                    "coverage",
                    "core/src/main/kotlin/spa/core/pool/Pool.kt",
                    null,
                    "Line coverage 25% is below the ticket threshold 80%.",
                    "r",
                ),
            ),
            Coverage.failures(touched, coverage, 80.0, "r"),
        )
    }

    @Test
    fun `zero touched files pass, and a zero threshold always passes`() {
        val coverage = Coverage.parseKoverXml(koverXml)
        assertEquals(emptyList(), Coverage.failures(emptyList(), coverage, 90.0, "r"))
        val poor =
            listOf(
                TouchedSource("core/src/main/kotlin/spa/core/pool/Pool.kt", "spa/core/pool/Pool.kt")
            )
        assertEquals(emptyList(), Coverage.failures(poor, coverage, 0.0, "r"))
        assertEquals(emptyList(), Coverage.failures(poor, null, 0.0, "r"))
    }

    @Test
    fun `a file with no executable lines passes, a missing report does not`() {
        val declarationsOnly =
            listOf(TouchedSource("core/src/main/kotlin/spa/core/Types.kt", "spa/core/Types.kt"))
        assertEquals(
            emptyList(),
            Coverage.failures(declarationsOnly, Coverage.parseKoverXml(koverXml), 80.0, "r"),
        )
        assertEquals(
            "Line coverage 0% is below the ticket threshold 80% (no Kover report was produced).",
            Coverage.failures(declarationsOnly, null, 80.0, "r").single().message,
        )
    }
}
