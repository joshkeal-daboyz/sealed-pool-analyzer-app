package spa.core.intake

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ManaBoxCsvParserTest {
    private val file = intakeResource("manabox-mixed.csv")
    private val lf = file.replace("\r\n", "\n")
    private val crlf = lf.replace("\n", "\r\n")

    @Test
    fun `the resource starts with a byte-order mark and its CRLF and LF forms parse the same`() {
        assertTrue(file.startsWith(PoolLines.BOM))
        assertTrue(crlf.contains("\r\n"))
        assertEquals(
            ManaBoxCsvParser.parse(lf.removePrefix(PoolLines.BOM), synIndex),
            ManaBoxCsvParser.parse(crlf, synIndex),
        )
    }

    @Test
    fun `rows resolve by exact Scryfall id with reordered columns and duplicates merged`() {
        val result = ManaBoxCsvParser.parse(crlf, synIndex)

        assertEquals(PoolFormat.MANABOX, result.format)
        assertEquals("syn", result.setCode)
        assertEquals(listOf("syn 1" to 3, "syn 13" to 1, "syn 2" to 1), result.counts())
        assertEquals(listOf(2, 3, 5, 9), result.resolvedLines)
        assertEquals(1, result.skippedLines)
        assertEquals(emptyList(), result.warnings)
    }

    @Test
    fun `a quoted name with a comma and a field with doubled quotes stay one field each`() {
        val records = CsvReader.records(lf)

        assertEquals(
            "Agadeem's Awakening // Agadeem, the Undercrypt",
            records[2].fields[1],
        )
        assertEquals("Synthetic \"Test\" Set", records[1].fields[2])
        assertEquals(11, records[1].fields.size)
    }

    @Test
    fun `unknown id, wrong set, and bad quantities are unresolved with their reasons`() {
        val result = ManaBoxCsvParser.parse(crlf, synIndex)

        assertEquals(
            listOf(
                6 to "unknown-scryfall-id",
                7 to "wrong-set",
                8 to "bad-quantity",
                10 to "bad-quantity",
            ),
            result.reasons(),
        )
        assertTrue(result.unresolved[1].rawLine.contains("Mikaeus, the Lunarch"))
        assertEveryLineAccounted(crlf, result, structural = listOf(1))
    }

    @Test
    fun `without a Set code column an unknown id is unknown-scryfall-id even for another set`() {
        val text =
            "Scryfall ID,Quantity\n" +
                "f57d8134-4ec9-40f2-8d7b-560f246215a5,1\n" +
                "409f9b88-f03e-40b6-9883-68c14c37c0de,1\n"

        val result = ManaBoxCsvParser.parse(text, synIndex)

        assertEquals(listOf(2 to "unknown-scryfall-id"), result.reasons())
        assertEquals(listOf("syn 1" to 1), result.counts())
    }

    @Test
    fun `a set code that is in the index with an unknown id is unknown-scryfall-id`() {
        val text = "Set code,Scryfall ID,Quantity\nSYN,not-an-id,1\n,also-not,1\n"

        val result = ManaBoxCsvParser.parse(text, synIndex)

        assertEquals(
            listOf(2 to "unknown-scryfall-id", 3 to "unknown-scryfall-id"),
            result.reasons(),
        )
    }

    @Test
    fun `a missing required column is an exception naming it`() {
        val error =
            assertFailsWith<PoolParseException> {
                ManaBoxCsvParser.parse(intakeResource("manabox-missing-quantity.csv"), synIndex)
            }

        assertEquals(listOf("Quantity"), error.missingColumns)
        assertTrue(error.message.orEmpty().contains("'Quantity'"))
    }

    @Test
    fun `an empty file is missing both required columns`() {
        val error = assertFailsWith<PoolParseException> { ManaBoxCsvParser.parse("\n\n", synIndex) }

        assertEquals(listOf("Scryfall ID", "Quantity"), error.missingColumns)
    }

    @Test
    fun `blank lines before the header are skipped and header names ignore case and spaces`() {
        val text = "\n  \n scryfall id , QUANTITY \n409f9b88-f03e-40b6-9883-68c14c37c0de , 2 \n"

        val result = ManaBoxCsvParser.parse(text, synIndex)

        assertEquals(listOf("syn 1" to 2), result.counts())
        assertEquals(listOf(4), result.resolvedLines)
        assertEquals(2, result.skippedLines)
    }

    @Test
    fun `a short row reads missing fields as empty and a quoted line break continues the record`() {
        val text =
            "Name,Scryfall ID,Quantity\n" +
                "\"Grizzly\nBears\",409f9b88-f03e-40b6-9883-68c14c37c0de,1\n" +
                "Short row\n" +
                "Serra Angel,b8c5e74c-96e7-4a1f-93b7-14d776fe4b2d,1\n"

        val result = ManaBoxCsvParser.parse(text, synIndex)

        assertEquals(listOf("syn 1" to 1, "syn 2" to 1), result.counts())
        assertEquals(listOf(2, 5), result.resolvedLines)
        assertEquals(listOf(4 to "unknown-scryfall-id"), result.reasons())
        assertEquals(
            "\"Grizzly\nBears\",409f9b88-f03e-40b6-9883-68c14c37c0de,1",
            CsvReader.records(text)[1].raw,
        )
    }

    @Test
    fun `an unclosed quote at the end of the file ends the field there`() {
        val records = CsvReader.records("a,\"b,c\nd")

        assertEquals(1, records.size)
        assertEquals(listOf("a", "b,c\nd"), records[0].fields)
    }
}
