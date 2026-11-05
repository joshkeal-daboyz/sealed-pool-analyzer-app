package spa.agent.deckpick

import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import spa.agent.deckpick.DeckPickTestCards.dawnCleric
import spa.agent.deckpick.DeckPickTestCards.duskPaladin
import spa.agent.deckpick.DeckPickTestCards.graveRat
import spa.agent.deckpick.DeckPickTestCards.ironGolem
import spa.agent.deckpick.DeckPickTestCards.resolve
import spa.agent.deckpick.DeckPickTestCards.skyMarshal
import spa.agent.deckpick.DeckPickTestCards.sunKnight
import spa.agent.deckpick.DeckPickTestCards.tideCaller
import spa.agent.poolgrade.PoolGradeTestCards
import spa.agent.poolgrade.PoolGradeTestCards.baseline
import spa.agent.poolgrade.PoolGradeTestCards.fillerOne
import spa.agent.poolgrade.PoolGradeTestCards.fillerThree
import spa.agent.poolgrade.PoolGradeTestCards.fillerTwo
import spa.agent.poolgrade.PoolGradeTestCards.flat
import spa.agent.poolgrade.PoolGradeTestCards.shuttleThree
import spa.agent.poolgrade.Weights
import spa.core.cards.Printing
import spa.core.metrics.ColorPair

/** Criterion 1: the set-grade and adjusted rankings, and the report's means. */
class DeckPickTest {
    private val eps = 1e-12

    private val scores =
        mapOf(
            skyMarshal to 4.0,
            sunKnight to 3.0,
            dawnCleric to 3.0,
            tideCaller to 2.5,
            ironGolem to 2.5,
            graveRat to 2.0,
            duskPaladin to 1.0,
        )

    private val setGrade: (Printing) -> Double? = { scores[it.card] }

    private val zero = Weights(synergy = 0.0, curve = 0.0, colour = 0.0, cap = 1.0)

    private fun small(): BuildSelection {
        val stream =
            requireNotNull(javaClass.getResourceAsStream("/spa/agent/deckpick/small-sealed.csv"))
        return stream.use { SealedBuilds.read(ByteArrayInputStream(it.readBytes()), resolve) }
    }

    @Test
    fun `the set-grade ranking ranks candidates by their grade`() {
        // d1/0: worked in OverlapTest, 0.7. d1/1 (WB, N = 4): Sun Knight (deck) and Dawn Cleric
        // (sideboard) 3.0; Iron Golem 2.5 (sideboard); Grave Rat 2.0 x2 (deck), 1 place left of
        // 2: 1 + 2 * 1/2 = 2, over 4 = 0.5.
        val (first, second) = small().builds

        assertEquals(0.7, DeckPick.setGradeOverlap(first, setGrade), eps)
        assertEquals(0.5, DeckPick.setGradeOverlap(second, setGrade), eps)
    }

    @Test
    fun `an ungraded candidate ranks below every graded one`() {
        // d1/0 with Sky Marshal ungraded and the rest 3.0: the 3.0 group is the other 7 copies,
        // 4 of them deck copies, for 5 places: 4 * 5/7, over 5 = 4/7.
        val build = small().builds.first()
        val grade: (Printing) -> Double? = { if (it.card == skyMarshal) null else 3.0 }

        assertEquals(4.0 / 7, DeckPick.setGradeOverlap(build, grade), eps)
    }

    @Test
    fun `the adjusted ranking uses the pool grader's adjusted grades for the build's pair`() {
        // Set xyz, WU, every set grade 2.0, only the colour rule at 0.5, and a baseline whose
        // means are 0: Filler One (W) and Filler Two (U) have 1 playable each against 0, so
        // r = 1 and both move to 2.5; the colourless Filler Three and Shuttle Three stay 2.0.
        // N = 2 (the two fillers in the deck): set grades tie all 4 copies, 2 * 2/4 over 2 = 0.5;
        // adjusted grades put both deck copies on top, 1.0.
        val p = PoolGradeTestCards::printing
        val build =
            Build(
                "x",
                "0",
                ColorPair.WU,
                listOf(
                    BuildCard(p(fillerOne), 1, 0),
                    BuildCard(p(fillerTwo), 1, 0),
                    BuildCard(p(fillerThree), 0, 1),
                    BuildCard(p(shuttleThree), 0, 1),
                ),
            )
        val colourOnly = Weights(synergy = 0.0, curve = 0.0, colour = 0.5, cap = 1.0)
        val grader = PoolGradeTestCards.grader(baseline(), colourOnly)

        assertEquals(0.5, DeckPick.setGradeOverlap(build, flat(2.0)), eps)
        assertEquals(1.0, DeckPick.adjustedOverlap(build, grader, flat(2.0)), eps)
    }

    @Test
    fun `the report gives the builds and each ranking's mean overlap`() {
        // Random: (5/8 + 4/6) / 2. Set grade: (0.7 + 0.5) / 2 = 0.6. With every weight 0 the
        // adjusted grade is the set grade, so the adjusted mean is 0.6 too and the difference 0.
        val selection = small()
        val grader = PoolGradeTestCards.grader(baseline(), zero)

        val report = DeckPick.measure(selection, setGrade, grader)

        assertEquals(2, report.used)
        assertEquals(selection.skipped, report.skipped)
        assertEquals((5.0 / 8 + 4.0 / 6) / 2, report.random, eps)
        assertEquals(0.6, report.setGrade, eps)
        assertEquals(0.6, report.adjusted, eps)
        assertEquals(0.0, report.difference, eps)
    }
}
