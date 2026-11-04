package spa.gate

import kotlin.test.Test
import kotlin.test.assertEquals

class ToolOutputTest {
    @Test
    fun `reads the files a failed ktfmt check names`() {
        val message =
            "Execution failed for task ':core:ktfmtCheckTest'.\n" +
                "[ktfmt] Found 2 files that are not properly formatted:\n" +
                "src\\test\\kotlin\\spa\\core\\Ugly.kt\n" +
                "src/main/kotlin/spa/core/Also.kt\n"
        assertEquals(
            listOf("src/test/kotlin/spa/core/Ugly.kt", "src/main/kotlin/spa/core/Also.kt"),
            ToolOutput.ktfmtFiles(message),
        )
        assertEquals(emptyList(), ToolOutput.ktfmtFiles("Ktfmt failed to run with 1 failures"))
    }

    @Test
    fun `reads Android Lint errors with file and line, skipping warnings`() {
        // Shape copied from a real AGP 9.5 lint-results-debug.xml, explanations trimmed.
        val xml =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <issues format="6" by="lint 9.5.0">
                <issue
                    id="OldTargetApi"
                    severity="Warning"
                    message="Not targeting the latest versions of Android."
                    category="Correctness">
                    <location
                        file="D:\repo\app\build.gradle.kts"
                        line="18"
                        column="9"/>
                </issue>
                <issue
                    id="ContentDescription"
                    severity="Error"
                    message="Missing `contentDescription` attribute on image"
                    category="Accessibility">
                    <location
                        file="D:\repo\app\src\main\res\layout\scratch.xml"
                        line="2"
                        column="2"/>
                </issue>
            </issues>
            """
                .trimIndent()
        assertEquals(
            listOf(
                Located(
                    "D:\\repo\\app\\src\\main\\res\\layout\\scratch.xml",
                    2,
                    "[ContentDescription] Missing `contentDescription` attribute on image",
                )
            ),
            ToolOutput.androidLintErrors(xml),
        )
    }

    @Test
    fun `names the Android Lint report of a lint task`() {
        assertEquals(
            "build/reports/lint-results-debug.xml",
            ToolOutput.androidLintReport("lintDebug"),
        )
        assertEquals("build/reports/lint-results.xml", ToolOutput.androidLintReport("lint"))
    }

    @Test
    fun `reads detekt checkstyle findings`() {
        val xml =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <checkstyle version="4.3">
            <file name="D:\repo\core\src\main\kotlin\spa\core\Magic.kt">
            	<error line="4" column="12" severity="error" message="This expression contains a magic number." source="detekt.MagicNumber" />
            </file>
            </checkstyle>
            """
                .trimIndent()
        assertEquals(
            listOf(
                Located(
                    "D:\\repo\\core\\src\\main\\kotlin\\spa\\core\\Magic.kt",
                    4,
                    "[MagicNumber] This expression contains a magic number.",
                )
            ),
            ToolOutput.detektFindings(xml),
        )
    }

    @Test
    fun `reads Kotlin compiler errors in both output formats and skips warnings`() {
        val output =
            """
            :core:compileKotlin
            w: file:///D:/repo/core/src/main/kotlin/spa/core/A.kt:1:1 Something to note.
            e: file:///D:/repo/core/src/main/kotlin/spa/core/Broken.kt:2:21 Unresolved reference 'undefinedThing'.
            e: file:///home/runner/work/repo/core/src/main/kotlin/My%20File.kt:7:3 Type mismatch.
            e: /old/style/Path.kt: (12, 5): Expecting ')'
            """
                .trimIndent()
        assertEquals(
            listOf(
                Located(
                    "D:/repo/core/src/main/kotlin/spa/core/Broken.kt",
                    2,
                    "Unresolved reference 'undefinedThing'.",
                ),
                Located(
                    "/home/runner/work/repo/core/src/main/kotlin/My File.kt",
                    7,
                    "Type mismatch.",
                ),
                Located("/old/style/Path.kt", 12, "Expecting ')'"),
            ),
            ToolOutput.compilerErrors(output),
        )
    }
}
