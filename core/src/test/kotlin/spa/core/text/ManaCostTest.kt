package spa.core.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import spa.core.text.Color.B
import spa.core.text.Color.G
import spa.core.text.Color.R
import spa.core.text.Color.U
import spa.core.text.Color.W

class ManaCostTest {
    @Test
    fun `plain cost parses to generic, pips, and mana value`() {
        assertEquals(
            ManaCost(generic = 2, pips = mapOf(R to 2.0), manaValue = 4),
            ManaCost.parse("{2}{R}{R}"),
        )
    }

    @Test
    fun `hybrid counts half toward each color and one toward mana value`() {
        val cost = ManaCost.parse("{1}{G/W}{G/W}") // Kitchen Finks
        assertEquals(ManaCost(generic = 1, pips = mapOf(G to 1.0, W to 1.0), manaValue = 3), cost)
        assertEquals(mapOf(G to 0.5, W to 0.5), ManaCost.parse("{G/W}").pips)
        assertEquals(1, ManaCost.parse("{G/W}").manaValue)
    }

    @Test
    fun `phyrexian counts one toward its color`() {
        assertEquals(ManaCost(pips = mapOf(W to 1.0), manaValue = 1), ManaCost.parse("{W/P}"))
        assertEquals(
            ManaCost(pips = mapOf(G to 0.5, W to 0.5), manaValue = 1),
            ManaCost.parse("{G/W/P}"),
        )
    }

    @Test
    fun `x adds nothing and sets hasX`() {
        val cost = ManaCost.parse("{X}{R}")
        assertEquals(ManaCost(pips = mapOf(R to 1.0), manaValue = 1, hasX = true), cost)
        assertTrue(ManaCost.parse("{X}{X}{G}").hasX)
        assertFalse(ManaCost.parse("{2}{G}").hasX)
    }

    @Test
    fun `colorless counts one generic`() {
        assertEquals(ManaCost(generic = 3, manaValue = 3), ManaCost.parse("{C}{C}{1}"))
    }

    @Test
    fun `two-or-color counts half toward the color, two toward mana value, none toward generic`() {
        assertEquals(ManaCost(pips = mapOf(W to 0.5), manaValue = 2), ManaCost.parse("{2/W}"))
        assertEquals(
            ManaCost(pips = mapOf(W to 1.5), manaValue = 6),
            ManaCost.parse("{2/W}{2/W}{2/W}"),
        )
    }

    @Test
    fun `colorless hybrid and snow add mana value but no generic`() {
        assertEquals(ManaCost(pips = mapOf(U to 0.5), manaValue = 1), ManaCost.parse("{C/U}"))
        assertEquals(ManaCost(manaValue = 1), ManaCost.parse("{S}"))
    }

    @Test
    fun `empty or missing cost parses to all zeros`() {
        val zero = ManaCost(generic = 0, pips = emptyMap(), manaValue = 0, hasX = false)
        assertEquals(zero, ManaCost.parse(""))
        assertEquals(zero, ManaCost.parse(null))
        assertEquals(zero, ManaCost.parse("  "))
        assertEquals(0.0, zero.coloredPipCount)
        assertEquals(emptySet(), zero.colors)
    }

    @Test
    fun `colored pip count sums pips and colors are keys in enum order`() {
        val cost = ManaCost.parse("{1}{G}{U/B}{W}") // keys added G, U, B, W
        assertEquals(3.0, cost.coloredPipCount)
        assertEquals(listOf(W, U, B, G), cost.colors.toList())
        assertEquals(listOf(W, U, B, G), cost.pips.keys.toList())
    }

    @Test
    fun `symbols are case-insensitive and split cards sum both halves`() {
        assertEquals(ManaCost.parse("{2}{R}{R}"), ManaCost.parse("{2}{r}{r}"))
        assertEquals(
            ManaCost(generic = 4, pips = mapOf(U to 1.0, R to 1.0), manaValue = 6),
            ManaCost.parse("{1}{U} // {3}{R}"),
        )
    }

    @Test
    fun `unknown symbols and stray text are rejected`() {
        assertFailsWith<IllegalArgumentException> { ManaCost.parse("{HW}") }
        assertFailsWith<IllegalArgumentException> { ManaCost.parse("{Q}") }
        assertFailsWith<IllegalArgumentException> { ManaCost.parse("{2/P}") }
        assertFailsWith<IllegalArgumentException> { ManaCost.parse("{W/U/B}") }
        assertFailsWith<IllegalArgumentException> { ManaCost.parse("2RR") }
    }
}
