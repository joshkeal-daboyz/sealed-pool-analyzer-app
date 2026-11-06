package spa.cli

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * SPA-070 criterion 6: the committed recordings in
 * `fixtures/recordings/review-canned-trk-manabox-01` are what a review of the `trk` sample pool
 * answered by [CannedClient] records. They are canned, not a model's output. A change to a prompt,
 * the config's model, the weights file or the pool changes the requests, so this test fails;
 * `-PupdateRecordings=true` rewrites the files instead.
 */
class CannedRecordingsTest {
    @TempDir lateinit var temp: Path

    private val committed = Fixtures.root.resolve("recordings/review-canned-trk-manabox-01")

    private fun regenerate(): Path {
        val directory = temp.resolve("regenerated")
        val run =
            runReview(
                Fixtures.pool("trk-manabox-01.csv"),
                "--set",
                "trk",
                "--data",
                Fixtures.data,
                "--record",
                directory.toString(),
                env = KEY_ENV,
                clients = { CannedClient() },
            )
        assertEquals(ExitCode.OK, run.exit, run.stderr)
        return directory
    }

    private fun recordings(directory: Path): Map<String, String> =
        directory
            .listDirectoryEntries("*.json")
            .associate { it.name to normalised(it) }
            .toSortedMap()

    @Test
    fun `the committed recordings equal the regenerated ones`() {
        val fresh = regenerate()
        if (System.getProperty("spa.updateRecordings") == "true") {
            committed.createDirectories()
            committed.listDirectoryEntries("*.json").forEach(Files::delete)
            fresh.listDirectoryEntries("*.json").forEach {
                Files.writeString(committed.resolve(it.name), normalised(it))
            }
        }

        val expected = recordings(fresh)
        val actual = recordings(committed)

        assertEquals(CannedClient.RECORDING_NAMES.sorted(), actual.keys.toList(), HOW)
        val different = expected.filter { (name, text) -> actual[name] != text }.keys
        assertTrue(
            different.isEmpty(),
            "recordings differ from the regenerated ones: $different. $HOW",
        )
    }

    @Test
    fun `the recordings are marked as canned and hold no key`() {
        val readme =
            normalised(committed.resolve("README.md")).split(Regex("""\s+""")).joinToString(" ")

        assertTrue("canned" in readme && "not a model" in readme)
        assertTrue("updateRecordings" in readme)
        committed.listDirectoryEntries("*.json").forEach {
            val text = Files.readString(it)
            assertTrue(("sk-" + "ant-") !in text && TEST_KEY !in text)
        }
    }

    private companion object {
        const val HOW =
            "Regenerate them with ./gradlew :cli:test --tests spa.cli.CannedRecordingsTest " +
                "-PupdateRecordings=true"
    }
}
