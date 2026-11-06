package spa.cli

import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import spa.agent.build.ConfigRules
import spa.agent.build.DeckParts
import spa.agent.build.DeckWeights
import spa.core.cards.Pool
import spa.core.cards.PoolEntry

/**
 * SPA-046 criterion 4: `fixtures/weights/deck-baseline.json` is a deck weights file that ranks
 * two-colour pairs by the sum of the spells' set grades alone.
 */
class DeckBaselineTest {
    @TempDir lateinit var temp: Path

    private val file = Path.of(Fixtures.data, "weights", "deck-baseline.json")

    @Test
    fun `the file loads as deck weights with only the grades weight above 0`() {
        val weights = DeckWeights.parse(Files.readString(file))

        assertEquals(1.0, weights.weight("grades"))
        DeckParts.ALL.filter { it.key != "grades" }.forEach {
            assertEquals(0.0, weights.weight(it.key), it.key)
        }
    }

    @Test
    fun `only the pairs rule is enabled`() {
        val weights = DeckWeights.parse(Files.readString(file))

        val enabled = ConfigRules.ALL.filter { weights.configs.settings(it)?.enabled == true }

        assertEquals(listOf("pairs"), enabled.map { it.key })
    }

    @Test
    fun `its ranked decks are all pairs with no splash, scored by the grades of the spells`() {
        val session = BuildSession.open(BacktestSyn.data(temp), "syn", file)
        val pool =
            Pool(
                listOf("Serra Angel", "Air Elemental", "Doom Blade", "Grizzly Bears").map { name ->
                    PoolEntry(session.index.printings.first { it.card.name == name }, 12)
                }
            )

        val ranked = session.rank(pool)

        assertEquals(6, ranked.size)
        ranked.forEach { deck ->
            assertEquals(2, deck.config.main.size, "${deck.config}")
            assertTrue(deck.config.splash.isEmpty(), "${deck.config}")
            val grades = deck.scored.deck.spells.sumOf { it.grade }
            assertTrue(grades > 0.0)
            assertTrue(abs(deck.scored.total - grades) < EPSILON, "${deck.config}")
        }
    }

    private companion object {
        const val EPSILON = 1e-9
    }
}
