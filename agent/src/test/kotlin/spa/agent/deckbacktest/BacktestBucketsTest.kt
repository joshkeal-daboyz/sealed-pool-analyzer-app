package spa.agent.deckbacktest

import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import spa.agent.deckpick.DeckPickTestCards

/**
 * SPA-061 criterion 1: reading builds of every win rate bucket, with the bucket on each, on the
 * hand-built cards of [DeckPickTestCards]. The default minimum stays 0.60.
 */
class BacktestBucketsTest {
    private val names =
        listOf(
            "Sun Knight",
            "Tide Caller",
            "Grave Rat",
            "Iron Golem",
            "Coastal Ruin",
            "Plains",
            "Bonus Reprint",
        )

    private fun row(draft: String, bucket: String): String =
        (listOf("XYZ", "Sealed", draft, "0", "WU", "", "True") +
                names.flatMap { listOf(if (it == "Iron Golem") "2" else "0", "0") } +
                bucket)
            .joinToString(",")

    private fun csv(vararg rows: String): ByteArray {
        val fixed =
            listOf(
                "expansion",
                "event_type",
                "draft_id",
                "build_index",
                "main_colors",
                "splash_colors",
                "won",
            )
        val counts = names.flatMap { listOf("deck_$it", "sideboard_$it") }
        val header = (fixed + counts + "user_game_win_rate_bucket").joinToString(",")
        return (listOf(header) + rows).joinToString("\n").plus("\n").toByteArray()
    }

    private fun read(vararg rows: String, minBucket: Double? = null): BacktestSelection {
        val input = ByteArrayInputStream(csv(*rows))
        return if (minBucket == null) BacktestBuilds.read(input, DeckPickTestCards.resolve)
        else BacktestBuilds.read(input, DeckPickTestCards.resolve, minBucket)
    }

    @Test
    fun `a bucket of 0_30 is kept when the minimum is 0 and skipped at the default`() {
        val all = read(row("a", "0.30"), minBucket = 0.0)
        val strong = read(row("a", "0.30"))

        assertEquals(listOf("a"), all.builds.map { it.draftId })
        assertEquals(1, all.strong)
        assertEquals(emptyList(), strong.builds)
        assertEquals(0, strong.strong)
    }

    @Test
    fun `at the default 0_60 is kept and 0_59 is skipped`() {
        val selection = read(row("a", "0.60"), row("b", "0.59"))

        assertEquals(listOf("a"), selection.builds.map { it.draftId })
        assertEquals(1, selection.strong)
    }

    @Test
    fun `a blank bucket is skipped and counted, with either minimum`() {
        val rows = arrayOf(row("a", ""), row("b", "n/a"), row("c", "0.40"))

        val all = read(*rows, minBucket = 0.0)
        val strong = read(*rows)

        assertEquals(listOf("c"), all.builds.map { it.draftId })
        assertEquals(2, all.blank)
        assertEquals(1, all.strong)
        assertEquals(emptyList(), strong.builds)
        assertEquals(2, strong.blank)
    }

    @Test
    fun `the kept build carries its bucket`() {
        val selection = read(row("a", "0.30"), row("b", "0.88"), minBucket = 0.0)

        assertEquals(listOf(0.30, 0.88), selection.builds.map { it.bucket })
    }
}
