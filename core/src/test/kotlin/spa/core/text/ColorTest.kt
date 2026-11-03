package spa.core.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ColorTest {
    @Test
    fun `colors are in WUBRG order`() {
        assertEquals("WUBRG", Color.entries.joinToString("") { it.name })
    }

    @Test
    fun `fromSymbol maps each letter in either case`() {
        for (color in Color.entries) {
            assertEquals(color, Color.fromSymbol(color.symbol))
            assertEquals(color, Color.fromSymbol(color.symbol.lowercaseChar()))
        }
    }

    @Test
    fun `fromSymbol returns null for colorless and other characters`() {
        listOf('C', 'X', '2', 'P', ' ').forEach { assertNull(Color.fromSymbol(it), "'$it'") }
    }
}
