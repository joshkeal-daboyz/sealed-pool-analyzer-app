package spa.agent.poolgrade

import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import spa.agent.poolgrade.PoolGradeTestCards.KEYWORDS
import spa.agent.poolgrade.PoolGradeTestCards.SET
import spa.agent.poolgrade.PoolGradeTestCards.THEMES
import spa.agent.poolgrade.PoolGradeTestCards.baseline
import spa.agent.poolgrade.PoolGradeTestCards.fillerOne
import spa.agent.poolgrade.PoolGradeTestCards.fillerTwo
import spa.agent.poolgrade.PoolGradeTestCards.flat
import spa.agent.poolgrade.PoolGradeTestCards.of
import spa.agent.poolgrade.PoolGradeTestCards.pool
import spa.core.cards.Card
import spa.core.metrics.ColorPair

/** A test-only part's result: its weight times the pair's candidate copies, and a reason. */
internal data class SeamResult(override val value: Double, val copies: Int) : Part {
    override val line: String?
        get() =
            if (value == 0.0) null
            else String.format(Locale.ROOT, "seam %+.2f: %d copies", value, copies)
}

/** A test-only part, known to no main code: its weight times the pair's candidate copies. */
internal object SeamPart : CardPart {
    override val key = "seam"

    override fun score(card: Card, context: PairContext, weight: Double): Part =
        SeamResult(weight * context.copies, context.copies)
}

/**
 * SPA-043 criterion 4: the seam. A test-only part, added to the list, reads its weight from a
 * weights file, moves the grade and writes its own reason line, with no other code change.
 */
class CardPartSeamTest {
    private val eps = 1e-9

    @Test
    fun `a test-only part added to the list changes the grade and gives its reason`() {
        val parts = CardParts.ALL + SeamPart
        val weights = Weights.parse("""{ "seam": 0.1, "cap": 1 }""", parts)
        val grader = PoolGrader(SET, THEMES, KEYWORDS, baseline(), weights, parts = parts)

        val grades = grader.grade(pool(fillerOne to 2, fillerTwo to 1), ColorPair.WU, flat(2.0))
        val card = grades.of(fillerOne)

        // Three candidate copies: 0.1 x 3 = 0.3, the other parts 0.
        assertEquals(2.3, card.adjusted, eps)
        assertEquals(parts.size, card.parts.size)
        assertEquals(SeamResult(0.1 * 3, 3), card.parts.last())
        assertEquals("seam +0.30: 3 copies", card.parts.last().line)
        assertEquals(listOf("seam +0.30: 3 copies"), card.parts.mapNotNull { it.line })
    }

    @Test
    fun `the parts are summed in list order and capped`() {
        val parts = CardParts.ALL + SeamPart
        val weights = Weights.parse("""{ "seam": 1, "cap": 0.5 }""", parts)
        val grader = PoolGrader(SET, THEMES, KEYWORDS, baseline(), weights, parts = parts)

        val card = grader.grade(pool(fillerOne to 2), ColorPair.WU, flat(2.0)).of(fillerOne)

        assertEquals(2.5, card.adjusted, eps)
    }

    @Test
    fun `a part with weight 0 gives no reason line`() {
        val parts = CardParts.ALL + SeamPart
        val grader = PoolGrader(SET, THEMES, KEYWORDS, baseline(), Weights.DEFAULT, parts = parts)

        val card = grader.grade(pool(fillerOne to 2), ColorPair.WU, flat(2.0)).of(fillerOne)

        assertNull(card.parts.last().line)
        assertEquals(0.0, card.parts.last().value)
    }

    @Test
    fun `the tag synergy part reads no tags at weight 0, and needs them above 0`() {
        val grader = PoolGrader(SET, THEMES, KEYWORDS, baseline(), Weights.DEFAULT)
        val card = grader.grade(pool(fillerOne to 2), ColorPair.WU, flat(2.0)).of(fillerOne)
        val tag = card.parts.filterIsInstance<TagSynergyPart>().single()

        assertEquals(0.0, tag.value)
        assertNull(tag.line)

        val weights = Weights(0.0, 0.0, 0.0, cap = 1.0, tagSynergy = 0.5)
        val untagged = PoolGrader(SET, THEMES, KEYWORDS, baseline(), weights)
        assertThrows<IllegalStateException> {
            untagged.grade(pool(fillerOne to 2), ColorPair.WU, flat(2.0))
        }
    }
}
