package spa.cli

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * SPA-055 criterion 3: `spa cares-import syn` on a hand-built valid response, and on each kind of
 * broken response. The packet is generated fresh by `spa cares-packet` from [CaresSyn]'s files: one
 * card file (`syn/1` to `syn/20`), `syn/2` already caring about `otag:draw` from Scryfall's tag.
 */
class CaresImportCommandTest {
    @TempDir lateinit var temp: Path

    private lateinit var data: Path
    private lateinit var packet: Path
    private val provenance = "claude-fable-5-1 via Claude Code, cares gaps v1, 2026-11-06"

    private val fableFile: Path
        get() = data.resolve("otags").resolve("syn.fable.json")

    private val response: Path
        get() = packet.resolve("cards-01.cares.json")

    private val suggestions: Path
        get() = packet.resolve("cares-table-suggestions.json")

    @BeforeEach
    fun packet() {
        data = CaresSyn.data(temp.resolve("data"))
        packet = temp.resolve("packet")
        val run = runCli("cares-packet", "syn", "--out", "$packet", "--data", "$data")
        assertEquals(ExitCode.OK, run.exit, run.stderr)
        write(
            """
            [
              { "id": "syn/3", "add": [{ "fact": "creature", "strength": 2, "reason": "Rewards creatures." }] },
              { "id": "syn/5", "add": [] }
            ]
            """
        )
    }

    private fun write(text: String) = Files.writeString(response, text.trimIndent())

    private fun import(vararg more: String): CliRun =
        runCli(
            "cares-import",
            "syn",
            "--packet",
            "$packet",
            "--provenance",
            provenance,
            "--data",
            "$data",
            *more,
        )

    /** Asserts exactly one `spa: ` line, containing every text of [texts], and no file. */
    private fun assertFails(vararg texts: String) {
        val run = import()
        assertEquals(ExitCode.ERROR, run.exit, run.stdout)
        assertEquals("", run.stdout)
        assertEquals(1, run.stderrLines().size, run.stderr)
        val line = run.stderrLines().single()
        assertTrue(line.startsWith("spa: "), line)
        texts.forEach { assertTrue(it in line, "'$it' not in '$line'") }
        assertTrue(Files.notExists(fableFile), "nothing is written")
    }

    private fun add(id: String, vararg adds: String) =
        """{ "id": "$id", "add": [${adds.joinToString(", ")}] }"""

    private fun care(fact: String, strength: Int = 2, reason: String = "A reason.") =
        """{ "fact": "$fact", "strength": $strength, "reason": ${Json.encodeToString(String.serializer(), reason)} }"""

    @Test
    fun `a valid set writes the Fable file, and prints the count`() {
        val run = import()

        assertEquals(ExitCode.OK, run.exit, run.stderr)
        assertEquals(listOf("syn: additions 1", "wrote $fableFile"), run.stdoutLines())
        val file = Json.parseToJsonElement(fableFile.readText()).jsonObject
        assertEquals("syn", file.getValue("set").jsonPrimitive.content)
        assertEquals(provenance, file.getValue("provenance").jsonPrimitive.content)
        val cards = file.getValue("cards").jsonArray.map { it.jsonObject }
        assertEquals(listOf("syn/3"), cards.map { it.getValue("id").jsonPrimitive.content })
        val cares = cards.single().getValue("cares").jsonArray.single().jsonObject
        assertEquals("creature", cares.getValue("fact").jsonPrimitive.content)
        assertEquals(2, cares.getValue("strength").jsonPrimitive.content.toInt())
        assertEquals("Rewards creatures.", cares.getValue("reason").jsonPrimitive.content)
        assertTrue(fableFile.readText().endsWith("}\n"))
    }

    @Test
    fun `only cards with additions are written, in collector order`() {
        write(
            "[" +
                listOf(
                        add("syn/5", care("creature", 1)),
                        add("syn/2", care("otag:cantrip", 3)),
                        add("syn/10", care("otag:draw", 1), care("creature", 2)),
                        add("syn/7"),
                    )
                    .joinToString(",") +
                "]"
        )
        val run = import()

        assertEquals(ExitCode.OK, run.exit, run.stderr)
        assertEquals("syn: additions 4", run.stdoutLines().first())
        val ids =
            Json.parseToJsonElement(fableFile.readText())
                .jsonObject
                .getValue("cards")
                .jsonArray
                .map { it.jsonObject.getValue("id").jsonPrimitive.content }
        assertEquals(listOf("syn/2", "syn/5", "syn/10"), ids)
    }

    @Test
    fun `a rerun gives the same bytes, and a response with no additions writes no cards`() {
        assertEquals(ExitCode.OK, import().exit)
        val first = fableFile.readText()
        assertEquals(ExitCode.OK, import().exit)
        assertEquals(first, fableFile.readText())

        write("[]")
        val empty = import()
        assertEquals(ExitCode.OK, empty.exit, empty.stderr)
        assertEquals("syn: additions 0", empty.stdoutLines().first())
        assertEquals(
            0,
            Json.parseToJsonElement(fableFile.readText())
                .jsonObject
                .getValue("cards")
                .jsonArray
                .size,
        )
    }

    @Test
    fun `a missing response file is named, and nothing is written`() {
        Files.delete(response)

        assertFails("cards-01.cares.json", "missing")
    }

    @Test
    fun `a card outside its file is named`() {
        write("[" + add("syn/99", care("creature")) + "]")

        assertFails("cards-01.cares.json", "syn/99", "not a card of")
    }

    @Test
    fun `a fact outside the vocabulary is named`() {
        write("[" + add("syn/3", care("creature"), care("nonsense")) + "]")

        assertFails("syn/3", "'nonsense'", "vocabulary.json")
    }

    @Test
    fun `a strength of 0 or 4 is named`() {
        write("[" + add("syn/3", care("creature", 0)) + "]")
        assertFails("syn/3", "strength 0")

        write("[" + add("syn/3", care("creature", 4)) + "]")
        assertFails("syn/3", "strength 4")
    }

    @Test
    fun `a repeated fact is named`() {
        write("[" + add("syn/3", care("creature", 1), care("creature", 3)) + "]")

        assertFails("syn/3", "'creature'", "twice")
    }

    @Test
    fun `a fact the card already cares about from Scryfall is named`() {
        write("[" + add("syn/2", care("otag:draw", 3)) + "]")

        assertFails("syn/2", "'otag:draw'", "already cares")
    }

    @Test
    fun `an empty reason is named`() {
        write("[" + add("syn/3", care("creature", 2, "")) + "]")
        assertFails("syn/3", "'creature'", "reason")

        write("[" + add("syn/3", care("creature", 2, "   ")) + "]")
        assertFails("syn/3", "'creature'", "reason")
    }

    @Test
    fun `every problem is one line, and nothing is written when any is`() {
        write(
            "[" +
                listOf(
                        add("syn/99", care("creature")),
                        add("syn/3", care("nonsense", 9, "")),
                        add("syn/2", care("otag:draw")),
                    )
                    .joinToString(",") +
                "]"
        )
        val run = import()

        assertEquals(ExitCode.ERROR, run.exit)
        assertEquals(5, run.stderrLines().size, run.stderr)
        assertTrue(Files.notExists(fableFile))
    }

    @Test
    fun `a response that is not JSON or not in shape is named, per entry`() {
        write("{ nope")
        assertFails("cards-01.cares.json", "not JSON")

        write("""{ "id": "syn/3" }""")
        assertFails("cards-01.cares.json", "not a JSON array")

        write("""[ { "id": "syn/3" }, 7, { "id": "syn/4", "add": [{ "fact": "creature" }] } ]""")
        val run = import()
        assertEquals(ExitCode.ERROR, run.exit)
        assertEquals(3, run.stderrLines().size, run.stderr)
        assertTrue(Files.notExists(fableFile))
    }

    private fun suggest(text: String) = Files.writeString(suggestions, text.trimIndent())

    @Test
    fun `a valid suggestion is printed, one line`() {
        suggest(
            """
            [ { "tag": "cantrip", "facts": [{ "fact": "creature", "strength": 1 }],
                "reason": "A cantrip cares about creatures." } ]
            """
        )
        val run = import()

        assertEquals(ExitCode.OK, run.exit, run.stderr)
        assertEquals(
            listOf(
                "syn: additions 1",
                "suggestion: cantrip: creature 1: A cantrip cares about creatures.",
                "wrote $fableFile",
            ),
            run.stdoutLines(),
        )
        assertTrue(Files.exists(fableFile))
    }

    @Test
    fun `a bad suggestion is named, whether its tag is in the table or not a Scryfall tag, or its fact unknown`() {
        suggest(
            """[ { "tag": "legends-matter", "facts": [{ "fact": "creature", "strength": 1 }], "reason": "r" } ]"""
        )
        assertFails("legends-matter", "already in cares.json")

        suggest(
            """[ { "tag": "cantrip", "facts": [{ "fact": "nonsense", "strength": 1 }], "reason": "r" } ]"""
        )
        assertFails("cantrip", "'nonsense'", "vocabulary.json")

        suggest(
            """[ { "tag": "name-matters", "facts": [{ "fact": "creature", "strength": 1 }], "reason": "r" } ]"""
        )
        assertFails("name-matters", "not a Scryfall tag")

        suggest("{ nope")
        assertFails("cares-table-suggestions.json", "not JSON")
    }

    @Test
    fun `a packet that is incomplete or not the set's, or a missing option, is named`() {
        Files.delete(packet.resolve("vocabulary.json"))
        val noVocabulary = import()
        assertEquals(ExitCode.ERROR, noVocabulary.exit)
        assertTrue("vocabulary.json" in noVocabulary.stderr, noVocabulary.stderr)
        assertTrue(Files.notExists(fableFile))

        val again = runCli("cares-packet", "syn", "--out", "$packet", "--data", "$data")
        assertEquals(ExitCode.OK, again.exit, again.stderr)
        Files.delete(packet.resolve("cares-01.json"))
        val noCares = import()
        assertEquals(ExitCode.ERROR, noCares.exit)
        assertTrue("cares-01.json" in noCares.stderr, noCares.stderr)

        assertEquals(
            ExitCode.OK,
            runCli("cares-packet", "syn", "--out", "$packet", "--data", "$data").exit,
        )
        Files.delete(packet.resolve("cards-01.json"))
        assertEquals(ExitCode.ERROR, import().exit)

        val absent =
            runCli(
                "cares-import",
                "syn",
                "--packet",
                "${temp.resolve("nowhere")}",
                "--provenance",
                "p",
                "--data",
                "$data",
            )
        assertEquals(ExitCode.ERROR, absent.exit)
        assertTrue("packet directory not found" in absent.stderr, absent.stderr)

        val noPacket = runCli("cares-import", "syn", "--provenance", "p", "--data", "$data")
        assertEquals(ExitCode.USAGE, noPacket.exit)
        val noProvenance = runCli("cares-import", "syn", "--packet", "$packet", "--data", "$data")
        assertEquals(ExitCode.USAGE, noProvenance.exit)
    }

    @Test
    fun `the import reads the cares table, so a tag added to it since the packet is rejected`() {
        suggest(
            """[ { "tag": "cantrip", "facts": [{ "fact": "creature", "strength": 1 }], "reason": "r" } ]"""
        )
        writeFile(
            data,
            "otags/cares.json",
            """{ "cares": { "cantrip": [{ "fact": "creature", "strength": 1 }] } }""",
        )

        assertFails("cantrip", "already in cares.json")
    }
}
