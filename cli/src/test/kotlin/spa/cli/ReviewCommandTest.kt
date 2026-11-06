package spa.cli

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import spa.agent.model.ModelRequest
import spa.agent.model.TokenEstimate
import spa.agent.review.AnalysisAgents
import spa.agent.review.PoolBundle
import spa.agent.review.ReviewText
import spa.core.sim.FixtureManifest

/**
 * SPA-070 criteria 1 to 5, 8, 9 and 12: the key, `--dry-run`, `--show-input`, a full review with a
 * fake client, the decks, the caps, a set missing data, and a test-only agent.
 */
class ReviewCommandTest {
    @TempDir lateinit var temp: Path

    private val pool = Fixtures.pool("trk-manabox-01.csv")
    private val data = arrayOf("--set", "trk", "--data", Fixtures.data)

    private fun review(vararg extra: String, env: Map<String, String> = KEY_ENV) =
        runReview(pool, *data, *extra, env = env, clients = { CannedClient() })

    private val ids = AnalysisAgents.ALL.map { it.id }

    // Criterion 1

    @Test
    fun `without a key the command says so, prints nothing and reads no file`() {
        val run = runReview("no-such-pool.csv", *data)

        assertEquals(ExitCode.ERROR, run.exit)
        assertEquals("ANTHROPIC_API_KEY is not set", run.stderr.trimEnd())
        assertEquals(1, run.stderrLines().size)
        assertEquals("", run.stdout)
    }

    @Test
    fun `a blank key is no key`() {
        val run = runReview(pool, *data, env = mapOf("ANTHROPIC_API_KEY" to "  "))

        assertEquals(ExitCode.ERROR, run.exit)
        assertEquals("ANTHROPIC_API_KEY is not set", run.stderr.trimEnd())
    }

    @Test
    fun `help lists review`() {
        val run = runCli("--help")

        assertTrue("review <pool-file> --set <code>" in run.stdout)
        assertTrue("--record <dir>" in run.stdout)
    }

    // Criterion 2

    @Test
    fun `dry run plans eight calls with no key and no client`() {
        val run = runReview(pool, *data, "--dry-run")

        assertEquals(ExitCode.OK, run.exit, run.stderr)
        val lines = run.stdoutLines()
        assertEquals(
            "Dry run: review of $pool (trk), no calls made.",
            lines.first(),
        )
        assertEquals("Model: claude-sonnet-5-5", lines[1])
        assertTrue(lines[2].matches(Regex("""Cached bundle: ~\d+ tokens \(estimated\)""")))
        val calls = lines.filter { it.startsWith("Call ") }
        val order = ids + "explainer"
        assertEquals(order.size, calls.size)
        order.forEachIndexed { i, id ->
            assertTrue(calls[i].startsWith("Call ${i + 1}: $id, ~"), calls[i])
        }
        assertTrue("(after the analysts; estimated)" in calls.last())
        val total = lines.single { it.startsWith("Total:") }
        assertTrue("of 300000 allowed" in total && "of 60000 allowed" in total, total)
        assertEquals("Within the caps.", lines.last())
        assertEquals("", run.stderr)
    }

    @Test
    fun `dry run reports a cap it is over`() {
        val input = runReview(pool, *data, "--dry-run", "--max-input-tokens", "1000")
        val output = runReview(pool, *data, "--dry-run", "--max-output-tokens", "100")

        assertEquals(ExitCode.OK, input.exit)
        assertTrue(
            input.stdoutLines().any { it.matches(Regex("""Over the input cap by ~\d+ tokens\.""")) }
        )
        assertFalse("Within the caps." in input.stdout)
        assertTrue(input.stdoutLines().any { "of 1000 allowed" in it })
        assertEquals(ExitCode.OK, output.exit)
        // 7 analysts at 3000 and the Explainer at 6000 make 27000.
        assertTrue("Over the output cap by 26900 tokens." in output.stdoutLines())
    }

    @Test
    fun `dry run names the model and the explainer's estimate follows its formula`() {
        val run = runReview(pool, *data, "--dry-run", "--model", "other-model")
        val setup = ReviewSetup.load(options(), PrintStream(ByteArrayOutputStream()))

        assertTrue("Model: other-model" in run.stdoutLines())
        val bundle =
            Regex("""Cached bundle: ~(\d+) tokens""").find(run.stdout)!!.groupValues[1].toLong()
        assertEquals(
            bundle,
            TokenEstimate.of(ModelRequest("", 0, "", "", PoolBundle.text(setup.input))),
        )
        val decks = setup.decks.joinToString("\n") { it.text() }
        val expected = bundle + decks.length / 4 + 60L * setup.config.maxFindings * ids.size
        val line = run.stdoutLines().single { it.startsWith("Call 8: explainer") }
        assertEquals(
            "Call 8: explainer, ~$expected input tokens, up to 6000 output tokens " +
                "(after the analysts; estimated)",
            line,
        )
    }

    private fun options() =
        ReviewOptions(
            Args.parse(
                listOf(pool, *data),
                valueOptions = setOf("set", "data"),
            ),
            pool,
            "trk",
            AnalysisAgents.ALL,
            Caps(null, null),
        )

    // Criterion 3

    @Test
    fun `show input prints the bundle and what each agent is given`() {
        val run = runReview(pool, *data, "--show-input")

        assertEquals(ExitCode.OK, run.exit, run.stderr)
        val lines = run.stdout.lines()
        assertEquals("# Pool bundle", lines.first())
        assertTrue(lines[1].startsWith("Set: trk (Star Trek), format speed:"), lines[1])
        listOf(
                "/totalCards = 85 (typical: median 85, p10 85, p90 85)",
                "/nonlandCards = 71 (typical: median 71, p10 67, p90 74)",
                "/basicLands = 5 (typical: median 5, p10 4, p90 6)",
                "/removalTotal = 9 (typical: median 8, p10 5, p90 11)",
                "/interactionTotal = 1 (typical: median 3, p10 1, p90 6)",
                "/fixingSources/count = 6 (typical: median 10, p10 7, p90 13)",
                "/pairs/WU/cards = 33 (typical: median 29, p10 24, p90 35)",
                "/pairs/WU/creatures = 19 (typical: median 15, p10 11, p90 20)",
                "/pairs/WU/removal = 2 (typical: median 2, p10 1, p90 4)",
                "/pairs/WU/fixingSources = 4 (typical: median 10, p10 7, p90 13)",
            )
            .forEach { assertTrue(it in lines, "missing line: $it") }
        val headings = lines.filter { it.startsWith("=== ") }
        assertEquals(
            AnalysisAgents.ALL.map { "=== ${it.id} (${it.title}) ===" },
            headings,
        )
        assertEquals("", run.stderr)
    }

    @Test
    fun `show input names an agent that cannot run`() {
        val dir = trkData(temp.resolve("data"), tags = false)

        val run = runReview(pool, "--set", "trk", "--data", dir.toString(), "--show-input")

        assertEquals(ExitCode.OK, run.exit, run.stderr)
        assertTrue(
            "=== synergy (Synergy) === not run: no synergy tags for this set" in run.stdout.lines()
        )
    }

    // Criterion 4

    private val headings =
        listOf(
            "Decks",
            "Verdict",
            "Colour depth",
            "Curve",
            "Mana and fixing",
            "Interaction",
            "Bombs and splashes",
            "Archetypes",
            "Synergy",
            "Disagreements",
        )

    @Test
    fun `a review prints every section in order and the same bytes twice`() {
        val first = review()
        val second = review()

        assertEquals(ExitCode.OK, first.exit, first.stderr)
        assertEquals(first.stdout, second.stdout)
        val lines = first.stdout.lines()
        val at = headings.map { heading ->
            lines.indexOfFirst { it == heading || it.startsWith("$heading  (") }
        }
        assertTrue(at.all { it >= 0 } && at == at.sorted(), "headings at $at")
        assertTrue("Missing: none" in lines)
        assertTrue(lines.any { it.startsWith("Usage: 8 calls") })
        assertEquals(ReviewText.ATTRIBUTION, lines.last { it.isNotBlank() })
    }

    @Test
    fun `json prints the structured review`() {
        val run = review("--json")

        assertEquals(ExitCode.OK, run.exit, run.stderr)
        val json = run.json()
        assertEquals(
            "set,setName,pool,decks,verdict,agents,disagreements,missing,usage,model,attribution",
            json.keys.joinToString(","),
        )
        val agents = json.getValue("agents").jsonArray.map { it.jsonObject }
        assertEquals(7, agents.size)
        assertTrue(agents.all { it.getValue("status").jsonPrimitive.content == "answered" })
        assertEquals(3, json.getValue("decks").jsonArray.size)
    }

    @Test
    fun `the client is made from the environment`() {
        var seen: Map<String, String>? = null
        val client = CannedClient()

        val run =
            runReview(
                pool,
                *data,
                env = KEY_ENV,
                clients = {
                    seen = it
                    client
                },
            )

        assertEquals(ExitCode.OK, run.exit)
        assertEquals(KEY_ENV, seen)
        assertEquals(8, client.requests.size)
        assertEquals((ids + "explainer").toSet(), client.requests.map { it.label }.toSet())
    }

    // Criterion 5

    @Test
    fun `the decks are the builder's, with its colours and scores`() {
        val run = review()
        val session =
            BuildSession.open(
                DataDir(Fixtures.root),
                "trk",
                Fixtures.root.resolve("weights/deck-tuned.json"),
                null,
                null,
            )
        val parsed =
            PoolGradesCommand.pool(
                Files.readString(Path.of(pool)),
                pool,
                session.index,
                PrintStream(ByteArrayOutputStream()),
            )
        val expected =
            session.rank(parsed).take(3).mapIndexed { i, deck ->
                Triple(
                    i + 1,
                    deck.config.toString(),
                    String.format(Locale.ROOT, "%.2f", deck.scored.total),
                )
            }

        val heading =
            Regex("""^Rank (\d+): (\S+?)(?: \(same cards as [^)]*\))?  score (-?\d+\.\d\d)$""")
        val printed =
            run.stdout
                .lines()
                .mapNotNull { heading.matchEntire(it) }
                .map { Triple(it.groupValues[1].toInt(), it.groupValues[2], it.groupValues[3]) }
        // The Explainer's notes also start with "Rank <n>:", so only the deck headings match.
        assertEquals(expected, printed)
        assertEquals(3, expected.size)
    }

    // Criterion 8

    @Test
    fun `a tiny cap skips every call and the decks still print`() {
        val client = CannedClient()

        val run =
            runReview(
                pool,
                *data,
                "--max-input-tokens",
                "1000",
                env = KEY_ENV,
                clients = { client },
            )

        assertEquals(ExitCode.OK, run.exit, run.stderr)
        assertTrue(client.requests.isEmpty())
        val missing = run.stdout.lines().single { it.startsWith("Missing: ") }
        ids.forEach { id ->
            assertTrue("$id (budget: input cap would be passed" in missing, missing)
        }
        assertTrue("explainer (no findings to explain)" in missing, missing)
        assertTrue("Rank 1: " in run.stdout)
        assertTrue("Usage: 0 calls" in run.stdout)
    }

    // Criterion 9

    @Test
    fun `a set with no tag file reviews without the synergy agent`() {
        val dir = trkData(temp.resolve("data"), tags = false)

        val run =
            runReview(
                pool,
                "--set",
                "trk",
                "--data",
                dir.toString(),
                env = KEY_ENV,
                clients = { CannedClient() },
            )

        assertEquals(ExitCode.OK, run.exit, run.stderr)
        val missing = run.stdout.lines().single { it.startsWith("Missing: ") }
        assertEquals("Missing: synergy (no synergy tags for this set)", missing)
        assertTrue(run.stdout.lines().any { it.startsWith("Usage: 7 calls") })
    }

    @Test
    fun `a set with no baseline runs with tables without typical and a stderr note`() {
        val dir = trkData(temp.resolve("data"), baseline = false)

        val run = runReview(pool, "--set", "trk", "--data", dir.toString(), "--show-input")

        assertEquals(ExitCode.OK, run.exit, run.stderr)
        assertTrue(run.stderrLines().single().startsWith("baseline: none"), run.stderr)
        assertTrue("/totalCards = 85" in run.stdout.lines())
        assertFalse("typical" in run.stdout.substringAfter("=== colour-depth"))
    }

    @Test
    fun `missing files are one line and exit 1`() {
        val noGrades = trkData(temp.resolve("nogrades"), grades = false)
        val cases =
            listOf(
                listOf("no-such-pool.csv", *data) to "pool file not found",
                listOf(pool, *data, "--config", "no-such.json") to "config not found",
                listOf(pool, *data, "--brief", "no-such.json") to "brief not found",
                listOf(pool, *data, "--weights", "no-such.json") to "weights file not found",
                listOf(pool, *data, "--tags", "no-such.json") to "tag file not found",
                listOf(pool, "--set", "trk", "--data", noGrades.toString()) to "no grade file",
                listOf(pool, "--set", "nope", "--data", Fixtures.data) to "no Scryfall cache",
            )
        for ((args, message) in cases) {
            val run = runReview(*args.toTypedArray(), env = KEY_ENV)
            assertEquals(ExitCode.ERROR, run.exit, "$args")
            assertEquals("", run.stdout)
            assertEquals(1, run.stderrLines().size, run.stderr)
            assertTrue(message in run.stderr, "$message in ${run.stderr}")
        }
    }

    @Test
    fun `a config with an unknown key is one line naming the key`() {
        val text = Files.readString(Fixtures.root.resolve("agents/review.json"))
        val config =
            writeFile(
                temp,
                "bad.json",
                text.replace("\"parallel\": 6,", "\"parallel\": 6, \"bogus\": 1,"),
            )

        val run = review("--config", config)

        assertEquals(ExitCode.ERROR, run.exit)
        assertEquals(1, run.stderrLines().size, run.stderr)
        assertTrue("bogus" in run.stderr, run.stderr)
    }

    @Test
    fun `an unresolved pool line is listed in the header and does not fail the run`() {
        val entry =
            FixtureManifest.load(Fixtures.root.resolve("pools")).fixtures.single {
                it.file == "trk-text-misspelled-01.txt"
            }

        val run =
            runReview(
                Fixtures.pool(entry.file),
                *data,
                env = KEY_ENV,
                clients = { CannedClient() },
            )

        assertEquals(ExitCode.OK, run.exit, run.stderr)
        assertTrue(
            "${entry.expectedUnresolved.size} unresolved lines" in run.stdout,
            run.stdout.lines().take(3).toString(),
        )
        assertTrue(entry.expectedUnresolved.isNotEmpty())
        assertEquals(entry.expectedUnresolved.size, run.stderrLines().count { "unresolved" in it })
    }

    // The command line

    @Test
    fun `a bad cap or a bad combination of options is exit 2`() {
        val bad =
            listOf(
                listOf("--max-input-tokens", "abc"),
                listOf("--max-input-tokens", "0"),
                listOf("--max-output-tokens", "-5"),
                listOf("--replay", "x", "--record", "y"),
                listOf("--dry-run", "--record", "y"),
                listOf("--show-input", "--replay", "x"),
                listOf("--bogus"),
            )
        for (extra in bad) {
            val run = runReview(pool, *data, *extra.toTypedArray(), env = KEY_ENV)
            assertEquals(ExitCode.USAGE, run.exit, "$extra")
            assertEquals("", run.stdout)
        }
        assertEquals(ExitCode.USAGE, runReview(*data).exit)
    }

    @Test
    fun `the model and the caps replace the config's`() {
        val client = CannedClient()

        val run =
            runReview(
                pool,
                *data,
                "--model",
                "model-x",
                "--max-output-tokens",
                "50000",
                env = KEY_ENV,
                clients = { client },
            )

        assertEquals(ExitCode.OK, run.exit, run.stderr)
        assertTrue(client.requests.all { it.model == "model-x" })
        assertTrue("(model model-x)" in run.stdout)
    }

    // Criterion 12

    @Test
    fun `a test-only agent in the list gets a request, a section and a usage entry`() {
        val config = writeFile(temp, "probe.json", configFor(ids + "probe"))
        val client = CannedClient(mapOf("probe" to CannedClient.PROBE))

        val run =
            runReview(
                pool,
                *data,
                "--config",
                config,
                env = KEY_ENV,
                agents = AnalysisAgents.ALL + ProbeAgent,
                clients = { client },
            )

        assertEquals(ExitCode.OK, run.exit, run.stderr)
        assertTrue("probe" in client.requests.map { it.label })
        assertTrue(run.stdout.lines().any { it.startsWith("Probe notes  (") })
        assertTrue(run.stdout.lines().any { it.startsWith("Usage: 9 calls") })
        assertTrue("Missing: none" in run.stdout.lines())
    }
}
