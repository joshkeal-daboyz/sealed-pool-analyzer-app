package spa.cli

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import spa.core.intake.PoolParser

/**
 * SPA-045 criterion 4: every deck of every configuration the rules give, over three fixture pools,
 * is legal as SPA-044's test checks it.
 */
class BuildConfigsLegalityTest {
    private val session = BuildSession.open(DataDir(Path.of(Fixtures.data)), "trk")

    @Test
    fun `every configuration of three pools builds only legal decks`() {
        val rule = session.weights.lands
        val splash = session.weights.splash
        var built = 0
        listOf("trk-manabox-01.csv", "trk-manabox-02.csv", "trk-manabox-03.csv").forEach { file ->
            val text = Files.readString(Fixtures.root.resolve("pools").resolve(file))
            val pool = PoolParser.parse(text, session.index).pool
            val counts = pool.cardCounts()
            val candidates = session.candidates(pool)
            val configs = session.configs(candidates)
            assertTrue(configs.size >= 100, "$file: ${configs.size} configurations")
            configs.forEach { config ->
                val name = "$file $config"
                val setup = session.setup(candidates, config)
                val scored = session.builder.build(setup) ?: return@forEach
                built++
                val deck = scored.deck
                assertEquals(40, deck.size, name)
                (deck.spells + deck.nonbasics)
                    .groupingBy { it.card.oracleId }
                    .eachCount()
                    .forEach { (id, copies) ->
                        val inPool = counts[id]
                        assertNotNull(inPool, "$name: $id is not in the pool")
                        assertTrue(copies <= inPool, "$name: $copies copies of $id, pool has $inPool")
                    }
                assertEquals(deck.landCount, deck.nonbasics.size + deck.basics.values.sum(), name)
                val ruled = rule.count(deck.meanManaValue, config.colours.size)
                assertTrue(ruled == deck.landCount || deck.spells.size == setup.capacity, name)
                assertTrue(
                    deck.spells.all { it.card.colors.all { c -> c in config.colours } },
                    name,
                )
                val splashCards = deck.spells.filter { it.isSplash(config) }
                assertTrue(splashCards.size <= splash.maxCards, "$name: ${splashCards.size}")
                splashCards.forEach {
                    assertTrue(it.splashPips(config) <= splash.maxPips, "$name: ${it.card.name}")
                }
            }
        }
        assertTrue(built >= 100, "$built decks built")
    }
}
