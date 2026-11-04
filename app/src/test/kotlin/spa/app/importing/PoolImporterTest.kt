package spa.app.importing

import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import spa.app.data.SetData
import spa.app.data.TestFixtures
import spa.core.intake.PoolFormat
import spa.core.intake.PoolParser
import spa.core.intake.UnresolvedReason

/** Plain JVM tests: [SetData] here reads `fixtures/` directly, the files the build bundles. */
class PoolImporterTest {
    private val importer = shared

    @Test
    fun detectIsPoolParsersDetection() {
        FIXTURES_BY_FORMAT.forEach { (file, format) ->
            val text = TestFixtures.text("pools/$file")
            assertEquals(PoolParser.detect(text), importer.detect(text))
            assertEquals(format, importer.detect(text))
        }
    }

    @Test
    fun importsAPoolWithTheDetectedFormatAndKeepsTheSource() = runTest {
        val source = PoolSource.File("trk-text-01.txt")

        val outcome = importer.import(TestFixtures.text("pools/trk-text-01.txt"), null, source)

        val success = outcome as ImportOutcome.Success
        assertEquals(PoolFormat.TEXT, success.format)
        assertEquals(PoolFormat.TEXT, success.result.format)
        assertEquals(source, success.source)
        assertTrue(success.result.pool.entries.isNotEmpty())
    }

    @Test
    fun importParsesExactlyAsAnalyzeDoes() = runTest {
        val text = TestFixtures.text("pools/trk-manabox-01.csv")
        val set = loaded()

        val success = importer.import(text, null, PoolSource.Pasted) as ImportOutcome.Success

        val expected = PoolParser.parse(text, set.index, null, set.brief.kitTemplate)
        assertEquals(expected, success.result)
        assertEquals(spa.core.metrics.MetricEngine.compute(expected.pool), success.metrics)
    }

    @Test
    fun aChosenFormatIsUsedInsteadOfDetection() = runTest {
        val text = TestFixtures.text("pools/trk-collector-01.txt")

        val outcome = importer.import(text, PoolFormat.TEXT, PoolSource.Pasted)

        val failure = outcome as ImportOutcome.Failure
        assertEquals(ImportFailureKind.NOT_A_POOL, failure.kind)
        assertTrue(failure.unresolved.all { it.reason == UnresolvedReason.UNKNOWN_NAME })
    }

    @Test
    fun aZeroByteFileIsEmpty() = runTest {
        val outcome = importer.importStream({ ByteArrayInputStream(ByteArray(0)) }, FILE)

        assertEquals(ImportOutcome.Failure(ImportFailureKind.EMPTY, FILE, emptyList()), outcome)
    }

    @Test
    fun blankAndSpaceOnlyLinesAreEmpty() = runTest {
        val text = listOf("", "   ", " ").joinToString("\n")

        val outcome = importer.import(text, null, PoolSource.Pasted)

        assertEquals(
            ImportOutcome.Failure(ImportFailureKind.EMPTY, PoolSource.Pasted, emptyList()),
            outcome,
        )
    }

    @Test
    fun aManaBoxHeaderAloneIsEmpty() = runTest {
        val header = TestFixtures.text("pools/trk-manabox-01.csv").lines().first()

        val outcome = importer.import(header, null, PoolSource.Pasted)

        assertEquals(
            ImportOutcome.Failure(ImportFailureKind.EMPTY, PoolSource.Pasted, emptyList()),
            outcome,
        )
    }

    @Test
    fun linesOnlyFromAnotherSetAreWrongSet() = runTest {
        val lines = TestFixtures.text("pools/trk-manabox-wrong-set-01.csv").lines()
        val text = listOf(lines[0], lines[4], lines[8]).joinToString("\n")

        val failure = importer.import(text, null, PoolSource.Pasted) as ImportOutcome.Failure

        assertEquals(ImportFailureKind.WRONG_SET, failure.kind)
        assertEquals(listOf(2, 3), failure.unresolved.map { it.lineNumber })
        assertEquals(listOf(lines[4], lines[8]), failure.unresolved.map { it.rawLine })
        assertTrue(failure.unresolved.all { it.reason == UnresolvedReason.WRONG_SET })
    }

    @Test
    fun proseIsNotAPool() = runTest {
        val failure = importer.import(PROSE, null, PoolSource.Pasted) as ImportOutcome.Failure

        assertEquals(ImportFailureKind.NOT_A_POOL, failure.kind)
        assertEquals((1..PROSE_LINES).toList(), failure.unresolved.map { it.lineNumber })
        assertTrue(failure.unresolved.all { it.reason == UnresolvedReason.UNKNOWN_NAME })
    }

    @Test
    fun aManaBoxHeaderMissingARequiredColumnIsNotAPool() = runTest {
        val outcome = importer.import("Name,Scryfall ID\nKirk,abc", null, PoolSource.Pasted)

        assertEquals(
            ImportOutcome.Failure(ImportFailureKind.NOT_A_POOL, PoolSource.Pasted, emptyList()),
            outcome,
        )
    }

    @Test
    fun theWrongSetFixtureWithSomeTrkCardsIsASuccess() = runTest {
        val text = TestFixtures.text("pools/trk-manabox-wrong-set-01.csv")

        val success = importer.import(text, null, FILE) as ImportOutcome.Success

        assertTrue(success.result.pool.entries.isNotEmpty())
        assertTrue(success.result.unresolved.isNotEmpty())
        assertTrue(success.result.unresolved.all { it.reason == UnresolvedReason.WRONG_SET })
    }

    @Test
    fun classifyFollowsTheSpecRules() {
        val set = loaded()
        val wrongSet = TestFixtures.text("pools/trk-manabox-wrong-set-01.csv").lines()
        // Line 5 again, claiming trk: its Scryfall id is not a trk card.
        val notTrk = wrongSet[4].replace(",TRC,", ",TRK,")
        val mixed = listOf(wrongSet[0], wrongSet[4], notTrk).joinToString("\n")

        val result = PoolParser.parse(mixed, set.index, null, set.brief.kitTemplate)

        assertEquals(0, result.pool.entries.size)
        assertEquals(UnresolvedReason.WRONG_SET, result.unresolved.first().reason)
        assertTrue(result.unresolved.any { it.reason != UnresolvedReason.WRONG_SET })
        assertEquals(ImportFailureKind.NOT_A_POOL, PoolImporter.classify(result))
    }

    @Test
    fun aValidStreamGivesTheSameSuccessAsItsText() = runTest {
        val file = TestFixtures.root.resolve("pools/trk-manabox-01.csv")
        val source = PoolSource.File(file.name)

        val fromStream = importer.importStream({ file.inputStream() }, source)

        assertEquals(
            importer.import(TestFixtures.text("pools/trk-manabox-01.csv"), null, source),
            fromStream,
        )
        assertTrue(fromStream is ImportOutcome.Success)
    }

    @Test
    fun malformedUtf8IsUnreadable() = runTest {
        val bytes = byteArrayOf(0xC3.toByte(), 0x28)

        val outcome = importer.importStream({ ByteArrayInputStream(bytes) }, FILE)

        assertEquals(
            ImportOutcome.Failure(ImportFailureKind.UNREADABLE, FILE, emptyList()),
            outcome,
        )
    }

    @Test
    fun aStreamThatThrowsIsUnreadable() = runTest {
        val opens = { throw IOException("permission revoked") }

        val outcome = importer.importStream(opens, FILE)

        assertEquals(
            ImportOutcome.Failure(ImportFailureKind.UNREADABLE, FILE, emptyList()),
            outcome,
        )
    }

    @Test
    fun aRevokedPermissionIsUnreadable() = runTest {
        val opens = { throw SecurityException("permission revoked") }

        val outcome = importer.importStream(opens, FILE)

        assertEquals(
            ImportOutcome.Failure(ImportFailureKind.UNREADABLE, FILE, emptyList()),
            outcome,
        )
    }

    @Test
    fun aStreamThatFailsWhileReadingIsUnreadable() = runTest {
        val failing =
            object : InputStream() {
                override fun read(): Int = throw IOException("disconnected")
            }

        val outcome = importer.importStream({ failing }, FILE)

        assertEquals(
            ImportOutcome.Failure(ImportFailureKind.UNREADABLE, FILE, emptyList()),
            outcome,
        )
    }

    @Test
    fun missingSetDataIsDataMissingNotAnException() = runTest {
        val empty =
            SetData(
                assets = { path -> throw FileNotFoundException(path) },
                root = Files.createTempDirectory("spa-empty").toFile().resolve("setdata"),
                stamp = "test",
            )
        val importer = PoolImporter(empty)

        val pasted = importer.import("1 Anything", null, PoolSource.Pasted)
        val streamed =
            importer.importStream({ ByteArrayInputStream("1 Anything".toByteArray()) }, FILE)

        assertTrue(pasted is ImportOutcome.DataMissing)
        assertTrue(streamed is ImportOutcome.DataMissing)
    }

    private fun loaded() = runBlocking { sharedData.load() }

    private companion object {
        val FILE = PoolSource.File("pool.csv")

        val FIXTURES_BY_FORMAT =
            listOf(
                "trk-manabox-01.csv" to PoolFormat.MANABOX,
                "trk-text-01.txt" to PoolFormat.TEXT,
                "trk-collector-01.txt" to PoolFormat.COLLECTOR,
            )

        const val PROSE_LINES = 10
        val PROSE =
            listOf(
                    "The morning fog rolled slowly over the quiet harbour.",
                    "Fishermen mended their nets beside the old stone wall.",
                    "A gull cried somewhere above the sleeping town.",
                    "Bread was baking in the shop at the end of the lane.",
                    "Children ran past with a kite that would not fly.",
                    "The church bell struck seven, then fell silent again.",
                    "Someone had left a bicycle leaning against the gate.",
                    "Clouds gathered in the west as the tide came in.",
                    "An old dog slept in a patch of thin sunlight.",
                    "By noon the fog had lifted and the boats were gone.",
                )
                .joinToString("\n")

        val sharedData: SetData by lazy {
            TestFixtures.setData(Files.createTempDirectory("spa-import").toFile())
        }
        val shared: PoolImporter by lazy { PoolImporter(sharedData) }
    }
}
