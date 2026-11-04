package spa.app.names

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import spa.app.results.kitWarningTitle
import spa.core.intake.KitWarningCode
import spa.core.intake.UnresolvedReason
import spa.core.metrics.ColorBucket
import spa.core.metrics.ColorPair
import spa.core.text.Color

/** The shared names of the spec's Component map, and its reason-words and kit-title tables. */
class NamesTest {
    @Test
    fun everyPairHasItsNameWithAnEnDashAndItsCode() {
        val expected =
            mapOf(
                ColorPair.WU to "White–Blue (WU)",
                ColorPair.WB to "White–Black (WB)",
                ColorPair.WR to "White–Red (WR)",
                ColorPair.WG to "White–Green (WG)",
                ColorPair.UB to "Blue–Black (UB)",
                ColorPair.UR to "Blue–Red (UR)",
                ColorPair.UG to "Blue–Green (UG)",
                ColorPair.BR to "Black–Red (BR)",
                ColorPair.BG to "Black–Green (BG)",
                ColorPair.RG to "Red–Green (RG)",
            )
        assertEquals(ColorPair.entries.toSet(), expected.keys)
        expected.forEach { (pair, name) -> assertEquals(name, PairNames.of(pair)) }
    }

    @Test
    fun everyBucketHasItsName() {
        val expected = listOf("White", "Blue", "Black", "Red", "Green", "Multicolour", "Colourless")
        assertEquals(expected, ColorBucket.entries.map(ColorNames::of))
    }

    @Test
    fun everyColourHasItsName() {
        assertEquals(
            listOf("White", "Blue", "Black", "Red", "Green"),
            Color.entries.map(ColorNames::of),
        )
    }

    @Test
    fun aColourListIsInWubrgOrderJoinedByCommas() {
        assertEquals("White, Red", ColorNames.list(setOf(Color.R, Color.W)))
        assertEquals("Green", ColorNames.list(listOf(Color.G)))
        assertEquals("", ColorNames.list(emptySet()))
    }

    @Test
    fun everyColourHasItsBasicLand() {
        assertEquals(
            listOf("Plains", "Island", "Swamp", "Mountain", "Forest"),
            Color.entries.map(BasicLandNames::of),
        )
    }

    @Test
    fun theTablesReasonsHaveWordsAndTheirCodeAfterThem() {
        assertEquals(
            "No trk or sds card has this name (unknown-name)",
            ReasonText.of(UnresolvedReason.UNKNOWN_NAME),
        )
        assertEquals(
            "No trk or sds card has this collector number (unknown-collector-number)",
            ReasonText.of(UnresolvedReason.UNKNOWN_COLLECTOR_NUMBER),
        )
        assertEquals(
            "This card is from another set (wrong-set)",
            ReasonText.of(UnresolvedReason.WRONG_SET),
        )
        assertEquals("This card is from another set", ReasonText.words(UnresolvedReason.WRONG_SET))
    }

    @Test
    fun aReasonTheTableDoesNotNameShowsItsCodeAlone() {
        val named =
            setOf(
                UnresolvedReason.UNKNOWN_NAME,
                UnresolvedReason.UNKNOWN_COLLECTOR_NUMBER,
                UnresolvedReason.WRONG_SET,
            )
        UnresolvedReason.entries
            .filterNot { it in named }
            .forEach { reason ->
                assertNull(reason.name, ReasonText.words(reason))
                assertEquals(reason.code, ReasonText.of(reason))
            }
    }

    @Test
    fun kitWarningTitlesFollowTheTableAndShowOtherCodesAlone() {
        assertEquals("Card count", kitWarningTitle(KitWarningCode.CARD_COUNT))
        assertEquals("Too many copies", kitWarningTitle(KitWarningCode.HIGH_COUNT))
        KitWarningCode.entries
            .filterNot { it == KitWarningCode.CARD_COUNT || it == KitWarningCode.HIGH_COUNT }
            .forEach { assertEquals(it.code, kitWarningTitle(it)) }
    }
}
