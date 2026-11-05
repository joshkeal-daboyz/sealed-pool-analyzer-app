package spa.cli

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readBytes
import kotlin.io.path.readText
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import spa.agent.tags.Axis
import spa.agent.tags.FactSupply
import spa.agent.tags.TagCard
import spa.agent.tags.TagFile
import spa.agent.tags.TagFiles
import spa.agent.tags.TagRole
import spa.agent.tags.review.ReviewPrompts

/**
 * SPA-052 criterion 1: `spa tags-review-packet trk --out <dir>` on the v1 trk tag file (frozen
 * since SPA-053 as `fixtures/tags/v1/trk.json`, given with `--tags`), and the command's options and
 * failures.
 */
class TagsReviewPacketCommandTest {
    @TempDir lateinit var temp: Path

    private val trkTags: Path = Path.of(Fixtures.v1Tags("trk"))

    private fun packet(set: String, out: Path, vararg extra: String): CliRun =
        runCli(
            "tags-review-packet",
            set,
            "--out",
            out.toString(),
            "--tags",
            trkTags.toString(),
            "--data",
            Fixtures.data,
            *extra,
        )

    private val trk: Path by lazy {
        temp.resolve("trk").also {
            val run = packet("trk", it)
            assertEquals(ExitCode.OK, run.exit, run.stderr)
        }
    }

    private fun numbered(stem: String) = (1..7).map { "$stem-%02d.json".format(it) }

    private fun cardFiles(dir: Path): List<Path> =
        dir.listDirectoryEntries()
            .filter { Regex("cards-[0-9]+\\.json").matches(it.name) }
            .sortedBy { it.name }

    private fun ids(file: Path): List<String> =
        Json.parseToJsonElement(file.readText()).jsonArray.map {
            it.jsonObject.getValue("id").jsonPrimitive.content
        }

    private fun bytes(dir: Path): Map<String, List<Byte>> =
        dir.listDirectoryEntries().associate { it.name to it.readBytes().toList() }

    @Test
    fun `it writes the files the ticket names, and prints a summary`() {
        val expected =
            setOf(
                "instructions.md",
                "facts.json",
                "tag-file.json",
                "review.schema.json",
                "fact-suggestions.schema.json",
            ) + numbered("cards") + numbered("tags")

        assertEquals(expected, trk.listDirectoryEntries().map { it.name }.toSet())
        val run = packet("trk", temp.resolve("again"))
        assertEquals(
            listOf("trk: 336 cards in 7 card files, 15 axes", "wrote ${temp.resolve("again")}"),
            run.stdoutLines(),
        )
    }

    @Test
    fun `the card files hold the 336 cards, byte-identical to the grade packet's`() {
        val grades = temp.resolve("grades")
        val graded =
            runCli("grade-packet", "trk", "--out", grades.toString(), "--data", Fixtures.data)
        assertEquals(ExitCode.OK, graded.exit, graded.stderr)

        assertEquals(numbered("cards"), cardFiles(trk).map { it.name })
        assertEquals(cardFiles(grades).map { it.name }, cardFiles(trk).map { it.name })
        cardFiles(grades).forEach {
            assertContentEquals(it.readBytes(), trk.resolve(it.name).readBytes(), it.name)
        }
        assertEquals(336, cardFiles(trk).sumOf { ids(it).size })
    }

    @Test
    fun `each tags file equals the tag file's entries for its card file's cards, in order`() {
        val tags = TagFiles.read(trkTags)
        val byId = tags.cards.associateBy { it.id }
        val serializer = ListSerializer(TagCard.serializer())

        val held =
            cardFiles(trk).flatMap { cardFile ->
                val tagsFile = trk.resolve(cardFile.name.replace("cards-", "tags-"))
                val entries = Json.decodeFromString(serializer, tagsFile.readText())
                assertEquals(ids(cardFile).map { byId.getValue(it) }, entries, tagsFile.name)
                entries
            }
        assertEquals(336, held.size)
        assertEquals(tags.cards.map { it.id }.toSet(), held.map { it.id }.toSet())
    }

    @Test
    fun `facts json is byte-identical to the v2 tags packet's`() {
        val tagsPacket = temp.resolve("tags")
        val run =
            runCli("tags-packet", "trk", "--out", tagsPacket.toString(), "--data", Fixtures.data)
        assertEquals(ExitCode.OK, run.exit, run.stderr)

        assertContentEquals(
            tagsPacket.resolve("facts.json").readBytes(),
            trk.resolve("facts.json").readBytes(),
        )
    }

    @Test
    fun `the instructions are the prompt exactly, then the axes, then the brief and themes`() {
        val prompt = ReviewPrompts.load("v1")!!
        val text = trk.resolve("instructions.md").readText()
        val axes = TagFiles.read(trkTags).axes

        assertTrue(text.startsWith(prompt + "\n## The set's synergy axes\n"), text.take(300))
        axes.forEach { assertTrue("- `${it.id}`, ${it.name}: ${it.definition}\n" in text, it.id) }
        val axesAt = text.indexOf("## The set's synergy axes")
        val briefAt = text.indexOf("## The format: ")
        val themesAt = text.indexOf("## Themes in this set")
        assertTrue(axesAt in 1 until briefAt && briefAt < themesAt, "$axesAt $briefAt $themesAt")
        assertTrue("`cards-01.review.json` to `cards-07.review.json`" in text, text)
        assertTrue("`fact-suggestions.json`" in text)
    }

    @Test
    fun `the schemas are the committed resources`() {
        listOf("review.schema.json", "fact-suggestions.schema.json").forEach {
            val resource = requireNotNull(TagFiles::class.java.getResource("/spa/agent/tags/$it"))
            assertEquals(resource.readText(), trk.resolve(it).readText(), it)
        }
    }

    @Test
    fun `tag-file json records the tag file's path and the SHA-256 of its bytes`() {
        val digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(trkTags))
        val sha = digest.joinToString("") { "%02x".format(it) }
        val source = Json.parseToJsonElement(trk.resolve("tag-file.json").readText()).jsonObject

        assertEquals(setOf("path", "sha256"), source.keys)
        assertEquals(
            trkTags.toAbsolutePath().normalize().toString(),
            source.getValue("path").jsonPrimitive.content,
        )
        assertEquals(sha, source.getValue("sha256").jsonPrimitive.content)
    }

    @Test
    fun `a rerun gives the same bytes, and keeps the review files`() {
        val first = bytes(trk)
        Files.writeString(trk.resolve("cards-01.review.json"), "[]")
        Files.writeString(trk.resolve("fact-suggestions.json"), "[]")
        Files.writeString(trk.resolve("tags-08.json"), "stale")

        val run = packet("trk", trk)

        assertEquals(ExitCode.OK, run.exit, run.stderr)
        val second = bytes(trk)
        assertEquals(first, second - "cards-01.review.json" - "fact-suggestions.json")
        assertEquals("[]", trk.resolve("cards-01.review.json").readText())
        assertEquals(first, bytes(temp.resolve("other").also { packet("trk", it) }))
    }

    /** A v2 tag file for `syn` with one supplied axis, in a data directory under [temp]. */
    private fun synData(): Path {
        val data = Fixtures.synDataDir(temp.resolve("data"))
        val axes =
            listOf(
                Axis(
                    "flying",
                    "Flying",
                    "A creature with flying.",
                    listOf(FactSupply("kw:flying", 2)),
                ),
                Axis("graveyard", "Graveyard", "Puts creature cards into a graveyard.", listOf()),
                Axis("lifegain", "Life gain", "Gains you life.", listOf()),
            )
        val cards =
            (1..20).map {
                val roles = if (it == 2) listOf(TagRole("flying", "payoff", 1)) else listOf()
                TagCard("syn/$it", "Card $it", roles, listOf())
            }
        writeFile(data, "tags/syn.json", TagFiles.write(TagFile("syn", "hand-built", axes, cards)))
        return data
    }

    @Test
    fun `--tags picks the tag file, and a supplied axis shows its supply`() {
        val data = synData()
        val moved = data.resolve("elsewhere.json")
        Files.move(data.resolve("tags/syn.json"), moved)
        val out = temp.resolve("syn")

        val run =
            runCli(
                "tags-review-packet",
                "syn",
                "--out",
                "$out",
                "--tags",
                "$moved",
                "--data",
                "$data",
            )

        assertEquals(ExitCode.OK, run.exit, run.stderr)
        val text = out.resolve("instructions.md").readText()
        assertTrue(
            "- `flying`, Flying: A creature with flying. Supplied by: `kw:flying` 2.\n" in text,
            text,
        )
        assertTrue(
            "- `graveyard`, Graveyard: Puts creature cards into a graveyard. Supplied by: no fact.\n" in
                text
        )
        assertEquals(listOf("cards-01.json"), cardFiles(out).map { it.name })
        assertTrue(out.resolve("tag-file.json").readText().contains("elsewhere.json"))
    }

    @Test
    fun `a missing or invalid tag file, a file for another set, or an unknown prompt is one line`() {
        val data = synData()
        val out = temp.resolve("syn")
        val review = { extra: Array<String> ->
            runCli("tags-review-packet", "syn", "--out", "$out", "--data", "$data", *extra)
        }

        val missing = review(arrayOf("--tags", "${data.resolve("none.json")}"))
        val other = review(arrayOf("--tags", "$trkTags"))
        val invalid = review(arrayOf("--tags", writeFile(data, "bad.json", "{}")))
        val prompt = review(arrayOf("--prompt", "v9"))

        listOf(missing, other, invalid, prompt).forEach {
            assertEquals(ExitCode.ERROR, it.exit, it.stderr)
            assertEquals(1, it.stderrLines().size, it.stderr)
        }
        assertTrue("tag file not found" in missing.stderr, missing.stderr)
        assertTrue("is for set 'trk', not 'syn'" in other.stderr, other.stderr)
        assertTrue("invalid tag file" in invalid.stderr, invalid.stderr)
        assertTrue("no tags review prompt v9" in prompt.stderr, prompt.stderr)
        assertTrue(Files.notExists(out), "nothing is written")
    }

    @Test
    fun `a tag file that lacks a pool card is one line`() {
        val data = synData()
        val tags = TagFiles.read(data.resolve("tags/syn.json"))
        writeFile(data, "tags/syn.json", TagFiles.write(tags.copy(cards = tags.cards.drop(1))))

        val run =
            runCli("tags-review-packet", "syn", "--out", "${temp.resolve("o")}", "--data", "$data")

        assertEquals(ExitCode.ERROR, run.exit)
        assertEquals(
            listOf("spa: the tag file ${data.resolve("tags/syn.json")} has no entry for syn/1"),
            run.stderrLines(),
        )
    }

    @Test
    fun `without --out it is a usage error`() {
        val run = runCli("tags-review-packet", "trk", "--data", Fixtures.data)

        assertEquals(ExitCode.USAGE, run.exit)
    }
}
