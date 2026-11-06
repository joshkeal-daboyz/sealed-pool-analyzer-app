package spa.cli

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import spa.agent.build.BuildConfig
import spa.agent.build.BuildSetup
import spa.agent.build.DeckCard
import spa.agent.build.ScoredDeck
import spa.core.intake.PoolParser
import spa.core.metrics.ColorPair

/**
 * SPA-044 criteria 1 and 3 over the manifest's `trk` pools: legal decks, and decks no single swap
 * improves, the same every time.
 */
class BuildLegalityTest {
    private val session = BuildSession.open(DataDir(Path.of(Fixtures.data)), "trk")

    private val pools: List<Pair<String, spa.core.cards.Pool>> by lazy {
        val manifest =
            Json.parseToJsonElement(Files.readString(Fixtures.root.resolve("pools/manifest.json")))
        manifest.jsonObject
            .getValue("fixtures")
            .jsonArray
            .map { it.jsonObject }
            .filter {
                it.getValue("set").jsonPrimitive.content == "trk" &&
                    it.getValue("expectedUnresolved").jsonArray.isEmpty()
            }
            .map {
                val file = it.getValue("file").jsonPrimitive.content
                val text = Files.readString(Fixtures.root.resolve("pools").resolve(file))
                file to PoolParser.parse(text, session.index).pool
            }
    }

    private val configs: List<BuildConfig> =
        ColorPair.entries.map {
            BuildConfig.parse(it.colors.joinToString("") { c -> "${c.symbol}" })
        } + listOf(BuildConfig.parse("WU/R"), BuildConfig.parse("BR/G"))

    private class Case(
        val name: String,
        val setup: BuildSetup,
        val built: ScoredDeck?,
        val skipped: Boolean,
    )

    private val cases: List<Case> by lazy {
        pools.flatMap { (file, pool) ->
            configs.map { config ->
                val setup = session.setup(pool, config)
                val enough = setup.capacity >= 40 - session.weights.lands.high
                Case(
                    "$file $config",
                    setup,
                    if (enough) session.builder.build(setup) else null,
                    !enough,
                )
            }
        }
    }

    @Test
    fun `the manifest has trk pools and few cases are skipped`() {
        assertTrue(pools.size >= 10, "${pools.size} pools")
        val skipped = cases.count { it.skipped }
        assertTrue(skipped * 4 < cases.size, "$skipped of ${cases.size} cases skipped")
    }

    @Test
    fun `every deck is legal`() {
        val rule = session.weights.lands
        val splash = session.weights.splash
        cases
            .filter { !it.skipped }
            .forEach { case ->
                val built = assertNotNull(case.built, case.name)
                val deck = built.deck
                val config = case.setup.config
                val counts =
                    pools.first { case.name.startsWith(it.first + " ") }.second.cardCounts()
                assertEquals(40, deck.size, case.name)
                (deck.spells + deck.nonbasics)
                    .groupingBy { it.card.oracleId }
                    .eachCount()
                    .forEach { (id, copies) ->
                        val inPool = counts[id]
                        assertNotNull(inPool, "${case.name}: $id is not in the pool")
                        assertTrue(
                            copies <= inPool,
                            "${case.name}: $copies copies of $id, pool has $inPool",
                        )
                    }
                assertEquals(
                    deck.landCount,
                    deck.nonbasics.size + deck.basics.values.sum(),
                    case.name,
                )
                // The rule's count, unless it needs more spells than the pool can supply.
                val ruled = rule.count(deck.meanManaValue, config.colours.size)
                assertTrue(
                    ruled == deck.landCount || deck.spells.size == case.setup.capacity,
                    case.name,
                )
                assertTrue(
                    deck.spells.all { it.card.colors.all { c -> c in config.colours } },
                    case.name,
                )
                val splashCards =
                    deck.spells.filter { s -> s.card.colors.any { it in config.splash } }
                assertTrue(splashCards.size <= splash.maxCards, "${case.name}: ${splashCards.size}")
                splashCards.forEach { card ->
                    val pips = card.pips.filterKeys { it in config.splash }.values.sum()
                    assertTrue(pips <= splash.maxPips, "${case.name}: ${card.card.name} has $pips")
                }
            }
    }

    /**
     * The gain of swapping the deck's [out] for [into], or null when the swap is not one of a deck
     * at this land count: it breaks the splash limit, or moves the mean across a land cutoff
     * (unless the pool can fill no more spells, see the build).
     */
    private fun gain(case: Case, built: ScoredDeck, out: DeckCard, into: DeckCard): Double? {
        val deck = built.deck
        val swapped = deck.spells.toMutableList().apply { remove(out) }.apply { add(into) }
        val next = session.builder.evaluate(case.setup, swapped, deck.landCount)
        val colours = case.setup.config.colours.size
        val ruled = session.weights.lands.count(next.deck.meanManaValue, colours)
        val splashOk =
            swapped.count { it.isSplash(case.setup.config) } <= session.weights.splash.maxCards
        val ruleOk = ruled == deck.landCount || deck.spells.size == case.setup.capacity
        return if (splashOk && ruleOk) next.total - built.total else null
    }

    @Test
    fun `no single swap raises the score and the deck is the same every time`() {
        cases
            .filter { !it.skipped }
            .forEach { case ->
                val built = assertNotNull(case.built, case.name)
                val deck = built.deck
                val used = deck.spells.groupingBy { it }.eachCount()
                val swaps =
                    used.keys.flatMap { out ->
                        case.setup.spells
                            .filter {
                                it.card !== out && used.getOrDefault(it.card, 0) < it.quantity
                            }
                            .map { out to it.card }
                    }
                swaps.forEach { (out, into) ->
                    val gain = gain(case, built, out, into) ?: Double.NEGATIVE_INFINITY
                    assertTrue(
                        gain <= 1e-9,
                        "${case.name}: ${out.card.name} for ${into.card.name} gains $gain",
                    )
                }
                val again = assertNotNull(session.builder.build(case.setup))
                assertEquals(deck.spells.map { it.id }, again.deck.spells.map { it.id }, case.name)
                assertEquals(built.total, again.total, case.name)
            }
    }
}
