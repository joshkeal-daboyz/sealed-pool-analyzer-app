package spa.app.store

import android.content.pm.ApplicationInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.xmlpull.v1.XmlPullParser
import spa.app.R

/**
 * Criterion 5: `data_extraction_rules.xml` excludes every domain the app has from both cloud backup
 * and device-to-device transfer, and the app does not allow backup. The merged manifest's
 * `dataExtractionRules` attribute is the Acceptance Tester's `aapt2 dump xmltree`.
 */
@RunWith(AndroidJUnit4::class)
class BackupRulesTest {
    private val context
        get() = RuntimeEnvironment.getApplication()

    /** Each section's tag and attributes, as (section, tag, domain). */
    private fun rules(): List<Triple<String, String, String?>> {
        val parser = context.resources.getXml(R.xml.data_extraction_rules)
        val rules = mutableListOf<Triple<String, String, String?>>()
        var section: String? = null
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name) {
                "data-extraction-rules" -> Unit
                "cloud-backup",
                "device-transfer" -> section = parser.name
                else ->
                    rules +=
                        Triple(
                            checkNotNull(section) { "${parser.name} outside a section" },
                            parser.name,
                            parser.getAttributeValue(null, "domain"),
                        )
            }
        }
        return rules
    }

    private fun excluded(section: String): Set<String?> =
        rules().filter { it.first == section && it.second == "exclude" }.map { it.third }.toSet()

    @Test
    fun cloudBackupExcludesEveryDomain() {
        assertTrue(excluded("cloud-backup").containsAll(DOMAINS))
    }

    @Test
    fun deviceTransferExcludesEveryDomain() {
        assertTrue(excluded("device-transfer").containsAll(DOMAINS))
    }

    @Test
    fun nothingIsIncluded() {
        val rules = rules()
        assertTrue(rules.isNotEmpty())
        assertEquals(listOf("exclude"), rules.map { it.second }.distinct())
    }

    @Test
    fun theAppDoesNotAllowBackup() {
        assertEquals(0, context.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
    }

    private companion object {
        /** Every domain the rules format has, the four the ticket names first. */
        val DOMAINS =
            setOf(
                "file",
                "database",
                "sharedpref",
                "root",
                "external",
                "device_file",
                "device_database",
                "device_sharedpref",
                "device_root",
            )
    }
}
