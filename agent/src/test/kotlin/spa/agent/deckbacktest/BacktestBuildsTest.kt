package spa.agent.deckbacktest

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import spa.agent.deckpick.DeckPickTestCards
import spa.core.text.Color

/**
 * SPA-046 criterion 1: reading a Sealed file of any colour count on the hand-built cards of
 * [DeckPickTestCards]. Every case is its own tiny file; the default build is two copies of Iron
 * Golem (a candidate in any colours) and eight Plains in the deck, one Iron Golem aside.
 */
class BacktestBuildsTest {
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

    private val golem = mapOf("Iron Golem" to 2, "Plains" to 8)

    @Suppress("LongParameterList")
    private fun row(
        draft: String = "d1",
        build: String = "0",
        main: String = "WU",
        splash: String = "",
        bucket: String = "0.65",
        deck: Map<String, Int> = golem,
        side: Map<String, Int> = mapOf("Iron Golem" to 1),
    ): String =
        (listOf("XYZ", "Sealed", draft, build, main, splash, "True") +
                names.flatMap { listOf("${deck[it] ?: 0}", "${side[it] ?: 0}") } +
                bucket)
            .joinToString(",")

    private fun csv(vararg rows: String): String {
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
        return (listOf(header) + rows).joinToString("\n") + "\n"
    }

    private fun read(vararg rows: String) = read(csv(*rows).toByteArray())

    private fun read(bytes: ByteArray) =
        BacktestBuilds.read(ByteArrayInputStream(bytes), DeckPickTestCards.resolve)

    private fun gzip(bytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(bytes) }
        return out.toByteArray()
    }

    private fun colours(text: String): Set<Color> = text.mapNotNull { Color.fromSymbol(it) }.toSet()

    @Test
    fun `a bucket of 0_60 is kept and 0_59 is skipped and not counted as strong`() {
        val selection = read(row(draft = "a", bucket = "0.60"), row(draft = "b", bucket = "0.59"))

        assertEquals(listOf("a"), selection.builds.map { it.draftId })
        assertEquals(1, selection.strong)
        assertEquals(BacktestSkip.entries.associateWith { 0 }, selection.skipped)
    }

    @Test
    fun `a blank or non-numeric bucket is not strong`() {
        val selection = read(row(draft = "a", bucket = ""), row(draft = "b", bucket = "n/a"))

        assertEquals(0, selection.strong)
        assertEquals(emptyList(), selection.builds)
    }

    @Test
    fun `a build is read once per draft and build index, from its first row`() {
        val selection =
            read(
                row(draft = "a", main = "WU", splash = "B"),
                row(draft = "a", main = "UB", splash = "R", deck = mapOf("Bonus Reprint" to 3)),
                row(draft = "a", build = "1", main = "WB"),
                row(draft = "a", build = "1", main = "WB"),
            )

        assertEquals(
            listOf("a" to "0", "a" to "1"),
            selection.builds.map { it.draftId to it.buildIndex },
        )
        assertEquals(2, selection.strong)
        val first = selection.builds.first()
        assertEquals(colours("WU"), first.main)
        assertEquals(colours("B"), first.splash)
        assertEquals(0, selection.skipped.getValue(BacktestSkip.UNRESOLVED))
    }

    @Test
    fun `the shapes are two, two plus a splash, and three or more main colours with or without`() {
        val selection =
            read(
                row(draft = "a", main = "WU"),
                row(draft = "b", main = "UW", splash = "B"),
                row(draft = "c", main = "WUB"),
                row(draft = "d", main = "WUB", splash = "R"),
                row(draft = "e", main = "WUBR"),
            )

        assertEquals(
            listOf(
                DeckShape.TWO,
                DeckShape.SPLASH,
                DeckShape.MULTI,
                DeckShape.MULTI,
                DeckShape.MULTI,
            ),
            selection.builds.map { it.shape },
        )
        assertEquals(
            mapOf(DeckShape.TWO to 1, DeckShape.SPLASH to 1, DeckShape.MULTI to 3),
            selection.shapes,
        )
    }

    @Test
    fun `a one-colour and a five-colour build are skipped, and still counted as strong`() {
        val selection = read(row(draft = "a", main = "W"), row(draft = "b", main = "WUBRG"))

        assertEquals(emptyList(), selection.builds)
        assertEquals(2, selection.strong)
        assertEquals(1, selection.skipped.getValue(BacktestSkip.ONE_COLOUR))
        assertEquals(1, selection.skipped.getValue(BacktestSkip.FIVE_COLOUR))
        assertEquals(DeckShape.entries.associateWith { 0 }, selection.shapes)
    }

    @Test
    fun `an unresolved name above 0 skips the build and a name at 0 does not`() {
        val selection =
            read(
                row(draft = "a", side = mapOf("Iron Golem" to 1, "Bonus Reprint" to 1)),
                row(draft = "b"),
            )

        assertEquals(listOf("b"), selection.builds.map { it.draftId })
        assertEquals(1, selection.skipped.getValue(BacktestSkip.UNRESOLVED))
        assertEquals(2, selection.shapes.getValue(DeckShape.TWO))
    }

    @Test
    fun `a build with no nonland card in its colours has no choice`() {
        val lands = mapOf("Plains" to 8, "Coastal Ruin" to 1)
        val selection =
            read(
                row(draft = "a", main = "UB", deck = lands, side = mapOf("Sun Knight" to 2)),
                row(
                    draft = "b",
                    main = "UB",
                    splash = "W",
                    deck = mapOf("Plains" to 8),
                    side = mapOf("Sun Knight" to 2),
                ),
            )

        assertEquals(listOf("b"), selection.builds.map { it.draftId })
        assertEquals(1, selection.skipped.getValue(BacktestSkip.NO_CHOICE))
    }

    @Test
    fun `a plain and a gzipped file read the same`() {
        val bytes =
            csv(row(draft = "a"), row(draft = "b", main = "WUB"), row(draft = "c", main = "W"))
                .toByteArray()

        val plain = read(bytes)
        val zipped = read(gzip(bytes))

        assertEquals(plain.builds, zipped.builds)
        assertEquals(plain.summary(), zipped.summary())
        assertEquals(2, plain.builds.size)
    }

    @Test
    fun `the read line gives the strong total, the shapes and the skips`() {
        val selection =
            read(
                row(draft = "a"),
                row(draft = "b", splash = "R"),
                row(draft = "c", main = "WUB"),
                row(draft = "d", main = "W"),
                row(draft = "e", main = "WUBRG"),
                row(draft = "f", side = mapOf("Bonus Reprint" to 1)),
                row(draft = "g", deck = mapOf("Plains" to 8), side = emptyMap()),
                row(draft = "h", bucket = "0.10"),
            )

        assertEquals(
            "read 7 strong builds: two 3, splash 1, multi 1; " +
                "skipped one-colour 1, five-colour 1, unresolved 1, no-choice 1",
            selection.summary(),
        )
    }

    @Test
    fun `a limit takes builds spread evenly, build i at position i times kept over n`() {
        val selection = read(*(0 until 10).map { row(draft = "d$it") }.toTypedArray())

        fun ids(limit: Int) = selection.limited(limit).map { it.draftId.removePrefix("d").toInt() }

        assertEquals(listOf(0, 3, 6), ids(3))
        assertEquals(listOf(0), ids(1))
        assertEquals((0 until 10).toList(), ids(10))
        assertEquals((0 until 10).toList(), ids(99))
        assertEquals(ids(4), ids(4))
    }

    @Test
    fun `the player is the registered deck copies, and the pool all copies but basics`() {
        val selection =
            read(
                row(
                    main = "WU",
                    splash = "B",
                    deck =
                        mapOf(
                            "Iron Golem" to 3,
                            "Sun Knight" to 2,
                            "Plains" to 7,
                            "Coastal Ruin" to 1,
                        ),
                    side = mapOf("Iron Golem" to 1, "Tide Caller" to 4, "Plains" to 2),
                )
            )
        val build = selection.builds.single()

        assertEquals(
            mapOf("oracle-Iron Golem" to 3, "oracle-Sun Knight" to 2),
            build.player.spells,
        )
        assertEquals(8, build.player.lands)
        assertEquals(colours("WU"), build.player.main)
        assertEquals(colours("B"), build.player.splash)
        val pool = build.pool().entries.associate { it.printing.card.name to it.count }
        assertEquals(
            mapOf("Iron Golem" to 4, "Sun Knight" to 2, "Tide Caller" to 4, "Coastal Ruin" to 1),
            pool,
        )
    }

    @Test
    fun `the configuration is the main and splash colours when they can be one`() {
        val config = assertNotNull(read(row(main = "WU", splash = "BR")).builds.single().config)

        assertEquals("WU/BR", "$config")
        assertNull(read(row(main = "WU", splash = "UB")).builds.single().config)
        assertNull(read(row(main = "WU", splash = "BRG")).builds.single().config)
    }
}
