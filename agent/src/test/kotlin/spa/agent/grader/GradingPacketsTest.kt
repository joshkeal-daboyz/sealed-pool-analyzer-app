package spa.agent.grader

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.io.TempDir
import spa.agent.grader.GraderTestCards.brief
import spa.agent.grader.GraderTestCards.numbered

/** Criteria 1 and 2: the packet's files, their order and sizes, and what the instructions say. */
class GradingPacketsTest {
    @TempDir lateinit var temp: Path

    private fun cardIds(text: String): List<String> =
        Json.parseToJsonElement(text).jsonArray.map { it.jsonObject["id"]!!.jsonPrimitive.content }

    @Test
    fun `the packet is the instructions, the schema, then card files of the given size`() {
        val packet = GradingPackets.build(numbered(20), brief(), cardsPerFile = 7)

        assertEquals(
            listOf(
                "instructions.md",
                "response.schema.json",
                "cards-01.json",
                "cards-02.json",
                "cards-03.json",
            ),
            packet.files.map { it.name },
        )
        val cardFiles = packet.files.drop(2).map { cardIds(it.text) }
        assertEquals(listOf(7, 7, 6), cardFiles.map { it.size })
        assertEquals((1..20).map { "xyz/$it" }, cardFiles.flatten())
    }

    @Test
    fun `the default is 50 cards per file`() {
        val packet = GradingPackets.build(numbered(120), brief())

        assertEquals(
            listOf(50, 50, 20),
            packet.files
                .filter { GradingPackets.isCardFile(it.name) }
                .map { cardIds(it.text).size },
        )
    }

    @Test
    fun `a card file holds the card fields, nulls included, and ends with a newline`() {
        val text = GradingPackets.build(numbered(1), brief()).files.last().text
        val card = Json.parseToJsonElement(text).jsonArray.single().jsonObject

        assertEquals(
            listOf(
                "id",
                "name",
                "manaCost",
                "typeLine",
                "rarity",
                "oracleText",
                "power",
                "toughness",
            ),
            card.keys.toList(),
        )
        assertTrue(text.endsWith("]\n"))
        assertFalse('\r' in text)
    }

    @Test
    fun `building twice gives the same bytes`() {
        assertEquals(
            GradingPackets.build(numbered(60), brief()),
            GradingPackets.build(numbered(60), brief()),
        )
    }

    @Test
    fun `file names`() {
        assertEquals("cards-01.json", GradingPackets.cardFileName(1))
        assertEquals("cards-12.json", GradingPackets.cardFileName(12))
        assertEquals("cards-03.response.json", GradingPackets.responseFileName("cards-03.json"))
        assertTrue(GradingPackets.isCardFile("cards-07.json"))
        assertFalse(GradingPackets.isCardFile("cards-07.response.json"))
        assertFalse(GradingPackets.isCardFile("instructions.md"))
    }

    @Test
    fun `the instructions are the prompt, then the brief, then the files`() {
        val instructions = GradingPackets.build(numbered(20), brief(), 7).files.first().text
        val prompt = GradingPackets.promptText
        val rendered = GradingPackets.renderBrief(brief())

        assertTrue(instructions.startsWith(prompt))
        assertTrue(instructions.indexOf(rendered) > prompt.length - 1)
        assertTrue("cards-01.json" in instructions.substringAfter(rendered))
        assertTrue("cards-03.json" in instructions.substringAfter(rendered))
        assertTrue("20 cards" in instructions)
        assertTrue("response.schema.json" in instructions.substringAfter(rendered))
        assertFalse('\r' in instructions)
    }

    @Test
    fun `the brief is rendered with speed, mechanics with rules text, archetypes with signposts`() {
        val rendered = GradingPackets.renderBrief(brief())

        listOf(
                "Test Set",
                "fast",
                "Most of them end by turn six.",
                "Kindle",
                "Kindle N deals N damage.",
                "(Reminder for kindle.)",
                "Overgrow checks for seven lands.",
                "WU",
                "Tether Control",
                "Tether and fly.",
                "Warden One",
                "Sacrifice Aggro",
                "Cinder A; B",
            )
            .forEach { assertTrue(it in rendered, "missing '$it' in:\n$rendered") }
    }

    @Test
    fun `the prompt states the scale and forbids tools other than reading and writing the packet`() {
        val prompt = GradingPackets.promptText

        assertTrue("0.0 to 5.0" in prompt)
        assertTrue("steps of 0.5" in prompt)
        assertTrue("3.0" in prompt)
        assertTrue("confidence" in prompt)
        assertTrue("Do not use the web" in prompt)
        assertFalse('\r' in prompt)
    }

    @Test
    fun `the response schema is a JSON array schema of id, score and confidence`() {
        val schema = Json.parseToJsonElement(GradingPackets.responseSchema).jsonObject
        val item = schema["items"]!!.jsonObject

        assertEquals("array", schema["type"]!!.jsonPrimitive.content)
        assertEquals(
            listOf("id", "score", "confidence"),
            (item["required"] as JsonArray).map { it.jsonPrimitive.content },
        )
        assertEquals(setOf("id", "score", "confidence"), (item["properties"] as JsonObject).keys)
    }

    @Test
    fun `writing replaces the old packet files and keeps response files`() {
        val dir = temp.resolve("packet")
        GradingPackets.write(GradingPackets.build(numbered(20), brief(), 7), dir)
        Files.writeString(dir.resolve("cards-01.response.json"), "[]")

        GradingPackets.write(GradingPackets.build(numbered(20), brief(), 10), dir)

        assertEquals(
            listOf(
                "cards-01.json",
                "cards-01.response.json",
                "cards-02.json",
                "instructions.md",
                "response.schema.json",
            ),
            dir.listDirectoryEntries().map { it.name }.sorted(),
        )
        assertEquals((11..20).map { "xyz/$it" }, cardIds(dir.resolve("cards-02.json").readText()))
    }

    @Test
    fun `written files equal the packet's text byte for byte`() {
        val packet = GradingPackets.build(numbered(3), brief())
        GradingPackets.write(packet, temp)

        packet.files.forEach {
            assertEquals(it.text, Files.readString(temp.resolve(it.name)), it.name)
        }
    }
}
