package spa.app.about

import android.content.Context
import java.time.OffsetDateTime
import java.time.ZoneOffset
import spa.app.data.SetData

/** What About shows of the build: the app's version and the bundled card data's snapshot date. */
data class AboutInfo(val version: String, val snapshotDate: String) {
    companion object {
        /** The bundled `fetched-at.txt` of the set the app reads, below the assets folder. */
        private const val FETCHED_AT = "${SetData.ASSET_DIR}/scryfall/${SetData.SET}/fetched-at.txt"

        /**
         * Reads the installed package's `versionName` and the bundled `fetched-at.txt`. The build
         * fails when that file is missing (SPA-015), so a missing asset here is a broken APK and
         * throws.
         */
        fun read(context: Context): AboutInfo {
            val app = context.applicationContext
            val version =
                checkNotNull(app.packageManager.getPackageInfo(app.packageName, 0).versionName) {
                    "the package has no versionName"
                }
            val fetchedAt = app.assets.open(FETCHED_AT).bufferedReader().use { it.readText() }
            return AboutInfo(version = version, snapshotDate = snapshotDateOf(fetchedAt))
        }
    }
}

/**
 * The date part (YYYY-MM-DD, UTC) of a `fetched-at.txt` timestamp such as `2026-11-03T18:19:53Z`.
 */
fun snapshotDateOf(fetchedAt: String): String =
    OffsetDateTime.parse(fetchedAt.trim())
        .atZoneSameInstant(ZoneOffset.UTC)
        .toLocalDate()
        .toString()
