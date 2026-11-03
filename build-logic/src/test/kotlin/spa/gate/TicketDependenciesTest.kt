package spa.gate

import kotlin.test.Test
import kotlin.test.assertEquals

class TicketDependenciesTest {
    private val scope =
        """
        ## Scope

        - Files or modules expected to change:
          - `core/build.gradle.kts`
        - Not in scope:
          - An `app` module.
        - Dependencies this ticket may add (none unless listed). Format: `group:artifact` for a library and `plugin:<plugin id>` for a plugin.
          - `plugin:org.jetbrains.kotlin.jvm`
          - `org.jetbrains.kotlinx:kotlinx-serialization-json`
          * `org.junit.jupiter:junit-jupiter` (tests only)

        Versions are the Developer's choice: `not:this-one`.
        - `also:not-this`
        """
            .trimIndent()

    @Test
    fun `reads backticked coordinates from the sub-bullets only`() {
        assertEquals(
            setOf(
                "plugin:org.jetbrains.kotlin.jvm",
                "org.jetbrains.kotlinx:kotlinx-serialization-json",
                "org.junit.jupiter:junit-jupiter",
            ),
            TicketDependencies.allowed(scope),
        )
    }

    @Test
    fun `examples on the heading line itself are not allowed coordinates`() {
        val allowed = TicketDependencies.allowed(scope)
        assertEquals(false, "group:artifact" in allowed)
    }

    @Test
    fun `no list, or a list that says none, allows nothing`() {
        assertEquals(emptySet(), TicketDependencies.allowed("## Scope\n- Files: x\n"))
        assertEquals(
            emptySet(),
            TicketDependencies.allowed(
                "- Dependencies this ticket may add (none unless listed).\n  - none\n"
            ),
        )
    }

    @Test
    fun `windows line endings are read the same way`() {
        assertEquals(
            TicketDependencies.allowed(scope),
            TicketDependencies.allowed(scope.replace("\n", "\r\n")),
        )
    }
}
