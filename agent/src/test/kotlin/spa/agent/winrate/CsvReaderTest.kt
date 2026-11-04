package spa.agent.winrate

import java.io.StringReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CsvReaderTest {
    private fun records(text: String): List<List<String>> {
        val reader = CsvReader(StringReader(text))
        return generateSequence { reader.next() }.toList()
    }

    @Test
    fun `plain fields split at commas, one record per line`() {
        assertEquals(
            listOf(listOf("a", "b", "c"), listOf("1", "2", "3")),
            records("a,b,c\n1,2,3\n"),
        )
    }

    @Test
    fun `a quoted field keeps its commas, doubled quotes and line breaks`() {
        assertEquals(
            listOf(listOf("x", "Abomination, Terrifying Titan", "say \"hi\"", "two\nlines", "")),
            records("x,\"Abomination, Terrifying Titan\",\"say \"\"hi\"\"\",\"two\nlines\",\n"),
        )
    }

    @Test
    fun `CRLF line ends and a last line without one both end a record`() {
        assertEquals(listOf(listOf("a", "b"), listOf("c", "d")), records("a,b\r\nc,d"))
    }

    @Test
    fun `empty input has no record`() {
        assertNull(CsvReader(StringReader("")).next())
    }
}
