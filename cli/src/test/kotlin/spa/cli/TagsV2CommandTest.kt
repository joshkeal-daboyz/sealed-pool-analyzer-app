package spa.cli

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.copyTo
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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import spa.agent.tags.FactSupply
import spa.agent.tags.TagFiles
import spa.agent.tags.TagPrompts

/**
 * SPA-051 criterion 3: `spa tags-packet` with tags prompt v2, the default, on trk and syn, and `spa
 * tags-import` of a v2 packet.
 */
class TagsV2CommandTest {
    @TempDir lateinit var temp: Path

    private fun packet(set: String, out: Path, vararg extra: String): CliRun =
        runCli("tags-packet", set, "--out", out.toString(), "--data", Fixtures.data, *extra)

    private fun cardFiles(dir: Path): List<Path> =
        dir.listDirectoryEntries("cards-*.json")
            .filter { !it.name.endsWith(".response.json") }
            .sortedBy { it.name }

    private fun names(dir: Path): Set<String> = dir.listDirectoryEntries().map { it.name }.toSet()

    private fun resource(name: String): Path =
        Path.of(requireNotNull(javaClass.getResource("/tags-import/$name")).toURI())

    private val trk: Path by lazy {
        temp.resolve("trk").also { assertEquals(ExitCode.OK, packet("trk", it).exit) }
    }

    @Test
    fun `the default trk pass 1 packet holds facts json beside the v1 files`() {
        val expected =
            setOf("instructions.md", "axes.schema.json", "facts.json") +
                (1..7).map { "cards-%02d.json".format(it) }

        assertEquals(expected, names(trk))
    }

    @Test
    fun `its card files are byte-identical to the grade packet's`() {
        val grades = temp.resolve("grades")
        val graded =
            runCli("grade-packet", "trk", "--out", grades.toString(), "--data", Fixtures.data)
        assertEquals(ExitCode.OK, graded.exit, graded.stderr)

        assertEquals(cardFiles(grades).map { it.name }, cardFiles(trk).map { it.name })
        cardFiles(grades).forEach {
            assertContentEquals(it.readBytes(), trk.resolve(it.name).readBytes(), it.name)
        }
    }

    @Test
    fun `its instructions start with the v2 prompt text exactly`() {
        val prompt = TagPrompts.load(TagPrompts.Pass.AXES, "v2")!!
        val text = trk.resolve("instructions.md").readText()

        assertTrue(text.startsWith(prompt.trimEnd() + "\n\n## The format: "), text.take(200))
        assertTrue("Give between 3 and 20 axes." in text)
        assertTrue(
            "per card in `facts.json`: what it is (a card type, legendary, its creature types)" in
                text
        )
    }

    @Test
    fun `its card facts section counts each of trk's facts`() {
        val text = trk.resolve("instructions.md").readText()
        val section = text.substringAfter("## Card facts\n\n").substringBefore("\n\n## ")
        val lines = section.lines().filter { it.startsWith("- `") }

        assertEquals(172, lines.size)
        assertEquals("- `creature`: 189", lines.first())
        assertTrue("- `land`: 36" in lines)
        assertTrue("- `kw:flying`: 10" in lines)
        assertTrue("- `type:human`: 68" in lines)
        assertTrue(text.indexOf("## Card facts") < text.indexOf("## The files in this folder"))
    }

    @Test
    fun `its facts json gives every card's facts`() {
        val json = Json.parseToJsonElement(trk.resolve("facts.json").readText()).jsonObject

        assertEquals(336, json.size)
        assertEquals(
            listOf("cheap", "creature", "kw:deathtouch", "kw:reach", "type:beast"),
            json.getValue("trk/207").jsonArray.map { it.jsonPrimitive.content },
        )
    }

    @Test
    fun `the v2 packets are blind`() {
        val roles = temp.resolve("roles")
        val run = packet("syn", roles, "--axes", resource("syn-v2/axes.response.json").toString())
        assertEquals(ExitCode.OK, run.exit, run.stderr)

        listOf(trk, roles).forEach { dir ->
            assertEquals(emptyList(), BlindCheck.forbiddenTextHits(dir), "$dir")
            assertEquals(emptyList(), BlindCheck.fieldNameHits(dir), "$dir")
        }
    }

    @Test
    fun `--prompt v2 is the default`() {
        val v2 = temp.resolve("v2")

        assertEquals(ExitCode.OK, packet("trk", v2, "--prompt", "v2").exit)

        names(trk).forEach {
            assertContentEquals(trk.resolve(it).readBytes(), v2.resolve(it).readBytes(), it)
        }
    }

    @Test
    fun `pass 2 shows each axis's suppliedBy and writes facts json`() {
        val out = temp.resolve("syn")

        val run = packet("syn", out, "--axes", resource("syn-v2/axes.response.json").toString())

        assertEquals(ExitCode.OK, run.exit, run.stderr)
        assertEquals(
            setOf("instructions.md", "response.schema.json", "axes.json", "facts.json") +
                "cards-01.json",
            names(out),
        )
        val text = out.resolve("instructions.md").readText()
        val prompt = TagPrompts.load(TagPrompts.Pass.ROLES, "v2")!!
        assertTrue(text.startsWith(prompt.trimEnd() + "\n\n## The set's synergy axes"))
        assertTrue(
            "- `flying`, Flying: A creature with flying. Supplied by: `kw:flying` 3.\n" in text
        )
        assertTrue("- `lifegain`, Life gain: Gains you life. Supplied by: no fact.\n" in text)
        assertTrue("\"suppliedBy\"" in out.resolve("axes.json").readText())
    }

    private fun axis(id: String, supply: String = "[]") =
        """{ "id": "$id", "name": "N", "definition": "D.", "suppliedBy": $supply }"""

    private fun plain(id: String) = """{ "id": "$id", "name": "N", "definition": "D." }"""

    private fun axes(n: Int, entry: (String) -> String) =
        (1..n).joinToString(",\n", "[\n", "\n]\n") { entry("axis-$it") }

    @Test
    fun `16 to 20 axes are accepted under v2 and rejected under v1`() {
        (16..20).forEach { n ->
            val v2 = writeFile(temp, "v2-$n.json", axes(n) { axis(it) })
            val run = packet("syn", temp.resolve("v2-$n"), "--axes", v2)
            assertEquals(ExitCode.OK, run.exit, run.stderr)
            assertTrue("$n axes" in run.stdout)

            val v1 = writeFile(temp, "v1-$n.json", axes(n) { plain(it) })
            val rejected = packet("syn", temp.resolve("v1-$n"), "--axes", v1, "--prompt", "v1")
            assertEquals(ExitCode.ERROR, rejected.exit)
            assertEquals(listOf("spa: v1-$n.json: $n axes; give 3 to 15"), rejected.stderrLines())
        }
        val many = writeFile(temp, "v2-21.json", axes(21) { axis(it) })
        assertEquals(
            listOf("spa: v2-21.json: 21 axes; give 3 to 20"),
            packet("syn", temp.resolve("v2-21"), "--axes", many).stderrLines(),
        )
    }

    @Test
    fun `a bad suppliedBy is one line per problem, exit 1, and nothing written`() {
        val text =
            listOf(
                    axis("a", """[{ "fact": "kw:banding", "strength": 1 }]"""),
                    axis("b", """[{ "fact": "creature", "strength": 0 }]"""),
                    axis("c", """[{ "fact": "land", "strength": 4 }]"""),
                    axis(
                        "d",
                        """[{ "fact": "creature", "strength": 1 }, { "fact": "creature", "strength": 2 }]""",
                    ),
                )
                .joinToString(",\n", "[\n", "\n]\n")
        val file = writeFile(temp, "bad.json", text)
        val out = temp.resolve("bad")

        val run = packet("syn", out, "--axes", file)

        assertEquals(ExitCode.ERROR, run.exit)
        assertEquals("", run.stdout)
        assertEquals(
            listOf(
                "spa: bad.json: axis 'a' is supplied by 'kw:banding', which no card of the set has",
                "spa: bad.json: axis 'b' strength 0 for 'creature' is not 1, 2 or 3",
                "spa: bad.json: axis 'c' strength 4 for 'land' is not 1, 2 or 3",
                "spa: bad.json: axis 'd' gives 'creature' 2 times",
            ),
            run.stderrLines(),
        )
        assertFalse(out.exists())
    }

    @Test
    fun `a v2 axes file needs suppliedBy on every axis`() {
        val file = writeFile(temp, "plain.json", axes(3) { plain(it) })

        val run = packet("syn", temp.resolve("plain"), "--axes", file)

        assertEquals(ExitCode.ERROR, run.exit)
        assertEquals(
            (1..3).map { "spa: plain.json: entry $it: suppliedBy must be an array" },
            run.stderrLines(),
        )
    }

    private fun importV2(packet: Path, data: Path, provenance: String) =
        runCli(
            "tags-import",
            "syn",
            "--packet",
            packet.toString(),
            "--provenance",
            provenance,
            "--data",
            data.toString(),
        )

    @Test
    fun `tags-import of a v2 packet stores each axis's suppliedBy and the provenance`() {
        val data = Fixtures.synDataDir(temp.resolve("data"))
        val packet = temp.resolve("packet")
        val axes = resource("syn-v2/axes.response.json").toString()
        val run =
            runCli(
                "tags-packet",
                "syn",
                "--axes",
                axes,
                "--out",
                packet.toString(),
                "--data",
                data.toString(),
            )
        assertEquals(ExitCode.OK, run.exit, run.stderr)
        resource("syn/cards-01.response.json").copyTo(packet.resolve("cards-01.response.json"))
        val provenance = "claude-fable-5-1 via Claude Code, tags prompt v2, 2026-11-05"

        val imported = importV2(packet, data, provenance)

        assertEquals(ExitCode.OK, imported.exit, imported.stderr)
        val file = TagFiles.read(data.resolve("tags/syn.json"))
        assertEquals(provenance, file.provenance)
        assertEquals(
            listOf(
                listOf(FactSupply("kw:flying", 3)),
                listOf(FactSupply("creature", 1)),
                emptyList(),
                listOf(FactSupply("kw:transform", 2)),
            ),
            file.axes.map { it.suppliedBy },
        )
    }

    @Test
    fun `tags-import rejects an axes json whose supply breaks the checks`() {
        val data = Fixtures.synDataDir(temp.resolve("data"))
        val packet = temp.resolve("packet")
        val axes = resource("syn-v2/axes.response.json").toString()
        runCli("tags-packet", "syn", "--axes", axes, "--out", "$packet", "--data", "$data")
        resource("syn/cards-01.response.json").copyTo(packet.resolve("cards-01.response.json"))
        val axesJson = packet.resolve("axes.json")
        Files.writeString(
            axesJson,
            axesJson
                .readText()
                .replace("\"kw:transform\"", "\"kw:banding\"")
                .replace("\"strength\": 3", "\"strength\": 4"),
        )

        val run = importV2(packet, data, "p v2")

        assertEquals(ExitCode.ERROR, run.exit)
        assertEquals(
            listOf(
                "spa: axes.json: axis 'double-faced' is supplied by 'kw:banding', which no card " +
                    "of the set has",
                "spa: axes.json: axis 'flying' strength 4 for 'kw:flying' is not 1, 2 or 3",
            ),
            run.stderrLines(),
        )
        assertFalse(data.resolve("tags").exists())
    }
}
