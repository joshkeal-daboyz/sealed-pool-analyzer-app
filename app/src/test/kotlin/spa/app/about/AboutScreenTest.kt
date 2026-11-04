package spa.app.about

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import spa.app.ClickableChecks

/**
 * Criteria 1 (order, A2, A6), 2 (B1, the README's words), 3 (B2, the Scryfall link), and 4 (the
 * version and the snapshot date, read from the build and the bundled `fetched-at.txt`).
 */
@RunWith(AndroidJUnit4::class)
class AboutScreenTest {
    @get:Rule val compose = createComposeRule()

    private var backs = 0

    @Before
    fun show() {
        shadowOf(RuntimeEnvironment.getApplication()).clearNextStartedActivities()
        compose.setContent { AboutKit.Screen(onBack = { backs++ }) }
        compose.waitForIdle()
    }

    private fun attributionNode(): SemanticsNodeInteraction =
        compose.onNode(hasText("Card data comes from", substring = true))

    private fun SemanticsNode.annotatedText(): AnnotatedString =
        config.getOrNull(SemanticsProperties.Text).orEmpty().single()

    private fun nextUrl(): Uri? {
        val next: Intent? = shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity
        if (next == null) return null
        assertEquals(Intent.ACTION_VIEW, next.action)
        return next.data
    }

    // Criterion 1.

    @Test
    fun showsNameVersionSnapshotHeadingAndAttributionInOrder() {
        val tops =
            listOf(
                    compose.onNode(hasText("Sealed Pool Analyzer")),
                    compose.onNode(hasText("Version ${AboutKit.versionName}")),
                    compose.onNode(hasText("Card data snapshot: ${AboutKit.snapshotDate}")),
                    compose.onNode(hasText("Card data") and isHeading()),
                    attributionNode(),
                )
                .map { it.fetchSemanticsNode().boundsInRoot.top }
        assertEquals(tops.sorted(), tops)
        assertEquals(tops.size, tops.toSet().size)
    }

    @Test
    fun theCardDataHeadingIsAHeading() {
        compose.onNode(hasText("Card data") and isHeading()).assertExists()
    }

    @Test
    fun theTopBarHasTheTitleAboutAndABackArrow() {
        compose.onNodeWithText("About").assertExists()
        compose.onNodeWithContentDescription("Back").assertHasClickAction().performClick()
        compose.waitForIdle()
        assertEquals(1, backs)
    }

    @Test
    fun theTopBarHasNoAboutIcon() {
        assertTrue(
            compose
                .onAllNodes(androidx.compose.ui.test.hasContentDescription("About"))
                .fetchSemanticsNodes()
                .isEmpty()
        )
    }

    // Criterion 2, B1.

    @Test
    fun theAttributionIsTheReadmesFirstTwoCardDataSentencesWordForWord() {
        val expected = AboutKit.attribution
        assertTrue(expected, expected.startsWith("Card data comes from Scryfall and"))
        assertTrue(expected, expected.endsWith("a trademark of Wizards of the Coast."))
        assertFalse(expected, expected.contains("fixtures/"))
        assertEquals(expected, attributionNode().fetchSemanticsNode().annotatedText().text)
    }

    @Test
    fun theReadmeParsingReducesLinksAndStopsAtTheSecondSentence() {
        val markdown =
            "# T\n\n## Card data\n\nFrom [Site](https://site.example) here. Two: yes.\n" +
                "Three. Four.\n\n## Next\n"
        val paragraph = AboutKit.cardDataParagraph(markdown)
        assertEquals("From Site here. Two: yes. Three. Four.", paragraph)
        assertEquals("From Site here. Two: yes.", AboutKit.firstTwoSentences(paragraph))
    }

    // Criterion 3, B2.

    @Test
    fun scryfallInTheFirstSentenceCarriesTheLink() {
        val text = attributionNode().fetchSemanticsNode().annotatedText()
        val links = text.getLinkAnnotations(0, text.length)
        assertEquals(1, links.size)
        val link = links.single()
        assertEquals(SCRYFALL_URL, (link.item as LinkAnnotation.Url).url)
        assertEquals("https://scryfall.com", SCRYFALL_URL)
        assertEquals("Scryfall", text.text.substring(link.start, link.end))
        assertEquals(text.text.indexOf("Scryfall"), link.start)
    }

    @Test
    fun tappingTheWordScryfallOpensScryfallInAViewIntent() {
        val node = attributionNode()
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        val start = layout.layoutInput.text.text.indexOf("Scryfall")
        val word = layout.getPathForRange(start, start + "Scryfall".length).getBounds()
        assertNull(nextUrl())
        node.performTouchInput { click(word.center) }
        compose.waitForIdle()
        assertEquals(Uri.parse(SCRYFALL_URL), nextUrl())
        assertNull(nextUrl())
    }

    @Test
    fun theWholeSentenceIsTheTargetAndMeetsA1() {
        val node = attributionNode()
        node
            .assertHasClickAction()
            .assertWidthIsAtLeast(ClickableChecks.MIN_TOUCH_TARGET)
            .assertHeightIsAtLeast(ClickableChecks.MIN_TOUCH_TARGET)
        // Away from the word, in the second sentence's last line.
        node.performTouchInput { click(Offset(width / 2f, height - 4.dp.toPx())) }
        compose.waitForIdle()
        assertEquals(Uri.parse(SCRYFALL_URL), nextUrl())
    }

    @Test
    fun theSentencesClickActionOpensScryfall() {
        attributionNode().performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertEquals(Uri.parse(SCRYFALL_URL), nextUrl())
    }

    // Criterion 4.

    @Test
    fun theVersionLineIsTheBuildsVersionName() {
        assertTrue(AboutKit.versionName, AboutKit.versionName.matches(Regex("""\d+\.\d+\.\d+""")))
        compose.onNodeWithText("Version ${AboutKit.versionName}").assertExists()
    }

    @Test
    fun theSnapshotLineIsTheDateOfTheBundledFetchedAt() {
        val date = AboutKit.snapshotDate
        assertTrue(date, date.matches(Regex("""\d{4}-\d{2}-\d{2}""")))
        compose.onNodeWithText("Card data snapshot: $date").assertExists()
    }

    @Test
    fun aboutInfoReadsTheBundledCopy() {
        val info = AboutInfo.read(RuntimeEnvironment.getApplication())
        assertNotNull(info)
        assertEquals(AboutInfo(AboutKit.versionName, AboutKit.snapshotDate), info)
    }

    @Test
    fun theSnapshotDateIsTheUtcDatePartOfTheTimestamp() {
        assertEquals("2026-11-03", snapshotDateOf("2026-11-03T18:19:53Z"))
        assertEquals("2026-11-03", snapshotDateOf("2026-11-03T18:19:53Z\n"))
        assertEquals("2026-11-04", snapshotDateOf("2026-11-03T23:30:00-02:00"))
    }
}
