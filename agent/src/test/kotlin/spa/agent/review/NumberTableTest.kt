package spa.agent.review

import kotlin.test.Test
import kotlin.test.assertEquals
import spa.core.baseline.Stat

class NumberTableTest {
    private val table =
        NumberTable(
            listOf(
                NumberEntry("/whole", 12.0, null),
                NumberEntry("/two", 0.25, Stat(median = 0.3, p10 = 0.1, p90 = 0.5, mean = 0.3)),
                NumberEntry("/five", 0.123456, null),
                NumberEntry("/tail", 0.5, Stat(median = 3.0, p10 = 2.0, p90 = 4.5, mean = 3.0)),
            )
        )

    @Test
    fun `text prints one line per entry in order`() {
        val expected =
            listOf(
                "/whole = 12",
                "/two = 0.25 (typical: median 0.3, p10 0.1, p90 0.5)",
                "/five = 0.1235",
                "/tail = 0.5 (typical: median 3, p10 2, p90 4.5)",
            )
        assertEquals(expected, table.text().lines())
    }

    @Test
    fun `a value with five decimals prints to four without a trailing zero`() {
        val rounded =
            NumberTable(listOf(NumberEntry("/a", 0.12005, null), NumberEntry("/b", 2.5, null)))
        assertEquals(listOf("/a = 0.1201", "/b = 2.5"), rounded.text().lines())
    }

    @Test
    fun `check accepts the exact value and one within 0 point 00005`() {
        assertEquals(NumberCheck.Ok, table.check("/two", 0.25))
        assertEquals(NumberCheck.Ok, table.check("/two", 0.25004))
        assertEquals(NumberCheck.Ok, table.check("/two", 0.24996))
    }

    @Test
    fun `check rejects a value 0 point 0001 away and names the expected one`() {
        assertEquals(NumberCheck.WrongValue(0.25), table.check("/two", 0.2501))
        assertEquals(NumberCheck.WrongValue(12.0), table.check("/whole", 11.9999))
    }

    @Test
    fun `check reports a key the table does not hold`() {
        assertEquals(NumberCheck.UnknownKey, table.check("/missing", 1.0))
    }
}
