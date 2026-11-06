package spa.cli

import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * SPA-045 criteria 3, 5 and 7: `spa build` without `--colours` prints the top ranked decks, each
 * explained, and (on CI) in under a minute.
 */
class BuildRankCommandTest {
    @TempDir lateinit var temp: Path

    private val pool = Fixtures.pool("trk-manabox-01.csv")

    private fun build(vararg extra: String, file: String = pool) =
        runCli("build", file, "--set", "trk", "--data", Fixtures.data, *extra)

    private val rankHeading = Regex("""^Rank (\d+): (\S+)(?: \(same cards as (.+)\))?$""")
    private val heading = Regex("""^Deck (\S+): score (-?\d+\.\d{3})$""")
    private val partLine = Regex("""^ {2}([A-Za-z]+) ([+-]\d+\.\d{3})(?:: (.+))?$""")
    private val spellLine = Regex("""^ {2}(\d+) (.+) {2}grade (\d\.\d) {2}(\S.*)$""")

    private fun rankHeadings(run: CliRun): List<String> =
        run.stdoutLines().filter { it.startsWith("Rank ") }

    private fun deckHeadings(run: CliRun): List<String> =
        run.stdoutLines().filter { it.startsWith("Deck ") }

    private fun assertOneLine(run: CliRun, fragment: String) {
        assertEquals(1, run.exit, run.stderr)
        assertEquals(1, run.stderrLines().size, run.stderr)
        assertEquals("", run.stdout)
        assertTrue(fragment in run.stderr, run.stderr)
    }

    @Test
    fun `without colours it prints the top 3 decks, each headed with its rank and colours`() {
        val run = build()

        assertEquals(0, run.exit, run.stderr)
        val ranks = rankHeadings(run).map { requireNotNull(rankHeading.matchEntire(it)) { it } }
        assertEquals(listOf("1", "2", "3"), ranks.map { it.groupValues[1] })
        val decks = deckHeadings(run).map { requireNotNull(heading.matchEntire(it)) { it } }
        assertEquals(ranks.map { it.groupValues[2] }, decks.map { it.groupValues[1] })
        val scores = decks.map { it.groupValues[2].toDouble() }
        assertEquals(scores.sortedDescending(), scores)
        assertTrue(run.stdoutLines().first().startsWith("Rank 1: "))
    }

    @Test
    fun `top 5 prints 5 decks, top 1 prints 1, and the shorter list is the start of the longer`() {
        val five = build("--top", "5")
        val one = build("--top", "1")

        assertEquals(0, five.exit, five.stderr)
        assertEquals(0, one.exit, one.stderr)
        assertEquals(5, rankHeadings(five).size)
        assertEquals(1, rankHeadings(one).size)
        assertEquals(deckHeadings(one).single(), deckHeadings(five).first())
        assertTrue(five.stdout.startsWith(one.stdout.trimEnd()))
    }

    @Test
    fun `a top of 0, a negative or a word is one line and exit 1`() {
        assertOneLine(build("--top", "0"), "--top")
        assertOneLine(build("--top", "-2"), "--top")
        assertOneLine(build("--top", "x"), "--top")
    }

    @Test
    fun `with colours it builds that one configuration and ignores top`() {
        val plain = build("--colours", "WU")
        val ignored = build("--colours", "WU", "--top", "0")

        assertEquals(0, plain.exit, plain.stderr)
        assertEquals(plain.stdout, ignored.stdout)
        assertEquals(1, deckHeadings(plain).size)
        assertTrue(deckHeadings(plain).single().startsWith("Deck WU: score"))
        assertEquals(0, rankHeadings(plain).size)
    }

    @Test
    fun `when no configuration can be built it is one line and exit 1`() {
        val text = Files.readString(Path.of(Fixtures.data, "weights", "deck-default.json"))
        val off = text.replace("\"enabled\": true", "\"enabled\": false")
        val file = temp.resolve("off.json")
        Files.writeString(file, off)

        val run = build("--weights", file.toString())

        assertOneLine(run, "no configuration")
    }

    @Test
    fun `a bad configs section in the weights file is one line naming the key`() {
        val text = Files.readString(Path.of(Fixtures.data, "weights", "deck-default.json"))
        val file = temp.resolve("bad.json")
        Files.writeString(file, text.replace("\"minFixing\"", "\"minFixingz\""))

        assertOneLine(build("--weights", file.toString()), "minFixingz")
    }

    /** The decks in [run]'s output: heading score, part values, and the spell lines' reasons. */
    private fun check(run: CliRun, name: String, expected: Int) {
        assertEquals(0, run.exit, "$name: ${run.stderr}")
        val lines = run.stdoutLines()
        val starts = lines.indices.filter { lines[it].startsWith("Deck ") }
        assertEquals(expected, starts.size, name)
        starts.forEachIndexed { n, start ->
            val end = starts.getOrElse(n + 1) { lines.size }
            val deck = lines.subList(start, end)
            val head =
                requireNotNull(heading.matchEntire(deck.first())) { "$name: ${deck.first()}" }
            val parts =
                deck
                    .drop(1)
                    .takeWhile { !it.startsWith("Spells") }
                    .map { requireNotNull(partLine.matchEntire(it)) { "$name part line: $it" } }
            val sum = parts.sumOf { it.groupValues[2].toDouble() }
            assertTrue(
                abs(head.groupValues[2].toDouble() - sum) <= 0.005,
                "$name ${head.groupValues[1]}: parts $sum against ${head.groupValues[2]}",
            )
            val from = deck.indexOfFirst { it.startsWith("Spells") }
            val to = deck.indexOfFirst { it.startsWith("Lands") }
            val rows = deck.subList(from + 1, to).filter { !it.startsWith("Mana value") }
            assertTrue(rows.isNotEmpty(), name)
            rows.forEach {
                val spell = requireNotNull(spellLine.matchEntire(it)) { "$name spell line: $it" }
                assertTrue(spell.groupValues[4].isNotBlank(), "$name reason: $it")
            }
        }
    }

    @Test
    fun `every printed deck's parts add to its score and every spell has a reason`() {
        val manifest =
            Json.parseToJsonElement(Files.readString(Fixtures.root.resolve("pools/manifest.json")))
        val files =
            manifest.jsonObject
                .getValue("fixtures")
                .jsonArray
                .map { it.jsonObject }
                .filter {
                    it.getValue("set").jsonPrimitive.content == "trk" &&
                        it.getValue("expectedUnresolved").jsonArray.isEmpty()
                }
                .map { it.getValue("file").jsonPrimitive.content }

        assertTrue(files.size >= 10, "${files.size} pools")
        files.forEach { check(build("--top", "3", file = Fixtures.pool(it)), it, 3) }
    }

    @Test
    fun `on CI the ranked build of a pool takes under 60 seconds`() {
        if (System.getenv("CI") == null) return
        val start = System.nanoTime()

        val run = build()

        val seconds = (System.nanoTime() - start) / NANOS_PER_SECOND
        assertEquals(0, run.exit, run.stderr)
        assertTrue(seconds < LIMIT_SECONDS, "$seconds seconds")
    }

    private companion object {
        const val NANOS_PER_SECOND = 1_000_000_000.0
        const val LIMIT_SECONDS = 60.0
    }
}
