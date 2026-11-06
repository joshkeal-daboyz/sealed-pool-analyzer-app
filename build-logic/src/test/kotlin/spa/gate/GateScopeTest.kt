package spa.gate

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GateScopeTest {
    private val everyModule = setOf("core", "agent", "cli", "app")

    private fun scope(vararg paths: String, full: Boolean = false, mergeBase: Boolean = true) =
        GateScope.of(paths.toList(), full, mergeBase)

    private fun assertScoped(expected: Set<String>, actual: GateScope) {
        assertEquals(expected, actual.modules)
        assertFalse(actual.full, "expected a scoped run, got the full gate")
        assertFalse(actual.docsOnly)
    }

    private fun assertFull(actual: GateScope) {
        assertTrue(actual.full, "expected the full gate")
        assertFalse(actual.docsOnly)
        assertEquals(everyModule, actual.modules)
    }

    @Test
    fun `only documentation paths are docs-only`() {
        val actual =
            scope("tickets/SPA-001.md", "design/x.png", "README.md", "pipeline/roles/developer.md")
        assertTrue(actual.docsOnly)
    }

    @Test
    fun `documentation paths are tickets, design and any Markdown file`() {
        assertTrue(GateScope.isDocumentation("tickets/goals/G-005.md"))
        assertTrue(GateScope.isDocumentation("design/screens/home.png"))
        assertTrue(GateScope.isDocumentation("core/NOTES.md"))
        assertFalse(GateScope.isDocumentation("pipeline/gate/run.sh"))
        assertFalse(GateScope.isDocumentation("designs/x.png"))
    }

    @Test
    fun `a cli change runs cli`() {
        assertScoped(setOf("cli"), scope("cli/src/main/kotlin/A.kt"))
    }

    @Test
    fun `an agent change runs agent and cli`() {
        assertScoped(setOf("agent", "cli"), scope("agent/src/main/kotlin/spa/agent/A.kt"))
    }

    @Test
    fun `a core change runs core, agent and cli`() {
        assertScoped(setOf("core", "agent", "cli"), scope("core/src/main/kotlin/spa/core/A.kt"))
    }

    @Test
    fun `a fixtures change runs core, agent and cli`() {
        assertScoped(setOf("core", "agent", "cli"), scope("fixtures/tags/trk.json"))
    }

    @Test
    fun `an app change alone runs app`() {
        assertScoped(setOf("app"), scope("app/src/main/kotlin/spa/app/A.kt"))
    }

    @Test
    fun `an app change with a cli change runs app and cli`() {
        assertScoped(
            setOf("app", "cli"),
            scope("app/src/main/kotlin/spa/app/A.kt", "cli/src/main/kotlin/A.kt"),
        )
    }

    @Test
    fun `a build-logic change runs the full gate`() {
        assertFull(scope("build-logic/src/main/kotlin/spa/gate/GatePlugin.kt"))
    }

    @Test
    fun `a version catalog change runs the full gate`() {
        assertFull(scope("gradle/libs.versions.toml"))
    }

    @Test
    fun `a gate script change runs the full gate`() {
        assertFull(scope("pipeline/gate/run.sh"))
    }

    @Test
    fun `a workflow change runs the full gate`() {
        assertFull(scope(".github/workflows/gate.yml"))
    }

    @Test
    fun `a root build file change runs the full gate`() {
        assertFull(scope("settings.gradle.kts"))
    }

    @Test
    fun `an unknown top-level folder runs the full gate`() {
        assertFull(scope("newthing/a.txt"))
    }

    @Test
    fun `the full flag runs the full gate even for a cli change`() {
        assertFull(scope("cli/src/main/kotlin/A.kt", full = true))
    }

    @Test
    fun `no merge-base runs the full gate`() {
        assertFull(scope("cli/src/main/kotlin/A.kt", mergeBase = false))
    }

    @Test
    fun `an empty change list runs the full gate`() {
        assertFull(scope())
    }

    @Test
    fun `documentation paths mixed with a cli change run cli`() {
        assertScoped(
            setOf("cli"),
            scope("tickets/SPA-057.md", "README.md", "design/x.png", "cli/src/main/kotlin/A.kt"),
        )
    }

    @Test
    fun `a full trigger among module changes runs the full gate`() {
        assertFull(scope("cli/src/main/kotlin/A.kt", "config/detekt/detekt.yml"))
    }

    @Test
    fun `Windows separators are read as slashes`() {
        assertScoped(setOf("cli"), scope("cli\\src\\main\\kotlin\\A.kt"))
    }
}
