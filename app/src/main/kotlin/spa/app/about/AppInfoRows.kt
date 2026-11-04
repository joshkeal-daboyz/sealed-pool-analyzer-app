package spa.app.about

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** What About shows of the build: the app's version and the bundled card data's snapshot date. */
data class AboutInfo(val version: String, val snapshotDate: String) {
    companion object {
        /** Reads the installed package's `versionName` and the bundled `fetched-at.txt`. */
        fun read(context: Context): AboutInfo = TODO()
    }
}

/**
 * The date part (YYYY-MM-DD, UTC) of a `fetched-at.txt` timestamp such as `2026-11-03T18:19:53Z`.
 */
fun snapshotDateOf(fetchedAt: String): String = TODO()

/** The app's name, "Version <version>", and "Card data snapshot: <date>". */
@Composable fun AppInfoRows(info: AboutInfo, modifier: Modifier = Modifier): Unit = TODO()
