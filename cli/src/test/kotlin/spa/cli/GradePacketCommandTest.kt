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
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import spa.core.cards.CardIndex

/**
 * Criterion 1: `spa grade-packet` writes the packet for the whole sealed pool, the same each run.
 */
class GradePacketCommandTest {
    @TempDir lateinit var temp: Path

    /**
     * The sealed pool's ids worked out here from [CardIndex], not by the code under test: per
     * distinct non-basic card, its printing in the main set (else the first companion) with the
     * lowest collector number, all in set order then collector order.
     */
    private fun expectedIds(set: String): List<String> {
        val index = CardIndex.load(Fixtures.root.resolve("scryfall"), set)
        fun number(n: String) = n.takeWhile { it.isDigit() }.toInt() to n
        val sorted =
            index.printings
                .filter { !it.card.isBasicLand }
                .sortedWith(
                    compareBy(
                        { index.setCodes.indexOf(it.setCode) },
                        { number(it.collectorNumber).first },
                        { it.collectorNumber },
                    )
                )
        return sorted.distinctBy { it.card.oracleId }.map { "${it.setCode}/${it.collectorNumber}" }
    }

    private fun packet(set: String, out: Path): CliRun =
        runCli("grade-packet", set, "--out", out.toString(), "--data", Fixtures.data)

    private fun cardFiles(dir: Path): List<Path> =
        dir.listDirectoryEntries("cards-*.json")
            .filter { !it.name.endsWith(".response.json") }
            .sortedBy { it.name }

    private fun ids(file: Path): List<String> =
        Json.parseToJsonElement(file.readText()).jsonArray.map {
            it.jsonObject["id"]!!.jsonPrimitive.content
        }

    @Test
    fun `syn's packet holds the instructions, the schema, and every pool card once`() {
        val out = temp.resolve("syn")

        val run = packet("syn", out)

        assertEquals(ExitCode.OK, run.exit, run.stderr)
        assertEquals("", run.stderr)
        assertEquals(
            listOf("instructions.md", "response.schema.json", "cards-01.json"),
            out.listDirectoryEntries()
                .map { it.name }
                .sortedBy { if (it.startsWith("c")) 1 else 0 },
        )
        val expected = expectedIds("syn")
        assertEquals(20, expected.size)
        assertEquals(expected, cardFiles(out).flatMap(::ids))
        assertEquals(
            listOf("syn: 20 cards in 1 card file", "wrote $out"),
            run.stdoutLines(),
        )
    }

    @Test
    fun `trk's packet has 50 cards per file except the last, every card once`() {
        val out = temp.resolve("trk")

        val run = packet("trk", out)

        assertEquals(ExitCode.OK, run.exit, run.stderr)
        val expected = expectedIds("trk")
        val files = cardFiles(out)
        val sizes = files.map { ids(it).size }
        assertTrue(sizes.dropLast(1).all { it == 50 }, sizes.toString())
        assertTrue(sizes.last() in 1..50, sizes.toString())
        assertEquals(expected, files.flatMap(::ids))
        assertEquals(
            (1..files.size).map { "cards-%02d.json".format(it) },
            files.map { it.name },
        )
        assertTrue(expected.any { it.startsWith("sds/") }, "the companion set is in the pool")
    }

    @Test
    fun `running it twice gives byte-identical files`() {
        val first = temp.resolve("first")
        val second = temp.resolve("second")

        assertEquals(ExitCode.OK, packet("syn", first).exit)
        val before = first.listDirectoryEntries().associate { it.name to it.readBytes() }
        assertEquals(ExitCode.OK, packet("syn", second).exit)
        assertEquals(ExitCode.OK, packet("syn", first).exit)

        val names = before.keys.sorted()
        assertEquals(names, second.listDirectoryEntries().map { it.name }.sorted())
        assertEquals(names, first.listDirectoryEntries().map { it.name }.sorted())
        names.forEach {
            assertContentEquals(before[it], second.resolve(it).readBytes(), it)
            assertContentEquals(before[it], first.resolve(it).readBytes(), it)
        }
    }

    @Test
    fun `without --out it is a usage error`() {
        val run = runCli("grade-packet", "syn", "--data", Fixtures.data)

        assertEquals(ExitCode.USAGE, run.exit)
        assertEquals("spa: --out <dir> is required", run.stderrLines().first())
    }

    @Test
    fun `without a set code it is a usage error`() {
        val run = runCli("grade-packet", "--out", temp.toString(), "--data", Fixtures.data)

        assertEquals(ExitCode.USAGE, run.exit)
        assertEquals("spa: missing set code", run.stderrLines().first())
    }

    @Test
    fun `a set with no Scryfall cache prints one line and exits 1`() {
        val run =
            runCli(
                "grade-packet",
                "syn",
                "--out",
                temp.resolve("o").toString(),
                "--data",
                temp.toString(),
            )

        assertEquals(ExitCode.ERROR, run.exit)
        assertEquals(1, run.stderrLines().size, run.stderr)
        assertTrue(run.stderrLines()[0].startsWith("spa: no Scryfall cache for set 'syn'"))
        assertTrue(Files.notExists(temp.resolve("o")))
    }

    @Test
    fun `a set with no brief prints one line naming it and exits 1`() {
        val data = Fixtures.synDataDir(temp.resolve("data"))
        Files.delete(data.resolve("briefs/syn.json"))

        val run =
            runCli(
                "grade-packet",
                "SYN",
                "--out",
                temp.resolve("o").toString(),
                "--data",
                data.toString(),
            )

        assertEquals(ExitCode.ERROR, run.exit)
        assertEquals(
            listOf("spa: brief not found: ${data.resolve("briefs").resolve("syn.json")}"),
            run.stderrLines(),
        )
    }

    @Test
    fun `an invalid brief prints one line and exits 1`() {
        val data = Fixtures.synDataDir(temp.resolve("data"))
        Files.writeString(data.resolve("briefs/syn.json"), "{}")

        val run =
            runCli(
                "grade-packet",
                "syn",
                "--out",
                temp.resolve("o").toString(),
                "--data",
                data.toString(),
            )

        assertEquals(ExitCode.ERROR, run.exit)
        assertEquals(1, run.stderrLines().size, run.stderr)
        assertTrue(run.stderrLines()[0].startsWith("spa: brief "), run.stderr)
    }
}
