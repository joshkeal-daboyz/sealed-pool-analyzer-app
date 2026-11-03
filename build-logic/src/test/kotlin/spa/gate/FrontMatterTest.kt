package spa.gate

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FrontMatterTest {
    private val ticket =
        """
        ---
        id: SPA-042
        goal: G-001
        title: "Parse the pool"
        status: queued
        depends_on: [SPA-001]
        budget:
          developer_attempts: 3
          wall_clock: 8h
        coverage_threshold: 85 # set by the Architect
        ---

        # SPA-042

        coverage_threshold: 10
        """
            .trimIndent()

    @Test
    fun `reads top-level keys and ignores nested ones and the body`() {
        val fields = FrontMatter.parse(ticket)
        assertEquals("SPA-042", fields["id"])
        assertEquals("Parse the pool", fields["title"])
        assertEquals("85", fields["coverage_threshold"])
        assertEquals("", fields["budget"])
        assertTrue("developer_attempts" !in fields)
    }

    @Test
    fun `coverage threshold comes from the front matter, not the body`() {
        assertEquals(85.0, FrontMatter.coverageThreshold(ticket))
    }

    @Test
    fun `threshold accepts decimals and a percent sign`() {
        assertEquals(72.5, FrontMatter.coverageThreshold("---\ncoverage_threshold: 72.5%\n---\n"))
        assertEquals(0.0, FrontMatter.coverageThreshold("---\ncoverage_threshold: 0\n---\n"))
    }

    @Test
    fun `missing, non-numeric, or out-of-range thresholds are errors`() {
        assertFailsWith<IllegalArgumentException> {
            FrontMatter.coverageThreshold("---\nid: x\n---")
        }
        assertFailsWith<IllegalArgumentException> {
            FrontMatter.coverageThreshold("---\ncoverage_threshold: high\n---")
        }
        assertFailsWith<IllegalArgumentException> {
            FrontMatter.coverageThreshold("---\ncoverage_threshold: 101\n---")
        }
    }

    @Test
    fun `a file without front matter has no fields`() {
        assertEquals(emptyMap(), FrontMatter.parse("# Just a heading\ncoverage_threshold: 5"))
    }

    @Test
    fun `windows line endings are read the same way`() {
        assertEquals(85.0, FrontMatter.coverageThreshold(ticket.replace("\n", "\r\n")))
    }
}
