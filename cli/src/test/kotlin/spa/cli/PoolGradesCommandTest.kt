package spa.cli

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import spa.core.metrics.ColorPair

/** SPA-038 criteria 3 and 4: `spa pool-grades` over trk-manabox-01, its options and its errors. */
class PoolGradesCommandTest {
    @TempDir lateinit var temp: Path

    private val pool = Fixtures.pool("trk-manabox-01.csv")

    private fun poolGrades(vararg extra: String) =
        runCli("pool-grades", pool, "--set", "trk", "--data", Fixtures.data, *extra)

    private fun compute(vararg extra: String) =
        PoolGradesCommand.compute(
            listOf(pool, "--set", "trk", "--data", Fixtures.data, *extra),
            PrintStream(ByteArrayOutputStream(), true, Charsets.UTF_8),
        )

    private fun number(text: String): Double = text.replace('−', '-').toDouble()

    private val heading =
        Regex("""^([WUBRG]{2})  mean (\d\.\d\d)  \((\d+) cards, (\d+) copies\)$""")
    private val cardLine =
        Regex("""^(\d\.\d\d)  (\S+) +(\d\.\d)  ([+−]\d\.\d\d)  (\d+)x (.+) \((\w+) (\w+)\)$""")
    private val reason = Regex("""^      (synergy|curve|colour) ([+−]\d\.\d\d): \S.*$""")

    /** One printed card with its reason lines. */
    private data class Printed(val line: String, val reasons: MutableList<String> = mutableListOf())

    /** The printed cards of each pair, by pair, in print order; fails on any unknown line. */
    private fun parse(lines: List<String>): LinkedHashMap<String, MutableList<Printed>> {
        val pairs = linkedMapOf<String, MutableList<Printed>>()
        lines.forEach { line ->
            when {
                heading.matches(line) ->
                    pairs[heading.find(line)!!.groupValues[1]] = mutableListOf()
                cardLine.matches(line) -> pairs.values.last() += Printed(line)
                reason.matches(line) -> pairs.values.last().last().reasons += line
                else -> error("unexpected line: '$line'")
            }
        }
        return pairs
    }

    @Test
    fun `every printed adjusted grade is its set grade plus its listed parts, capped, in range`() {
        val run = poolGrades()
        assertEquals(ExitCode.OK, run.exit, run.stderr)
        val printed = parse(run.stdoutLines())
        val computed = compute().associateBy { it.pair.name }

        assertEquals(ColorPair.entries.map { it.name }.toSet(), printed.keys)
        assertEquals(computed.keys.toList(), printed.keys.toList())
        var cards = 0
        printed.forEach { (pair, list) ->
            val expected = computed.getValue(pair).cards
            assertEquals(expected.size, list.size, pair)
            list.zip(expected).forEach { (card, adjustment) ->
                val g = cardLine.find(card.line)!!.groupValues
                val adjusted = number(g[1])
                val set = number(g[3])
                val change = number(g[4])
                val parts = card.reasons.map { number(reason.find(it)!!.groupValues[2]) }
                val capped = parts.sum().coerceIn(-1.0, 1.0)
                val sum = (set + capped).coerceIn(0.0, 5.0)

                assertTrue(abs(adjusted - sum) <= 0.016, "${card.line}: parts $parts")
                assertTrue(abs(adjusted - set - change) <= 0.011, card.line)
                assertTrue(abs(change) <= 1.0 && adjusted in 0.0..5.0, card.line)
                assertEquals(GradesCommand.label(adjustment.adjusted), g[2], card.line)
                // Each nonzero part has a reason line, and only those.
                val nonzero = adjustment.parts.filter { it.value != 0.0 }
                assertEquals(nonzero.size, card.reasons.size, card.line)
                cards++
            }
        }
        assertTrue(cards > 100, "only $cards card lines")
    }

    @Test
    fun `pairs are printed highest mean first, each card once, highest adjusted first`() {
        val pairs = compute()
        val means = pairs.map { it.mean }

        assertEquals(10, pairs.size)
        assertEquals(means.sortedDescending(), means)
        pairs.forEach { pair ->
            val adjusted = pair.cards.map { it.adjusted }
            assertEquals(adjusted.sortedDescending(), adjusted, pair.pair.name)
            assertEquals(pair.cards.size, pair.cards.map { it.printing.card.oracleId }.toSet().size)
        }
    }

    @Test
    fun `--pair prints one pair, in either letter order`() {
        val wu = poolGrades("--pair", "WU")
        val uw = poolGrades("--pair", "uw")

        assertEquals(ExitCode.OK, wu.exit, wu.stderr)
        assertEquals(wu.stdout, uw.stdout)
        val headings = wu.stdoutLines().filter { heading.matches(it) }
        assertEquals(1, headings.size)
        assertTrue(headings.single().startsWith("WU  mean "), headings.single())
        assertEquals("WU  mean ", wu.stdoutLines().first().take(9))
        // Enterprise NX-01 (3.635, B+) and its reasons, as the ticket's examples spell them.
        val lines = wu.stdoutLines()
        val at = lines.indexOfFirst { it.endsWith("1x Enterprise NX-01 (trk 58)") }
        assertTrue(
            lines[at].matches(Regex("""^3\.6[34]  B\+  3\.5  \+0\.1[34]  1x .*""")),
            lines[at],
        )
        val reasons = lines.subList(at + 1, at + 4)
        assertTrue(
            reasons[0].startsWith("      synergy +0.16: 8 partners in the pool, 6.07 expected ("),
            reasons[0],
        )
        assertEquals("      curve −0.14: 7 cards at mana value 2, 4.48 typical", reasons[1])
        assertTrue(reasons[2].startsWith("      colour +0.12: "), reasons[2])
    }

    @Test
    fun `a bad --pair is one line and exit 1`() {
        listOf("WX", "W", "WUB", "WW").forEach {
            val run = poolGrades("--pair", it)
            assertEquals(ExitCode.ERROR, run.exit, it)
            assertEquals(1, run.stderrLines().size, run.stderr)
            assertEquals("", run.stdout)
        }
    }

    @Test
    fun `without --grades the v2 file is used, else the v1 file`() {
        val data = DataDir(temp)
        Files.createDirectories(temp.resolve("grades"))
        assertThrows<CliFailure> { PoolGradesCommand.defaultGradesFile(data, "xyz") }
        writeFile(temp, "grades/xyz.json", "{}")
        assertEquals(
            temp.resolve("grades/xyz.json"),
            PoolGradesCommand.defaultGradesFile(data, "xyz"),
        )
        writeFile(temp, "grades/xyz.v2.json", "{}")
        assertEquals(
            temp.resolve("grades/xyz.v2.json"),
            PoolGradesCommand.defaultGradesFile(data, "xyz"),
        )

        val v2 = Fixtures.root.resolve("grades/trk.v2.json").toString()
        assertEquals(poolGrades("--grades", v2).stdout, poolGrades().stdout)
    }

    @Test
    fun `--grades takes the set grades from the named file`() {
        // trk.v2.json with Enterprise NX-01 (trk 58) at 1.0 instead of 3.5.
        val v2 =
            Json.parseToJsonElement(Files.readString(Fixtures.root.resolve("grades/trk.v2.json")))
        val grades =
            v2.jsonObject.getValue("grades").jsonArray.map { entry ->
                val obj = entry.jsonObject
                if (obj.getValue("collectorNumber").jsonPrimitive.content == "58") {
                    JsonObject(obj + ("score" to JsonPrimitive(1.0)))
                } else {
                    obj
                }
            }
        val file =
            writeFile(
                temp,
                "edited.json",
                JsonObject(v2.jsonObject + ("grades" to JsonArray(grades))).toString(),
            )

        val enterprise =
            compute("--pair", "WU", "--grades", file).single().cards.single {
                it.printing.collectorNumber == "58"
            }
        assertEquals(1.0, enterprise.setGrade)
    }

    @Test
    fun `--weights takes the weights from the named file`() {
        val zero =
            writeFile(temp, "zero.json", """{ "synergy": 0, "curve": 0, "colour": 0, "cap": 1 }""")
        val run = poolGrades("--weights", zero)

        assertEquals(ExitCode.OK, run.exit, run.stderr)
        val cards = run.stdoutLines().filter { cardLine.matches(it) }
        assertTrue(cards.isNotEmpty())
        cards.forEach {
            val g = cardLine.find(it)!!.groupValues
            assertEquals(number(g[3]), number(g[1]), it)
            assertEquals(0.0, number(g[4]), it)
        }
        assertTrue(run.stdoutLines().none { reason.matches(it) })

        val half =
            writeFile(
                temp,
                "half.json",
                """{ "synergy": 1.0, "curve": 0.5, "colour": 0.5, "cap": 0.5 }""",
            )
        compute("--weights", half)
            .flatMap { it.cards }
            .forEach { assertTrue(abs(it.change) <= 0.5 + 1e-9, it.toString()) }
    }

    @Test
    fun `the default weights file holds the default weights`() {
        val text = Files.readString(Fixtures.root.resolve("weights/default.json"))
        assertEquals(spa.agent.poolgrade.Weights.DEFAULT, spa.agent.poolgrade.Weights.parse(text))
    }

    @Test
    fun `an unknown set, an unreadable pool or a bad weights file is one line and exit 1`() {
        val badJson = writeFile(temp, "bad.json", "{ not json")
        val missingKey =
            writeFile(temp, "short.json", """{ "synergy": 0.5, "curve": 0.25, "colour": 0.25 }""")
        val runs =
            listOf(
                runCli("pool-grades", pool, "--set", "zzz", "--data", Fixtures.data),
                runCli(
                    "pool-grades",
                    temp.resolve("absent.csv").toString(),
                    "--set",
                    "trk",
                    "--data",
                    Fixtures.data,
                ),
                poolGrades("--weights", badJson),
                poolGrades("--weights", missingKey),
                poolGrades("--weights", temp.resolve("absent.json").toString()),
                poolGrades("--grades", temp.resolve("absent.json").toString()),
                runCli("pool-grades", pool, "--data", Fixtures.data),
            )
        runs.forEach { run ->
            assertEquals(ExitCode.ERROR, run.exit, run.stderr)
            assertEquals(1, run.stderrLines().size, run.stderr)
            assertTrue(run.stderrLines().single().startsWith("spa: "), run.stderr)
            assertEquals("", run.stdout)
        }
    }

    @Test
    fun `the help text lists the command`() {
        assertTrue(
            Main.USAGE.contains(
                "pool-grades <pool-file> --set <code> [--pair <XY>] [--grades <file>]"
            ),
            Main.USAGE,
        )
        assertTrue(Main.USAGE.contains("[--weights <file>]"), Main.USAGE)
    }
}
