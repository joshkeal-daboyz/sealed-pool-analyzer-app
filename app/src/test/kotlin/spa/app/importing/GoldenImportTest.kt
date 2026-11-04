package spa.app.importing

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import spa.app.data.SetData
import spa.app.data.TestFixtures
import spa.core.baseline.MetricFlattener

/**
 * Every `trk` pool fixture, imported through [PoolImporter] backed by the APK's bundled assets,
 * gives what `spa analyze` wrote to its golden file. Every expected value is read from the golden.
 */
@RunWith(AndroidJUnit4::class)
class GoldenImportTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun everyTrkFixtureMatchesItsGolden() = runTest {
        val manifest = JSONObject(TestFixtures.text("pools/manifest.json")).getJSONArray("fixtures")
        val trk =
            objects(manifest).filter { it.getString("set") == "trk" }.map { it.getString("file") }
        assertEquals(TRK_FIXTURES, trk.size)
        val importer = PoolImporter(SetData.forContext(context))

        trk.forEach { file ->
            val golden = JSONObject(TestFixtures.text("golden/$file.json"))
            val outcome =
                importer.import(TestFixtures.text("pools/$file"), null, PoolSource.File(file))
            assertTrue("$file: $outcome", outcome is ImportOutcome.Success)
            check(file, outcome as ImportOutcome.Success, golden)
        }
    }

    private fun check(file: String, success: ImportOutcome.Success, golden: JSONObject) {
        val pool = golden.getJSONObject("pool")
        val result = success.result
        assertEquals(file, PoolSource.File(file), success.source)
        assertEquals(file, pool.getString("format"), success.format.name)
        assertEquals(file, pool.getInt("entries"), result.pool.entries.size)
        assertEquals(file, pool.getInt("skippedLines"), result.skippedLines)
        assertEquals(
            file,
            objects(pool.getJSONArray("unresolved")).map {
                Triple(it.getInt("line"), it.getString("text"), it.getString("reason"))
            },
            result.unresolved.map { Triple(it.lineNumber, it.rawLine, it.reason.code) },
        )
        assertEquals(
            file,
            objects(pool.getJSONArray("kitWarnings")).map {
                val expected = it.getJSONObject("expected")
                listOf(
                    it.getString("code"),
                    expected.getInt("min"),
                    expected.getInt("max"),
                    it.getInt("actual"),
                    it.getString("message"),
                )
            },
            result.warnings.map {
                listOf(it.code.code, it.expected.first, it.expected.last, it.actual, it.message)
            },
        )
        val expectedMetrics = flatten(golden.getJSONObject("metrics"))
        val actualMetrics = MetricFlattener.flatten(success.metrics)
        assertEquals("$file: metric pointers", expectedMetrics.keys, actualMetrics.keys)
        expectedMetrics.forEach { (pointer, value) ->
            assertEquals("$file $pointer", value, actualMetrics.getValue(pointer), 0.0)
        }
    }

    private companion object {
        // The ticket's acceptance criterion 5 names this count.
        const val TRK_FIXTURES = 23

        fun objects(array: JSONArray): List<JSONObject> =
            (0 until array.length()).map { array.getJSONObject(it) }

        /**
         * The golden's metrics as numeric JSON pointers, by [MetricFlattener]'s documented rules,
         * written independently over org.json: objects recurse; a list that is empty or holds any
         * non-numeric leaf becomes `<pointer>/count`; other non-numeric leaves are dropped.
         */
        fun flatten(root: JSONObject): Map<String, Double> {
            val out = linkedMapOf<String, Double>()
            walk(root, "", out)
            return out
        }

        private fun walk(value: Any, pointer: String, out: MutableMap<String, Double>) {
            when (value) {
                is JSONObject ->
                    value.keys().forEach { key ->
                        walk(value.get(key), "$pointer/${escape(key)}", out)
                    }
                is JSONArray -> {
                    val items = (0 until value.length()).map { value.get(it) }
                    if (items.isNotEmpty() && items.all(::isNumeric)) {
                        items.forEachIndexed { at, item -> walk(item, "$pointer/$at", out) }
                    } else {
                        out["$pointer/${MetricFlattener.COUNT}"] = items.size.toDouble()
                    }
                }
                is Number -> out[pointer] = value.toDouble()
            }
        }

        private fun isNumeric(value: Any): Boolean =
            when (value) {
                is JSONObject -> value.keys().asSequence().all { isNumeric(value.get(it)) }
                is JSONArray -> (0 until value.length()).all { isNumeric(value.get(it)) }
                else -> value is Number
            }

        private fun escape(key: String) = key.replace("~", "~0").replace("/", "~1")
    }
}
