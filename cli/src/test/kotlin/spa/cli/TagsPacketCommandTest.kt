package spa.cli

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readBytes
import kotlin.io.path.readText
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Criteria 1, 2 and the `--prompt` half of 5: `spa tags-packet` in its two passes. Pass 1 writes
 * the axes packet; pass 2, given an axes file, checks it first and writes the roles packet.
 */
class TagsPacketCommandTest {
    @TempDir lateinit var temp: Path

    /**
     * `spa tags-packet` with prompt v1 unless [extra] names a prompt (SPA-051 made v2 the default).
     */
    private fun packet(set: String, out: Path, vararg extra: String): CliRun {
        val prompt = if ("--prompt" in extra) emptyArray() else arrayOf("--prompt", "v1")
        return runCli(
            "tags-packet",
            set,
            "--out",
            out.toString(),
            "--data",
            Fixtures.data,
            *extra,
            *prompt,
        )
    }

    private fun cardFiles(dir: Path): List<Path> =
        dir.listDirectoryEntries("cards-*.json")
            .filter { !it.name.endsWith(".response.json") }
            .sortedBy { it.name }

    private fun cardCount(dir: Path): Int =
        cardFiles(dir).sumOf { Json.parseToJsonElement(it.readText()).jsonArray.size }

    private fun names(dir: Path): Set<String> = dir.listDirectoryEntries().map { it.name }.toSet()

    private val synAxes: Path
        get() =
            Path.of(
                requireNotNull(javaClass.getResource("/tags-import/syn/axes.response.json")).toURI()
            )

    @Test
    fun `pass 1 for trk writes the instructions, the axes schema and 7 card files of 336 cards`() {
        val out = temp.resolve("trk")

        val run = packet("trk", out)

        assertEquals(ExitCode.OK, run.exit, run.stderr)
        val expected =
            setOf("instructions.md", "axes.schema.json") +
                (1..7).map { "cards-%02d.json".format(it) }
        assertEquals(expected, names(out))
        assertEquals(336, cardCount(out))
        assertEquals(listOf("trk: 336 cards in 7 card files", "wrote $out"), run.stdoutLines())
    }

    @Test
    fun `pass 1 run twice gives byte-identical files`() {
        val first = temp.resolve("first")
        val second = temp.resolve("second")

        assertEquals(ExitCode.OK, packet("trk", first).exit)
        assertEquals(ExitCode.OK, packet("trk", second).exit)

        assertEquals(names(first), names(second))
        names(first).forEach {
            assertContentEquals(first.resolve(it).readBytes(), second.resolve(it).readBytes(), it)
        }
    }

    @Test
    fun `the card files are byte-identical to grade-packet's`() {
        val tags = temp.resolve("tags")
        val grades = temp.resolve("grades")

        assertEquals(ExitCode.OK, packet("trk", tags).exit)
        val graded =
            runCli("grade-packet", "trk", "--out", grades.toString(), "--data", Fixtures.data)
        assertEquals(ExitCode.OK, graded.exit, graded.stderr)

        assertEquals(cardFiles(grades).map { it.name }, cardFiles(tags).map { it.name })
        cardFiles(grades).forEach {
            assertContentEquals(it.readBytes(), tags.resolve(it.name).readBytes(), it.name)
        }
    }

    @Test
    fun `the other pools have their pinned card and file counts`() {
        val pinned = listOf(Triple("msh", 276, 6), Triple("hob", 188, 4), Triple("fra", 280, 6))

        pinned.forEach { (set, cards, files) ->
            val out = temp.resolve(set)
            val run = packet(set, out)
            assertEquals(ExitCode.OK, run.exit, run.stderr)
            assertEquals(files, cardFiles(out).size, set)
            assertEquals(cards, cardCount(out), set)
        }
    }

    @Test
    fun `pass 1 instructions start with the axes prompt and carry the brief and theme counts`() {
        val out = temp.resolve("syn")

        assertEquals(ExitCode.OK, packet("syn", out).exit)

        val text = out.resolve("instructions.md").readText()
        assertTrue(text.startsWith("You are reading a Magic: The Gathering set"), text.take(80))
        val brief = text.indexOf("## The format: ")
        val themes = text.indexOf("## Themes in this set")
        assertTrue(brief > 0 && themes > brief, "brief then themes")
        assertTrue("`axes.response.json`" in text)
    }

    @Test
    fun `pass 2 writes the axes in the given order and the response schema`() {
        val out = temp.resolve("syn")

        val run = packet("syn", out, "--axes", synAxes.toString())

        assertEquals(ExitCode.OK, run.exit, run.stderr)
        assertEquals(
            setOf(
                "instructions.md",
                "response.schema.json",
                "axes.json",
                "cards-01.json",
            ),
            names(out),
        )
        val text = out.resolve("instructions.md").readText()
        assertTrue(text.startsWith("You are tagging every card of a Magic: The Gathering set"))
        val lines =
            listOf(
                "- `flying`, Flying: A creature with flying.",
                "- `graveyard`, Graveyard: Puts creature cards into a graveyard.",
                "- `lifegain`, Life gain: Gains you life.",
                "- `double-faced`, Double-faced cards: A card with two faces that transforms.",
            )
        val at = lines.map { text.indexOf(it) }
        assertTrue(at.all { it > 0 }, "every axis line is there: $at")
        assertEquals(at.sorted(), at, "in the given order")
        assertTrue(at.last() < text.indexOf("## The format: "), "axes come before the brief")
        assertTrue(text.indexOf("## Themes in this set") > text.indexOf("## The format: "))
        val schema = out.resolve("response.schema.json").readText()
        assertTrue("\"enabler\"" in schema && "\"payoff\"" in schema)
        assertTrue("\"double-faced\"" in schema, "the schema names the packet's axes")
        assertEquals(
            listOf("syn: 20 cards in 1 card file, 4 axes", "wrote $out"),
            run.stdoutLines(),
        )
    }

    @Test
    fun `pass 2 into the pass 1 directory replaces the axes schema and keeps the axes response`() {
        val out = temp.resolve("syn")
        assertEquals(ExitCode.OK, packet("syn", out).exit)
        val response = out.resolve("axes.response.json")
        Files.copy(synAxes, response)

        assertEquals(ExitCode.OK, packet("syn", out, "--axes", response.toString()).exit)

        assertFalse(out.resolve("axes.schema.json").exists())
        assertTrue(response.exists())
        assertTrue(out.resolve("response.schema.json").exists())
    }

    /** Writes [text] as an axes file, runs pass 2, and checks it fails with exactly [problems]. */
    private fun assertAxesRejected(text: String, vararg problems: String) {
        val axes = writeFile(temp, "axes.response.json", text)
        val out = temp.resolve("out")

        val run = packet("syn", out, "--axes", axes)

        assertEquals(ExitCode.ERROR, run.exit)
        assertEquals("", run.stdout)
        assertEquals(problems.map { "spa: $it" }, run.stderrLines())
        assertFalse(out.exists(), "nothing is written")
    }

    private fun axis(id: String, definition: String? = "Something.") =
        if (definition == null) """{ "id": "$id", "name": "N" }"""
        else """{ "id": "$id", "name": "N", "definition": "$definition" }"""

    private fun axes(vararg entries: String) = entries.joinToString(",\n", "[\n", "\n]\n")

    @Test
    fun `fewer than 3 axes`() {
        assertAxesRejected(
            axes(axis("a"), axis("b")),
            "axes.response.json: 2 axes; give 3 to 15",
        )
    }

    @Test
    fun `more than 15 axes`() {
        val sixteen = (1..16).map { axis("axis-$it") }.toTypedArray()

        assertAxesRejected(axes(*sixteen), "axes.response.json: 16 axes; give 3 to 15")
    }

    @Test
    fun `a duplicate id`() {
        assertAxesRejected(
            axes(axis("tokens"), axis("sacrifice"), axis("tokens")),
            "axes.response.json: id 'tokens' is given 2 times",
        )
    }

    @Test
    fun `an id with a capital`() {
        assertAxesRejected(
            axes(axis("tokens"), axis("Sacrifice"), axis("artifacts")),
            "axes.response.json: entry 2: id 'Sacrifice' is not lowercase letters, digits " +
                "and hyphens",
        )
    }

    @Test
    fun `an id with a space`() {
        assertAxesRejected(
            axes(axis("tokens"), axis("plus one"), axis("artifacts")),
            "axes.response.json: entry 2: id 'plus one' is not lowercase letters, digits " +
                "and hyphens",
        )
    }

    @Test
    fun `a missing definition`() {
        assertAxesRejected(
            axes(axis("tokens"), axis("sacrifice"), axis("artifacts", definition = null)),
            "axes.response.json: entry 3: definition must be a non-empty string",
        )
    }

    @Test
    fun `several problems are each named on their own line`() {
        assertAxesRejected(
            axes(axis("Tokens"), axis("b", definition = "")),
            "axes.response.json: entry 1: id 'Tokens' is not lowercase letters, digits and hyphens",
            "axes.response.json: entry 2: definition must be a non-empty string",
            "axes.response.json: 2 axes; give 3 to 15",
        )
    }

    @Test
    fun `an axes file that is not a JSON array`() {
        assertAxesRejected(
            "{}",
            "axes.response.json: not a JSON array of {id, name, definition}",
        )
    }

    @Test
    fun `a missing axes file is one line and exit 1`() {
        val run = packet("syn", temp.resolve("out"), "--axes", temp.resolve("nope.json").toString())

        assertEquals(ExitCode.ERROR, run.exit)
        assertEquals(1, run.stderrLines().size, run.stderr)
        assertTrue(run.stderrLines().single().startsWith("spa: axes file not found: "))
    }

    @Test
    fun `--prompt v9, which has no file, is one line and exit 1 in either pass`() {
        val pass1 = packet("syn", temp.resolve("one"), "--prompt", "v9")
        val pass2 =
            packet("syn", temp.resolve("two"), "--prompt", "v9", "--axes", synAxes.toString())

        listOf(pass1, pass2).forEach { run ->
            assertEquals(ExitCode.ERROR, run.exit)
            assertEquals(1, run.stderrLines().size, run.stderr)
            assertTrue(run.stderrLines().single().startsWith("spa: no tags prompt v9"), run.stderr)
        }
        assertFalse(temp.resolve("one").exists())
        assertFalse(temp.resolve("two").exists())
    }

    @Test
    fun `--prompt v1 still writes the v1 packet, and is no longer the default`() {
        // SPA-051: the default became v2 (TagsV2CommandTest); v1 stays selectable.
        val default = temp.resolve("default")
        val v1 = temp.resolve("v1")

        val run = runCli("tags-packet", "syn", "--out", "$default", "--data", Fixtures.data)
        assertEquals(ExitCode.OK, run.exit)
        assertEquals(ExitCode.OK, packet("syn", v1, "--prompt", "v1").exit)

        val text = v1.resolve("instructions.md").readText()
        assertTrue("Give between 3 and 15 axes." in text)
        assertFalse(v1.resolve("facts.json").exists())
        assertTrue(default.resolve("facts.json").exists())
    }

    @Test
    fun `without --out it is a usage error`() {
        val run = runCli("tags-packet", "syn", "--data", Fixtures.data)

        assertEquals(ExitCode.USAGE, run.exit)
        assertEquals("spa: --out <dir> is required", run.stderrLines().first())
    }
}
