package spa.agent.build

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** SPA-044: the deck weights file, keyed, versioned, and strict about what it holds. */
class DeckWeightsTest {
    private val valid =
        """
        {
          "version": 1,
          "weights": { "grades": 1, "curve": 0.5, "creatures": 1, "removal": 1,
                       "castability": 1, "fixing": 1, "synergy": 0.1 },
          "params": {
            "curve": { "target1": 1, "target2": 5, "target3": 4, "target4": 3, "target5": 2,
                       "target6": 1 },
            "creatures": { "minCreatures": 14 },
            "removal": { "cap": 6 }
          },
          "lands": { "lowCurve": 2.6, "highCurve": 3.4, "low": 16, "normal": 17, "high": 18 },
          "splash": { "maxCards": 3, "maxPips": 1 }
        }
        """
            .trimIndent()

    private fun failure(text: String): String =
        assertFailsWith<IllegalArgumentException> { DeckWeights.parse(text) }.message!!

    @Test
    fun `a valid file is read`() {
        val weights = DeckWeights.parse(valid)

        assertEquals(0.5, weights.weight(CurveDeckPart.KEY))
        assertEquals(0.1, weights.weight(SynergyDeckPart.KEY))
        assertEquals(LandRule(2.6, 3.4, 16, 17, 18), weights.lands)
        assertEquals(SplashRule(3, 1.0), weights.splash)
        assertEquals(14.0, weights.params(CreaturesDeckPart)["minCreatures"])
    }

    @Test
    fun `a part missing from the weights has weight 0`() {
        val text = valid.replace("\"fixing\": 1,", "")

        assertEquals(0.0, DeckWeights.parse(text).weight(FixingDeckPart.KEY))
    }

    @Test
    fun `a part added to the list is accepted by key`() {
        val extra =
            object : DeckPart {
                override val key = "extra"

                override fun score(deck: Deck, weight: Double, params: PartParams) =
                    DeckPartScore(key, weight, "extra")
            }
        val text = valid.replace("\"grades\": 1,", "\"grades\": 1, \"extra\": 2,")

        val weights = DeckWeights.parse(text, DeckParts.ALL + extra)

        assertEquals(2.0, weights.weight("extra"))
        assertTrue("extra" in failure(text).also { assertTrue("unknown" in it) })
    }

    @Test
    fun `each mistake is named`() {
        assertTrue("not JSON" in failure("{"))
        assertTrue("object" in failure("[]"))
        assertTrue("'oops'" in failure(valid.replace("\"splash\"", "\"oops\": 1, \"splash\"")))
        assertTrue("'bogus'" in failure(valid.replace("\"grades\": 1", "\"bogus\": 1")))
        assertTrue("version" in failure(valid.replace("\"version\": 1", "\"version\": 2")))
        assertTrue("'grades'" in failure(valid.replace("\"grades\": 1", "\"grades\": -1")))
        assertTrue("'grades'" in failure(valid.replace("\"grades\": 1", "\"grades\": \"1\"")))
        assertTrue("minCreatures" in failure(valid.replace("\"minCreatures\": 14", "\"x\": 14")))
        assertTrue("'cap2'" in failure(valid.replace("\"cap\": 6", "\"cap\": 6, \"cap2\": 1")))
        assertTrue("'params'" in failure(valid.replace("\"removal\": {", "\"nosuch\": {")))
        assertTrue("normal" in failure(valid.replace("\"normal\": 17,", "")))
        assertTrue("'low'" in failure(valid.replace("\"low\": 16", "\"low\": 1.5")))
        assertTrue("'maxCards'" in failure(valid.replace("\"maxCards\": 3", "\"maxCards\": -1")))
    }

    @Test
    fun `land counts must be in order and fit a 40 card deck`() {
        assertTrue("order" in failure(valid.replace("\"low\": 16", "\"low\": 18")))
        assertTrue("40" in failure(valid.replace("\"high\": 18", "\"high\": 41")))
    }

    @Test
    fun `colours are read as main and splash`() {
        val config = BuildConfig.parse("wu/r")

        assertEquals("WU/R", config.toString())
        assertEquals("WUB", BuildConfig.parse("BUW").toString())
        assertEquals(setOf(spa.core.text.Color.R), config.splash)
        listOf("", "W/", "XY", "WW", "WU/W", "WU/RGB", "W/U/R", "W-U").forEach {
            assertFailsWith<IllegalArgumentException>(it) { BuildConfig.parse(it) }
        }
    }
}
