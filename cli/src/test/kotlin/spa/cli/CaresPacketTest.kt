package spa.cli

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readBytes
import kotlin.io.path.readText
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import spa.agent.facts.CardFacts
import spa.agent.otags.CaresTable
import spa.agent.otags.OtagFile

/**
 * SPA-055 criteria 1 and 7: `spa cares-packet` on the committed trk files and on hand-built `syn`
 * files (a grandchild of `name-matters`).
 */
class CaresPacketTest {
    @TempDir lateinit var temp: Path

    private val data = DataDir(Fixtures.root)

    private val nameTagsOfTrk =
        listOf(
            "name-matters",
            "tutor-self",
            "tutors-by-name",
            "differently-named-lands-matter",
            "hate-named",
        )

    private fun packet(
        set: String,
        name: String,
        vararg more: String,
        dataDir: String = Fixtures.data,
    ) =
        runCli(
            "cares-packet",
            set,
            "--out",
            temp.resolve(name).toString(),
            "--data",
            dataDir,
            *more,
        )

    private fun trk(name: String = "trk"): Path {
        val run = packet("trk", name)
        assertEquals(ExitCode.OK, run.exit, run.stderr)
        return temp.resolve(name)
    }

    private fun json(file: Path) = Json.parseToJsonElement(file.readText())

    private fun entries(file: Path): List<JsonObject> = json(file).jsonArray.map { it.jsonObject }

    private fun text(element: kotlinx.serialization.json.JsonElement?): String? =
        element?.takeUnless { it is JsonNull }?.jsonPrimitive?.content

    private fun id(card: JsonObject): String = text(card["id"])!!

    /** A vocabulary's fact to description. */
    private fun vocabulary(packet: Path): Map<String, String?> =
        entries(packet.resolve("vocabulary.json")).associate {
            text(it["fact"])!! to text(it["description"])
        }

    private fun cardFile(n: Int) = "cards-%02d.json".format(n)

    private fun caresFile(n: Int) = "cares-%02d.json".format(n)

    @Test
    fun `trk writes the named files, and the card files are the grade packet's`() {
        val packet = trk()
        val grade = temp.resolve("grade")
        assertEquals(
            ExitCode.OK,
            runCli("grade-packet", "trk", "--out", "$grade", "--data", Fixtures.data).exit,
        )

        val cardFiles = (1..7).map(::cardFile)
        assertEquals(
            (listOf(
                    "instructions.md",
                    "facts.json",
                    "vocabulary.json",
                    "cares-gaps.schema.json",
                    "cares-table-suggestions.schema.json",
                ) + cardFiles + (1..7).map(::caresFile))
                .sorted(),
            packet.listDirectoryEntries().map { it.name }.sorted(),
        )
        for (name in cardFiles) {
            assertContentEquals(grade.resolve(name).readBytes(), packet.resolve(name).readBytes())
        }
    }

    @Test
    fun `each cares file is what the cares table gives the cards of its card file`() {
        val packet = trk()
        val otags = OtagFile.parse(Fixtures.root.resolve("otags/trk.json").readText()).byId()
        val table = CaresTable.parse(Fixtures.root.resolve("otags/cares.json").readText())

        var checked = 0
        for (n in 1..7) {
            val ids = entries(packet.resolve(cardFile(n))).map(::id)
            val cares = entries(packet.resolve(caresFile(n)))
            assertEquals(ids, cares.map(::id))
            for (card in cares) {
                val given =
                    card.getValue("cares").jsonArray.associate {
                        text(it.jsonObject["fact"])!! to text(it.jsonObject["strength"])!!.toInt()
                    }
                assertEquals(table.of(otags.getValue(id(card))), given, id(card))
                checked++
            }
        }
        assertEquals(336, checked)
    }

    @Test
    fun `the vocabulary holds every code fact and every otag with ancestors, and no name tag`() {
        val packet = trk()
        val tagsFile = json(Fixtures.root.resolve("otags/trk.tags.json")).jsonObject
        val described =
            tagsFile.getValue("tags").jsonArray.associate {
                text(it.jsonObject["slug"])!! to text(it.jsonObject["description"])
            }
        val vocabulary = vocabulary(packet)

        val otagEntries = vocabulary.filterKeys { it.startsWith("otag:") }
        assertEquals(681, otagEntries.size)
        for ((fact, description) in otagEntries) {
            assertEquals(described.getValue(fact.removePrefix("otag:")), description, fact)
        }
        assertEquals(emptyList(), nameTagsOfTrk.filter { "otag:$it" in vocabulary })
        val index = Commands.loadIndex(data, "trk")
        val code = CardFacts.load(data.scryfall, index).values.flatten().toSet()
        assertEquals(code.sorted(), vocabulary.keys.filterNot { it.startsWith("otag:") }.sorted())
        assertTrue(vocabulary.getValue("legendary").orEmpty().isNotBlank())
        assertEquals(vocabulary.keys.sorted(), vocabulary.keys.toList())
    }

    @Test
    fun `no card has an otag fact for a name tag, and the other facts are kept`() {
        val packet = trk()
        val facts = json(packet.resolve("facts.json")).jsonObject
        val index = Commands.loadIndex(data, "trk")
        val otags = OtagFile.parse(Fixtures.root.resolve("otags/trk.json").readText()).byId()
        val all = CardFacts.load(data.scryfall, index, otags = otags)

        assertEquals(336, facts.size)
        val named = nameTagsOfTrk.map { "otag:$it" }.toSet()
        for ((id, given) in facts) {
            val expected = (all.getValue(id) - named).sorted()
            assertEquals(expected, given.jsonArray.map { it.jsonPrimitive.content }, id)
        }
        assertTrue(all.values.any { card -> named.any { it in card } }, "name tags are on cards")
    }

    @Test
    fun `the instructions are the prompt, the cares table, the brief, and the files`() {
        val text = trk().resolve("instructions.md").readText()

        assertEquals(CaresPromptPin.TEXT + "\n\n", text.take(CaresPromptPin.TEXT.length + 2))
        val resource =
            requireNotNull(javaClass.getResource("/spa/agent/otags/cares-gaps-v1.md")).readText()
        assertEquals(CaresPromptPin.TEXT, resource.replace("\r\n", "\n").trimEnd())
        val line = text.lines().single { "`differently-named-lands-matter`" in it }
        assertTrue("`land`" in line && "3" in line, line)
        assertTrue("`lands-matter`" in text)
        assertTrue(
            text.indexOf("`lands-matter`") < text.indexOf("## The format: "),
            "the table comes before the brief",
        )
        for (name in
            listOf(
                "facts.json",
                "vocabulary.json",
                "cares-01.json",
                "cares-07.json",
                "cares-gaps.schema.json",
                "cares-table-suggestions.schema.json",
                "cards-01.cares.json",
                "cards-07.cares.json",
                "cares-table-suggestions.json",
            )) {
            assertTrue("`$name`" in text, name)
        }
    }

    @Test
    fun `a rerun gives the same bytes`() {
        val first = trk("first")
        val second = trk("second")

        val names = first.listDirectoryEntries().map { it.name }.sorted()
        assertEquals(names, second.listDirectoryEntries().map { it.name }.sorted())
        for (name in names) {
            assertContentEquals(first.resolve(name).readBytes(), second.resolve(name).readBytes())
        }
    }

    @Test
    fun `a rewritten packet keeps the response files and drops its old cares files`() {
        val out = trk("again")
        Files.writeString(out.resolve("cards-01.cares.json"), "[]")
        Files.writeString(out.resolve("cares-09.json"), "[]")
        val run = packet("trk", "again")

        assertEquals(ExitCode.OK, run.exit, run.stderr)
        assertTrue(Files.exists(out.resolve("cards-01.cares.json")))
        assertTrue(Files.notExists(out.resolve("cares-09.json")))
    }

    @Test
    fun `syn leaves a grandchild of name-matters out, and keeps a look-alike`() {
        val syn = CaresSyn.data(temp.resolve("data"))
        val run = packet("syn", "syn", dataDir = "$syn")
        assertEquals(ExitCode.OK, run.exit, run.stderr)
        val packet = temp.resolve("syn")

        val otags = vocabulary(packet).filterKeys { it.startsWith("otag:") }
        assertEquals(
            mapOf(
                "otag:cantrip" to null,
                "otag:card-advantage" to "Gains cards.",
                "otag:draw" to "Draws cards.",
                "otag:legends-matter" to "Cares about legends.",
                "otag:named-things" to "Has a name.",
            ),
            otags,
        )
        val facts = json(packet.resolve("facts.json")).jsonObject
        assertEquals(20, facts.size)
        val named = CaresSyn.nameTags.map { "otag:$it" }
        val all = facts.values.flatMap { f -> f.jsonArray.map { it.jsonPrimitive.content } }
        assertEquals(emptyList(), all.filter { it in named })
        assertTrue(
            "otag:draw" in facts.getValue("syn/1").jsonArray.map { it.jsonPrimitive.content }
        )
        assertTrue(
            "otag:named-things" in
                facts.getValue("syn/5").jsonArray.map { it.jsonPrimitive.content }
        )
    }

    @Test
    fun `syn cares files keep what name tags give`() {
        val syn = CaresSyn.data(temp.resolve("data"))
        assertEquals(ExitCode.OK, packet("syn", "syn", dataDir = "$syn").exit)

        val cares =
            entries(temp.resolve("syn/cares-01.json")).associate {
                id(it) to
                    it.getValue("cares").jsonArray.map { c ->
                        text(c.jsonObject["fact"])!! to text(c.jsonObject["strength"])!!.toInt()
                    }
            }
        assertEquals(20, cares.size)
        assertEquals(listOf("otag:draw" to 2), cares.getValue("syn/2"))
        assertEquals(listOf("creature" to 1), cares.getValue("syn/4"))
        assertEquals(emptyList(), cares.getValue("syn/1"))
        val text = temp.resolve("syn/instructions.md").readText()
        assertTrue(text.lines().any { "`tutors-by-name`" in it && "`creature`" in it })
    }

    @Test
    fun `an explicit --otags file is read in place of the set's`() {
        val syn = CaresSyn.data(temp.resolve("data"))
        val other =
            writeFile(temp, "other.json", CaresSyn.otags.replace("\"legends-matter\"", "\"draw\""))
        assertEquals(ExitCode.OK, packet("syn", "syn", "--otags", other, dataDir = "$syn").exit)

        val syn2 = entries(temp.resolve("syn/cares-01.json")).first { id(it) == "syn/2" }
        assertEquals(JsonArray(emptyList()), syn2.getValue("cares"))
    }

    @Test
    fun `a missing otags file, tags file or --out, or an invalid tags file, is one line`() {
        val syn = Fixtures.synDataDir(temp.resolve("bare"))
        val noOtags = packet("syn", "a", dataDir = "$syn")
        assertEquals(ExitCode.ERROR, noOtags.exit)
        assertEquals(1, noOtags.stderrLines().size, noOtags.stderr)
        assertTrue("no otags for set 'syn'" in noOtags.stderr, noOtags.stderr)

        assertEquals(ExitCode.ERROR, packet("syn", "a", "--otags", "none", dataDir = "$syn").exit)

        writeFile(syn, "otags/syn.json", CaresSyn.otags)
        val noTags = packet("syn", "b", dataDir = "$syn")
        assertEquals(ExitCode.ERROR, noTags.exit)
        assertEquals(1, noTags.stderrLines().size, noTags.stderr)
        assertTrue("tags file not found" in noTags.stderr, noTags.stderr)

        writeFile(syn, "otags/syn.tags.json", "{ nope")
        val bad = packet("syn", "c", dataDir = "$syn")
        assertEquals(ExitCode.ERROR, bad.exit)
        assertTrue("invalid tags file" in bad.stderr, bad.stderr)

        writeFile(
            syn,
            "otags/syn.tags.json",
            CaresSyn.tags.replaceFirst("\"set\": \"syn\"", "\"set\": \"abc\""),
        )
        val other = packet("syn", "d", dataDir = "$syn")
        assertEquals(ExitCode.ERROR, other.exit)
        assertTrue("is for set 'abc', not 'syn'" in other.stderr, other.stderr)

        val noOut = runCli("cares-packet", "syn", "--data", "$syn")
        assertEquals(ExitCode.USAGE, noOut.exit)
        assertTrue(Files.notExists(temp.resolve("a")))
    }

    @Test
    fun `help lists both commands`() {
        assertTrue("cares-packet <code> --out <dir>" in Main.USAGE)
        assertTrue("cares-import <code> --packet <dir> --provenance <text>" in Main.USAGE)
    }
}
