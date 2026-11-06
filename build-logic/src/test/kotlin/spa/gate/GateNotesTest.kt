package spa.gate

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GateNotesTest {
    @Test
    fun `the unit-tests note names the modules run`() {
        assertEquals("modules: cli", GateScope.unitTestsNote(listOf("cli")))
    }

    @Test
    fun `the unit-tests note lists modules in a fixed order`() {
        assertEquals(
            "modules: core, agent, cli, build-logic",
            GateScope.unitTestsNote(listOf("build-logic", "cli", "core", "agent")),
        )
    }

    @Test
    fun `the unit-tests note says none when no module ran`() {
        assertEquals("modules: none", GateScope.unitTestsNote(emptyList()))
    }

    @Test
    fun `ui-tests is skipped with a note when the app is out of scope`() {
        assertEquals(
            "skipped: no app changes",
            UiTests.skipNote(hasAppModule = true, appInScope = false),
        )
    }

    @Test
    fun `ui-tests is skipped with the no-app-module note when there is no app`() {
        assertEquals(
            "skipped: no app module",
            UiTests.skipNote(hasAppModule = false, appInScope = false),
        )
        assertEquals(
            "skipped: no app module",
            UiTests.skipNote(hasAppModule = false, appInScope = true),
        )
    }

    @Test
    fun `ui-tests runs when the app is in scope`() {
        assertNull(UiTests.skipNote(hasAppModule = true, appInScope = true))
    }
}
