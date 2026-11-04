package spa.app.importing

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import spa.app.data.TestFixtures
import spa.app.testing.TestImports
import spa.core.intake.PoolFormat

/**
 * SPA-022 re-plan 2: a successful import carries the exact text it parsed, so the pool file can
 * hold it without the file being read twice.
 */
class SuccessTextTest {
    private val importer = TestImports.poolImporter

    @Test
    fun aPastedImportCarriesThePastedText() = runTest {
        val text = TestFixtures.text("pools/trk-text-01.txt")

        val success = importer.import(text, null, PoolSource.Pasted) as ImportOutcome.Success

        assertEquals(text, success.text)
    }

    @Test
    fun anImportWithAChosenFormatCarriesItsText() = runTest {
        val text = TestFixtures.text("pools/trk-collector-01.txt")

        val success =
            importer.import(text, PoolFormat.COLLECTOR, PoolSource.Pasted) as ImportOutcome.Success

        assertEquals(text, success.text)
    }

    @Test
    fun aStreamedImportCarriesTheDecodedFileText() = runTest {
        val bytes = TestFixtures.text("pools/trk-manabox-01.csv").toByteArray(Charsets.UTF_8)
        var opened = 0

        val outcome =
            importer.importStream(
                {
                    opened++
                    bytes.inputStream()
                },
                PoolSource.File("trk-manabox-01.csv"),
            )

        val success = outcome as ImportOutcome.Success
        assertEquals(String(bytes, Charsets.UTF_8), success.text)
        assertEquals("the file is read once", 1, opened)
    }
}
