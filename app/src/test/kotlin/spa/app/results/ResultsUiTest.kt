package spa.app.results

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import spa.app.data.TestFixtures
import spa.app.importing.ImportOutcome
import spa.app.importing.PoolSource
import spa.app.testing.TestImports
import spa.core.baseline.Stat
import spa.core.metrics.ColorBucket

/**
 * Criterion 1: `ResultsUi` holds exactly the background's pointers, with golden and baseline
 * values.
 */
@RunWith(AndroidJUnit4::class)
class ResultsUiTest {
    @Test
    fun everyTrkFixtureGivesTheGoldenValuesBesideTheBaseline() {
        val manifest = JSONObject(TestFixtures.text("pools/manifest.json")).getJSONArray("fixtures")
        val trk =
            (0 until manifest.length())
                .map { manifest.getJSONObject(it) }
                .filter { it.getString("set") == "trk" }
                .map { it.getString("file") }
        assertEquals(TRK_FIXTURES, trk.size)

        trk.forEach { file ->
            val outcome = runBlocking {
                TestImports.poolImporter.import(
                    TestImports.pool(file),
                    null,
                    PoolSource.File(file),
                )
            }
            assertTrue("$file: $outcome", outcome is ImportOutcome.Success)
            val ui = ResultsUi.from(outcome as ImportOutcome.Success, ResultsKit.baseline)
            val values = ui.values
            assertEquals(
                "$file: pointers",
                ResultsKit.ALL_POINTERS,
                values.map { it.pointer }.toSet(),
            )
            assertEquals(
                "$file: no pointer twice",
                values.size,
                values.map { it.pointer }.toSet().size,
            )
            values.forEach { shown ->
                val pointer = shown.pointer
                assertEquals(
                    "$file $pointer",
                    ResultsKit.goldenValue(file, pointer),
                    shown.value.toDouble(),
                    0.0,
                )
                val (median, p10, p90) = ResultsKit.typicalExact(pointer)
                val stat = checkNotNull(shown.typical) { "$file $pointer has no typical" }
                assertEquals("$file $pointer median", median, stat.median, 0.0)
                assertEquals("$file $pointer p10", p10, stat.p10, 0.0)
                assertEquals("$file $pointer p90", p90, stat.p90, 0.0)
            }
        }
    }

    @Test
    fun theSectionsHoldTheirValuesInTheSpecsOrder() {
        val ui = ResultsUi.from(ResultsKit.success(FILE), ResultsKit.baseline)
        assertEquals(ResultsKit.GLANCE_POINTERS, ui.glance.map { it.pointer })
        assertEquals(ResultsKit.goldenPairOrder(FILE), ui.pairs.map { it.pair.name })
        ui.pairs.forEach {
            assertEquals("/pairs/${it.pair}/cards", it.cards.pointer)
            assertEquals("/pairs/${it.pair}/creatures", it.creatures.pointer)
        }
        assertEquals(ColorBucket.entries, ui.colors.map { it.bucket })
        val white = ui.colors.first()
        assertEquals(
            listOf(
                "/byColor/W/cards/total",
                "/byColor/W/playables/total",
                "/byColor/W/creatures",
                "/byColor/W/removal",
                "/byColor/W/interaction",
                "/byColor/W/cards/common",
                "/byColor/W/cards/uncommon",
                "/byColor/W/cards/rare",
                "/byColor/W/cards/mythic",
                "/byColor/W/playables/common",
                "/byColor/W/playables/uncommon",
                "/byColor/W/playables/rare",
                "/byColor/W/playables/mythic",
            ),
            white.values.map { it.pointer },
        )
        assertEquals(ResultsKit.kits, ui.kits)
        assertEquals(
            ResultsKit.goldenFixing(FILE).map { it.first },
            ui.fixingSources.map { it.name },
        )
    }

    @Test
    fun outsideIsBelowP10AboveP90AndNothingBetween() {
        val stat = Stat(median = 8.0, p10 = 5.0, p90 = 11.0, mean = 8.0)
        assertEquals(Outside.BELOW, DisplayedValue("/x", 4, stat).outside)
        assertNull(DisplayedValue("/x", 5, stat).outside)
        assertNull(DisplayedValue("/x", 11, stat).outside)
        assertEquals(Outside.ABOVE, DisplayedValue("/x", 12, stat).outside)
        assertNull(DisplayedValue("/x", 12, null).outside)
    }

    @Test
    fun problemsCountUnresolvedLinesAndKitWarnings() {
        val misspelled = "trk-text-misspelled-01.txt"
        val ui = ResultsUi.from(ResultsKit.success(misspelled), ResultsKit.baseline)
        assertEquals(
            ResultsKit.goldenUnresolved(misspelled).size +
                ResultsKit.goldenWarnings(misspelled).size,
            ui.problemCount,
        )
        assertFalse(ui.someFromAnotherSet)
        val clean = ResultsUi.from(ResultsKit.success(FILE), ResultsKit.baseline)
        assertEquals(0, clean.problemCount)
    }

    @Test
    fun aWrongSetLineInAPoolWithEntriesSetsTheNote() {
        val ui =
            ResultsUi.from(ResultsKit.success("trk-manabox-wrong-set-01.csv"), ResultsKit.baseline)
        assertTrue(ui.someFromAnotherSet)
        val bad =
            ResultsUi.from(
                ResultsKit.success("trk-collector-bad-number-01.txt"),
                ResultsKit.baseline,
            )
        assertFalse(bad.someFromAnotherSet)
    }

    private companion object {
        const val FILE = "trk-manabox-01.csv"

        // The ticket's criterion 1 names this count; the test asserts it from the manifest.
        const val TRK_FIXTURES = 23
    }
}
