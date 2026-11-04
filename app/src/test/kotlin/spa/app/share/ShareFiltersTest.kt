package spa.app.share

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import spa.app.MainActivity

/**
 * Criterion 1 from the merged manifest: `MainActivity` is a share target and an "Open with" handler
 * for the three CSV types, and for nothing else. The Acceptance Tester checks the APK with aapt2.
 */
@RunWith(AndroidJUnit4::class)
class ShareFiltersTest {
    private val context = RuntimeEnvironment.getApplication()
    private val uri = Uri.parse("content://spa.app.test.shared/doc/pool.csv")

    private fun handlers(intent: Intent): List<String> =
        context.packageManager
            .queryIntentActivities(intent.setPackage(context.packageName), PackageManager.MATCH_ALL)
            .map { it.activityInfo.name }

    @Test
    fun sendAndViewResolveToMainActivityForEachCsvType() {
        CSV_TYPES.forEach { type ->
            val send = Intent(Intent.ACTION_SEND).setType(type).addCategory(Intent.CATEGORY_DEFAULT)
            val view =
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, type)
                    .addCategory(Intent.CATEGORY_DEFAULT)
            assertEquals(type, listOf(MainActivity::class.java.name), handlers(send))
            assertEquals(type, listOf(MainActivity::class.java.name), handlers(view))
        }
    }

    @Test
    fun otherTypesDoNotResolve() {
        listOf("text/plain", "image/png", "application/json").forEach { type ->
            val send = Intent(Intent.ACTION_SEND).setType(type)
            val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, type)
            assertTrue(type, handlers(send).isEmpty())
            assertTrue(type, handlers(view).isEmpty())
        }
    }

    private companion object {
        val CSV_TYPES = listOf("text/csv", "text/comma-separated-values", "application/csv")
    }
}
