package spa.core.metrics

import kotlin.test.Test
import kotlin.test.assertEquals
import spa.core.cards.PoolEntry
import spa.core.cards.Printing

/**
 * Added by the Test Inspector: Definitions rules no syn card reaches. No syn card has the BOUNCE
 * tag, and none has a mana value of 7 or more, so these use syn printings with one field changed.
 */
class InspectorMetricsTest {
    private fun withText(number: String, name: String, text: String): Printing {
        val printing = synPrinting(number)
        val card = printing.card
        val face = card.faces.single().copy(name = name, oracleText = text)
        return printing.copy(
            card = card.copy(oracleId = "inspector-$name", name = name, faces = listOf(face))
        )
    }

    private fun withManaValue(number: String, name: String, manaValue: Double): Printing {
        val printing = synPrinting(number)
        val card =
            printing.card.copy(oracleId = "inspector-$name", name = name, manaValue = manaValue)
        return printing.copy(card = card)
    }

    @Test
    fun `a bounce card is interaction and a card with two interaction tags counts once`() {
        // Counterspell's cost and colors (U, instant, uncommon) with other rules text.
        val bounce =
            withText(Syn.COUNTERSPELL, "Bounce", "Return target creature to its owner's hand.")
        val both =
            withText(
                Syn.COUNTERSPELL,
                "Both",
                "Counter target spell. Return target creature to its owner's hand.",
            )
        val metrics =
            MetricEngine.compute(
                poolOf(PoolEntry(bounce, 1), PoolEntry(both, 1), entry(Syn.GIANT_GROWTH))
            )

        // Bounce (BOUNCE), Both (COUNTER and BOUNCE, one card), Giant Growth (TRICK).
        assertEquals(3, metrics.interactionTotal)
        // Bounce and Both.
        assertEquals(2, metrics.byColor.getValue(ColorBucket.U).interaction)
        // Bounce and Both are castable in WU; Giant Growth is not.
        assertEquals(2, metrics.pairs.getValue(ColorPair.WU).interaction)
        // All three are castable in UG.
        assertEquals(3, metrics.pairs.getValue(ColorPair.UG).interaction)
        assertEquals(0, metrics.removalTotal)
    }

    @Test
    fun `mana value 7 and above go in the last curve bucket`() {
        val seven = withManaValue(Syn.AIR_ELEMENTAL, "Seven", 7.0)
        val nine = withManaValue(Syn.AIR_ELEMENTAL, "Nine", 9.0)
        val metrics =
            MetricEngine.compute(
                poolOf(PoolEntry(seven, 1), PoolEntry(nine, 2), entry(Syn.SERRA_ANGEL))
            )
        val wu = metrics.pairs.getValue(ColorPair.WU)

        // Seven, Nine x2, Serra Angel.
        assertEquals(4, wu.cards)
        assertEquals(
            curveOf(
                // 5: Serra Angel.
                CurveBucket(5, creatures = 1, noncreatures = 0),
                // 7 or more: Seven and Nine x2, all creatures (Air Elemental's type line).
                CurveBucket(Curve.TOP, creatures = 3, noncreatures = 0),
            ),
            wu.curve,
        )
    }
}
