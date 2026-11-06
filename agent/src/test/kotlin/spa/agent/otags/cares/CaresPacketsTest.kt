package spa.agent.otags.cares

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import spa.agent.grader.GraderTestCards.brief
import spa.agent.grader.GraderTestCards.numbered
import spa.agent.grader.GradingPacket
import spa.agent.otags.CaresTable
import spa.agent.otags.OtagTagInfo
import spa.agent.otags.OtagTagsFile
import spa.agent.tags.TagPacketSet

/**
 * SPA-055 criteria 1 and 7 on a hand-made set of 60 cards (two card files): the cares packet's
 * files, its vocabulary without name tags, and how it is written.
 */
class CaresPacketsTest {
    @TempDir lateinit var temp: Path

    private val index = numbered(60)

    private val facts =
        (1..60).associate { "xyz/$it" to setOf("creature") } +
            mapOf(
                "xyz/1" to setOf("creature", "legendary", "otag:card-advantage", "otag:draw"),
                "xyz/2" to setOf("creature", "otag:legends-matter"),
                "xyz/3" to setOf("land", "otag:name-matters", "otag:names-child"),
            )

    private val otags =
        mapOf(
            "xyz/1" to listOf("card-advantage", "draw"),
            "xyz/2" to listOf("legends-matter"),
            "xyz/3" to listOf("name-matters", "names-child"),
        )

    private val table =
        CaresTable.parse(
            """
            { "cares": {
              "legends-matter": [{ "fact": "otag:draw", "strength": 2 }],
              "names-child": [{ "fact": "land", "strength": 3 }, { "fact": "creature", "strength": 1 }]
            } }
            """
        )

    private val tags =
        OtagTagsFile(
            "xyz",
            "hand-built",
            "2026-11-05T22:00:00Z",
            "Card tags from Scryfall Tagger (tagger.scryfall.com)",
            listOf(
                OtagTagInfo("card-advantage", "Gains cards.", emptyList()),
                OtagTagInfo("draw", null, listOf("card-advantage")),
                OtagTagInfo("legends-matter", "Cares about legends.", emptyList()),
                OtagTagInfo("name-matters", "Names matter.", emptyList()),
                OtagTagInfo("names-child", "A child.", listOf("name-matters")),
            ),
        )

    private val set = CaresPacketSet(TagPacketSet(index, brief(), null, facts), otags, table, tags)

    private fun packet(): GradingPacket = CaresPackets.build(set, "Fill the gaps.\n")

    private fun text(packet: GradingPacket, name: String) =
        packet.files.single { it.name == name }.text

    @Test
    fun `the packet holds the named files, with the card files as the grading packet writes them`() {
        assertEquals(
            listOf(
                "instructions.md",
                "cares-gaps.schema.json",
                "cares-table-suggestions.schema.json",
                "vocabulary.json",
                "facts.json",
                "cards-01.json",
                "cards-02.json",
                "cares-01.json",
                "cares-02.json",
            ),
            packet().files.map { it.name },
        )
    }

    @Test
    fun `the vocabulary lists code facts and tags with descriptions, and no name tag`() {
        val entries = CaresVocabulary.parse(text(packet(), "vocabulary.json"))

        assertEquals(
            listOf(
                "creature" to "The front face is a creature card.",
                "land" to "The front face is a land card.",
                "legendary" to "The front face is a legendary card.",
                "otag:card-advantage" to "Gains cards.",
                "otag:draw" to null,
                "otag:legends-matter" to "Cares about legends.",
            ),
            entries.map { it.fact to it.description },
        )
    }

    @Test
    fun `facts leave out the name tags, and cares files hold what the table gives`() {
        val packet = packet()

        val facts = text(packet, "facts.json")
        assertTrue("otag:draw" in facts && "otag:legends-matter" in facts)
        assertTrue("name-matters" !in facts && "names-child" !in facts, facts)
        val cares = text(packet, "cares-01.json")
        assertEquals(
            60,
            Regex("\"id\"")
                .findAll(text(packet, "cares-01.json") + text(packet, "cares-02.json"))
                .count(),
        )
        assertTrue("\"fact\": \"otag:draw\"" in cares && "\"strength\": 2" in cares, cares)
        assertTrue("\"fact\": \"land\"" in cares && "\"fact\": \"creature\"" in cares, cares)
    }

    @Test
    fun `the instructions are the prompt, the table, the brief and the files`() {
        val text = text(packet(), "instructions.md")

        assertTrue(text.startsWith("Fill the gaps.\n\n## The cares table\n"), text)
        assertTrue("- `names-child`: `land` 3, `creature` 1\n" in text, text)
        assertTrue(text.indexOf("## The cares table") < text.indexOf("## The format: Test Set"))
        assertTrue(
            text.indexOf("## The format: Test Set") < text.indexOf("## The files in this folder")
        )
        assertTrue(
            "`cares-01.json` to `cares-02.json`" in text &&
                "`cards-01.cares.json` to `cards-02.cares.json`" in text
        )
        assertTrue(text.endsWith("when you have a suggestion.\n"), text)
    }

    @Test
    fun `the same inputs give the same bytes, and the file names pair up`() {
        assertEquals(packet(), packet())
        assertEquals("cares-03.json", CaresPackets.caresFileName("cards-03.json"))
        assertEquals("cards-03.cares.json", CaresPackets.responseFileName("cards-03.json"))
        assertEquals("cards-03.json", CaresPackets.cardFileName("cards-03.cares.json"))
    }

    @Test
    fun `writing replaces old packet files and keeps the responses`() {
        val dir = temp.resolve("packet")
        CaresPackets.write(packet(), dir)
        Files.writeString(dir.resolve("cards-01.cares.json"), "[]")
        Files.writeString(dir.resolve("cares-table-suggestions.json"), "[]")
        Files.writeString(dir.resolve("cards-09.json"), "[]")
        Files.writeString(dir.resolve("cares-09.json"), "[]")
        Files.writeString(dir.resolve("notes.txt"), "mine")

        CaresPackets.write(packet(), dir)

        val names = dir.listDirectoryEntries().map { it.name }.sorted()
        assertTrue("cards-01.cares.json" in names && "cares-table-suggestions.json" in names)
        assertTrue("cards-09.json" !in names && "cares-09.json" !in names && "notes.txt" in names)
        assertEquals(text(packet(), "vocabulary.json"), dir.resolve("vocabulary.json").readText())
    }

    @Test
    fun `a missing resource is an IllegalArgumentException, and the prompt loads`() {
        assertFailsWith<IllegalArgumentException> { CaresResources.text("nothing.md") }
        assertTrue(CaresResources.prompt.startsWith("You are filling gaps"))
    }

    @Test
    fun `code facts are described by family`() {
        val described =
            listOf(
                    "artifact",
                    "legendary",
                    "cheap",
                    "enters-trigger",
                    "dies-trigger",
                    "makes-token",
                    "type:human",
                    "kw:flying",
                    "other",
                )
                .map(CodeFactDescriptions::of)

        assertEquals(described.size, described.toSet().size)
        assertEquals("A card fact.", described.last())
        assertTrue("human" in described[6] && "flying" in described[7])
    }

    @Test
    fun `the vocabulary text reads back, and anything else is rejected`() {
        val entries = listOf(VocabularyEntry("land", "A land."), VocabularyEntry("otag:x", null))

        assertEquals(entries, CaresVocabulary.parse(CaresVocabulary.encode(entries)))
        assertFailsWith<IllegalArgumentException> { CaresVocabulary.parse("{ nope") }
    }
}
