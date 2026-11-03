package spa.cli

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import spa.core.sim.FixtureManifest

/**
 * Pins `spa analyze`'s output for every pool in `fixtures/pools/manifest.json` against
 * `fixtures/golden/<pool file>.json`. The volatile fields `tool.version` and `pool.file` are left
 * out of both. Run with `-PupdateGoldens=true` to rewrite the golden files from the current output.
 */
class GoldenTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtures")
    fun `analyze output matches the golden file`(file: String, set: String, format: String) {
        val actual = pinnedOutput(file, set, format)
        val golden = Goldens.file(file)

        if (Goldens.update) {
            Files.createDirectories(golden.parent)
            Files.writeString(golden, actual)
            return
        }
        if (!golden.exists()) {
            fail("Golden file $golden is missing. Write it with: ${Goldens.COMMAND}")
        }
        assertEquals(
            Goldens.normalize(Files.readString(golden)),
            actual,
            "spa analyze output for $file differs from $golden. If the change is intended and a " +
                "ticket says so, rewrite the goldens with: ${Goldens.COMMAND}",
        )
    }

    @Test
    fun `every golden file belongs to a manifest entry`() {
        val expected = fixtures().map { "${it[0]}.json" }.toSet()
        val stray =
            if (Goldens.dir.exists()) {
                Goldens.dir
                    .listDirectoryEntries("*.json")
                    .map { it.name }
                    .filterNot { it in expected }
            } else {
                emptyList()
            }
        assertTrue(stray.isEmpty(), "Golden files with no manifest entry: $stray")
    }

    @Test
    fun `stripping removes only the volatile fields`() {
        val run =
            runCli(
                "analyze",
                Fixtures.pool("trk-manabox-01.csv"),
                "--set",
                "trk",
                "--data",
                Fixtures.data,
            )
        val full = run.json()
        val stripped = Json.parseToJsonElement(Goldens.strip(run.stdout)).jsonObject

        assertEquals(full.keys, stripped.keys)
        assertEquals(listOf("name"), stripped.getValue("tool").jsonObject.keys.toList())
        assertEquals(
            full.getValue("pool").jsonObject.keys - "file",
            stripped.getValue("pool").jsonObject.keys,
        )
        assertEquals(full.getValue("metrics"), stripped.getValue("metrics"))
        assertEquals(full.getValue("baseline"), stripped.getValue("baseline"))
    }

    @Test
    fun `normalize turns CRLF into LF`() {
        assertEquals("{\n  \"a\": 1\n}\n", Goldens.normalize("{\r\n  \"a\": 1\r\n}\r\n"))
    }

    /** `spa analyze` for one manifest entry, with the volatile fields stripped. */
    private fun pinnedOutput(file: String, set: String, format: String): String {
        val run =
            runCli(
                "analyze",
                Fixtures.pool(file),
                "--set",
                set,
                "--format",
                format,
                "--data",
                Fixtures.data,
            )
        assertEquals(ExitCode.OK, run.exit, run.stderr)
        return Goldens.strip(run.stdout)
    }

    companion object {
        @JvmStatic
        fun fixtures(): List<Array<String>> {
            val entries = FixtureManifest.load(Fixtures.root.resolve("pools")).fixtures
            check(entries.isNotEmpty()) { "The manifest lists no fixtures." }
            return entries.map { arrayOf(it.file, it.set, it.format) }
        }
    }
}

/** Where the golden files live and how output is turned into their contents. */
internal object Goldens {
    const val COMMAND = "./gradlew :cli:test -PupdateGoldens=true"

    val dir: Path = Fixtures.root.resolve("golden")

    /** Whether the test task was run with `-PupdateGoldens=true`. */
    val update: Boolean = System.getProperty("spa.updateGoldens") == "true"

    private val json = Json { prettyPrint = true }

    fun file(poolFile: String): Path = dir.resolve("$poolFile.json")

    /** [text] with every CRLF turned into LF. */
    fun normalize(text: String): String = text.replace("\r\n", "\n")

    /**
     * The analyze document in [stdout] without `tool.version` and `pool.file`, pretty-printed the
     * way `spa analyze` prints, ending in a newline. Every other key keeps its place and value.
     */
    fun strip(stdout: String): String {
        val document = Json.parseToJsonElement(stdout).jsonObject
        val stripped =
            JsonObject(
                document.mapValues { (key, value) ->
                    when (key) {
                        "tool" -> JsonObject(value.jsonObject - "version")
                        "pool" -> JsonObject(value.jsonObject - "file")
                        else -> value
                    }
                }
            )
        return normalize(json.encodeToString(JsonObject.serializer(), stripped) + "\n")
    }
}
