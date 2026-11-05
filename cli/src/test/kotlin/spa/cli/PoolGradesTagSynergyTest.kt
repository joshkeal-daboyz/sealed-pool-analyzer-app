package spa.cli

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import spa.agent.poolgrade.CardAdjustment
import spa.agent.poolgrade.PairGrades
import spa.agent.poolgrade.TagSynergyPart
import spa.agent.poolgrade.Weights

/**
 * SPA-043 criteria 2 and 3: the weights files, and the tag synergy part through `spa pool-grades
 * --tags` on the frozen v1 trk tags, trk-manabox-01, pair WU, with only `tagSynergy` 0.5 (SPA-048's
 * pinned values).
 */
class PoolGradesTagSynergyTest {
    @TempDir lateinit var temp: Path

    private val pool = Fixtures.pool("trk-manabox-01.csv")

    private val v1 = Fixtures.v1Tags("trk")

    private fun tagOnly(): String =
        writeFile(temp, "tag-only.json", """{ "tagSynergy": 0.5, "cap": 1.0 }""")

    private fun args(vararg extra: String): List<String> =
        listOf(pool, "--set", "trk", "--pair", "WU", "--data", Fixtures.data) + extra

    private fun compute(vararg extra: String): PairGrades {
        val err = ByteArrayOutputStream()
        return PoolGradesCommand.compute(args(*extra), PrintStream(err, true, Charsets.UTF_8))
            .single()
    }

    private fun poolGrades(vararg extra: String): CliRun =
        runCli("pool-grades", *args(*extra).toTypedArray())

    private fun PairGrades.card(number: String): CardAdjustment = cards.single {
        it.printing.setCode == "trk" && it.printing.collectorNumber == number
    }

    private fun assertNear(expected: Double, actual: Double, what: String) =
        assertTrue(abs(expected - actual) < 0.0005, "$what: expected $expected, got $actual")

    /** The reason lines printed under the card line ending in [name]. */
    private fun reasons(run: CliRun, name: String): List<String> {
        val lines = run.stdoutLines()
        val at = lines.indexOfFirst { it.endsWith(name) }
        assertTrue(at >= 0, "no line for $name")
        return lines.drop(at + 1).takeWhile { it.startsWith("      ") }
    }

    @Test
    fun `Enterprise NX-01 moves by its tag synergy part`() {
        val grades = compute("--tags", v1, "--weights", tagOnly())
        val card = grades.card("58")
        val tag = card.parts.filterIsInstance<TagSynergyPart>().single()

        assertEquals("Enterprise NX-01", card.printing.card.name)
        assertEquals(3.5, card.setGrade)
        assertEquals(26, tag.syn)
        assertNear(20.690, tag.expected, "e")
        assertNear(0.128333, tag.value, "part")
        assertNear(3.628, card.adjusted, "adjusted")
        assertEquals(listOf(tag), card.parts.filter { it.value != 0.0 })
    }

    @Test
    fun `Humpback Whales moves down by the full weight`() {
        val card = compute("--tags", v1, "--weights", tagOnly()).card("65")

        assertEquals("Humpback Whales", card.printing.card.name)
        assertEquals(3.0, card.setGrade)
        assertNear(2.5, card.adjusted, "adjusted")
    }

    @Test
    fun `each card prints its tag synergy reason line`() {
        val run = poolGrades("--tags", v1, "--weights", tagOnly())

        assertEquals(ExitCode.OK, run.exit, run.stderr)
        assertEquals(
            listOf(
                "      tag synergy +0.13: 26 with the pool, 20.69 typical (Starfleet Spanner " +
                    "Jockey, Hemmer, Fixing What Is Broken, Cetacean Ops)"
            ),
            reasons(run, "1x Enterprise NX-01 (trk 58)"),
        )
        assertEquals(
            listOf(
                "      tag synergy −0.50: 0 with the pool, 3.03 typical (no partners in the pool)"
            ),
            reasons(run, "1x Humpback Whales (trk 65)"),
        )
        assertTrue(run.stdoutLines().any { it.startsWith("3.63  ") && "Enterprise NX-01" in it })
    }

    @Test
    fun `with tagSynergy 0 no tag file is read`() {
        val absent = temp.resolve("absent.json").toString()

        val run = poolGrades("--tags", absent)

        assertEquals(ExitCode.OK, run.exit, run.stderr)
        assertEquals(poolGrades().stdout, run.stdout)
    }

    @Test
    fun `with tagSynergy above 0 a missing tag file is one line and exit 1`() {
        val run =
            poolGrades("--tags", temp.resolve("absent.json").toString(), "--weights", tagOnly())

        assertEquals(ExitCode.ERROR, run.exit)
        assertEquals(1, run.stderrLines().size, run.stderr)
        assertTrue(run.stderrLines().single().startsWith("spa: "), run.stderr)
        assertEquals("", run.stdout)
    }

    @Test
    fun `without --tags the set's own tag file is read`() {
        val run = poolGrades("--weights", tagOnly())

        assertEquals(ExitCode.OK, run.exit, run.stderr)
        assertTrue(run.stdoutLines().any { it.startsWith("      tag synergy ") }, run.stdout)
    }

    @Test
    fun `default json and tuned json parse to the same weights as before, tagSynergy 0`() {
        val default = Weights.parse(Files.readString(Fixtures.root.resolve("weights/default.json")))
        val tuned = Weights.parse(Files.readString(Fixtures.root.resolve("weights/tuned.json")))

        assertEquals(Weights(synergy = 0.5, curve = 0.25, colour = 0.25, cap = 1.0), default)
        assertEquals(Weights(synergy = 0.0, curve = 0.25, colour = 0.5, cap = 1.0), tuned)
        assertEquals(0.0, default.tagSynergy)
        assertEquals(0.0, tuned.tagSynergy)
    }

    @Test
    fun `an unknown key, a negative number and a non-number are each one line and exit 1`() {
        listOf(
                """{ "tagSynergy": 0.5, "cap": 1, "comment": 1 }""" to "'comment'",
                """{ "tagSynergy": -0.5, "cap": 1 }""" to "'tagSynergy'",
                """{ "tagSynergy": "high", "cap": 1 }""" to "'tagSynergy'",
            )
            .forEachIndexed { i, (text, named) ->
                val run = poolGrades("--weights", writeFile(temp, "bad$i.json", text))
                assertEquals(ExitCode.ERROR, run.exit, text)
                assertEquals(1, run.stderrLines().size, run.stderr)
                assertTrue(named in run.stderr, run.stderr)
                assertEquals("", run.stdout)
            }
    }
}
