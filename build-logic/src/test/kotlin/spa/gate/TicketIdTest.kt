package spa.gate

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TicketIdTest {
    @Test
    fun `explicit --ticket wins over everything`() {
        assertEquals("SPA-007", TicketId.resolve("SPA-007", "SPA-008", "SPA-009-branch"))
    }

    @Test
    fun `GATE_TICKET is used when --ticket is absent`() {
        assertEquals("SPA-008", TicketId.resolve(null, "SPA-008", "SPA-009-branch"))
        assertEquals("SPA-008", TicketId.resolve("  ", "SPA-008", "SPA-009-branch"))
    }

    @Test
    fun `the first SPA id in the branch name is used when neither is given`() {
        assertEquals("SPA-012", TicketId.resolve(null, null, "feature/SPA-012-then-SPA-013"))
        assertEquals("SPA-001", TicketId.resolve(null, "", "SPA-001"))
    }

    @Test
    fun `none when no source names a ticket`() {
        assertEquals(TicketId.NONE, TicketId.resolve(null, null, "main"))
        assertEquals(TicketId.NONE, TicketId.resolve(null, null, null))
        assertEquals(TicketId.NONE, TicketId.resolve(null, null, "spa-001-lowercase"))
    }

    @Test
    fun `a malformed explicit ticket is a usage error`() {
        val error =
            assertFailsWith<IllegalArgumentException> { TicketId.resolve("SPA-1", null, null) }
        assertEquals("--ticket must look like SPA-###, got 'SPA-1'", error.message)
        assertFailsWith<IllegalArgumentException> { TicketId.resolve(null, "ticket-3", null) }
    }
}
