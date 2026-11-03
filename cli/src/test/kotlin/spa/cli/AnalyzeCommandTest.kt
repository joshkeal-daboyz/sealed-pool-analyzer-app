package spa.cli

import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import spa.core.brief.BriefLoader
import spa.core.cards.CardIndex
import spa.core.intake.PoolParser
import spa.core.sim.FixtureManifest

class AnalyzeCommandTest {
    @TempDir lateinit var temp: Path

    private fun analyze(vararg args: String): CliRun =
        runCli("analyze", *args, "--data", Fixtures.data)

    private fun assertError(run: CliRun, message: String) {
        assertEquals(ExitCode.ERROR, run.exit)
        assertEquals("", run.stdout)
        assertEquals(listOf("spa: $message"), run.stderrLines())
    }

    @Test
    fun `prints the output contract with the set's baseline`() {
        val file = Fixtures.pool("trk-manabox-01.csv")
        val run = analyze(file, "--set", "trk")

        assertEquals(ExitCode.OK, run.exit)
        assertEquals("", run.stderr)
        val json = run.json()
        assertEquals(listOf("tool", "set", "pool", "metrics", "baseline"), json.keys.toList())
        val tool = json.getValue("tool").jsonObject
        assertEquals("spa", tool.getValue("name").jsonPrimitive.content)
        assertEquals(Output.version, tool.getValue("version").jsonPrimitive.content)
        assertEquals("trk", json.getValue("set").jsonPrimitive.content)
        val pool = json.getValue("pool").jsonObject
        assertEquals(
            listOf("file", "format", "entries", "skippedLines", "unresolved", "kitWarnings"),
            pool.keys.toList(),
        )
        assertEquals(file, pool.getValue("file").jsonPrimitive.content)
        assertEquals("MANABOX", pool.getValue("format").jsonPrimitive.content)
        assertTrue(pool.getValue("entries").jsonPrimitive.int in 1..85)
        assertEquals(0, pool.getValue("unresolved").jsonArray.size)
        val metrics = json.getValue("metrics").jsonObject
        assertEquals(85, metrics.getValue("totalCards").jsonPrimitive.int)
        assertEquals(10, metrics.getValue("pairs").jsonObject.size)
        val baseline = json.getValue("baseline").jsonObject
        assertEquals("trk", baseline.getValue("setCode").jsonPrimitive.content)
        assertTrue("/totalCards" in baseline.getValue("stats").jsonObject)
    }

    @Test
    fun `entries counts resolved pool entries, not cards`() {
        val file = Fixtures.pool("trk-text-01.txt")
        val pool = analyze(file, "--set", "trk").json().getValue("pool").jsonObject

        val index = CardIndex.load(Fixtures.root.resolve("scryfall"), "trk")
        val parsed = PoolParser.parse(Path.of(file).toFile().readText(), index)
        assertEquals(parsed.pool.entries.size, pool.getValue("entries").jsonPrimitive.int)
        assertNotEquals(parsed.pool.totalCount, parsed.pool.entries.size)
    }

    @Test
    fun `two runs on the same input print the same bytes`() {
        val args = arrayOf(Fixtures.pool("trk-collector-01.txt"), "--set", "trk")

        assertEquals(analyze(*args).stdout, analyze(*args).stdout)
    }

    @Test
    fun `--no-baseline prints a null baseline and says so on stderr`() {
        val run = analyze(Fixtures.pool("syn-text-01.txt"), "--set", "syn", "--no-baseline")

        assertEquals(ExitCode.OK, run.exit)
        assertEquals(JsonNull, run.json()["baseline"])
        assertEquals(listOf("baseline: none (--no-baseline)"), run.stderrLines())
    }

    @Test
    fun `a set without a baseline file prints a null baseline and says so on stderr`() {
        val data = Fixtures.synDataDir(temp)
        val run =
            runCli("analyze", Fixtures.pool("syn-text-01.txt"), "--set", "syn", "--data", "$data")

        assertEquals(ExitCode.OK, run.exit)
        assertEquals(JsonNull, run.json()["baseline"])
        assertEquals(1, run.stderrLines().size)
        assertTrue(run.stderr.startsWith("baseline: none for set 'syn'"))
    }

    @Test
    fun `--format overrides detection`() {
        val text = Fixtures.pool("trk-text-01.txt")

        val detected = analyze(text, "--set", "trk").json()["pool"]!!.jsonObject
        assertEquals("TEXT", detected.getValue("format").jsonPrimitive.content)
        val forced = analyze(text, "--set", "trk", "--format", "COLLECTOR")
        assertEquals(ExitCode.OK, forced.exit)
        val pool = forced.json().getValue("pool").jsonObject
        assertEquals("COLLECTOR", pool.getValue("format").jsonPrimitive.content)
        assertEquals(0, pool.getValue("entries").jsonPrimitive.int)
    }

    @Test
    fun `--brief replaces the set's brief for kit warnings`() {
        // The same kit with five boosters instead of six holds 5 x 14 + 1 = 71 cards.
        val trk = Path.of(Fixtures.brief("trk")).toFile().readText()
        val fiveBoosters = trk.replace("\"boosters\": 6", "\"boosters\": 5")
        assertNotEquals(trk, fiveBoosters)
        val brief = writeFile(temp, "five.json", fiveBoosters)
        val pool = Fixtures.pool("trk-manabox-01.csv")

        val warnings = { run: CliRun ->
            assertEquals(ExitCode.OK, run.exit)
            run.json().getValue("pool").jsonObject.getValue("kitWarnings").jsonArray.map {
                it.jsonObject
            }
        }
        val cardCount = { run: CliRun ->
            warnings(run).single { it.getValue("code").jsonPrimitive.content == "card-count" }
        }
        val ownBrief = cardCount(analyze(pool, "--set", "trk"))
        assertEquals(85, ownBrief.getValue("expected").jsonObject["min"]!!.jsonPrimitive.int)
        val given = cardCount(analyze(pool, "--set", "trk", "--brief", brief))
        assertEquals(71, given.getValue("expected").jsonObject["min"]!!.jsonPrimitive.int)
    }

    @Test
    fun `kit warnings write expected as a min and max range`() {
        val run = analyze(Fixtures.pool("trk-text-misspelled-01.txt"), "--set", "trk")

        assertEquals(ExitCode.OK, run.exit)
        val warning =
            run.json()
                .getValue("pool")
                .jsonObject
                .getValue("kitWarnings")
                .jsonArray
                .map { it.jsonObject }
                .single { it.getValue("code").jsonPrimitive.content == "card-count" }
        assertEquals(listOf("code", "expected", "actual", "message"), warning.keys.toList())
        val expected = warning.getValue("expected").jsonObject
        assertEquals(listOf("min", "max"), expected.keys.toList())
        assertEquals(85, expected.getValue("min").jsonPrimitive.int)
        assertEquals(85, expected.getValue("max").jsonPrimitive.int)
        // The CLI reports the kit validator's warning as it is; it does not recount.
        val file = Fixtures.pool("trk-text-misspelled-01.txt")
        val index = CardIndex.load(Fixtures.root.resolve("scryfall"), "trk")
        val kit = BriefLoader.load(Path.of(Fixtures.brief("trk"))).kitTemplate
        val core =
            PoolParser.parse(Path.of(file).toFile().readText(), index, kitTemplate = kit)
                .warnings
                .single { it.code.code == "card-count" }
        assertEquals(core.actual, warning.getValue("actual").jsonPrimitive.int)
        assertEquals(core.message, warning.getValue("message").jsonPrimitive.content)
    }

    @Test
    fun `the misspelled fixture lists exactly the manifest's unresolved lines`() {
        val entry =
            FixtureManifest.load(Fixtures.root.resolve("pools")).fixtures.single {
                it.file == "trk-text-misspelled-01.txt"
            }
        val run = analyze(Fixtures.pool(entry.file), "--set", entry.set)

        assertEquals(ExitCode.OK, run.exit)
        val json = run.json()
        val unresolved = json.getValue("pool").jsonObject.getValue("unresolved").jsonArray
        assertEquals(
            entry.expectedUnresolved.map { it.line to it.reason },
            unresolved.map {
                val line = it.jsonObject
                line.getValue("line").jsonPrimitive.int to
                    line.getValue("reason").jsonPrimitive.content
            },
        )
        unresolved.forEach {
            assertFalse(it.jsonObject.getValue("text").jsonPrimitive.content.isBlank())
        }
        val metrics = json.getValue("metrics").jsonObject
        assertEquals(entry.expectedCards, metrics.getValue("totalCards").jsonPrimitive.int)
    }

    @Test
    fun `a missing --set exits 1`() {
        assertError(analyze(Fixtures.pool("trk-text-01.txt")), "--set <code> is required")
    }

    @Test
    fun `a missing pool file exits 1 with one line`() {
        val missing = temp.resolve("nope.csv")

        assertError(analyze("$missing", "--set", "trk"), "pool file not found: $missing")
    }

    @Test
    fun `a set without a cache exits 1 with one line`() {
        val run = analyze(Fixtures.pool("trk-text-01.txt"), "--set", "zzz")

        assertEquals(ExitCode.ERROR, run.exit)
        assertEquals(1, run.stderrLines().size)
        assertTrue(run.stderr.startsWith("spa: no Scryfall cache for set 'zzz'"))
    }

    @Test
    fun `a missing brief exits 1 with one line`() {
        val missing = temp.resolve("brief.json")
        val run = analyze(Fixtures.pool("trk-text-01.txt"), "--set", "trk", "--brief", "$missing")

        assertError(run, "brief not found: $missing")
    }

    @Test
    fun `an invalid brief exits 1 with one line`() {
        val brief = writeFile(temp, "bad.json", "{\"setCode\": 7}")
        val run = analyze(Fixtures.pool("trk-text-01.txt"), "--set", "trk", "--brief", brief)

        assertEquals(ExitCode.ERROR, run.exit)
        assertEquals(1, run.stderrLines().size)
        assertTrue(run.stderr.startsWith("spa: brief $brief is invalid ("))
    }

    @Test
    fun `an unreadable pool file exits 1 with one line`() {
        val pool = writeFile(temp, "pool.csv", "Name,Scryfall ID\nX,abc\n")
        val run = analyze(pool, "--set", "trk")

        assertEquals(ExitCode.ERROR, run.exit)
        assertEquals(1, run.stderrLines().size)
        assertTrue(run.stderr.startsWith("spa: cannot parse pool file $pool: "))
    }

    @Test
    fun `an unreadable baseline exits 1 with one line`() {
        val data = Fixtures.synDataDir(temp)
        writeFile(data, "baselines/syn.json", "not json")
        val run =
            runCli("analyze", Fixtures.pool("syn-text-01.txt"), "--set", "syn", "--data", "$data")

        assertEquals(ExitCode.ERROR, run.exit)
        assertEquals(1, run.stderrLines().size)
        assertTrue(run.stderr.startsWith("spa: cannot read baseline "))
    }

    @Test
    fun `bad flags exit 2`() {
        val pool = Fixtures.pool("trk-text-01.txt")

        assertEquals(ExitCode.USAGE, analyze(pool, "--set", "trk", "--format", "csv").exit)
        assertEquals(ExitCode.USAGE, analyze(pool, "--set", "trk", "--colour").exit)
        assertEquals(ExitCode.USAGE, analyze("--set", "trk").exit)
        assertEquals(ExitCode.USAGE, analyze(pool, pool, "--set", "trk").exit)
    }
}
