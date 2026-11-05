package spa.agent.deckpick

import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.GZIPOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import spa.agent.deckpick.DeckPickTestCards.coastalRuin
import spa.agent.deckpick.DeckPickTestCards.dawnCleric
import spa.agent.deckpick.DeckPickTestCards.duskPaladin
import spa.agent.deckpick.DeckPickTestCards.graveRat
import spa.agent.deckpick.DeckPickTestCards.ironGolem
import spa.agent.deckpick.DeckPickTestCards.plains
import spa.agent.deckpick.DeckPickTestCards.resolve
import spa.agent.deckpick.DeckPickTestCards.skyMarshal
import spa.agent.deckpick.DeckPickTestCards.sunKnight
import spa.agent.deckpick.DeckPickTestCards.tideCaller
import spa.core.cards.Card
import spa.core.metrics.ColorPair

/**
 * Criterion 1: build selection, candidates and N on the committed `small-sealed.csv`.
 *
 * Its rows (bucket, `main_colors`, and what decides the build):
 * ```
 * d1/0  0.64  WU   kept; two rows (two games), read once
 * d1/1  0.6   WB   kept; a second build of the same draft, bucket exactly 0.60
 * d2/0  0.58  WU   weak (it also has Bonus Reprint, but the bucket is checked first)
 * d3/0  blank WU   no bucket
 * d4/0  0.66  WUB  three colours (it also has Bonus Reprint; colours come first)
 * d5/0  0.62  WU   unresolved: Bonus Reprint x1 in the sideboard
 * d6/0  0.7   WU   no choice: every candidate copy (Sun Knight x2, Tide Caller) is in the deck
 * d7/0  0.7   W    one colour
 * d8/0  0.68  WU   no choice: N = 0 (the deck holds only B cards)
 * ```
 *
 * `drawn_Bonus Reprint` is 1 in d1/0's first row: only `deck_` and `sideboard_` columns count.
 */
class SealedBuildsTest {
    private fun bytes(): ByteArray =
        requireNotNull(javaClass.getResourceAsStream("/spa/agent/deckpick/small-sealed.csv")).use {
            it.readBytes()
        }

    private fun selection() = SealedBuilds.read(ByteArrayInputStream(bytes()), resolve)

    private fun Build.copies(card: Card): Pair<Int, Int> =
        cards.single { it.printing.card == card }.let { it.deck to it.sideboard }

    @Test
    fun `only the two strong two-colour fully resolved builds with a choice are kept`() {
        val builds = selection().builds

        assertEquals(listOf("d1" to "0", "d1" to "1"), builds.map { it.draftId to it.buildIndex })
        assertEquals(listOf(ColorPair.WU, ColorPair.WB), builds.map { it.pair })
    }

    @Test
    fun `each skipped build is counted under the first check it fails`() {
        val expected =
            mapOf(
                SkipReason.NO_BUCKET to 1,
                SkipReason.WEAK to 1,
                SkipReason.COLOURS to 2,
                SkipReason.UNRESOLVED to 1,
                SkipReason.NO_CHOICE to 2,
            )

        assertEquals(expected, selection().skipped)
    }

    @Test
    fun `a build holds every card with a deck or sideboard copy`() {
        val build = selection().builds.first()

        assertEquals(
            listOf(
                sunKnight,
                dawnCleric,
                tideCaller,
                graveRat,
                skyMarshal,
                duskPaladin,
                ironGolem,
                coastalRuin,
                plains,
            ),
            build.cards.map { it.printing.card },
        )
        assertEquals(2 to 0, build.copies(sunKnight))
        assertEquals(1 to 1, build.copies(tideCaller))
        assertEquals(8 to 0, build.copies(plains))
    }

    @Test
    fun `candidates leave out lands and off-colour cards and keep colourless ones`() {
        // d1/0, WU: Sun Knight 2+0, Dawn Cleric 0+1, Tide Caller 1+1, Sky Marshal 1+0 (WU),
        // Iron Golem 1+1 (colourless). Out: Grave Rat (B), Dusk Paladin (WB), Coastal Ruin and
        // Plains (lands).
        val build = selection().builds.first()

        assertEquals(
            listOf(sunKnight, dawnCleric, tideCaller, skyMarshal, ironGolem),
            build.candidates.map { it.printing.card },
        )
    }

    @Test
    fun `copies count separately in the candidates and in N`() {
        // d1/0: 2 + 1 + 2 + 1 + 2 = 8 candidate copies; deck copies 2 + 1 + 1 + 1 = 5.
        // d1/1, WB: Sun Knight 1+0, Dawn Cleric 0+1, Grave Rat 2+0, Dusk Paladin 1+0, Iron Golem
        // 0+1: 6 copies, N = 4. Tide Caller (U) is out.
        val (first, second) = selection().builds

        assertEquals(8, first.candidateCopies)
        assertEquals(5, first.n)
        assertEquals(6, second.candidateCopies)
        assertEquals(4, second.n)
    }

    @Test
    fun `the pool is every deck and sideboard copy but basic lands`() {
        val pool = selection().builds.first().pool()
        val counts = pool.entries.associate { it.printing.card.name to it.count }

        assertEquals(
            mapOf(
                "Sun Knight" to 2,
                "Dawn Cleric" to 1,
                "Tide Caller" to 2,
                "Grave Rat" to 2,
                "Sky Marshal, the Bold" to 1,
                "Dusk Paladin" to 1,
                "Iron Golem" to 2,
                "Coastal Ruin" to 1,
            ),
            counts,
        )
    }

    @Test
    fun `a gzipped file reads the same as the plain one`(@TempDir dir: Path) {
        val file = dir.resolve("small-sealed.csv.gz")
        GZIPOutputStream(Files.newOutputStream(file)).use { it.write(bytes()) }

        assertEquals(selection(), SealedBuilds.read(file, resolve))
    }
}
