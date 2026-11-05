package spa.cli

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import spa.agent.tags.Axis
import spa.agent.tags.FactSupply
import spa.agent.tags.TagCard
import spa.agent.tags.TagCorrection
import spa.agent.tags.TagFile
import spa.agent.tags.TagFiles
import spa.agent.tags.TagPartner
import spa.agent.tags.TagReview
import spa.agent.tags.TagRole
import spa.agent.tags.review.Correction
import spa.agent.tags.review.ReviewAction
import spa.agent.tags.review.ReviewActions
import spa.agent.tags.review.ReviewCard
import spa.agent.tags.review.ReviewOutcome

/**
 * SPA-052 criteria 3 and 5: `spa tags-review-import syn` on hand-built reviews of a hand-built v2
 * tag file. The review packet is made fresh by `spa tags-review-packet`; `syn` has 20 cards in one
 * card file, `syn/1` to `syn/20`.
 */
class TagsReviewImportCommandTest {
    @TempDir lateinit var temp: Path

    private lateinit var data: Path
    private lateinit var packet: Path
    private val provenance = "claude-fable-5-1 via Claude Code, tags review prompt v1, 2026-11-05"

    private val tagPath: Path
        get() = data.resolve("tags").resolve("syn.json")

    private val reviewFile: Path
        get() = packet.resolve("cards-01.review.json")

    private val axes =
        listOf(
            Axis("flying", "Flying", "A creature with flying.", listOf(FactSupply("kw:flying", 2))),
            Axis(
                "graveyard",
                "Graveyard",
                "Puts creatures into a graveyard.",
                listOf(FactSupply("dies-trigger", 1)),
            ),
            Axis("lifegain", "Life gain", "Gains you life.", listOf()),
        )

    private fun role(axis: String, role: String, strength: Int) = TagRole(axis, role, strength)

    private val names =
        listOf(
            "Grizzly Bears",
            "Serra Angel",
            "Air Elemental",
            "Counterspell",
            "Doom Blade",
            "Gravedigger",
            "Llanowar Elves",
            "Giant Growth",
            "Kitchen Finks",
            "Lightning Helix",
            "Delver of Secrets // Insectile Aberration",
            "Huntmaster of the Fells // Ravager of the Fells",
            "Agadeem's Awakening // Agadeem, the Undercrypt",
            "Bonecrusher Giant // Stomp",
            "Murderous Rider // Swift End",
            "Fire // Ice",
            "Wear // Tear",
            "Mind Stone",
            "Evolving Wilds",
            "Temple of Mystery",
        )

    private val base =
        TagFile(
            set = "syn",
            provenance = "claude-fable-5-1 via Claude Code, tags prompt v2, 2026-11-05",
            axes = axes,
            cards =
                names.mapIndexed { i, name ->
                    val id = "syn/${i + 1}"
                    when (i + 1) {
                        2 -> TagCard(id, name, listOf(role("flying", "payoff", 1)), listOf())
                        6 ->
                            TagCard(
                                id,
                                name,
                                listOf(role("graveyard", "payoff", 2)),
                                listOf(TagPartner("syn/5", 1)),
                            )
                        9 -> TagCard(id, name, listOf(role("lifegain", "enabler", 2)), listOf())
                        10 ->
                            TagCard(
                                id,
                                name,
                                listOf(role("lifegain", "enabler", 1)),
                                listOf(TagPartner("syn/9", 1)),
                            )
                        12 -> TagCard(id, name, listOf(), (1..5).map { TagPartner("syn/$it", 1) })
                        else -> TagCard(id, name, listOf(), listOf())
                    }
                },
        )

    private lateinit var baseText: String

    @BeforeEach
    fun packet() {
        data = Fixtures.synDataDir(temp.resolve("data"))
        baseText = TagFiles.write(base)
        writeFile(data, "tags/syn.json", baseText)
        packet = temp.resolve("packet")
        val run = runCli("tags-review-packet", "syn", "--out", "$packet", "--data", "$data")
        assertEquals(ExitCode.OK, run.exit, run.stderr)
    }

    private fun review(vararg corrections: String) {
        Files.writeString(reviewFile, corrections.joinToString(",\n", "[\n", "\n]\n"))
    }

    private fun args(): Array<String> =
        arrayOf("syn", "--packet", "$packet", "--provenance", provenance, "--data", "$data")

    private fun import(): CliRun = runCli("tags-review-import", *args())

    private fun assertFails(vararg problems: String) {
        val run = import()
        assertEquals(ExitCode.ERROR, run.exit, run.stderr)
        assertEquals("", run.stdout)
        assertEquals(problems.map { "spa: $it" }, run.stderrLines())
        assertEquals(baseText, tagPath.readText(), "nothing is written")
    }

    private val file = "cards-01.review.json"

    private val five =
        arrayOf(
            """{ "id": "syn/10", "action": "remove-partner", "partner": "syn/9", "reason": "No link." }""",
            """{ "id": "syn/3", "action": "add-role", "axis": "flying", "role": "payoff", "strength": 1, "reason": "Rewards fliers." }""",
            """{ "id": "syn/2", "action": "set-strength", "axis": "flying", "role": "payoff", "strength": 2, "reason": "Clearly better." }""",
            """{ "id": "syn/10", "action": "remove-role", "axis": "lifegain", "role": "enabler", "reason": "Not the point." }""",
            """{ "id": "syn/5", "action": "add-partner", "partner": "syn/6", "strength": 2, "reason": "Fills the yard." }""",
        )

    private val suggestion =
        """[{ "fact": "gains-life", "rule": "Its rules text says you gain life.",
              "examples": ["syn/9", "syn/10"], "reason": "Several cards gain life." }]"""

    @Test
    fun `a valid review writes the corrected file with its review block, and prints the counts`() {
        review(*five)
        Files.writeString(packet.resolve("fact-suggestions.json"), suggestion)

        val run = import()

        assertEquals(ExitCode.OK, run.exit, run.stderr)
        assertEquals("", run.stderr)
        assertEquals(
            listOf(
                "syn: 5 corrections from 1 review file: add-role 1, remove-role 1, " +
                    "set-strength 1, add-partner 1, remove-partner 1",
                "fact suggestion: gains-life: Its rules text says you gain life. " +
                    "(examples syn/9, syn/10): Several cards gain life.",
                "wrote $tagPath",
            ),
            run.stdoutLines(),
        )
        val cards =
            base.cards.map {
                when (it.id) {
                    "syn/2" -> it.copy(roles = listOf(role("flying", "payoff", 2)))
                    "syn/3" -> it.copy(roles = listOf(role("flying", "payoff", 1)))
                    "syn/5" -> it.copy(partners = listOf(TagPartner("syn/6", 2)))
                    "syn/10" -> it.copy(roles = listOf(), partners = listOf())
                    else -> it
                }
            }
        val expected =
            base.copy(
                cards = cards,
                review =
                    TagReview(
                        provenance,
                        linkedMapOf(
                            "add-role" to 1,
                            "remove-role" to 1,
                            "set-strength" to 1,
                            "add-partner" to 1,
                            "remove-partner" to 1,
                        ),
                        listOf(
                            TagCorrection(
                                "syn/2",
                                "set-strength",
                                "flying",
                                "payoff",
                                null,
                                2,
                                "Clearly better.",
                            ),
                            TagCorrection(
                                "syn/3",
                                "add-role",
                                "flying",
                                "payoff",
                                null,
                                1,
                                "Rewards fliers.",
                            ),
                            TagCorrection(
                                "syn/5",
                                "add-partner",
                                partner = "syn/6",
                                strength = 2,
                                reason = "Fills the yard.",
                            ),
                            TagCorrection(
                                "syn/10",
                                "remove-partner",
                                partner = "syn/9",
                                reason = "No link.",
                            ),
                            TagCorrection(
                                "syn/10",
                                "remove-role",
                                "lifegain",
                                "enabler",
                                reason = "Not the point.",
                            ),
                        ),
                    ),
            )
        val text = tagPath.readText()
        assertEquals(expected, TagFiles.parse(text))
        assertEquals(emptyList(), TagFiles.validate(Json.parseToJsonElement(text)))
        assertEquals(
            base.provenance,
            TagFiles.parse(text).provenance,
            "the tagging provenance stays",
        )
    }

    @Test
    fun `an empty review is valid, and writes the file unchanged but for a zero review`() {
        review()

        val run = import()

        assertEquals(ExitCode.OK, run.exit, run.stderr)
        assertEquals(
            "syn: 0 corrections from 1 review file: add-role 0, remove-role 0, set-strength 0, " +
                "add-partner 0, remove-partner 0",
            run.stdoutLines().first(),
        )
        val written = TagFiles.read(tagPath)
        assertEquals(base, written.copy(review = null))
        assertEquals(emptyList(), written.review!!.corrections)
    }

    @Test
    fun `a correction for a card outside its file`() {
        review(
            """{ "id": "syn/21", "action": "remove-partner", "partner": "syn/9", "reason": "x" }"""
        )

        assertFails("$file: correction 1: syn/21 is not a card in cards-01.json")
    }

    @Test
    fun `removing a missing role`() {
        review(
            """{ "id": "syn/2", "action": "remove-role", "axis": "flying", "role": "enabler", "reason": "x" }"""
        )

        assertFails("$file: correction 1: syn/2 has no enabler role on flying to remove")
    }

    @Test
    fun `setting the strength of a missing role`() {
        review(
            """{ "id": "syn/1", "action": "set-strength", "axis": "lifegain", "role": "payoff", "strength": 2, "reason": "x" }"""
        )

        assertFails(
            "$file: correction 1: syn/1 has no payoff role on lifegain to set the strength of"
        )
    }

    @Test
    fun `removing a missing partner`() {
        review(
            """{ "id": "syn/6", "action": "remove-partner", "partner": "syn/7", "reason": "x" }"""
        )

        assertFails("$file: correction 1: syn/6 has no partner syn/7 to remove")
    }

    @Test
    fun `a duplicate add of a role or a partner`() {
        review(
            """{ "id": "syn/2", "action": "add-role", "axis": "flying", "role": "payoff", "strength": 3, "reason": "x" }""",
            """{ "id": "syn/6", "action": "add-partner", "partner": "syn/5", "strength": 2, "reason": "x" }""",
        )

        assertFails(
            "$file: correction 1: syn/2 already has the payoff role on flying",
            "$file: correction 2: syn/6 already has the partner syn/5",
        )
    }

    @Test
    fun `an empty reason`() {
        review(
            """{ "id": "syn/6", "action": "remove-partner", "partner": "syn/5", "reason": " " }"""
        )

        assertFails("$file: correction 1: the reason is empty")
    }

    @Test
    fun `a result with a strength of 4`() {
        review(
            """{ "id": "syn/2", "action": "set-strength", "axis": "flying", "role": "payoff", "strength": 4, "reason": "x" }"""
        )

        assertFails("$file: syn/2 strength 4 for payoff on flying is not 1, 2 or 3")
    }

    @Test
    fun `a result with an unknown axis`() {
        review(
            """{ "id": "syn/1", "action": "add-role", "axis": "tokens", "role": "enabler", "strength": 1, "reason": "x" }"""
        )

        assertFails("$file: syn/1 axis 'tokens' is not one of the packet's axes")
    }

    @Test
    fun `a result with six partners`() {
        review(
            """{ "id": "syn/12", "action": "add-partner", "partner": "syn/6", "strength": 1, "reason": "x" }"""
        )

        assertFails("$file: syn/12 has 6 partners; give at most 5")
    }

    @Test
    fun `a result with a partner outside the pool, or a bad role`() {
        review(
            """{ "id": "syn/1", "action": "add-partner", "partner": "syn/99", "strength": 1, "reason": "x" }""",
            """{ "id": "syn/1", "action": "add-role", "axis": "flying", "role": "maker", "strength": 1, "reason": "x" }""",
        )

        assertFails(
            "$file: syn/1 role 'maker' on flying is not enabler or payoff",
            "$file: syn/1 partner syn/99 is not a card in this packet",
        )
    }

    @Test
    fun `every problem is named in one run`() {
        review(
            """{ "id": "syn/21", "action": "remove-partner", "partner": "syn/9", "reason": "x" }""",
            """{ "id": "syn/2", "action": "set-strength", "axis": "flying", "role": "payoff", "strength": 4, "reason": "x" }""",
            """{ "id": "syn/6", "action": "remove-partner", "partner": "syn/5", "reason": "" }""",
        )

        assertFails(
            "$file: correction 3: the reason is empty",
            "$file: correction 1: syn/21 is not a card in cards-01.json",
            "$file: syn/2 strength 4 for payoff on flying is not 1, 2 or 3",
        )
    }

    @Test
    fun `a missing review file`() {
        assertFails(
            "$file: missing; write one for cards-01.json, an empty array when no card needs a " +
                "correction"
        )
    }

    @Test
    fun `a malformed fact suggestions file`() {
        review()
        Files.writeString(packet.resolve("fact-suggestions.json"), """[{ "fact": "x" }]""")

        assertFails("fact-suggestions.json: suggestion 1: rule must be a string")
    }

    @Test
    fun `a tag file changed since the packet was built is one line`() {
        review(*five)
        val changed = TagFiles.write(base.copy(provenance = "edited"))
        Files.writeString(tagPath, changed)

        val run = import()

        assertEquals(ExitCode.ERROR, run.exit)
        assertEquals(
            listOf(
                "spa: the tag file $tagPath changed since the packet was built; run " +
                    "'spa tags-review-packet syn' again"
            ),
            run.stderrLines(),
        )
        assertEquals(changed, tagPath.readText(), "nothing is written")
    }

    @Test
    fun `a second import of the same packet stops, because the first changed the tag file`() {
        review(*five)
        assertEquals(ExitCode.OK, import().exit)

        assertEquals(ExitCode.ERROR, import().exit)
    }

    @Test
    fun `a packet with no tag-file json, or no directory, is one line`() {
        review()
        Files.delete(packet.resolve("tag-file.json"))

        val run = import()
        val none =
            runCli(
                "tags-review-import",
                "syn",
                "--packet",
                "${temp.resolve("none")}",
                "--provenance",
                "p",
                "--data",
                "$data",
            )

        assertEquals(ExitCode.ERROR, run.exit)
        assertEquals(
            listOf(
                "spa: tag-file.json: missing; run 'spa tags-review-packet syn' to write the packet"
            ),
            run.stderrLines(),
        )
        assertEquals(ExitCode.ERROR, none.exit)
        assertEquals(
            listOf("spa: packet directory not found: ${temp.resolve("none")}"),
            none.stderrLines(),
        )
    }

    @Test
    fun `without --packet or --provenance it is a usage error`() {
        val noPacket = runCli("tags-review-import", "syn", "--provenance", "p", "--data", "$data")
        val noProvenance =
            runCli("tags-review-import", "syn", "--packet", "$packet", "--data", "$data")

        assertEquals(ExitCode.USAGE, noPacket.exit)
        assertEquals(ExitCode.USAGE, noProvenance.exit)
        assertTrue("--packet <dir> is required" in noPacket.stderr, noPacket.stderr)
        assertTrue("--provenance <text> is required" in noProvenance.stderr, noProvenance.stderr)
    }

    /** A test-only action: takes every role off a card. */
    private object ClearRoles : ReviewAction {
        override val name = "clear-roles"
        override val fields = emptySet<String>()

        override fun apply(card: ReviewCard, correction: Correction): ReviewOutcome =
            ReviewOutcome.Applied(card.copy(roles = emptyList()))
    }

    @Test
    fun `a test-only action added to the list applies with no other code change`() {
        review("""{ "id": "syn/9", "action": "clear-roles", "reason": "Not a gainer here." }""")
        assertFails(
            "$file: correction 1: unknown action 'clear-roles'; use add-role, remove-role, " +
                "set-strength, add-partner or remove-partner"
        )
        val out = ByteArrayOutputStream()
        val err = ByteArrayOutputStream()

        val exit =
            TagsReviewImportCommand(ReviewActions.ALL + ClearRoles)
                .run(
                    args().toList(),
                    PrintStream(out, true, StandardCharsets.UTF_8),
                    PrintStream(err, true, StandardCharsets.UTF_8),
                )

        assertEquals(ExitCode.OK, exit, err.toString(StandardCharsets.UTF_8))
        val written = TagFiles.read(tagPath)
        assertEquals(listOf(), written.cards.single { it.id == "syn/9" }.roles)
        assertEquals(1, written.review!!.counts["clear-roles"])
        assertEquals(
            TagCorrection("syn/9", "clear-roles", reason = "Not a gainer here."),
            written.review!!.corrections.single(),
        )
        assertTrue("clear-roles 1" in out.toString(StandardCharsets.UTF_8))
    }

    @Test
    fun `help lists both commands`() {
        assertTrue(
            "tags-review-packet <code> --out <dir> [--tags <file>] [--prompt <version>]" in
                Main.USAGE
        )
        assertTrue(
            "tags-review-import <code> --packet <dir> --provenance <text> [--data <dir>]" in
                Main.USAGE
        )
    }
}
