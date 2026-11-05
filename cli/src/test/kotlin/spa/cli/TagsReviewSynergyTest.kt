package spa.cli

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import spa.agent.tags.Axis
import spa.agent.tags.FactSupply
import spa.agent.tags.TagCorrection
import spa.agent.tags.TagFile
import spa.agent.tags.TagFiles
import spa.agent.tags.TagReview

/**
 * SPA-052 criterion 4: `spa synergy-table` and `spa synergy-check` on trk tag files with a `review`
 * block and with 20 v2 axes, and the v1 trk file (no review) reading and printing as before. The v1
 * trk file is the frozen copy `fixtures/tags/v1/trk.json` since SPA-053.
 */
class TagsReviewSynergyTest {
    @TempDir lateinit var temp: Path

    private val v1Path: Path = Path.of(Fixtures.v1Tags("trk"))

    private val committed: TagFile = TagFiles.read(v1Path)

    /** A data directory holding trk's Scryfall cache, its checks, and the tag file [tags]. */
    private fun data(tags: TagFile): String {
        val dir = temp.resolve("data")
        listOf("trk", "sds").forEach { set ->
            val cache = dir.resolve("scryfall/$set").createDirectories()
            Fixtures.root.resolve("scryfall/$set").listDirectoryEntries().forEach {
                if (Files.notExists(cache.resolve(it.fileName.toString()))) {
                    it.copyTo(cache.resolve(it.fileName.toString()))
                }
            }
        }
        val checks = dir.resolve("synergy-checks").createDirectories().resolve("trk.json")
        if (Files.notExists(checks)) Fixtures.root.resolve("synergy-checks/trk.json").copyTo(checks)
        val text = TagFiles.write(tags)
        assertEquals(emptyList(), TagFiles.validate(Json.parseToJsonElement(text)))
        writeFile(dir, "tags/trk.json", text)
        return dir.toString()
    }

    private fun table(data: String) = runCli("synergy-table", "--set", "trk", "--data", data)

    private fun check(data: String) = runCli("synergy-check", "--set", "trk", "--data", data)

    private val review =
        TagReview(
            "claude-fable-5-1 via Claude Code, tags review prompt v1, 2026-11-05",
            linkedMapOf(
                "add-role" to 0,
                "remove-role" to 0,
                "set-strength" to 0,
                "add-partner" to 0,
                "remove-partner" to 1,
            ),
            listOf(
                TagCorrection("trk/1", "remove-partner", partner = "trk/2", reason = "Hand-built.")
            ),
        )

    @Test
    fun `the committed trk file reads and writes back byte for byte, with no review`() {
        val path = v1Path

        assertEquals(null, committed.review)
        assertEquals(path.readText(), TagFiles.write(committed))
    }

    @Test
    fun `a review block changes neither command's output on trk`() {
        val v1 = arrayOf("--tags", v1Path.toString())
        val before =
            runCli("synergy-table", "--set", "trk", *v1, "--data", Fixtures.data) to
                runCli("synergy-check", "--set", "trk", *v1, "--data", Fixtures.data)
        val reviewed = data(committed.copy(review = review))

        val after = table(reviewed) to check(reviewed)

        listOf(before.first, before.second, after.first, after.second).forEach {
            assertEquals(ExitCode.OK, it.exit, it.stderr)
        }
        assertTrue(before.first.stdoutLines().size > 100)
        assertEquals(before.first.stdout, after.first.stdout)
        assertEquals(before.second.stdout, after.second.stdout)
    }

    @Test
    fun `both commands run on a v2 trk file with 20 axes`() {
        val extra =
            listOf("legendary", "land", "artifact", "makes-token", "enters-trigger").map {
                Axis("$it-axis", it, "Cards with $it.", listOf(FactSupply(it, 2)))
            }
        val v2 =
            committed.copy(
                axes = committed.axes.map { it.copy(suppliedBy = listOf()) } + extra,
                review = review,
            )
        assertEquals(20, v2.axes.size)
        val data = data(v2)

        val readBack = TagFiles.read(Path.of(data, "tags", "trk.json"))
        val table = table(data)
        val check = check(data)

        assertEquals(v2, readBack)
        assertEquals(ExitCode.OK, table.exit, table.stderr)
        assertEquals(ExitCode.OK, check.exit, check.stderr)
        assertTrue(" fact " in table.stdoutLines().first(), table.stdoutLines().first())
        assertTrue(
            Regex("""^\d+ of \d+ checks pass \(\d+ independent\)$""")
                .matches(check.stdoutLines().last())
        )
    }
}
