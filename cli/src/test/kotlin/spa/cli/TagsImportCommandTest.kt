package spa.cli

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.copyTo
import kotlin.io.path.readText
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import spa.agent.tags.Axis
import spa.agent.tags.TagCard
import spa.agent.tags.TagCheck
import spa.agent.tags.TagChecks
import spa.agent.tags.TagFile
import spa.agent.tags.TagFiles
import spa.agent.tags.TagPartner
import spa.agent.tags.TagRole

/**
 * Criteria 4 and the seam half of 5: `spa tags-import syn` on a hand-built valid response set and
 * on each kind of broken response. The pass-2 packet is generated fresh by `spa tags-packet` from
 * the hand-built axes; `syn` has 20 cards, one card file, ids `syn/1` to `syn/20`.
 */
class TagsImportCommandTest {
    @TempDir lateinit var temp: Path

    private lateinit var data: Path
    private lateinit var packet: Path
    private val provenance = "claude-fable-5-1 via Claude Code, tags prompt v1, 2026-11-05"
    private val tagFile: Path
        get() = data.resolve("tags").resolve("syn.json")

    private fun resource(name: String): Path =
        Path.of(requireNotNull(javaClass.getResource("/tags-import/$name")).toURI())

    private val response: Path
        get() = packet.resolve("cards-01.response.json")

    @BeforeEach
    fun packet() {
        data = Fixtures.synDataDir(temp.resolve("data"))
        packet = temp.resolve("packet")
        val run =
            runCli(
                "tags-packet",
                "syn",
                "--axes",
                resource("syn/axes.response.json").toString(),
                "--out",
                packet.toString(),
                "--data",
                data.toString(),
                // SPA-051 made v2 the default; these are the v1 tests.
                "--prompt",
                "v1",
            )
        assertEquals(ExitCode.OK, run.exit, run.stderr)
        resource("syn/cards-01.response.json").copyTo(response)
    }

    private fun args(): List<String> =
        listOf(
            "syn",
            "--packet",
            packet.toString(),
            "--provenance",
            provenance,
            "--data",
            data.toString(),
        )

    private fun import(): CliRun = runCli("tags-import", *args().toTypedArray())

    /** Replaces [old] with [new] on the response line of card [id]; [old] must be on it. */
    private fun edit(id: String, old: String, new: String) {
        val lines = response.readText().lines()
        val at = lines.indexOfFirst { "\"id\": \"$id\"," in it }
        assertTrue(at >= 0 && old in lines[at], "fixture line of $id has no '$old'")
        val edited = lines.toMutableList().also { it[at] = it[at].replace(old, new) }
        Files.writeString(response, edited.joinToString("\n"))
    }

    private fun assertFails(vararg problems: String) {
        val run = import()
        assertEquals(ExitCode.ERROR, run.exit)
        assertEquals("", run.stdout)
        assertEquals(problems.map { "spa: $it" }, run.stderrLines())
        assertTrue(Files.notExists(data.resolve("tags")), "nothing is written")
    }

    private fun role(axis: String, role: String, strength: Int) = TagRole(axis, role, strength)

    private fun card(
        n: Int,
        name: String,
        roles: List<TagRole> = emptyList(),
        partners: List<TagPartner> = emptyList(),
    ) = TagCard("syn/$n", name, roles, partners)

    /** The tag file the hand-built responses must give, written out here by hand. */
    private val expected =
        TagFile(
            set = "syn",
            provenance = provenance,
            axes =
                listOf(
                    Axis("flying", "Flying", "A creature with flying."),
                    Axis("graveyard", "Graveyard", "Puts creature cards into a graveyard."),
                    Axis("lifegain", "Life gain", "Gains you life."),
                    Axis(
                        "double-faced",
                        "Double-faced cards",
                        "A card with two faces that transforms.",
                    ),
                ),
            cards =
                listOf(
                    card(1, "Grizzly Bears"),
                    card(2, "Serra Angel", listOf(role("flying", "enabler", 2))),
                    card(3, "Air Elemental", listOf(role("flying", "enabler", 2))),
                    card(4, "Counterspell"),
                    card(5, "Doom Blade", listOf(role("graveyard", "enabler", 1))),
                    card(
                        6,
                        "Gravedigger",
                        listOf(role("graveyard", "payoff", 2), role("graveyard", "enabler", 1)),
                        listOf(TagPartner("syn/5", 1)),
                    ),
                    card(7, "Llanowar Elves"),
                    card(8, "Giant Growth"),
                    card(9, "Kitchen Finks", listOf(role("lifegain", "enabler", 2))),
                    card(
                        10,
                        "Lightning Helix",
                        listOf(role("lifegain", "enabler", 1)),
                        listOf(TagPartner("syn/9", 1)),
                    ),
                    card(
                        11,
                        "Delver of Secrets // Insectile Aberration",
                        listOf(role("double-faced", "enabler", 1)),
                    ),
                    card(
                        12,
                        "Huntmaster of the Fells // Ravager of the Fells",
                        listOf(role("lifegain", "enabler", 2), role("double-faced", "enabler", 1)),
                        listOf(TagPartner("syn/12", 1)),
                    ),
                    card(13, "Agadeem's Awakening // Agadeem, the Undercrypt"),
                    card(14, "Bonecrusher Giant // Stomp"),
                    card(
                        15,
                        "Murderous Rider // Swift End",
                        listOf(role("graveyard", "enabler", 1)),
                    ),
                    card(16, "Fire // Ice"),
                    card(17, "Wear // Tear"),
                    card(18, "Mind Stone"),
                    card(19, "Evolving Wilds"),
                    card(20, "Temple of Mystery"),
                ),
        )

    @Test
    fun `valid responses write a tag file that validates and reads back equal`() {
        val run = import()

        assertEquals(ExitCode.OK, run.exit, run.stderr)
        assertEquals(
            listOf("syn: 20 cards tagged on 4 axes from 1 response file", "wrote $tagFile"),
            run.stdoutLines(),
        )
        val text = tagFile.readText()
        assertEquals(emptyList(), TagFiles.validate(Json.parseToJsonElement(text)))
        assertEquals(expected, TagFiles.read(tagFile))
        assertEquals(text, TagFiles.write(TagFiles.read(tagFile)))
    }

    @Test
    fun `a card naming itself as a partner imports`() {
        edit("syn/9", "\"partners\": []", "\"partners\": [{ \"id\": \"syn/9\", \"strength\": 2 }]")

        val run = import()

        assertEquals(ExitCode.OK, run.exit, run.stderr)
        val finks = TagFiles.read(tagFile).cards.single { it.id == "syn/9" }
        assertEquals(listOf(TagPartner("syn/9", 2)), finks.partners)
    }

    @Test
    fun `a missing card`() {
        val text = response.readText()
        val line = "  { \"id\": \"syn/4\", \"roles\": [], \"partners\": [] },\n"
        assertTrue(line in text)
        Files.writeString(response, text.replace(line, ""))

        assertFails("cards-01.response.json: syn/4 is not tagged")
    }

    @Test
    fun `a card given twice`() {
        edit("syn/4", "\"id\": \"syn/4\"", "\"id\": \"syn/1\"")

        assertFails(
            "cards-01.response.json: syn/1 is tagged 2 times",
            "cards-01.response.json: syn/4 is not tagged",
        )
    }

    @Test
    fun `an unknown card id`() {
        edit("syn/4", "\"id\": \"syn/4\"", "\"id\": \"syn/44\"")

        assertFails(
            "cards-01.response.json: syn/44 is not a card in this packet",
            "cards-01.response.json: syn/4 is not tagged",
        )
    }

    @Test
    fun `an unknown axis`() {
        edit("syn/2", "\"axis\": \"flying\"", "\"axis\": \"flyers\"")

        assertFails("cards-01.response.json: syn/2 axis 'flyers' is not one of the packet's axes")
    }

    @Test
    fun `a bad role`() {
        edit("syn/2", "\"role\": \"enabler\"", "\"role\": \"supplier\"")

        assertFails(
            "cards-01.response.json: syn/2 role 'supplier' on flying is not enabler or payoff"
        )
    }

    @Test
    fun `a role strength of 0`() {
        edit("syn/2", "\"strength\": 2", "\"strength\": 0")

        assertFails(
            "cards-01.response.json: syn/2 strength 0 for enabler on flying is not 1, 2 or 3"
        )
    }

    @Test
    fun `a role strength of 4`() {
        edit("syn/2", "\"strength\": 2", "\"strength\": 4")

        assertFails(
            "cards-01.response.json: syn/2 strength 4 for enabler on flying is not 1, 2 or 3"
        )
    }

    @Test
    fun `a partner strength of 0 or 4`() {
        edit(
            "syn/10",
            "{ \"id\": \"syn/9\", \"strength\": 1 }",
            "{ \"id\": \"syn/9\", \"strength\": 4 }",
        )
        edit(
            "syn/6",
            "{ \"id\": \"syn/5\", \"strength\": 1 }",
            "{ \"id\": \"syn/5\", \"strength\": 0 }",
        )

        assertFails(
            "cards-01.response.json: syn/6 partner syn/5 strength 0 is not 1, 2 or 3",
            "cards-01.response.json: syn/10 partner syn/9 strength 4 is not 1, 2 or 3",
        )
    }

    @Test
    fun `a repeated axis and role`() {
        edit(
            "syn/6",
            "{ \"axis\": \"graveyard\", \"role\": \"enabler\", \"strength\": 1 }",
            "{ \"axis\": \"graveyard\", \"role\": \"payoff\", \"strength\": 1 }",
        )

        assertFails("cards-01.response.json: syn/6 gives payoff on graveyard 2 times")
    }

    @Test
    fun `a partner outside the pool`() {
        edit("syn/10", "\"id\": \"syn/9\"", "\"id\": \"syn/99\"")

        assertFails("cards-01.response.json: syn/10 partner syn/99 is not a card in this packet")
    }

    @Test
    fun `six partners`() {
        val six = (2..7).joinToString(", ") { "{ \"id\": \"syn/$it\", \"strength\": 1 }" }
        edit("syn/1", "\"partners\": []", "\"partners\": [$six]")

        assertFails("cards-01.response.json: syn/1 has 6 partners; give at most 5")
    }

    @Test
    fun `five partners import`() {
        val five = (2..6).joinToString(", ") { "{ \"id\": \"syn/$it\", \"strength\": 1 }" }
        edit("syn/1", "\"partners\": []", "\"partners\": [$five]")

        assertEquals(ExitCode.OK, import().exit)
    }

    @Test
    fun `a repeated partner`() {
        val twice = "{ \"id\": \"syn/2\", \"strength\": 1 }, { \"id\": \"syn/2\", \"strength\": 3 }"
        edit("syn/1", "\"partners\": []", "\"partners\": [$twice]")

        assertFails("cards-01.response.json: syn/1 names partner syn/2 2 times")
    }

    @Test
    fun `a missing response file`() {
        Files.delete(response)

        assertFails("cards-01.response.json: missing; tag every card in cards-01.json")
    }

    @Test
    fun `a malformed entry is named with its position`() {
        edit("syn/3", "\"roles\": [", "\"roles\": 7, \"was\": [")

        val run = import()

        assertEquals(ExitCode.ERROR, run.exit)
        assertEquals(
            listOf("spa: cards-01.response.json: entry 3: roles must be an array"),
            run.stderrLines().take(1),
        )
    }

    @Test
    fun `a packet with no axes file`() {
        Files.delete(packet.resolve("axes.json"))

        assertFails(
            "axes.json: missing; run 'spa tags-packet syn --axes <file>' to write the packet"
        )
    }

    @Test
    fun `a problem leaves an existing tag file untouched`() {
        val tags = Files.createDirectories(data.resolve("tags"))
        Files.writeString(tags.resolve("syn.json"), "old")
        edit("syn/2", "\"strength\": 2", "\"strength\": 4")

        val run = import()

        assertEquals(ExitCode.ERROR, run.exit)
        assertEquals("old", tags.resolve("syn.json").readText())
    }

    @Test
    fun `a test-only check added to the list rejects a response with no other change`() {
        val noPartnerless = TagCheck { input ->
            input.responses
                .filter { it.roles.isEmpty() && it.partners.isEmpty() && it.id == "syn/1" }
                .map { "${it.file}: ${it.id} has no tags (test-only check)" }
        }
        val out = ByteArrayOutputStream()
        val err = ByteArrayOutputStream()

        val exit =
            TagsImportCommand(TagChecks.ALL + noPartnerless)
                .run(
                    args(),
                    PrintStream(out, true, StandardCharsets.UTF_8),
                    PrintStream(err, true, StandardCharsets.UTF_8),
                )

        assertEquals(ExitCode.ERROR, exit)
        assertEquals(
            listOf("spa: cards-01.response.json: syn/1 has no tags (test-only check)"),
            err.toString(StandardCharsets.UTF_8).lines().filter { it.isNotBlank() },
        )
        assertTrue(Files.notExists(data.resolve("tags")), "nothing is written")
        assertEquals(ExitCode.OK, import().exit, "the default list accepts the same responses")
    }

    @Test
    fun `without --packet or --provenance it is a usage error`() {
        val noPacket =
            runCli("tags-import", "syn", "--provenance", provenance, "--data", data.toString())
        val noProvenance =
            runCli("tags-import", "syn", "--packet", packet.toString(), "--data", data.toString())

        assertEquals(ExitCode.USAGE, noPacket.exit)
        assertEquals("spa: --packet <dir> is required", noPacket.stderrLines().first())
        assertEquals(ExitCode.USAGE, noProvenance.exit)
        assertEquals("spa: --provenance <text> is required", noProvenance.stderrLines().first())
    }

    @Test
    fun `a packet directory that does not exist`() {
        val run =
            runCli(
                "tags-import",
                "syn",
                "--packet",
                temp.resolve("nope").toString(),
                "--provenance",
                provenance,
                "--data",
                data.toString(),
            )

        assertEquals(ExitCode.ERROR, run.exit)
        assertEquals(
            "spa: packet directory not found: ${temp.resolve("nope")}",
            run.stderrLines().single(),
        )
    }
}
