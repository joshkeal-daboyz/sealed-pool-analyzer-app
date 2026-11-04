package spa.app.testing

import org.json.JSONObject
import spa.app.data.TestFixtures

/** Header values read from `fixtures/golden/<file>.json`, what `spa analyze` wrote for a pool. */
object Goldens {
    data class Header(val format: String, val totalCards: Int, val entries: Int, val skipped: Int)

    fun header(file: String): Header {
        val golden = JSONObject(TestFixtures.text("golden/$file.json"))
        val pool = golden.getJSONObject("pool")
        return Header(
            format = pool.getString("format"),
            totalCards = golden.getJSONObject("metrics").getInt("totalCards"),
            entries = pool.getInt("entries"),
            skipped = pool.getInt("skippedLines"),
        )
    }

    /** The spec's format names, by the golden's format code. */
    val FORMAT_NAMES =
        mapOf(
            "MANABOX" to "ManaBox CSV",
            "TEXT" to "List with counts",
            "COLLECTOR" to "Collector numbers",
        )
}
