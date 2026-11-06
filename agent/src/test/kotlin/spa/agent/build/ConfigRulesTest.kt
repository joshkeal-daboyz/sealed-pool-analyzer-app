package spa.agent.build

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import spa.core.text.Color

/** SPA-045 criterion 1: the colour configurations a pool supports, one small rule each. */
class ConfigRulesTest {
    private val b = setOf(Color.B)
    private val g = setOf(Color.G)

    /** One plain spell of each colour: a pool with no fixing. */
    private val plain: List<Candidate> =
        listOf(
                BuildTestCards.spell("Wa", BuildTestCards.W, "{W}", 1),
                BuildTestCards.spell("Ub", BuildTestCards.U, "{U}", 1),
                BuildTestCards.spell("Bc", b, "{B}", 1),
                BuildTestCards.spell("Rd", BuildTestCards.R, "{R}", 1),
                BuildTestCards.spell("Ge", g, "{G}", 1),
            )
            .map { Candidate(it, 1) }

    private val wu = setOf(Color.W, Color.U)
    private val br = setOf(Color.B, Color.R)

    /** Alternately lands and spells, each supplying the colours given. */
    private fun fixing(vararg supplies: Set<Color>): List<Candidate> =
        supplies.mapIndexed { i, colours ->
            if (i % 2 == 0) {
                Candidate(BuildTestCards.land("Land $i", colours), 1)
            } else {
                val spell =
                    BuildTestCards.spell("Fixer $i", BuildTestCards.W, "{W}", 1, fixes = colours)
                Candidate(spell, 1)
            }
        }

    private fun configs(
        pool: List<Candidate>,
        section: String = ConfigTestWeights.DEFAULT,
        rules: List<ConfigRule> = ConfigRules.ALL,
    ): List<BuildConfig> = ConfigRules.configs(pool, ConfigTestWeights.parse(section, rules), rules)

    private fun fourColour(all: List<BuildConfig>) = all.filter { it.main.size == 4 }

    @Test
    fun `a pool with no fixing gets 100 configurations, each once`() {
        val all = configs(plain)

        assertEquals(100, all.size)
        assertEquals(100, all.toSet().size)
        assertEquals(10, all.count { it.main.size == 2 && it.splash.isEmpty() })
        assertEquals(30, all.count { it.main.size == 2 && it.splash.size == 1 })
        assertEquals(30, all.count { it.main.size == 2 && it.splash.size == 2 })
        assertEquals(10, all.count { it.main.size == 3 && it.splash.isEmpty() })
        assertEquals(20, all.count { it.main.size == 3 && it.splash.size == 1 })
        assertEquals(0, fourColour(all).size)
    }

    @Test
    fun `four fixing cards that each supply two of WUBR give WUBR and no other four colour set`() {
        val all = configs(plain + fixing(wu, br, wu, br))

        assertEquals(101, all.size)
        assertEquals(listOf(BuildConfig.parse("WUBR")), fourColour(all))
    }

    @Test
    fun `three fixing cards are not enough, and a card counts once per copy`() {
        assertEquals(0, fourColour(configs(plain + fixing(wu, br, wu))).size)
        val twoCopies = fixing(wu, br).map { Candidate(it.card, 2) }

        assertEquals(listOf("WUBR"), fourColour(configs(plain + twoCopies)).map { "$it" })
    }

    @Test
    fun `maxSplashColours 1 removes the 30 two splash configurations`() {
        val one =
            ConfigTestWeights.DEFAULT.replace("\"maxSplashColours\": 2", "\"maxSplashColours\": 1")

        val all = configs(plain, one)

        assertEquals(70, all.size)
        assertEquals(0, all.count { it.splash.size == 2 })
    }

    @Test
    fun `tripleSplash false removes the triples with a splash`() {
        val none =
            ConfigTestWeights.DEFAULT.replace("\"tripleSplash\": true", "\"tripleSplash\": false")

        assertEquals(80, configs(plain, none).size)
    }

    @Test
    fun `a rule that is not enabled gives nothing`() {
        val full = plain + fixing(wu, br, wu, br)

        fun without(rule: String): Int {
            val off =
                ConfigTestWeights.DEFAULT.replace(
                    "\"$rule\": { \"enabled\": true",
                    "\"$rule\": { \"enabled\": false",
                )
            return configs(full, off).size
        }

        assertEquals(101 - 10, without("pairs"))
        assertEquals(101 - 60, without("pairSplash"))
        assertEquals(101 - 30, without("triples"))
        assertEquals(101 - 1, without("fourColour"))
    }

    @Test
    fun `a rule missing from the file is off`() {
        val section = """ "configs": { "pairs": { "enabled": true } } """

        assertEquals(10, configs(plain, section).size)
    }

    private fun failure(section: String): String =
        assertFailsWith<IllegalArgumentException> { ConfigTestWeights.parse(section) }.message!!

    @Test
    fun `an unknown key in configs is an error naming it`() {
        val default = ConfigTestWeights.DEFAULT

        assertTrue("pairz" in failure(default.replace("\"pairs\"", "\"pairz\"")))
        assertTrue("minFixingz" in failure(default.replace("\"minFixing\"", "\"minFixingz\"")))
        assertTrue("maxSplashColours" in failure(default.replace(", \"maxSplashColours\": 2", "")))
    }

    @Test
    fun `a rule added to the list adds its configuration and nothing else changes`() {
        val extra =
            object : ConfigRule {
                override val key = "extra"

                override fun configs(pool: List<Candidate>, params: ConfigParams) =
                    listOf(BuildConfig.parse("G/WU"), BuildConfig.parse("WU"))
            }
        val rules = ConfigRules.ALL + extra
        val section = ConfigTestWeights.DEFAULT.replace("\"pairs\":", "\"extra\": {}, \"pairs\":")

        val all = configs(plain, section, rules)

        assertEquals(101, all.size)
        assertTrue(BuildConfig.parse("G/WU") in all)
        assertEquals(1, all.count { it == BuildConfig.parse("WU") })
    }
}
