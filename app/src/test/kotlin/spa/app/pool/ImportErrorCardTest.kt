package spa.app.pool

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import spa.app.Importer
import spa.app.importing.ImportFailureKind
import spa.app.importing.ImportOutcome
import spa.app.importing.PoolSource
import spa.app.testing.TestImports
import spa.core.intake.UnresolvedLine

/**
 * SPA-017 criterion 4 and P4: one test per error card, driven through the ViewModel and the app's
 * import paths with the inputs built here. Each card is above the inputs and announced politely.
 */
@RunWith(AndroidJUnit4::class)
class ImportErrorCardTest {
    @get:Rule val compose = createComposeRule()

    private val app = PoolApp(compose)

    @Before fun setUp() = TestDocuments.install()

    private fun count(text: String) =
        compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().size

    /** The card titled [title]: polite live region, shown above "Open file". */
    private fun assertCard(title: String, body: String) {
        app.waitForText(title)
        val card =
            compose.onNode(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.LiveRegion,
                    LiveRegionMode.Polite,
                ) and hasAnyDescendant(hasText(title))
            )
        card.assertIsDisplayed()
        compose.onNodeWithText(body).assertIsDisplayed()
        val cardBottom = card.fetchSemanticsNode().boundsInRoot.bottom
        val openTop = compose.onNodeWithText("Open file").fetchSemanticsNode().boundsInRoot.top
        assertTrue("card bottom $cardBottom is below Open file $openTop", cardBottom <= openTop)
    }

    /** The spec's reason words, with the code after them. */
    private fun reasonText(line: UnresolvedLine): String {
        val words =
            when (line.reason.code) {
                "unknown-name" -> "No trk or sds card has this name"
                "unknown-collector-number" -> "No trk or sds card has this collector number"
                "wrong-set" -> "This card is from another set"
                else -> return line.reason.code
            }
        return "$words (${line.reason.code})"
    }

    private fun assertLineShown(line: UnresolvedLine) {
        compose.onNodeWithText("Line ${line.lineNumber}").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(line.rawLine).performScrollTo().assertIsDisplayed()
        assertTrue(count(reasonText(line)) > 0)
    }

    private fun analyzePasted(text: String) {
        compose.pasteBox().performTextReplacement(text)
        compose.onNodeWithText("Analyze pool").performClick()
        compose.waitForIdle()
    }

    @Test
    fun emptyFile() {
        app.launch()
        app.pick(TestDocuments.put("empty.csv", ByteArray(0)))
        assertCard("This file is empty", "empty.csv has no cards in it.")
    }

    @Test
    fun notAPoolShowsThreeLinesThenAllTen() {
        val lines = runBlocking {
            val outcome = TestImports.poolImporter.import(PROSE_TEXT, null, PoolSource.Pasted)
            (outcome as ImportOutcome.Failure).also {
                assertEquals(ImportFailureKind.NOT_A_POOL, it.kind)
            }
        }
            .unresolved
        assertEquals(10, lines.size)

        app.launch()
        analyzePasted(PROSE_TEXT)
        assertCard(
            "This doesn't look like a pool",
            "None of the 10 lines in the pasted text is a trk card.",
        )
        lines.take(3).forEach(::assertLineShown)
        assertEquals(0, count("Line 4"))

        compose.onNodeWithText("Show all 10 lines").performScrollTo().performClick()
        compose.waitForIdle()
        lines.forEach(::assertLineShown)
        assertEquals(0, count("Show all"))
    }

    @Test
    fun notAPoolWithNoLines() {
        app.launch()
        // A ManaBox header without its Quantity and Name columns: no line can be read.
        val csv =
            "Set code,Collector number,Scryfall ID\nTRK,95,c567253e-a2ee-45de-b3c6-3aeafbf8237b\n"
        app.pick(TestDocuments.put("no-columns.csv", csv.toByteArray()))
        assertCard(
            "This doesn't look like a pool",
            "no-columns.csv isn't a ManaBox CSV, a list with counts, or a collector-number list.",
        )
        assertEquals(0, count("Line "))
        assertEquals(0, count("Show all"))
    }

    @Test
    fun wrongSet() {
        val fixture = TestImports.pool("trk-manabox-wrong-set-01.csv").lines()
        val csv = listOf(fixture[0], fixture[4], fixture[8]).joinToString("\n")
        app.launch()
        app.pick(TestDocuments.put("wrong-set.csv", csv.toByteArray()))
        assertCard(
            "This pool is from another set",
            "Every card in wrong-set.csv is from a set other than trk. This app reads Star Trek " +
                "(trk) and its bonus sheet (sds) only.",
        )
        listOf("Line 2", fixture[4], "Line 3", fixture[8]).forEach {
            compose.onNodeWithText(it).performScrollTo().assertIsDisplayed()
        }
        assertEquals(2, count("This card is from another set (wrong-set)"))
        assertEquals(0, count("Show all"))
    }

    @Test
    fun cantReadBytesThatAreNotUtf8() {
        app.launch()
        app.pick(TestDocuments.put("binary.csv", PNG_BYTES))
        assertCard(
            "Couldn't read this file",
            "binary.csv couldn't be opened as text. Try exporting it again, or paste its contents.",
        )
    }

    @Test
    fun cantReadAStreamThatThrows() {
        app.launch()
        app.pick(TestDocuments.throwing("gone.csv"))
        assertCard(
            "Couldn't read this file",
            "gone.csv couldn't be opened as text. Try exporting it again, or paste its contents.",
        )
    }

    @Test
    fun cardDataMissingDisablesEveryInput() {
        app.launch(
            app.realViewModel(Importer { _, _, _ -> ImportOutcome.DataMissing(Exception("gone")) })
        )
        analyzePasted("TRK 1701d")
        assertCard(
            "Card data is missing",
            "The app's trk card data didn't load. Reinstall the app.",
        )
        compose.onNodeWithText("Open file").assertIsNotEnabled()
        // A disabled field has no SetText action, so it is found by its editable text.
        compose
            .onNode(
                SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText) and
                    hasText("Paste your pool")
            )
            .assertIsNotEnabled()
        listOf("Auto", "ManaBox CSV", "List with counts", "Collector numbers").forEach {
            compose.onNodeWithText(it).assertIsNotEnabled()
        }
        compose.onNodeWithText("Analyze pool").assertIsNotEnabled()
    }

    private companion object {
        /** The first bytes of a PNG file: 0x89 is not valid UTF-8. */
        val PNG_BYTES =
            byteArrayOf(
                0x89.toByte(),
                0x50,
                0x4E,
                0x47,
                0x0D,
                0x0A,
                0x1A,
                0x0A,
                0x00,
                0x00,
                0x00,
                0x0D,
                0x49,
                0x48,
                0x44,
                0x52,
            )
    }
}
