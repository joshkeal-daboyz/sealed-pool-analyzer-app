package spa.agent.backtest

import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import spa.agent.grade.GradeEntry
import spa.agent.grade.GradeFile
import spa.agent.grade.GradeFileHeader
import spa.agent.grade.GradeSource
import spa.core.cards.Rarity

/** Criteria 1 and 2: the backtest's groups, missing cards, and printed lines. */
class BacktestTest {
    private fun entry(number: Int, score: Double, set: String = "tst") =
        GradeEntry("Card $number", number.toString(), set, score, 0.5)

    private fun file(source: GradeSource, provenance: String, vararg grades: GradeEntry) =
        GradeFile(
            GradeFileHeader("tst", source, provenance, "2026-11-04T00:00:00Z"),
            grades.toList(),
        )

    private fun model(vararg grades: GradeEntry) =
        file(GradeSource.MODEL, "claude-opus-5-5, prompt v1, 2026-11-04", *grades)

    private fun winRates(vararg grades: GradeEntry) =
        file(GradeSource.WIN_RATE, "17Lands public game data, Last-Modified 2026-07-27", *grades)

    /** Cards 1 to 9 are common, 10 to 19 uncommon, 20 to 29 rare, 30 and up mythic. */
    private fun rarity(set: String, number: String): Rarity? {
        val n = number.toIntOrNull() ?: return null
        return when {
            set != "tst" -> null
            n < 10 -> Rarity.COMMON
            n < 20 -> Rarity.UNCOMMON
            n < 30 -> Rarity.RARE
            else -> Rarity.MYTHIC
        }
    }

    private fun group(report: BacktestReport, group: BacktestGroup): GroupResult =
        report.groups.single { it.group == group }

    @Test
    fun `groups come in order and each counts the cards both files grade`() {
        // Commons 1 to 5 agree exactly; uncommons 10 to 14 are reversed. Rare 20 and mythic 30
        // are one card each, under the five-card minimum.
        val wr =
            winRates(
                entry(1, 1.0),
                entry(2, 2.0),
                entry(3, 3.0),
                entry(4, 4.0),
                entry(5, 5.0),
                entry(10, 1.0),
                entry(11, 2.0),
                entry(12, 3.0),
                entry(13, 4.0),
                entry(14, 5.0),
                entry(20, 2.0),
                entry(30, 3.0),
            )
        val m =
            model(
                entry(1, 0.5),
                entry(2, 1.0),
                entry(3, 1.5),
                entry(4, 2.0),
                entry(5, 2.5),
                entry(10, 5.0),
                entry(11, 4.0),
                entry(12, 3.0),
                entry(13, 2.0),
                entry(14, 1.0),
                entry(20, 4.0),
                entry(30, 1.0),
            )

        val report = Backtest.run(m, wr, ::rarity)

        assertEquals(BacktestGroup.entries.toList(), report.groups.map { it.group })
        assertEquals(1.0, group(report, BacktestGroup.COMMON).rho!!, 1e-12)
        assertEquals(5, group(report, BacktestGroup.COMMON).n)
        assertEquals(-1.0, group(report, BacktestGroup.UNCOMMON).rho!!, 1e-12)
        assertEquals(GroupResult(BacktestGroup.RARE, null, 1), group(report, BacktestGroup.RARE))
        assertEquals(
            GroupResult(BacktestGroup.MYTHIC, null, 1),
            group(report, BacktestGroup.MYTHIC),
        )
        assertEquals(12, group(report, BacktestGroup.ALL).n)
        assertEquals(emptyList(), report.missingFromModel)
    }

    @Test
    fun `a card missing from the model file is listed and left out of every group`() {
        // Win-rate scores 1..6 for cards 1..6; the model skips card 6 and grades 1..5 identically.
        val wr = winRates(*(1..6).map { entry(it, it.toDouble() / 2) }.toTypedArray())
        val m = model(*(1..5).map { entry(it, it.toDouble() / 2) }.toTypedArray())

        val report = Backtest.run(m, wr, ::rarity)

        assertEquals(listOf(entry(6, 3.0)), report.missingFromModel)
        assertEquals(GroupResult(BacktestGroup.ALL, 1.0, 5), group(report, BacktestGroup.ALL))
        assertEquals(GroupResult(BacktestGroup.COMMON, 1.0, 5), group(report, BacktestGroup.COMMON))
    }

    @Test
    fun `cards are matched by set code and collector number, and model-only cards are ignored`() {
        // The model grades card 1 of set "abc" (not graded by win rate) and card 99 (unknown).
        // Card 1 of "tst" is matched despite the different name and case of the set code.
        val wr = winRates(*(1..5).map { entry(it, it.toDouble()) }.toTypedArray())
        val m =
            model(
                GradeEntry("Another Name", "1", "TST", 1.0, 0.5),
                entry(2, 2.0),
                entry(3, 3.0),
                entry(4, 4.0),
                entry(5, 5.0),
                entry(1, 5.0, set = "abc"),
                entry(99, 0.0),
            )

        val report = Backtest.run(m, wr, ::rarity)

        assertEquals(GroupResult(BacktestGroup.ALL, 1.0, 5), group(report, BacktestGroup.ALL))
        assertEquals(emptyList(), report.missingFromModel)
    }

    @Test
    fun `a card of unknown rarity counts only in all`() {
        val wr =
            winRates(
                *(1..5).map { entry(it, it.toDouble()) }.toTypedArray(),
                entry(1, 0.0, set = "zzz"),
            )
        val m =
            model(
                *(1..5).map { entry(it, it.toDouble()) }.toTypedArray(),
                entry(1, 0.0, set = "zzz"),
            )

        val report = Backtest.run(m, wr, ::rarity)

        assertEquals(6, group(report, BacktestGroup.ALL).n)
        assertEquals(5, group(report, BacktestGroup.COMMON).n)
    }

    @Test
    fun `the lines give rho to three places, n, both provenances, then the missing cards`() {
        // Commons: win rate [1, 2, 2, 3, 4] ranks [1, 2.5, 2.5, 4, 5]; model [1, 3, 2, 4, 5]
        // ranks [1, 3, 2, 4, 5]. Means 3. Deviations [-2, -0.5, -0.5, 1, 2] and [-2, 0, -1, 1, 2]:
        // products 4 + 0 + 0.5 + 1 + 4 = 9.5, squares 9.5 and 10. rho = 9.5 / sqrt(95) = 0.975.
        val wr =
            winRates(
                entry(1, 1.0),
                entry(2, 2.0),
                entry(3, 2.0),
                entry(4, 3.0),
                entry(5, 4.0),
                entry(6, 4.5),
            )
        val m = model(entry(1, 1.0), entry(2, 3.0), entry(3, 2.0), entry(4, 4.0), entry(5, 5.0))

        val lines = Backtest.run(m, wr, ::rarity).lines()

        assertEquals(
            listOf(
                "all: rho 0.975, n 5",
                "common: rho 0.975, n 5",
                "uncommon: rho n/a, n 0",
                "rare: rho n/a, n 0",
                "mythic: rho n/a, n 0",
                "model: claude-opus-5-5, prompt v1, 2026-11-04",
                "winrates: 17Lands public game data, Last-Modified 2026-07-27",
                "missing from model: 1 card",
                "missing: Card 6 (tst 6)",
            ),
            lines,
        )
    }

    @Test
    fun `a constant list prints n-a even with five cards or more`() {
        val wr = winRates(*(1..6).map { entry(it, it.toDouble() / 2) }.toTypedArray())
        val m = model(*(1..6).map { entry(it, 2.5) }.toTypedArray())

        val report = Backtest.run(m, wr, ::rarity)

        assertEquals(GroupResult(BacktestGroup.ALL, null, 6), group(report, BacktestGroup.ALL))
        assertEquals("all: rho n/a, n 6", report.lines().first())
        assertEquals("missing from model: 0 cards", report.lines()[7])
    }

    @Test
    fun `a negative rho keeps its sign and two missing cards read cards`() {
        // Five commons reversed: model [2.5, 2, 1.5, 1, 0.5] against [0.5..2.5] is -1.
        val wr = winRates(*(1..7).map { entry(it, it.toDouble() / 2) }.toTypedArray())
        val m = model(*(1..5).map { entry(it, (6 - it).toDouble() / 2) }.toTypedArray())

        val lines = Backtest.run(m, wr, ::rarity).lines()

        assertEquals("all: rho -1.000, n 5", lines[0])
        assertEquals("missing from model: 2 cards", lines[7])
        assertEquals(listOf("missing: Card 6 (tst 6)", "missing: Card 7 (tst 7)"), lines.drop(8))
    }

    @Test
    fun `the win-rate side is ranked by gihwr when present`() {
        // Win-rate scores all 3.0 (constant), gihwr rises with the model's score: rho 1 by gihwr,
        // undefined by score.
        val wr =
            (1..5).map { GradeEntry("Card $it", "$it", "tst", 3.0, 0.5, 0.50 + it / 100.0, 900) }
        val m = (1..5).map { entry(it, it.toDouble()) }
        val report = Backtest.run(model(*m.toTypedArray()), winRates(*wr.toTypedArray()), ::rarity)
        assertEquals(1.0, group(report, BacktestGroup.ALL).rho!!, 1e-12)
    }

    @Test
    fun `a win-rate entry without gihwr is ranked by its score`() {
        // Cards 1 to 4 carry gihwr [0.51, 0.52, 0.53, 0.54] with falling scores [5, 4, 3, 2];
        // card 5 has no gihwr and is ranked by its score 3.0. Ranking values [0.51, 0.52, 0.53,
        // 0.54, 3.0] give ranks [1, 2, 3, 4, 5], equal to the model's, so rho is 1. Ranking by
        // score alone ([5, 4, 3, 2, 3]) would give a negative rho.
        val wr =
            (1..4).map {
                GradeEntry("Card $it", "$it", "tst", 6.0 - it, 0.5, 0.50 + it / 100.0, 900)
            } + entry(5, 3.0)
        val m = (1..5).map { entry(it, it.toDouble()) }
        val report = Backtest.run(model(*m.toTypedArray()), winRates(*wr.toTypedArray()), ::rarity)
        assertEquals(1.0, group(report, BacktestGroup.ALL).rho!!, 1e-12)
    }
}
