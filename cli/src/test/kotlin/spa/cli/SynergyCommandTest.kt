package spa.cli

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Path
import kotlin.math.abs
import kotlin.math.max
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import spa.agent.synergy.CardSynergy
import spa.agent.synergy.PoolSynergy
import spa.core.metrics.ColorPair

/**
 * SPA-048 criteria 3, 4 and 6: `spa synergy` on trk-manabox-01 in WU with the v1 trk tags, frozen
 * since SPA-053 as `fixtures/tags/v1/trk.json`, and weight 0.5, against an independent script's
 * values (scratchpad `tools/synergy.js`, 2026-11-05) to 3 decimals, every printed line, and the
 * errors.
 */
class SynergyCommandTest {
    private val pool = Fixtures.pool("trk-manabox-01.csv")

    private val v1 = listOf("--tags", Fixtures.v1Tags("trk"))

    private val wu: PoolSynergy by lazy {
        val err = ByteArrayOutputStream()
        SynergyCommand.compute(
            listOf(pool, "--set", "trk", "--pair", "WU") + v1 + listOf("--data", Fixtures.data),
            PrintStream(err, true, Charsets.UTF_8),
        )
    }

    private val run: CliRun by lazy {
        runCli(
            "synergy",
            pool,
            "--set",
            "trk",
            "--pair",
            "WU",
            *v1.toTypedArray(),
            "--data",
            Fixtures.data,
        )
    }

    private fun card(number: String): CardSynergy =
        wu.cards.single { it.printing.setCode == "trk" && it.printing.collectorNumber == number }

    private fun assertNear(expected: Double, actual: Double, what: String) =
        assertTrue(abs(expected - actual) < 0.0005, "$what: expected $expected, got $actual")

    @Test
    fun `WU has 24 distinct candidates, 33 copies and 117 fitting set cards`() {
        assertEquals(ColorPair.WU, wu.pair)
        assertEquals(24, wu.cards.size)
        assertEquals(33, wu.copies)
        assertEquals(33, wu.cards.sumOf { it.quantity })
        assertEquals(117, wu.fitting)
    }

    @Test
    fun `Enterprise NX-01 matches the script`() {
        val card = card("58")
        assertEquals("Enterprise NX-01", card.printing.card.name)
        assertEquals(26, card.syn)
        assertNear(20.690, card.expected, "e")
        assertNear(0.128, card.value, "part")
    }

    @Test
    fun `Humpback Whales matches the script`() {
        val card = card("65")
        assertEquals("Humpback Whales", card.printing.card.name)
        assertEquals(0, card.syn)
        assertNear(3.034, card.expected, "e")
        assertNear(-0.500, card.value, "part")
    }

    @Test
    fun `Anthropological Mission matches the script`() {
        val card = card("46")
        assertEquals("Anthropological Mission", card.printing.card.name)
        assertEquals(79, card.syn)
        assertNear(62.345, card.expected, "e")
        assertNear(0.134, card.value, "part")
    }

    @Test
    fun `every printed line keeps the formula, and every card has a reason line`() {
        assertEquals(ExitCode.OK, run.exit)
        val lines = run.stdout.lines().dropLastWhile { it.isEmpty() }
        val weight = 0.5
        val pattern = Regex("""^([+-]\d+\.\d{3}) {2}syn (\d+) {2}e (\d+\.\d{3}) {2}(\d+) (.+)$""")

        assertEquals(2 * 24, lines.size)
        val parts =
            lines.chunked(2).map { (line, reason) ->
                val match = pattern.matchEntire(line)
                assertTrue(match != null, "card line: $line")
                val (part, syn, e) = match.destructured
                val quantity = match.groupValues[4]
                val name = match.groupValues[5]
                val value = part.toDouble()
                val expected =
                    weight *
                        ((syn.toInt() - e.toDouble()) / max(e.toDouble(), 1.0)).coerceIn(-1.0, 1.0)
                assertTrue(abs(value) <= weight, "within the weight: $line")
                assertTrue(abs(value - expected) < 0.0015, "formula: $line gives $expected")
                assertTrue(quantity.toInt() >= 1 && name.isNotBlank(), line)
                assertTrue(reason.startsWith("  "), "reason under $line: $reason")
                if (syn.toInt() > 0) {
                    assertTrue(reason.startsWith("  partners: "), "partners for $line: $reason")
                } else {
                    assertEquals("  no partners in the pool", reason)
                }
                value
            }
        assertEquals(parts.sortedDescending(), parts, "highest part first")
    }

    @Test
    fun `the printed line for Enterprise NX-01`() {
        val line = run.stdoutLines().single { it.endsWith(" Enterprise NX-01") }
        assertEquals("+0.128  syn 26  e 20.690  1 Enterprise NX-01", line)
    }

    @Test
    fun `--weights reads another weights file, and weight 0 turns the part off`(
        @TempDir dir: Path
    ) {
        val off = writeFile(dir, "off.json", """{ "weight": 0 }""")
        val run =
            runCli(
                "synergy",
                pool,
                "--set",
                "trk",
                "--pair",
                "UW",
                "--weights",
                off,
                "--data",
                Fixtures.data,
            )

        assertEquals(ExitCode.OK, run.exit)
        val cards = run.stdoutLines().filter { !it.startsWith("  ") }
        assertEquals(24, cards.size)
        assertTrue(cards.all { it.startsWith("+0.000  ") }, cards.toString())
    }

    @Test
    fun `the default weights file holds weight one half`() {
        assertEquals(
            """{ "weight": 0.5 }""",
            java.nio.file.Files.readString(Fixtures.root.resolve("weights/tag-synergy.json"))
                .trim(),
        )
    }

    private fun assertOneLine(run: CliRun, text: String) {
        assertEquals(ExitCode.ERROR, run.exit, run.stderr)
        assertEquals("", run.stdout)
        assertEquals(1, run.stderrLines().size, run.stderr)
        assertTrue(text in run.stderr, run.stderr)
    }

    /** `spa synergy` with the pool, `--set trk`, `--pair WU` and the fixtures unless given. */
    private fun synergy(
        file: String = pool,
        set: String = "trk",
        pair: String? = "WU",
        data: String = Fixtures.data,
        extra: List<String> = emptyList(),
    ): CliRun {
        val pairArgs = if (pair == null) emptyList() else listOf("--pair", pair)
        val args = listOf("synergy", file, "--set", set) + pairArgs + extra + listOf("--data", data)
        return runCli(*args.toTypedArray())
    }

    @Test
    fun `errors are one line and exit 1`(@TempDir dir: Path) {
        assertOneLine(
            synergy(data = Fixtures.synDataDir(dir).toString()),
            "no Scryfall cache for set 'trk'",
        )
        assertOneLine(synergy(set = "zzz"), "zzz")
        assertOneLine(synergy(file = dir.resolve("none.csv").toString()), "pool file not found")
        assertOneLine(synergy(pair = "WW"), "unknown pair 'WW'")
        assertOneLine(synergy(pair = null), "--pair")
        val bad = writeFile(dir, "bad.json", """{ "weight": -1 }""")
        assertOneLine(synergy(extra = listOf("--weights", bad)), "invalid weights file")
        val none = dir.resolve("no.json").toString()
        assertOneLine(synergy(extra = listOf("--weights", none)), "weights file not found")
    }

    @Test
    fun `a missing tag file is one line and exit 1`(@TempDir dir: Path) {
        val data = dir.resolve("data")
        val scryfall = Fixtures.root.resolve("scryfall")
        listOf("trk", "sds").forEach { set ->
            val target = data.resolve("scryfall/$set")
            java.nio.file.Files.createDirectories(target)
            java.nio.file.Files.list(scryfall.resolve(set)).use { files ->
                files.forEach {
                    java.nio.file.Files.copy(it, target.resolve(it.fileName.toString()))
                }
            }
        }
        assertOneLine(
            runCli("synergy", pool, "--set", "trk", "--pair", "WU", "--data", data.toString()),
            "no tag file for set 'trk'",
        )
    }

    @Test
    fun `help lists the command`() {
        assertTrue("synergy <pool-file> --set <code> --pair <XY>" in Main.USAGE)
    }

    @Test
    fun `--tags reads that file instead of the data directory's tag file`(@TempDir dir: Path) {
        val tags = dir.resolve("elsewhere.json")
        java.nio.file.Files.copy(Path.of(Fixtures.v1Tags("trk")), tags)
        val run = synergy(extra = listOf("--tags", "$tags"))

        assertEquals(ExitCode.OK, run.exit, run.stderr)
        assertEquals(
            "+0.128  syn 26  e 20.690  1 Enterprise NX-01",
            run.stdoutLines().single { it.endsWith(" Enterprise NX-01") },
        )
    }

    @Test
    fun `a missing --tags file is one line and exit 1`(@TempDir dir: Path) {
        val none = dir.resolve("none.json")
        val run = synergy(extra = listOf("--tags", "$none"))

        assertOneLine(run, "tag file not found: $none")
    }

    @Test
    fun `help names --tags`() {
        assertTrue(
            "synergy <pool-file> --set <code> --pair <XY> [--weights <file>] [--tags <file>]" in
                Main.USAGE
        )
    }
}
