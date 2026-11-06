package spa.cli

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * SPA-055 criterion 4: the Fable cares file (`<data>/otags/<code>.fable.json`) joins Scryfall's
 * cares in the cares term, on `syn` with [CaresSyn]'s otags and cares table (`syn/2` cares about
 * `otag:draw` at 2; `syn/1` has `otag:cantrip` and `otag:draw`), and a set with no Fable file
 * scores as after SPA-054, on the committed trk files.
 */
class CaresScoringTest {
    @TempDir lateinit var temp: Path

    private val names =
        listOf(
            "Grizzly Bears",
            "Serra Angel",
            "Air Elemental",
            "Counterspell",
            "Doom Blade",
            "Gravedigger",
            "Llanowar Elves",
            "Giant Growth",
            "Kitchen Finks",
            "Lightning Helix",
            "Delver of Secrets // Insectile Aberration",
            "Huntmaster of the Fells // Ravager of the Fells",
            "Agadeem's Awakening // Agadeem, the Undercrypt",
            "Bonecrusher Giant // Stomp",
            "Murderous Rider // Swift End",
            "Fire // Ice",
            "Wear // Tear",
            "Mind Stone",
            "Evolving Wilds",
            "Temple of Mystery",
        )

    private fun tagFile(): String {
        val cards =
            names.withIndex().joinToString(",\n") { (i, name) ->
                """{ "id": "syn/${i + 1}", "name": "$name", "roles": [], "partners": [] }"""
            }
        return """
            { "set": "syn", "provenance": "hand-built",
              "axes": [
                { "id": "a", "name": "A", "definition": "A." },
                { "id": "b", "name": "B", "definition": "B." },
                { "id": "c", "name": "C", "definition": "C." }
              ],
              "cards": [
            $cards
              ] }
            """
    }

    private fun fable(vararg cards: String) =
        """{ "set": "syn", "provenance": "hand-built", "cards": [${cards.joinToString(",")}] }"""

    private fun card(id: String, fact: String, strength: Int) =
        """{ "id": "$id", "cares": [{ "fact": "$fact", "strength": $strength, "reason": "r" }] }"""

    /** A `syn` data directory with the otags files and tag file, and [fable] when not null. */
    private fun data(fable: String?, withOtags: Boolean = true): Path {
        val data = Fixtures.synDataDir(temp.resolve("data-${counter++}"))
        writeFile(data, "tags/syn.json", tagFile())
        if (withOtags) CaresSyn.addFiles(data)
        if (fable != null) writeFile(data, "otags/syn.fable.json", fable)
        return data
    }

    private var counter = 0

    private fun table(data: Path, vararg more: String) =
        runCli("synergy-table", "--set", "syn", "--data", "$data", *more)

    private fun pair(score: Int, a: String, b: String) =
        "$score  axis 0  partner 0  cares $score  $a with $b"

    private val grizzly = "Grizzly Bears (syn/1)"
    private val serra = "Serra Angel (syn/2)"
    private val air = "Air Elemental (syn/3)"
    private val doom = "Doom Blade (syn/5)"

    @Test
    fun `without a Fable file the cares are Scryfall's alone`() {
        val run = table(data(null))

        assertEquals(ExitCode.OK, run.exit, run.stderr)
        val lines = run.stdoutLines()
        assertTrue(pair(2, grizzly, serra) in lines)
        assertTrue(pair(2, serra, air) in lines)
        assertTrue(lines.none { doom in it && grizzly in it })
    }

    @Test
    fun `a card gains a cares tag from the Fable file and scores with matching facts`() {
        val run = table(data(fable(card("syn/5", "otag:cantrip", 2))))

        assertEquals(ExitCode.OK, run.exit, run.stderr)
        val lines = run.stdoutLines()
        assertTrue(pair(2, grizzly, doom) in lines, lines.take(8).toString())
        assertTrue(pair(2, grizzly, serra) in lines)
        assertTrue(lines.none { doom in it && air in it })
    }

    @Test
    fun `the larger strength wins when Scryfall and Fable give the same fact`() {
        val higher = table(data(fable(card("syn/2", "otag:draw", 3)))).stdoutLines()
        assertTrue(pair(3, grizzly, serra) in higher)
        assertTrue(pair(3, serra, air) in higher)
        assertFalse(pair(2, grizzly, serra) in higher)

        val lower = table(data(fable(card("syn/2", "otag:draw", 1)))).stdoutLines()
        assertTrue(pair(2, grizzly, serra) in lower)
        assertFalse(pair(3, grizzly, serra) in lower)
    }

    @Test
    fun `facts --card shows the merged cares tags`() {
        val data = data(fable(card("syn/5", "otag:cantrip", 2), card("syn/2", "otag:draw", 3)))

        val doomLine =
            runCli("facts", "--set", "syn", "--card", "syn/5", "--data", "$data").stdoutLines()
        val serraLine =
            runCli("facts", "--set", "syn", "--card", "syn/2", "--data", "$data").stdoutLines()

        assertTrue(doomLine.single().endsWith(" cares:otag:cantrip=2"), "$doomLine")
        assertTrue(serraLine.single().endsWith(" cares:otag:draw=3"), "$serraLine")
    }

    @Test
    fun `a Fable file alone works when the set has no otags file, and --otags none reads none`() {
        val data = data(fable(card("syn/5", "creature", 1)), withOtags = false)
        val run = table(data)

        assertEquals(ExitCode.OK, run.exit, run.stderr)
        assertTrue(
            pair(1, grizzly, doom) in run.stdoutLines(),
            run.stdoutLines().take(5).toString(),
        )

        val none = table(data, "--otags", "none")
        assertEquals(ExitCode.OK, none.exit, none.stderr)
        assertEquals(table(data(null, withOtags = false), "--otags", "none").stdout, none.stdout)
    }

    @Test
    fun `an invalid Fable file, or one for another set or with a strength out of range, is one line`() {
        val bad = table(data("{ nope"))
        assertEquals(ExitCode.ERROR, bad.exit)
        assertEquals(1, bad.stderrLines().size, bad.stderr)
        assertTrue("invalid Fable cares file" in bad.stderr, bad.stderr)

        val other = table(data(fable().replace("\"syn\"", "\"abc\"")))
        assertEquals(ExitCode.ERROR, other.exit)
        assertTrue("is for set 'abc', not 'syn'" in other.stderr, other.stderr)

        val strong = table(data(fable(card("syn/5", "creature", 4))))
        assertEquals(ExitCode.ERROR, strong.exit)
        assertEquals(1, strong.stderrLines().size, strong.stderr)
        assertTrue("invalid Fable cares file" in strong.stderr, strong.stderr)
    }

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(text.replace("\r\n", "\n").toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    /**
     * trk has no Fable file, so the table and check print what they printed after SPA-054. The
     * digest is of the output at 86f80b2 (origin/main before SPA-055) on the frozen v1 tags, with
     * the committed otags; the check lines are the two original checks, without their status, which
     * the owner changes.
     */
    @Test
    fun `trk has no Fable file, and its table and checks are unchanged`() {
        assertFalse(Files.exists(Fixtures.root.resolve("otags/trk.fable.json")))
        val v1 = arrayOf("--tags", Fixtures.v1Tags("trk"), "--data", Fixtures.data)

        val table = runCli("synergy-table", "--set", "trk", *v1)
        assertEquals(ExitCode.OK, table.exit, table.stderr)
        assertEquals(10522, table.stdoutLines().size)
        assertEquals(
            "12aebdb11ad9e2e084f22c41d2c2e02b7c42d097280f011abb1671722df23ab7",
            sha256(table.stdout),
        )

        val check = runCli("synergy-check", "--set", "trk", *v1)
        assertEquals(ExitCode.OK, check.exit, check.stderr)
        val lines =
            check.stdoutLines().map { it.replace(Regex(" (independent|seen) "), " STATUS ") }
        assertEquals(
            "PASS automatons-with-itself STATUS score 3  axis 0  partner 3  cares 0",
            lines[0],
        )
        assertEquals(
            "PASS enters-with-beam-me-up STATUS 590 pairs  524 meeting min 1  share 0.8881  mean 1.7068",
            lines[1],
        )
    }
}
