package spa.app.pair

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import spa.app.data.TestFixtures
import spa.app.importing.ImportOutcome
import spa.app.importing.PoolSource
import spa.app.results.ResultsKit
import spa.app.testing.TestImports
import spa.core.metrics.ColorPair

/**
 * Criterion 1: for every trk fixture and every pair, `PairDetailUi` holds exactly the background's
 * pointers, each with the golden's value and the baseline's stat, and the golden's splash list.
 */
@RunWith(AndroidJUnit4::class)
class PairDetailUiTest {
    private fun trkFixtures(): List<String> {
        val manifest = JSONObject(TestFixtures.text("pools/manifest.json")).getJSONArray("fixtures")
        return (0 until manifest.length())
            .map { manifest.getJSONObject(it) }
            .filter { it.getString("set") == "trk" }
            .map { it.getString("file") }
    }

    private fun import(file: String): ImportOutcome.Success {
        val outcome = runBlocking {
            TestImports.poolImporter.import(TestImports.pool(file), null, PoolSource.File(file))
        }
        assertTrue("$file: $outcome", outcome is ImportOutcome.Success)
        return outcome as ImportOutcome.Success
    }

    @Test
    fun everyTrkFixtureAndPairGivesTheGoldenValuesBesideTheBaseline() {
        val trk = trkFixtures()
        assertEquals(TRK_FIXTURES, trk.size)
        trk.forEach { file ->
            val success = import(file)
            assertEquals(PairKit.PAIRS, ColorPair.entries.map { it.name })
            ColorPair.entries.forEach { pair ->
                val ui = PairDetailUi.from(success, pair, ResultsKit.baseline)
                val where = "$file $pair"
                assertEquals(where, pair, ui.pair)
                assertEquals(
                    "$where: pointers",
                    PairKit.pointers(pair.name),
                    ui.values.map { it.pointer },
                )
                ui.values.forEach { shown ->
                    val pointer = shown.pointer
                    assertEquals(
                        "$where $pointer",
                        ResultsKit.goldenValue(file, pointer),
                        shown.value.toDouble(),
                        0.0,
                    )
                    val (median, p10, p90) = ResultsKit.typicalExact(pointer)
                    val stat = checkNotNull(shown.typical) { "$where $pointer has no typical" }
                    assertEquals("$where $pointer median", median, stat.median, 0.0)
                    assertEquals("$where $pointer p10", p10, stat.p10, 0.0)
                    assertEquals("$where $pointer p90", p90, stat.p90, 0.0)
                }
                assertEquals(
                    "$where: splash list",
                    PairKit.goldenSplash(file, pair.name),
                    ui.splashCandidates.map {
                        PairKit.Splash(it.name, it.color.name, it.reason.name)
                    },
                )
                assertEquals(
                    "$where: unresolved lines",
                    PairKit.goldenUnresolved(file),
                    ui.unresolvedLines,
                )
            }
        }
    }

    @Test
    fun theSectionsHoldTheirValuesInTheSpecsOrder() {
        val ui = PairDetailUi.from(import(PairKit.FILE), ColorPair.WU, ResultsKit.baseline)
        val at = "/pairs/WU"
        assertEquals(listOf("W", "U"), ui.lands.map { it.color.name })
        assertEquals(
            listOf("$at/landRecommendation/W", "$at/landRecommendation/U"),
            ui.lands.map { it.lands.pointer },
        )
        assertEquals(PairKit.COUNTS.map { "$at/$it" }, ui.counts.map { it.pointer })
        assertEquals(PairKit.BUCKETS.toList(), ui.curve.map { it.manaValue })
        ui.curve.forEachIndexed { i, row ->
            assertEquals("$at/curve/buckets/$i/creatures", row.creatures.pointer)
            assertEquals("$at/curve/buckets/$i/noncreatures", row.other.pointer)
        }
        assertEquals("$at/splashCandidates/count", ui.splashCount.pointer)
    }

    @Test
    fun aPairWithNoCardsHasZeroLandsForBothColours() {
        val success = TestImports.success(PairKit.EMPTY_WU_POOL)
        val ui = PairDetailUi.from(success, ColorPair.WU, ResultsKit.baseline)
        assertTrue(success.metrics.pairs.getValue(ColorPair.WU).landRecommendation.isEmpty())
        assertEquals(PairKit.pointers("WU"), ui.values.map { it.pointer })
        ui.values
            .filterNot { it.pointer.endsWith("splashCandidates/count") }
            .forEach { assertEquals(it.pointer, 0, it.value) }
    }

    private companion object {
        const val TRK_FIXTURES = 23
    }
}
