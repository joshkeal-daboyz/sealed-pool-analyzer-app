package spa.core.metrics

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import spa.core.cards.Pool
import spa.core.cards.PoolEntry
import spa.core.cards.Rarity
import spa.core.text.Color.B
import spa.core.text.Color.G
import spa.core.text.Color.U
import spa.core.text.Color.W

class MetricEngineTest {
    private val handBuilt =
        poolOf(
            entry(Syn.GRIZZLY_BEARS, 2),
            entry(Syn.COUNTERSPELL),
            entry(Syn.LIGHTNING_HELIX),
            entry(Syn.HUNTMASTER),
            entry(Syn.FIRE_ICE),
            entry(Syn.WEAR_TEAR),
            entry(Syn.MIND_STONE),
            entry(Syn.TEMPLE_OF_MYSTERY),
            entry(Syn.FOREST, 3),
        )

    @Test
    fun `compute is pure and ignores entry order`() {
        val first = MetricEngine.compute(handBuilt)

        assertEquals(first, MetricEngine.compute(handBuilt))
        assertEquals(first, MetricEngine.compute(Pool(handBuilt.entries.reversed())))
        assertEquals(first, MetricEngine.compute(Pool(handBuilt.entries.shuffled(Random(6)))))
    }

    @Test
    fun `JSON has exactly the PoolMetrics fields and enum names as map keys`() {
        val metrics = MetricEngine.compute(handBuilt)
        val json = Json.encodeToJsonElement(PoolMetrics.serializer(), metrics).jsonObject

        assertEquals(
            setOf(
                "totalCards",
                "nonlandCards",
                "basicLands",
                "nonbasicLands",
                "byColor",
                "fixingSources",
                "removalTotal",
                "interactionTotal",
                "pairs",
            ),
            json.keys,
        )
        assertEquals(
            listOf("W", "U", "B", "R", "G", "MULTI", "COLORLESS"),
            json.getValue("byColor").jsonObject.keys.toList(),
        )
        val pairs = json.getValue("pairs").jsonObject
        assertEquals(ColorPair.entries.map { it.name }, pairs.keys.toList())
        val ug = pairs.getValue("UG").jsonObject
        assertEquals(setOf("U", "G"), ug.getValue("landRecommendation").jsonObject.keys)
        assertEquals(setOf("U", "G"), ug.getValue("castProbability").jsonObject.keys)
        assertEquals(metrics, Json.decodeFromJsonElement(PoolMetrics.serializer(), json))
    }

    @Test
    fun `empty pool gives all zeros and ten pairs`() {
        val metrics = MetricEngine.compute(Pool(emptyList()))

        assertEquals(
            PoolMetrics(
                totalCards = 0,
                nonlandCards = 0,
                basicLands = 0,
                nonbasicLands = 0,
                byColor = ColorBucket.entries.associateWith { zeroColor },
                fixingSources = emptyList(),
                removalTotal = 0,
                interactionTotal = 0,
                pairs = ColorPair.entries.associateWith { emptyPair(it, 0.0, 0.0) },
            ),
            metrics,
        )
    }

    @Test
    fun `pool of only lands has land counts and no nonland metrics`() {
        val metrics =
            MetricEngine.compute(
                poolOf(
                    entry(Syn.TEMPLE_OF_MYSTERY),
                    entry(Syn.EVOLVING_WILDS),
                    entry(Syn.PLAINS, 3),
                    entry(Syn.FOREST, 2),
                )
            )

        // 1 + 1 + 3 + 2.
        assertEquals(7, metrics.totalCards)
        assertEquals(0, metrics.nonlandCards)
        // Plains x3, Forest x2.
        assertEquals(5, metrics.basicLands)
        // Temple of Mystery, Evolving Wilds.
        assertEquals(2, metrics.nonbasicLands)
        assertEquals(ColorBucket.entries.associateWith { zeroColor }, metrics.byColor)
        assertEquals(0, metrics.removalTotal)
        assertEquals(0, metrics.interactionTotal)
        assertEquals(
            listOf(FixingSource("Temple of Mystery", setOf(G, U), 1)),
            metrics.fixingSources,
        )
        // No castable cards, so no land recommendation; the only sources are Temple of Mystery's
        // one copy for U and G: 1 - C(39,9)/C(40,9) = 9/40 = 0.225.
        assertEquals(emptyPair(ColorPair.WB, 0.0, 0.0), metrics.pairs.getValue(ColorPair.WB))
        assertEquals(
            emptyPair(ColorPair.UG, 0.225, 0.225).copy(fixingSources = 1),
            metrics.pairs.getValue(ColorPair.UG),
        )
        assertEquals(
            emptyPair(ColorPair.WU, 0.0, 0.225).copy(fixingSources = 1),
            metrics.pairs.getValue(ColorPair.WU),
        )
    }

    @Test
    fun `a card with count 3 contributes 3 to every count`() {
        val three = MetricEngine.compute(poolOf(entry(Syn.DOOM_BLADE, 3)))

        assertEquals(3, three.totalCards)
        assertEquals(3, three.nonlandCards)
        assertEquals(3, three.removalTotal)
        assertEquals(
            ColorMetrics(
                rarities(common = 3),
                rarities(common = 3),
                0,
                removal = 3,
                interaction = 0,
            ),
            three.byColor.getValue(ColorBucket.B),
        )
        val wb = three.pairs.getValue(ColorPair.WB)
        assertEquals(3, wb.cards)
        assertEquals(3, wb.noncreatures)
        assertEquals(3, wb.removal)
        assertEquals(curveOf(CurveBucket(2, creatures = 0, noncreatures = 3)), wb.curve)
        // One color with pips: all 17 lands are Swamps. 1 - C(23,9)/C(40,9) = 0.99701.
        assertEquals(mapOf(B to 17), wb.landRecommendation)
        assertEquals(mapOf(W to 0.0, B to 0.997), wb.castProbability)
        // Three entries of one copy each are the same pool.
        val split = poolOf(entry(Syn.DOOM_BLADE), entry(Syn.DOOM_BLADE), entry(Syn.DOOM_BLADE))
        assertEquals(three, MetricEngine.compute(split))
    }

    @Test
    fun `double-faced cards count by their front face`() {
        val metrics =
            MetricEngine.compute(poolOf(entry(Syn.AGADEEMS_AWAKENING), entry(Syn.HUNTMASTER)))

        // Agadeem's back face is a land, but its front is a sorcery: not a land.
        assertEquals(2, metrics.nonlandCards)
        assertEquals(0, metrics.nonbasicLands)
        // Agadeem: mana value 3 from {X}{B}{B}{B}, a noncreature.
        assertEquals(
            curveOf(CurveBucket(3, creatures = 0, noncreatures = 1)),
            metrics.pairs.getValue(ColorPair.BG).curve,
        )
        // Huntmaster: front face Creature at mana value 4, red-green.
        val rg = metrics.pairs.getValue(ColorPair.RG)
        assertEquals(curveOf(CurveBucket(4, creatures = 1, noncreatures = 0)), rg.curve)
        assertEquals(1, rg.removal)
    }

    @Test
    fun `colorless artifact is castable in all ten pairs`() {
        val metrics = MetricEngine.compute(poolOf(entry(Syn.MIND_STONE)))

        ColorPair.entries.forEach { pair ->
            val pairMetrics = metrics.pairs.getValue(pair)
            assertEquals(1, pairMetrics.cards, "$pair")
            assertEquals(
                curveOf(CurveBucket(2, creatures = 0, noncreatures = 1)),
                pairMetrics.curve,
            )
            // No pips, so no land recommendation.
            assertEquals(emptyMap(), pairMetrics.landRecommendation)
        }
        assertEquals(rarities(common = 1), metrics.byColor.getValue(ColorBucket.COLORLESS).cards)
    }

    @Test
    fun `a rare or mythic printing with one off-color pip is a splash candidate`() {
        val rare = synPrinting(Syn.GIANT_GROWTH).copy(rarity = Rarity.RARE)
        val mythic = synPrinting(Syn.LLANOWAR_ELVES).copy(rarity = Rarity.MYTHIC)
        val metrics =
            MetricEngine.compute(
                Pool(
                    listOf(
                        PoolEntry(rare, 1),
                        PoolEntry(mythic, 1),
                        // Common and not removal: no reason to splash it.
                        entry(Syn.GRIZZLY_BEARS),
                        // Rare, but {1}{B}{B} is two pips.
                        entry(Syn.MURDEROUS_RIDER),
                    )
                )
            )

        assertEquals(
            listOf(
                SplashCandidate("Giant Growth", G, SplashReason.RARE_OR_MYTHIC),
                SplashCandidate("Llanowar Elves", G, SplashReason.RARE_OR_MYTHIC),
            ),
            metrics.pairs.getValue(ColorPair.WU).splashCandidates,
        )
    }

    @Test
    fun `fixing sources merge printings and count copies`() {
        val temple = synPrinting(Syn.TEMPLE_OF_MYSTERY)
        val reprint = temple.copy(scryfallId = "test-temple-2", collectorNumber = "20a")
        val metrics =
            MetricEngine.compute(
                Pool(
                    listOf(
                        PoolEntry(temple, 2),
                        PoolEntry(reprint, 1),
                        entry(Syn.FOREST, 0),
                    )
                )
            )

        assertEquals(
            listOf(FixingSource("Temple of Mystery", setOf(G, U), 3)),
            metrics.fixingSources,
        )
        assertEquals(3, metrics.pairs.getValue(ColorPair.UB).fixingSources)
        // The Forest entry has no copies.
        assertEquals(3, metrics.totalCards)
    }

    @Test
    fun `curve buckets and creature split add up for 100 random pools`() {
        val random = Random(20261103)
        val printings = synCards.printings
        repeat(100) { round ->
            val entries =
                List(random.nextInt(0, 41)) {
                    PoolEntry(printings.random(random), random.nextInt(1, 4))
                }
            val metrics = MetricEngine.compute(Pool(entries))
            metrics.pairs.forEach { (pair, pairMetrics) ->
                val buckets = pairMetrics.curve.buckets
                val where = "pool $round, $pair"
                assertEquals(Curve.TOP + 1, buckets.size, where)
                assertEquals(pairMetrics.cards, buckets.sumOf { it.total }, where)
                assertEquals(
                    pairMetrics.cards,
                    pairMetrics.creatures + pairMetrics.noncreatures,
                    where,
                )
                assertEquals(pairMetrics.creatures, buckets.sumOf { it.creatures }, where)
                assertTrue(pairMetrics.castProbability.keys == pair.colors, where)
            }
        }
    }

    private fun emptyPair(pair: ColorPair, first: Double, second: Double) =
        ColorPairMetrics(
            cards = 0,
            creatures = 0,
            noncreatures = 0,
            curve = curveOf(),
            removal = 0,
            interaction = 0,
            fixingSources = 0,
            splashCandidates = emptyList(),
            landRecommendation = emptyMap(),
            castProbability = mapOf(pair.first to first, pair.second to second),
        )
}
