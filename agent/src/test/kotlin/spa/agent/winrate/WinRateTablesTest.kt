package spa.agent.winrate

import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import spa.agent.grade.GradeEntry
import spa.agent.grade.GradeFileHeader
import spa.agent.grade.GradeFiles
import spa.agent.grade.GradeSource
import spa.core.cards.Card
import spa.core.cards.CardIndex
import spa.core.cards.Face
import spa.core.cards.Printing
import spa.core.cards.Rarity

class WinRateTablesTest {
    private fun card(name: String, vararg faces: String): Card {
        val faceNames = if (faces.isEmpty()) listOf(name) else faces.toList()
        return Card(
            oracleId = "oracle-$name",
            name = name,
            layout = if (faces.isEmpty()) "normal" else "transform",
            faces = faceNames.map { Face(it, "{1}{G}", 2.0, emptySet(), "Creature", "", "2", "2") },
            colors = emptySet(),
            colorIdentity = emptySet(),
            manaValue = 2.0,
            typeLine = "Creature",
            isCreature = true,
            isLand = false,
            isBasicLand = false,
            rarity = Rarity.COMMON,
        )
    }

    private fun printing(card: Card, setCode: String, number: String) =
        Printing("id-$setCode-$number", setCode, number, Rarity.COMMON, emptyList(), card)

    private val alpha = card("Alpha")
    private val dfc = card("Front // Back", "Front", "Back")
    private val reprint = card("Reprint")
    private val few = card("Rare Few")

    // Alpha has a second, showcase printing; the grade names the main set's first printing.
    private val index =
        CardIndex(
            listOf("xyz", "cmp"),
            listOf(
                printing(alpha, "xyz", "1"),
                printing(dfc, "xyz", "2"),
                printing(few, "xyz", "3"),
                printing(alpha, "xyz", "301"),
                printing(reprint, "cmp", "7"),
            ),
        )

    private val data =
        GameDataSummary(
            games = 5000,
            cards =
                listOf(
                    CardWinRate("Alpha", games = 1000, wins = 600),
                    CardWinRate("Bonus Sheet Card", games = 2000, wins = 1800),
                    CardWinRate("Front", games = 1000, wins = 500),
                    CardWinRate("Rare Few", games = 499, wins = 400),
                    CardWinRate("Reprint", games = 600, wins = 330),
                ),
        )

    private val table =
        WinRateTables.build(
            "xyz",
            data,
            index,
            "17Lands public game data game_data_public.XYZ.PremierDraft.csv.gz, 2026-07-27",
            "2026-11-04T12:00:00Z",
        )

    @Test
    fun `the scale is over the matched cards with 500+ games only`() {
        // Alpha 0.60, Front 0.50, Reprint 0.55: m = 0.55,
        // s = sqrt((0.05^2 + 0.05^2 + 0) / 3) = sqrt(0.005 / 3) = 0.040825.
        // The unmatched 0.90 and the 499-game card are left out.
        val scale = requireNotNull(table.scale)
        assertEquals(0.55, scale.mean, 1e-12)
        assertEquals(sqrt(0.005 / 3), scale.sd, 1e-12)
    }

    @Test
    fun `each matched card with 500+ games gets one grade with its GIH WR and games`() {
        // Alpha: 2.5 + 0.05 / 0.040825 = 3.72 -> 3.5; confidence 1000 / 3000 = 0.333.
        // Front: 2.5 - 1.22 = 1.28 -> 1.5, under its full double-faced name.
        // Reprint: 2.5 + 0 = 2.5 in its companion set; confidence 600 / 2600 = 0.231.
        assertEquals(
            listOf(
                GradeEntry("Alpha", "1", "xyz", 3.5, 0.333, gihwr = 0.6, games = 1000),
                GradeEntry("Front // Back", "2", "xyz", 1.5, 0.333, gihwr = 0.5, games = 1000),
                GradeEntry("Reprint", "7", "cmp", 2.5, 0.231, gihwr = 0.55, games = 600),
            ),
            table.file.grades,
        )
    }

    @Test
    fun `the header names the set, WIN_RATE, the provenance and the time`() {
        assertEquals(
            GradeFileHeader(
                "xyz",
                GradeSource.WIN_RATE,
                "17Lands public game data game_data_public.XYZ.PremierDraft.csv.gz, 2026-07-27",
                "2026-11-04T12:00:00Z",
            ),
            table.file.header,
        )
    }

    @Test
    fun `unmatched names and cards under 500 games are reported, not graded`() {
        assertEquals(listOf("Bonus Sheet Card"), table.unmatched)
        assertEquals(listOf(CardWinRate("Rare Few", 499, 400)), table.ungraded)
    }

    @Test
    fun `the table is a valid grade file`() {
        assertEquals(table.file, GradeFiles.parse(GradeFiles.write(table.file)))
    }

    @Test
    fun `a set with no card at 500 games has an empty table and no scale`() {
        val thin = GameDataSummary(10, listOf(CardWinRate("Alpha", 10, 5)))
        val empty = WinRateTables.build("xyz", thin, index, "p", "2026-11-04T12:00:00Z")
        assertEquals(null, empty.scale)
        assertEquals(emptyList(), empty.file.grades)
        assertEquals(listOf(CardWinRate("Alpha", 10, 5)), empty.ungraded)
    }
}
