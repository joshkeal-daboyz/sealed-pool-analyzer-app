package spa.agent.tags

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import spa.agent.grader.GraderTestCards.brief
import spa.agent.grader.GraderTestCards.numbered
import spa.agent.grader.GradingPackets
import spa.agent.grader.PacketThemes

/** The two tagging packets, built on hand-made sets of 60 cards (two card files) and of 3. */
class TagPacketsTest {
    @TempDir lateinit var temp: Path

    private val index = numbered(60)
    private val axes =
        listOf(
            Axis("tokens", "Tokens", "Makes a token."),
            Axis("sacrifice", "Sacrifice", "Sacrifices a permanent."),
            Axis("lands", "Lands", "A land."),
        )
    private val noThemes = PacketThemes(emptyList(), emptyMap())

    private fun names(packet: spa.agent.grader.GradingPacket) = packet.files.map { it.name }

    private fun text(packet: spa.agent.grader.GradingPacket, name: String) =
        packet.files.single { it.name == name }.text

    @Test
    fun `pass 1 has the prompt, brief, themes and file list, and the grading card files`() {
        val packet = TagPackets.axesPacket(index, brief(), noThemes, "Axes prompt.\r\n\r\n")

        assertEquals(
            listOf("instructions.md", "axes.schema.json", "cards-01.json", "cards-02.json"),
            names(packet),
        )
        val grading = GradingPackets.build(index, brief()).files.drop(2)
        assertEquals(grading, packet.files.drop(2))
        val instructions = text(packet, "instructions.md")
        assertTrue(instructions.startsWith("Axes prompt.\n\n## The format: Test Set\n"))
        assertTrue("## Themes in this set" in instructions)
        assertTrue("`cards-01.json` to `cards-02.json`: the 60 cards of the set" in instructions)
        assertTrue("- You write `axes.response.json`." in instructions)
        Json.parseToJsonElement(text(packet, "axes.schema.json"))
    }

    @Test
    fun `pass 2 lists the axes before the brief and writes axes json and the response schema`() {
        val packet = TagPackets.rolesPacket(index, brief(), null, "Roles prompt.", axes)

        assertEquals(
            listOf(
                "instructions.md",
                "response.schema.json",
                "axes.json",
                "cards-01.json",
                "cards-02.json",
            ),
            names(packet),
        )
        val instructions = text(packet, "instructions.md")
        assertTrue(
            instructions.startsWith(
                "Roles prompt.\n\n## The set's synergy axes\n\nUse these axes only, each by its " +
                    "id.\n\n- `tokens`, Tokens: Makes a token.\n- `sacrifice`, Sacrifice: " +
                    "Sacrifices a permanent.\n- `lands`, Lands: A land.\n\n## The format: "
            ),
            instructions,
        )
        assertFalse("## Themes in this set" in instructions)
        assertTrue("You write `cards-01.response.json` to `cards-02.response.json`" in instructions)
        val schema = text(packet, "response.schema.json")
        Json.parseToJsonElement(schema)
        assertTrue("\"enum\": [\"tokens\", \"sacrifice\", \"lands\"]" in schema)
        assertEquals(axes, AxesFile.parse(text(packet, "axes.json"), "axes.json").axes)
    }

    @Test
    fun `a pool in one card file names that file alone`() {
        val packet = TagPackets.rolesPacket(numbered(3), brief(), null, "P", axes)

        val instructions = text(packet, "instructions.md")
        assertTrue("- `cards-01.json`: the 3 cards of the set" in instructions)
        assertTrue("You write `cards-01.response.json`, one beside" in instructions)
    }

    @Test
    fun `writing replaces earlier packet files and keeps responses`() {
        val dir = temp.resolve("p")
        TagPackets.write(TagPackets.axesPacket(index, brief(), null, "P"), dir)
        Files.writeString(dir.resolve("axes.response.json"), "[]")
        Files.writeString(dir.resolve("cards-01.response.json"), "[]")
        Files.writeString(dir.resolve("cards-09.json"), "[]")

        TagPackets.write(TagPackets.rolesPacket(numbered(3), brief(), null, "P", axes), dir)

        assertEquals(
            listOf(
                "axes.json",
                "axes.response.json",
                "cards-01.json",
                "cards-01.response.json",
                "instructions.md",
                "response.schema.json",
            ),
            dir.listDirectoryEntries().map { it.name }.sorted(),
        )
        assertFalse(dir.resolve("axes.schema.json").exists())
        assertTrue(dir.resolve("instructions.md").readText().startsWith("P\n\n"))
    }
}
