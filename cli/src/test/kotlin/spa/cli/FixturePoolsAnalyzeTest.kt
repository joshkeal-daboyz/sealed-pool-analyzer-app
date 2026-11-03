package spa.cli

import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import spa.core.sim.FixtureEntry
import spa.core.sim.FixtureManifest

/** Every pool in `fixtures/pools/manifest.json`, run through `spa analyze` with its set. */
class FixturePoolsAnalyzeTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtures")
    fun `analyze exits 0 with metrics for all ten pairs`(file: String, set: String) {
        val run = runCli("analyze", Fixtures.pool(file), "--set", set, "--data", Fixtures.data)

        assertEquals(ExitCode.OK, run.exit, run.stderr)
        val metrics = run.json().getValue("metrics")
        assertNotEquals(JsonNull, metrics)
        assertEquals(10, metrics.jsonObject.getValue("pairs").jsonObject.size)
    }

    companion object {
        @JvmStatic
        fun fixtures(): List<Array<String>> {
            val entries: List<FixtureEntry> =
                FixtureManifest.load(Fixtures.root.resolve("pools")).fixtures
            check(entries.isNotEmpty()) { "The manifest lists no fixtures." }
            return entries.map { arrayOf(it.file, it.set) }
        }
    }
}
