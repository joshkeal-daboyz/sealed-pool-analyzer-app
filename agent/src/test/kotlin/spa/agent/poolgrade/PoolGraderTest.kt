package spa.agent.poolgrade

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import spa.agent.poolgrade.PoolGradeTestCards.admiral
import spa.agent.poolgrade.PoolGradeTestCards.baseline
import spa.agent.poolgrade.PoolGradeTestCards.beacon
import spa.agent.poolgrade.PoolGradeTestCards.card
import spa.agent.poolgrade.PoolGradeTestCards.dockingBay
import spa.agent.poolgrade.PoolGradeTestCards.fillerOne
import spa.agent.poolgrade.PoolGradeTestCards.fillerThree
import spa.agent.poolgrade.PoolGradeTestCards.fillerTwo
import spa.agent.poolgrade.PoolGradeTestCards.flat
import spa.agent.poolgrade.PoolGradeTestCards.grader
import spa.agent.poolgrade.PoolGradeTestCards.of
import spa.agent.poolgrade.PoolGradeTestCards.pool
import spa.agent.poolgrade.PoolGradeTestCards.shuttleOne
import spa.agent.poolgrade.PoolGradeTestCards.shuttleThree
import spa.agent.poolgrade.PoolGradeTestCards.shuttleTwo
import spa.core.cards.Pool
import spa.core.metrics.ColorPair
import spa.core.text.Color

/**
 * Criterion 1: each rule alone and together, on hand-built pools of set `xyz` (see
 * [PoolGradeTestCards]), pair WU. Every expected value is worked by hand from the rules in
 * SPA-038's Background; the working is in each test's comment.
 */
class PoolGraderTest {
    private val eps = 1e-9

    private fun wu(pool: Pool, grader: PoolGrader = grader(), grade: Double = 2.0) =
        grader.grade(pool, ColorPair.WU, flat(grade))

    // Synergy. Docking Bay refers to Spacecraft: its partners have Spacecraft (the three
    // shuttles), so its q is 3 of the 9 other WU nonland set cards.

    @Test
    fun `a payoff that refers to a theme counts the copies that have it`() {
        // 9 candidates; partners: Shuttle One x2, Two, Three = 4; e = 1/3 * 8 = 8/3.
        // (4 - 8/3) / (8/3) = 0.5; synergy = 0.5 * 0.5 = 0.25.
        val grades =
            wu(
                pool(
                    dockingBay to 1,
                    shuttleOne to 2,
                    shuttleTwo to 1,
                    shuttleThree to 1,
                    fillerOne to 4,
                )
            )
        val synergy = grades.of(dockingBay).synergy

        assertEquals(4, synergy.partners)
        assertEquals(8.0 / 3, synergy.expected, eps)
        assertEquals(0.25, synergy.value, eps)
        assertEquals(listOf("Spacecraft"), synergy.themes)
    }

    @Test
    fun `another card that only refers to the same theme is not a partner of a payoff`() {
        // Beacon refers to Spacecraft, as Docking Bay does: no partner. 8 candidates, n = 0,
        // e = 1/3 * 7 = 7/3; (0 - 7/3) / (7/3) = -1; synergy = -0.5.
        val synergy = wu(pool(dockingBay to 1, beacon to 1, fillerOne to 6)).of(dockingBay).synergy

        assertEquals(0, synergy.partners)
        assertEquals(7.0 / 3, synergy.expected, eps)
        assertEquals(-0.5, synergy.value, eps)
    }

    @Test
    fun `a card that has a theme counts copies that have or refer to it, and its own second copy`() {
        // Shuttle One has Spacecraft; its q is 4 of 9 (Docking Bay, Shuttle Two, Shuttle Three,
        // Beacon). 9 candidates; partners: its other copy, Docking Bay, Beacon = 3.
        // e = 4/9 * 8 = 32/9; (3 - 32/9) / (32/9) = -5/32; synergy = 0.5 * -5/32 = -0.078125.
        val synergy =
            wu(pool(shuttleOne to 2, dockingBay to 1, beacon to 1, fillerOne to 5))
                .of(shuttleOne)
                .synergy

        assertEquals(3, synergy.partners)
        assertEquals(32.0 / 9, synergy.expected, eps)
        assertEquals(-0.078125, synergy.value, eps)
    }

    @Test
    fun `a card with no theme has no synergy`() {
        val grades = wu(pool(fillerOne to 3, shuttleOne to 4, admiral to 1))

        listOf(fillerOne, admiral).forEach {
            val synergy = grades.of(it).synergy
            assertEquals(0.0, synergy.value)
            assertEquals(emptyList(), synergy.themes)
        }
    }

    @Test
    fun `an expectation below 1 divides by 1`() {
        // 3 candidates; n = 1 (Shuttle One); e = 1/3 * 2 = 2/3; (1 - 2/3) / max(2/3, 1) = 1/3.
        // synergy = 0.5 / 3. Dividing by e would give 0.25.
        val synergy =
            wu(pool(dockingBay to 1, shuttleOne to 1, fillerOne to 1)).of(dockingBay).synergy

        assertEquals(1, synergy.partners)
        assertEquals(2.0 / 3, synergy.expected, eps)
        assertEquals(0.5 / 3, synergy.value, eps)
    }

    @Test
    fun `an expectation below 1 with no partner gives minus a third of the weight`() {
        // 2 candidates; n = 0; e = 1/3; (0 - 1/3) / 1 = -1/3; synergy = -0.5 / 3.
        val synergy = wu(pool(dockingBay to 1, fillerOne to 1)).of(dockingBay).synergy

        assertEquals(-0.5 / 3, synergy.value, eps)
    }

    @Test
    fun `synergy is clamped to the weight`() {
        // 6 candidates; n = 5; e = 1/3 * 5 = 5/3; (5 - 5/3) / (5/3) = 2, clamped to 1.
        val synergy = wu(pool(dockingBay to 1, shuttleOne to 5)).of(dockingBay).synergy

        assertEquals(5, synergy.partners)
        assertEquals(0.5, synergy.value, eps)
    }

    @Test
    fun `cards outside the pair are not candidates and do not count as partners`() {
        // Red Ship has Spacecraft but is red: not a WU candidate. 2 candidates; n = 0.
        val red = PoolGradeTestCards.redShip
        val grades = wu(pool(dockingBay to 1, red to 3, fillerOne to 1))

        assertEquals(0, grades.of(dockingBay).synergy.partners)
        assertEquals(2, grades.copies)
        assertTrue(grades.cards.none { it.printing.card == red })
    }

    // Curve. Colourless cards with no theme, so only the curve moves.

    private val gear = card("Gear", mv = 2)
    private val bot = card("Bot", mv = 2, creature = true)
    private val colossus = card("Colossus", mv = 7, creature = true)
    private val titan = card("Titan", mv = 9, creature = true)

    private val curveBaseline =
        baseline(
            mapOf(
                "/pairs/WU/curve/buckets/2/creatures" to 2.0,
                "/pairs/WU/curve/buckets/2/noncreatures" to 1.0,
                "/pairs/WU/curve/buckets/7/creatures" to 1.5,
                "/pairs/WU/curve/buckets/7/noncreatures" to 0.5,
            )
        )

    private fun curveOf(pool: Pool, card: spa.core.cards.Card) =
        wu(pool, grader(curveBaseline)).of(card).curve

    @Test
    fun `a short bucket raises the card`() {
        // k = 1, m = 3: (3 - 1) / 3 = 2/3; curve = 0.25 * 2/3.
        val curve = curveOf(pool(gear to 1), gear)

        assertEquals(2, curve.manaValue)
        assertEquals(1, curve.count)
        assertEquals(3.0, curve.typical, eps)
        assertEquals(0.25 * 2 / 3, curve.value, eps)
    }

    @Test
    fun `an even bucket, creatures and noncreatures together, leaves it`() {
        // k = 2 noncreatures + 1 creature = 3 = m.
        val curve = curveOf(pool(gear to 2, bot to 1), gear)

        assertEquals(3, curve.count)
        assertEquals(0.0, curve.value, eps)
    }

    @Test
    fun `a crowded bucket lowers the card, down to the weight`() {
        // k = 4: (3 - 4) / 3 = -1/3. k = 9: (3 - 9) / 3 = -2, clamped to -1.
        assertEquals(-0.25 / 3, curveOf(pool(gear to 4), gear).value, eps)
        assertEquals(-0.25, curveOf(pool(gear to 9), gear).value, eps)
    }

    @Test
    fun `mana value 7 or more shares the top bucket`() {
        // Titan (9) and Colossus (7) are both in bucket 7: k = 3, m = 2; (2 - 3) / 2 = -0.5.
        val curve = curveOf(pool(titan to 1, colossus to 2), titan)

        assertEquals(7, curve.manaValue)
        assertEquals(3, curve.count)
        assertEquals(2.0, curve.typical, eps)
        assertEquals(-0.125, curve.value, eps)
    }

    @Test
    fun `a typical count below 1 divides by 1`() {
        // Bucket 3's baseline is 0: k = 1; (0 - 1) / max(0, 1) = -1; curve = -0.25.
        val three = card("Three", mv = 3)
        assertEquals(-0.25, curveOf(pool(three to 1), three).value, eps)
    }

    // Colour depth. W playables: Filler One x6 (Admiral is gold, not a W playable); U: Filler
    // Two x3. Baseline W 4, U 5.

    private val colourBaseline =
        baseline(mapOf("/byColor/W/playables/total" to 4.0, "/byColor/U/playables/total" to 5.0))

    private fun colourGrades(w: Int = 6) =
        wu(
            pool(fillerOne to w, fillerTwo to 3, admiral to 1, fillerThree to 1),
            grader(colourBaseline),
        )

    @Test
    fun `a mono-coloured card follows its colour's depth`() {
        // r(W) = (6 - 4) / 4 = 0.5; colour = 0.125. r(U) = (3 - 5) / 5 = -0.4; colour = -0.1.
        val white = colourGrades().of(fillerOne).colour
        val blue = colourGrades().of(fillerTwo).colour

        assertEquals(Color.W, white.colour)
        assertEquals(6, white.playables)
        assertEquals(4.0, white.typical, eps)
        assertEquals(0.125, white.value, eps)
        assertEquals(-0.1, blue.value, eps)
    }

    @Test
    fun `a gold card follows its shallowest colour`() {
        val colour = colourGrades().of(admiral).colour

        assertEquals(Color.U, colour.colour)
        assertEquals(3, colour.playables)
        assertEquals(5.0, colour.typical, eps)
        assertEquals(-0.1, colour.value, eps)
    }

    @Test
    fun `a colourless card has no colour part`() {
        val colour = colourGrades().of(fillerThree).colour

        assertNull(colour.colour)
        assertEquals(0.0, colour.value)
    }

    @Test
    fun `colour depth is clamped to the weight`() {
        // r(W) = (12 - 4) / 4 = 2, clamped to 1.
        assertEquals(0.25, colourGrades(w = 12).of(fillerOne).colour.value, eps)
    }

    // Together, the cap and the 0 to 5 range. Docking Bay (W, 2) with five Shuttle Ones (W, 2):
    // synergy (5 - 5/3) / (5/3) = 2 -> 1; curve k = 6, m = 12: 0.5; colour: W playables 6,
    // typical 3: r = 1.

    private val togetherBaseline =
        baseline(
            mapOf(
                "/pairs/WU/curve/buckets/2/creatures" to 12.0,
                "/byColor/W/playables/total" to 3.0,
            )
        )

    private val together = pool(dockingBay to 1, shuttleOne to 5)

    @Test
    fun `the parts add to the set grade`() {
        // 2.0 + 0.5 + 0.25 * 0.5 + 0.25 * 1 = 2.875.
        val card = wu(together, grader(togetherBaseline)).of(dockingBay)

        assertEquals(0.5, card.synergy.value, eps)
        assertEquals(0.125, card.curve.value, eps)
        assertEquals(0.25, card.colour.value, eps)
        assertEquals(2.875, card.adjusted, eps)
        assertEquals(0.875, card.change, eps)
        assertEquals(2.0, card.setGrade)
    }

    @Test
    fun `the parts' sum is capped`() {
        // Weights 1, 0.5, 0.5: 1 + 0.25 + 0.5 = 1.75, capped to 1.0.
        val weights = Weights(synergy = 1.0, curve = 0.5, colour = 0.5, cap = 1.0)
        val card = wu(together, grader(togetherBaseline, weights)).of(dockingBay)

        assertEquals(1.75, card.parts.sumOf { it.value }, eps)
        assertEquals(3.0, card.adjusted, eps)
        val down = Weights(synergy = 1.0, curve = 0.5, colour = 0.5, cap = 0.3)
        assertEquals(2.3, wu(together, grader(togetherBaseline, down)).of(dockingBay).adjusted, eps)
    }

    @Test
    fun `the adjusted grade stays within 0 and 5`() {
        // High: 4.5 + 0.875 = 5.375 -> 5.0. Low: Docking Bay with seven Filler Ones and a zero
        // baseline: synergy -0.5, curve (0 - 8) / 1 -> -0.25, colour (8 - 0) / 1 -> +0.25;
        // 0.3 - 0.5 = -0.2 -> 0.
        assertEquals(
            5.0,
            wu(together, grader(togetherBaseline), grade = 4.5).of(dockingBay).adjusted,
        )
        val low = wu(pool(dockingBay to 1, fillerOne to 7), grade = 0.3).of(dockingBay)

        assertEquals(-0.5, low.parts.sumOf { it.value }, eps)
        assertEquals(0.0, low.adjusted)
    }

    // The pair's listing.

    @Test
    fun `cards are listed once each, highest adjusted first, and ungraded ones are left out`() {
        val grades =
            grader(togetherBaseline).grade(
                pool(dockingBay to 1, shuttleOne to 5, fillerTwo to 2),
                ColorPair.WU,
            ) {
                if (it.card == fillerTwo) null else 2.0
            }

        assertEquals(8, grades.copies)
        assertEquals(listOf(dockingBay, shuttleOne), grades.cards.map { it.printing.card })
        assertEquals(5, grades.of(shuttleOne).quantity)
        val adjusted = grades.cards.map { it.adjusted }
        assertEquals(adjusted.sortedDescending(), adjusted)
    }

    @Test
    fun `a pair's mean is over its best 23 copies`() {
        // Docking Bay 1 copy, Shuttle One 5, Filler One 20. Curve (12 - 26) / 12 -> -0.25 and
        // colour (26 - 3) / 3 -> +0.25 for all. Synergy: Filler One 0; Docking Bay n 5,
        // e 25/3: -0.2; Shuttle One n 5, e 100/9: -0.275. So Filler One 2.0, Docking Bay 1.8,
        // Shuttle One 1.725, and the best 23 copies are Filler One x20, Docking Bay and two
        // shuttles.
        val grades =
            wu(pool(dockingBay to 1, shuttleOne to 5, fillerOne to 20), grader(togetherBaseline))
        val expected = (20 * 2.0 + 1.8 + 2 * 1.725) / 23

        assertEquals(
            listOf(fillerOne, dockingBay, shuttleOne),
            grades.cards.map { it.printing.card },
        )
        assertEquals(1.725, grades.of(shuttleOne).adjusted, eps)
        assertEquals(26, grades.copies)
        assertEquals(expected, grades.mean, eps)
        val small = wu(pool(fillerOne to 2, fillerTwo to 1))
        assertEquals(small.cards.sumOf { it.adjusted * it.quantity } / 3, small.mean, eps)
    }

    @Test
    fun `every pair is graded, highest mean first, and an empty pair has mean 0`() {
        val all = grader().gradeAll(pool(fillerOne to 4, fillerTwo to 1), flat(3.0))

        assertEquals(ColorPair.entries.toSet(), all.map { it.pair }.toSet())
        val means = all.map { it.mean }
        assertEquals(means.sortedDescending(), means)
        val empty = all.single { it.pair == ColorPair.BR }
        assertEquals(0, empty.copies)
        assertEquals(0.0, empty.mean)
        // Ties keep the pairs' order: BR, BG and RG hold nothing, so their means tie at 0.
        assertEquals(
            listOf(ColorPair.BR, ColorPair.BG, ColorPair.RG),
            all.takeLast(3).map { it.pair },
        )
    }
}
