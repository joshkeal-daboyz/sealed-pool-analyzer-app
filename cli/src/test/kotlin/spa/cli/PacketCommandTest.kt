package spa.cli

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** SPA-071 criteria 1 to 7: `spa packet` over the trk sample pool and a set with no tag file. */
class PacketCommandTest {
    @TempDir lateinit var temp: Path

    private val pool = Fixtures.pool("trk-manabox-01.csv")

    private fun packet(vararg extra: String, data: String = Fixtures.data) =
        runCli("packet", pool, "--set", "trk", "--data", data, *extra)

    /** The packet's text as a file holds it: line feeds only. */
    private val text: String by lazy {
        val run = packet()
        assertEquals(0, run.exit, run.stderr)
        run.stdout.replace("\r\n", "\n")
    }

    private fun section(name: String): List<String> =
        text.lines().dropWhile { it != name }.drop(1).takeWhile { !it.startsWith("## ") }

    /** A copy of the trk data under [temp], leaving out each of [without] (paths under it). */
    private fun trkData(vararg without: String): String {
        val data = temp.resolve("data-${without.joinToString("-").replace('/', '_')}")
        val parts =
            listOf(
                "scryfall/trk",
                "scryfall/sds",
                "briefs/trk.json",
                "baselines/trk.json",
                "grades/trk.json",
                "grades/trk.v2.json",
                "tags/trk.json",
                "otags/trk.json",
                "otags/trk.tags.json",
                "otags/trk.fable.json",
                "otags/cares.json",
                "weights/deck-tuned.json",
            )
        parts
            .filter { it !in without }
            .forEach { part ->
                val source = Fixtures.root.resolve(part)
                val target = data.resolve(part)
                target.parent.createDirectories()
                if (Files.isDirectory(source)) {
                    target.createDirectories()
                    source.listDirectoryEntries().forEach { it.copyTo(target.resolve(it.fileName)) }
                } else {
                    source.copyTo(target)
                }
            }
        return data.toString()
    }

    @Test
    fun `help lists packet`() {
        assertTrue("packet <pool-file> --set <code>" in runCli("--help").stdout)
    }

    @Test
    fun `out writes the file and says so, and without out the same bytes go to stdout`() {
        val out = temp.resolve("packet.md")

        val run = packet("--out", out.toString())

        assertEquals(0, run.exit, run.stderr)
        assertEquals("Packet written to $out", run.stdout.trimEnd())
        assertEquals(1, run.stdout.lines().count { it.isNotEmpty() })
        assertEquals(text, Files.readString(out))
        assertFalse(Files.readString(out).contains('\r'))
        assertTrue(text.endsWith("\n"))
    }

    @Test
    fun `out replaces an existing file`() {
        val out = temp.resolve("packet.md")
        Files.writeString(out, "old")

        assertEquals(0, packet("--out", out.toString()).exit)

        assertEquals(text, Files.readString(out))
    }

    @Test
    fun `the seven sections come in order, once each, after the opening text`() {
        val headings = text.lines().filter { it.startsWith("#") }

        assertEquals(
            listOf(
                "# Sealed pool review packet",
                "## Pool",
                "## Numbers",
                "## Fixing",
                "## Synergy",
                "## Decks",
                "## Sources",
            ),
            headings,
        )
        assertTrue(text.startsWith("# Sealed pool review packet\n\n$OPENING\n\n## Pool\n"))
        assertTrue(text.endsWith("## Sources\n\n$SOURCES\n"))
        assertFalse("\n\n\n" in text)
    }

    @Test
    fun `the numbers are the engine's with the typical spread beside them`() {
        val numbers = section("## Numbers")

        listOf(
                "/totalCards = 85 (typical: median 85, p10 85, p90 85)",
                "/nonlandCards = 71 (typical: median 71, p10 67, p90 74)",
                "/basicLands = 5 (typical: median 5, p10 4, p90 6)",
                "/removalTotal = 9 (typical: median 8, p10 5, p90 11)",
                "/interactionTotal = 1 (typical: median 3, p10 1, p90 6)",
                "/fixingSources/count = 6 (typical: median 10, p10 7, p90 13)",
                "/pairs/WU/cards = 33 (typical: median 29, p10 24, p90 35)",
                "/pairs/WU/creatures = 19 (typical: median 15, p10 11, p90 20)",
                "/pairs/WU/removal = 2 (typical: median 2, p10 1, p90 4)",
                "/pairs/WU/fixingSources = 4 (typical: median 10, p10 7, p90 13)",
            )
            .forEach { assertTrue(it in numbers, it) }
        assertTrue(numbers.none { "/curve/" in it })
        val body = numbers.filter { it.isNotEmpty() }
        assertEquals(body.sortedBy { it.substringBefore(" = ") }, body)
        assertTrue(body.all { it.startsWith("/") && " = " in it })
        assertTrue(body.none { Regex("""= -?\d+\.\d{5,}""").containsMatchIn(it) })
        assertTrue(body.none { Regex("""\.0\b""").containsMatchIn(it.substringBefore(" (")) })
        assertTrue(body.any { Regex("""= -?\d+\.\d{1,4} \(""").containsMatchIn(it) })
    }

    @Test
    fun `a missing baseline is a note on stderr and no typical text`() {
        val run = packet(data = trkData("baselines/trk.json"))

        assertEquals(0, run.exit, run.stderr)
        assertTrue(run.stderr.contains("baseline"), run.stderr)
        val numbers = run.stdout.replace("\r\n", "\n").lines()
        assertTrue(numbers.any { it == "/totalCards = 85" })
        assertFalse("typical" in run.stdout)
    }

    @Test
    fun `the pool section lists each distinct card by name with its grade`() {
        val lines = section("## Pool").filter { it.isNotEmpty() }

        assertTrue(lines.first().startsWith("Set: trk (Star Trek), 85 cards, "), lines.first())
        val distinct = lines.first().substringAfter("85 cards, ").substringBefore(" distinct")
        assertEquals(lines.size - 1, distinct.toInt())
        val dead = lines.single { it.startsWith("1x He's Dead, Jim | ") }
        assertTrue(" | common | grade 4.0 (A−) | " in dead, dead)
        val swamp = lines.single { it.startsWith("3x Swamp | ") }
        assertTrue(" | grade none | " in swamp, swamp)
        val names = lines.drop(1).map { it.substringAfter("x ").substringBefore(" | ") }
        assertEquals(names.sortedWith(compareBy { it }), names)
        assertTrue(lines.drop(1).all { it.split(" | ").size >= 6 }, "six fields per card")
        assertTrue(lines.none { it.endsWith(" ") })
    }

    @Test
    fun `the fixing section is the pool form of fixing`() {
        val fixing = runCli("fixing", pool, "--set", "trk", "--data", Fixtures.data)

        assertEquals(
            fixing.stdout.replace("\r\n", "\n").trimEnd(),
            section("## Fixing").joinToString("\n").trimEnd(),
        )
    }

    @Test
    fun `the decks are what build prints`() {
        val build =
            runCli(
                "build",
                pool,
                "--set",
                "trk",
                "--weights",
                Fixtures.root.resolve("weights/deck-tuned.json").toString(),
                "--top",
                "3",
                "--data",
                Fixtures.data,
            )

        assertEquals(0, build.exit, build.stderr)
        val decks = section("## Decks").joinToString("\n").trim()
        assertEquals(build.stdout.replace("\r\n", "\n").trim(), decks)
        assertEquals(3, decks.lines().count { it.startsWith("Rank ") })
    }

    @Test
    fun `top 1 gives one deck`() {
        val run = packet("--top", "1")

        assertEquals(0, run.exit, run.stderr)
        val decks = run.stdout.replace("\r\n", "\n")
        assertEquals(1, decks.lines().count { it.startsWith("Rank ") })
        assertTrue(decks.lines().any { it.startsWith("Rank 1: ") })
    }

    @Test
    fun `a pool that builds no deck says so and is not an error`() {
        val csv = Files.readAllLines(Path.of(pool)).take(4).joinToString("\n") + "\n"
        val small = writeFile(temp, "small.csv", csv)

        val run = runCli("packet", small, "--set", "trk", "--data", Fixtures.data)

        assertEquals(0, run.exit, run.stderr)
        val decks = run.stdout.replace("\r\n", "\n").lines()
        assertTrue("No deck could be built from this pool." in decks)
        assertTrue("## Sources" in decks)
    }

    @Test
    fun `the synergy section lists at most 20 pairs, highest first, each scoring 1 or more`() {
        val lines = section("## Synergy").filter { it.isNotEmpty() }

        assertTrue(lines.size in 1..20, "$lines")
        val scores = lines.map { it.substringBefore(' ').toInt() }
        assertTrue(scores.all { it >= 1 })
        assertEquals(scores.sortedDescending(), scores)
        assertTrue(lines.all { " with " in it })
    }

    @Test
    fun `a set with no tag file has no synergy tags and is not an error`() {
        val run = packet(data = trkData("tags/trk.json"))

        assertEquals(0, run.exit, run.stderr)
        val synergy =
            run.stdout.replace("\r\n", "\n").lines().dropWhile { it != "## Synergy" }.drop(1)
        assertEquals("", synergy[0])
        assertEquals("No synergy tags for this set.", synergy[1])
        assertTrue("## Decks" in run.stdout)
    }

    @Test
    fun `running twice gives the same bytes`() {
        assertEquals(packet().stdout, packet().stdout)
    }

    @Test
    fun `a missing pool file is one line on stderr and exit 1, and writes no file`() {
        val out = temp.resolve("none.md")

        val run =
            runCli("packet", "nope.csv", "--set", "trk", "--data", Fixtures.data, "--out", "$out")

        assertEquals(1, run.exit)
        assertEquals(1, run.stderrLines().size, run.stderr)
        assertFalse(out.exists())
    }

    @Test
    fun `a missing set cache, brief or grade file is exit 1 and writes no file`() {
        val out = temp.resolve("none.md")
        val cases =
            listOf(
                temp.resolve("empty").also { it.createDirectories() }.toString(),
                trkData("briefs/trk.json"),
                trkData("grades/trk.json", "grades/trk.v2.json"),
            )

        cases.forEach { data ->
            val run = packet("--out", "$out", data = data)
            assertEquals(1, run.exit, "$data: ${run.stderr}")
            assertEquals(1, run.stderrLines().size, "$data: ${run.stderr}")
            assertFalse(out.exists())
        }
        val brief = packet("--out", "$out", "--brief", temp.resolve("no-brief.json").toString())
        assertEquals(1, brief.exit)
        assertFalse(out.exists())
    }

    @Test
    fun `an unknown flag is exit 2 and a bad top is exit 1`() {
        assertEquals(2, packet("--nope").exit)
        assertEquals(2, runCli("packet", "--set", "trk", "--data", Fixtures.data).exit)
        assertEquals(1, packet("--top", "0").exit)
        assertEquals(1, packet("--top", "many").exit)
    }

    @Test
    fun `a weights file other than the default is used`() {
        val default = Fixtures.root.resolve("weights/deck-default.json").toString()

        val run = packet("--weights", default)

        assertEquals(0, run.exit, run.stderr)
        val tuned = Fixtures.root.resolve("weights/deck-tuned.json")
        if (Files.readString(tuned) != Files.readString(Path.of(default))) {
            assertTrue(run.stdout != text)
        }
    }

    private companion object {
        const val SOURCES =
            "Card data from Scryfall (scryfall.com). Card text and images are property of " +
                "Wizards of the Coast."

        val OPENING =
            """
            |You are helping a player build a Magic: The Gathering limited deck from their sealed pool. Everything below was worked out by code from the player's own pool: the cards with a grade for each (0 to 5, relative to this set), the pool's numbers beside what a typical pool from this set holds, the pool's fixing, the strongest card-to-card synergies, and the three best decks the deck builder found, with its reasons.
            |
            |Please:
            |1. Say which of the three decks to register, and why, in a short paragraph.
            |2. Go through the pool by colour: where it is deep, where it is thin, and how the removal, the curve and the mana compare with the typical pool.
            |3. Name cards left out of the best deck that deserve a second look, and any card in it that you would cut, with the reason.
            |4. Say what you are unsure of, and anything in this packet that looks wrong.
            |
            |Use only this packet. Quote card names exactly as written here, and take numbers from the packet rather than computing your own.
            """
                .trimMargin()
    }
}
