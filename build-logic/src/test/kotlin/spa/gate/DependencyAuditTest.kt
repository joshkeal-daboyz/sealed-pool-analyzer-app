package spa.gate

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DependencyAuditTest {
    private val base =
        """
        [libraries]
        json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version = "1.12.0" }
        """
            .trimIndent()

    private val head =
        base +
            "\nunlisted = { module = \"com.example:surprise\", version = \"1.0\" }" +
            "\nlisted = { module = \"org.junit.jupiter:junit-jupiter\", version = \"6.1.3\" }" +
            "\n\n[plugins]\nkover = { id = \"org.jetbrains.kotlinx.kover\", version = \"0.9.11\" }"

    private val ticket =
        """
        - Dependencies this ticket may add (none unless listed):
          - `org.junit.jupiter:junit-jupiter`
          - `plugin:org.jetbrains.kotlinx.kover`
        """
            .trimIndent()

    @Test
    fun `an added coordinate the ticket does not list fails, naming it and its line`() {
        val failures = DependencyAudit.catalogFailures("SPA-005", ticket, base, head, "repro")
        assertEquals(
            listOf(
                GateFailure(
                    "dependencies",
                    "gradle/libs.versions.toml",
                    3,
                    "com.example:surprise was added to [libraries] but is not listed under " +
                        "\"Dependencies this ticket may add\" in tickets/SPA-005.md.",
                    "repro",
                )
            ),
            failures,
        )
    }

    @Test
    fun `additions the ticket lists pass`() {
        val allListed = ticket + "\n  - `com.example:surprise`"
        assertEquals(
            emptyList(),
            DependencyAudit.catalogFailures("SPA-005", allListed, base, head, "r"),
        )
    }

    @Test
    fun `a catalog created by the ticket must list every entry`() {
        val failures = DependencyAudit.catalogFailures("SPA-005", ticket, null, head, "r")
        assertEquals(
            listOf("org.jetbrains.kotlinx:kotlinx-serialization-json", "com.example:surprise"),
            failures.map { it.message.substringBefore(" was added") },
        )
    }

    @Test
    fun `with ticket none an added entry fails`() {
        val failures = DependencyAudit.catalogFailures(TicketId.NONE, null, base, head, "r")
        assertEquals(3, failures.size)
        assertTrue(failures.all { "ticket none" in it.message })
    }

    @Test
    fun `with ticket none any change to the catalog fails, even a version bump`() {
        val bumped = base.replace("1.12.0", "1.13.0")
        val failures = DependencyAudit.catalogFailures(TicketId.NONE, null, base, bumped, "r")
        assertEquals(1, failures.size)
        assertEquals(null, failures.single().line)
    }

    @Test
    fun `with ticket none an unchanged catalog passes, whatever its line endings`() {
        val crlf = base.replace("\n", "\r\n") + "\r\n"
        assertEquals(
            emptyList(),
            DependencyAudit.catalogFailures(TicketId.NONE, null, base, crlf, "r"),
        )
    }

    @Test
    fun `the catalog entries an app ticket lists pass, and an unlisted one fails`() {
        // Entries in the shape SPA-013 adds them: a BOM-managed library has no version.
        val appHead =
            base +
                "\ncompose-bom = { module = \"androidx.compose:compose-bom\", version = \"1\" }" +
                "\ncompose-ui = { module = \"androidx.compose.ui:ui\" }" +
                "\nextra = { module = \"androidx.navigation:navigation-compose\" }" +
                "\n\n[plugins]" +
                "\nandroid-application = { id = \"com.android.application\", version = \"9.5.0\" }"
        val appTicket =
            """
            - Dependencies this ticket may add (none unless listed). Pick one snapshot library:
              - `plugin:com.android.application`
              - `androidx.compose:compose-bom`
              - `androidx.compose.ui:ui`
            """
                .trimIndent()
        val failures = DependencyAudit.catalogFailures("SPA-013", appTicket, base, appHead, "r")
        assertEquals(
            listOf("androidx.navigation:navigation-compose was added to [libraries]"),
            failures.map { it.message.substringBefore(" but") },
        )
        assertEquals(5, failures.single().line)
    }

    @Test
    fun `a missing ticket file fails when the catalog grew`() {
        val failures = DependencyAudit.catalogFailures("SPA-404", null, base, head, "r")
        assertEquals("Ticket file tickets/SPA-404.md not found.", failures.single().message)
    }

    @Test
    fun `literal coordinates in build scripts fail with file and line`() {
        val scripts =
            mapOf(
                "core/build.gradle.kts" to
                    "dependencies {\n    implementation(\"com.example:lib:1.0\")\n}",
                "build.gradle.kts" to "plugins { alias(libs.plugins.ktfmt) }",
            )
        val failure = DependencyAudit.literalFailures(scripts, "r").single()
        assertEquals("core/build.gradle.kts", failure.file)
        assertEquals(2, failure.line)
        assertTrue("com.example:lib:1.0" in failure.message)
    }
}
