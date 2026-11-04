package spa.core.baseline

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.measureTimedValue
import spa.core.brief.briefFixturesDir
import spa.core.cards.fixturesDir

/** Rebuilds each committed baseline from the parameters stored in it and compares the bytes. */
class BaselineGoldenTest {
    private fun rebuild(set: String): String {
        val committed =
            normalizeLineEndings(Files.readString(baselineFixturesDir.resolve("$set.json")))
        val stored = Baseline.decode(committed)
        assertEquals(set, stored.setCode)
        val rebuilt =
            BaselineBuilder.load(fixturesDir, briefFixturesDir, set)
                .build(stored.seed, stored.kits)
                .encode()
        assertEquals(committed, rebuilt, "fixtures/baselines/$set.json differs from a rebuild")
        return committed
    }

    @Test
    fun `syn json is rebuilt byte for byte`() {
        val stored = Baseline.decode(rebuild("syn"))
        assertEquals(1, stored.seed)
        assertEquals(500, stored.kits)
    }

    @Test
    fun `trk json is rebuilt byte for byte, in under 60 seconds on CI`() {
        val (committed, took) = measureTimedValue { rebuild("trk") }
        println("trk rebuild took $took")
        assertRebuildBudget(took, System.getenv("CI"))
        val stored = Baseline.decode(committed)
        assertEquals(20261106, stored.seed)
        assertEquals(2000, stored.kits)
    }
}
