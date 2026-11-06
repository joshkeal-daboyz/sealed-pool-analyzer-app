package spa.cli

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import spa.agent.model.ReplayModelClient

/** SPA-070 criteria 6 and 7: `--replay` runs offline and `--record` writes what it replays. */
class ReviewReplayTest {
    @TempDir lateinit var temp: Path

    private val pool = Fixtures.pool("trk-manabox-01.csv")
    private val data = arrayOf("--set", "trk", "--data", Fixtures.data)

    @Test
    fun `replay of the canned recordings prints a complete review with no key and no client`() {
        val run = runReview(pool, *data, "--replay", CANNED.toString())

        assertEquals(ExitCode.OK, run.exit, run.stderr)
        val lines = run.stdout.lines()
        assertTrue("Missing: none" in lines)
        assertTrue(lines.any { it.startsWith("Usage: 8 calls") })
        assertTrue("Disagreements" in lines)
        assertTrue(lines.any { it.startsWith("- Fixing against removal [mana, interaction]") })
        assertEquals(run.stdout, runReview(pool, *data, "--replay", CANNED.toString()).stdout)
    }

    @Test
    fun `replay prints the structured review too`() {
        val run = runReview(pool, *data, "--replay", CANNED.toString(), "--json")

        assertEquals(ExitCode.OK, run.exit, run.stderr)
        assertEquals(8, run.json().getValue("usage").jsonObject.getValue("calls").jsonPrimitive.int)
    }

    @Test
    fun `record writes one file per call that replays to the same review and holds no key`() {
        val directory = temp.resolve("recorded")

        val live =
            runReview(
                pool,
                *data,
                "--record",
                directory.toString(),
                env = KEY_ENV,
                clients = { CannedClient() },
            )

        assertEquals(ExitCode.OK, live.exit, live.stderr)
        val files = directory.listDirectoryEntries().map { it.name }.sorted()
        assertEquals(CannedClient.RECORDING_NAMES.sorted(), files)
        files.forEach { assertFalse(TEST_KEY in Files.readString(directory.resolve(it)), it) }
        ReplayModelClient.fromDirectory(directory)
        val replayed = runReview(pool, *data, "--replay", directory.toString())
        assertEquals(ExitCode.OK, replayed.exit, replayed.stderr)
        assertEquals(live.stdout, replayed.stdout)
    }

    @Test
    fun `record into a directory that holds recordings is one line and exit 1`() {
        val directory = temp.resolve("recorded")
        Files.createDirectories(directory)
        Files.writeString(directory.resolve("curve.json"), "{}")

        val run =
            runReview(
                pool,
                *data,
                "--record",
                directory.toString(),
                env = KEY_ENV,
                clients = { CannedClient() },
            )

        assertEquals(ExitCode.ERROR, run.exit)
        assertEquals(1, run.stderrLines().size, run.stderr)
        assertTrue("already holds recordings" in run.stderr)
    }

    @Test
    fun `replay from a directory that is not there is one line and exit 1`() {
        val run = runReview(pool, *data, "--replay", temp.resolve("none").toString())

        assertEquals(ExitCode.ERROR, run.exit)
        assertEquals(1, run.stderrLines().size, run.stderr)
        assertTrue("replay directory not found" in run.stderr)
    }

    @Test
    fun `replay with a changed request says the recordings no longer match`() {
        val run = runReview(pool, *data, "--replay", CANNED.toString(), "--model", "other-model")

        assertEquals(ExitCode.ERROR, run.exit)
        assertEquals("", run.stdout)
        assertEquals(1, run.stderrLines().size, run.stderr)
        assertTrue("no answer for a request of this review" in run.stderr, run.stderr)
    }

    @Test
    fun `replay of recordings that do not parse is one line and exit 1`() {
        val directory = temp.resolve("broken")
        Files.createDirectories(directory)
        Files.writeString(directory.resolve("curve.json"), "not json")

        val run = runReview(pool, *data, "--replay", directory.toString())

        assertEquals(ExitCode.ERROR, run.exit)
        assertEquals(1, run.stderrLines().size, run.stderr)
        assertTrue("cannot read the recordings" in run.stderr, run.stderr)
    }

    private companion object {
        val CANNED: Path = Fixtures.root.resolve("recordings/review-canned-trk-manabox-01")
    }
}
