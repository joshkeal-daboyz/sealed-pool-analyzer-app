package spa.cli

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * SPA-054 criteria 2 and 5: `--otags` on `facts` and the synergy commands, on set `syn` with a
 * hand-built otags file, cares table and tag file (no roles; Grizzly Bears names Counterspell, 1).
 *
 * Otags: Grizzly Bears `cantrip`, `draw`; Serra Angel `legends-matter`; Air Elemental `draw`. The
 * cares table maps `legends-matter` to `otag:draw` 2, so Serra Angel cares about both drawers.
 */
class OtagsCommandsTest {
    @TempDir lateinit var temp: Path

    private val otags =
        """
        {
          "set": "syn",
          "source": "hand-built",
          "updatedAt": "2026-11-05T22:00:00Z",
          "attribution": "Card tags from Scryfall Tagger (tagger.scryfall.com)",
          "cards": [
            { "id": "syn/1", "otags": ["cantrip", "draw"] },
            { "id": "syn/2", "otags": ["legends-matter"] },
            { "id": "syn/3", "otags": ["draw"] }
          ]
        }
        """

    private val cares =
        """{ "cares": { "legends-matter": [{ "fact": "otag:draw", "strength": 2 }] } }"""

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
                val partners = if (i == 0) """[{ "id": "syn/4", "strength": 1 }]""" else "[]"
                """{ "id": "syn/${i + 1}", "name": "$name", "roles": [], "partners": $partners }"""
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

    /**
     * A data directory for `syn` with its tag file, and the otags file and cares table if asked.
     */
    private fun data(withOtags: Boolean = true): Path {
        val data = Fixtures.synDataDir(temp.resolve(if (withOtags) "with" else "without"))
        writeFile(data, "tags/syn.json", tagFile())
        if (withOtags) {
            writeFile(data, "otags/syn.json", otags)
            writeFile(data, "otags/cares.json", cares)
        }
        return data
    }

    private fun table(data: Path, vararg extra: String) =
        runCli("synergy-table", "--set", "syn", "--data", data.toString(), *extra)

    @Test
    fun `synergy-table shows the cares part beside axis and partner when it is not 0`() {
        val run = table(data())

        assertEquals(ExitCode.OK, run.exit, run.stderr)
        assertEquals(
            listOf(
                "2  axis 0  partner 0  cares 2  Grizzly Bears (syn/1) with Serra Angel (syn/2)",
                "2  axis 0  partner 0  cares 2  Serra Angel (syn/2) with Air Elemental (syn/3)",
                "1  axis 0  partner 1  Grizzly Bears (syn/1) with Counterspell (syn/4)",
                "20 cards, 210 pairs with the diagonal, 3 nonzero, 0 nonzero on the diagonal, " +
                    "maximum 2",
            ),
            run.stdoutLines(),
        )
    }

    @Test
    fun `a set without an otags file, or --otags none, scores exactly as before`() {
        val before =
            listOf(
                "1  axis 0  partner 1  Grizzly Bears (syn/1) with Counterspell (syn/4)",
                "20 cards, 210 pairs with the diagonal, 1 nonzero, 0 nonzero on the diagonal, " +
                    "maximum 1",
            )

        val without = table(data(withOtags = false))
        val none = table(data(), "--otags", "none")

        assertEquals(before, without.stdoutLines(), without.stderr)
        assertEquals(before, none.stdoutLines(), none.stderr)
    }

    @Test
    fun `--otags reads the given file`() {
        val data = data()
        val elsewhere = writeFile(temp, "elsewhere/syn-otags.json", otags)
        Files.delete(data.resolve("otags/syn.json"))

        val run = table(data, "--otags", elsewhere)

        assertEquals(ExitCode.OK, run.exit, run.stderr)
        assertEquals("maximum 2", run.stdoutLines().last().substringAfterLast(", "))
    }

    @Test
    fun `a missing, invalid or other set's otags file is one line, exit 1, on every command`() {
        val data = data()
        val absent = temp.resolve("absent.json").toString()
        val pool = writeFile(temp, "pool.txt", "1 Grizzly Bears\n1 Serra Angel\n")
        val base = arrayOf("--set", "syn", "--data", data.toString())
        val commands =
            listOf(
                arrayOf("synergy-table", *base),
                arrayOf("synergy", pool, "--pair", "WG", *base),
                arrayOf("synergy-check", *base),
                arrayOf("facts", *base),
            )
        for (command in commands) {
            val run = runCli(*command, "--otags", absent)
            assertEquals(ExitCode.ERROR, run.exit, command.first())
            assertEquals(listOf("spa: otags file not found: $absent"), run.stderrLines())
        }

        val other = writeFile(temp, "other.json", otags.replace("\"syn\"", "\"trk\""))
        val otherRun = table(data, "--otags", other)
        assertEquals(ExitCode.ERROR, otherRun.exit)
        assertEquals(
            listOf("spa: otags file $other is for set 'trk', not 'syn'"),
            otherRun.stderrLines(),
        )

        val bad = writeFile(temp, "bad.json", "{}")
        val badRun = table(data, "--otags", bad)
        assertEquals(ExitCode.ERROR, badRun.exit)
        assertTrue(badRun.stderrLines().single().startsWith("spa: invalid otags file $bad"))

        writeFile(data, "otags/cares.json", "[]")
        val badCares = table(data)
        assertEquals(ExitCode.ERROR, badCares.exit)
        assertTrue(badCares.stderrLines().single().startsWith("spa: invalid cares table "))
    }

    @Test
    fun `facts gives each card's otag facts, and --card its cares tags`() {
        val data = data()
        val base = arrayOf("--set", "syn", "--data", data.toString())

        val counts = runCli("facts", *base).stdoutLines()
        val plain = runCli("facts", *base, "--otags", "none").stdoutLines()
        assertTrue("otag:draw 2" in counts, counts.toString())
        assertTrue("otag:cantrip 1" in counts, counts.toString())
        assertTrue(
            counts.none {
                it.startsWith("otag:") &&
                    it !in setOf("otag:draw 2", "otag:cantrip 1", "otag:legends-matter 1")
            }
        )
        assertEquals("20 cards, ${plain.size - 1 + 3} distinct facts", counts.last())

        val angel = runCli("facts", *base, "--card", "Serra Angel").stdoutLines().single()
        val angelPlain =
            runCli("facts", *base, "--card", "Serra Angel", "--otags", "none")
                .stdoutLines()
                .single()
        assertEquals(
            angelPlain.substringAfter(": ").split(" ").toSet() + "otag:legends-matter",
            angel.substringAfter(": ").substringBefore(" cares:").split(" ").toSet(),
        )
        assertTrue(angel.endsWith(" cares:otag:draw=2"), angel)
        assertTrue(angel.startsWith("Serra Angel (syn/2): "), angel)
        assertTrue(" cares:" !in angelPlain)
    }

    @Test
    fun `help names --otags on facts and the synergy commands`() {
        val help = runCli("--help").stdout

        assertTrue(
            "synergy-table --set <code> [--min <score>] [--tags <file>] [--otags <file>]" in help,
            help,
        )
        assertTrue("[--tags <file>] [--otags <file>] [--data <dir>]" in help, help)
        assertTrue(
            "synergy-check --set <code> [--tags <file>] [--otags <file>] [--data <dir>]" in help,
            help,
        )
        assertTrue(
            "facts --set <code> [--card <name or id>] [--otags <file>] [--data <dir>]" in help,
            help,
        )
    }
}
