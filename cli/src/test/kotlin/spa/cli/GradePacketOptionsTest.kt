package spa.cli

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readBytes
import kotlin.io.path.readText
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import spa.agent.grader.GradeImport
import spa.agent.grader.GradingPackets
import spa.agent.themes.PoolThemes
import spa.core.brief.BriefLoader
import spa.core.cards.CardIndex

/** SPA-036: `spa grade-packet` with and without `--prompt <file>` and `--themes`. */
class GradePacketOptionsTest {
    @TempDir lateinit var temp: Path

    private fun packet(set: String, out: Path, vararg extra: String): CliRun {
        val run =
            runCli("grade-packet", set, "--out", out.toString(), "--data", Fixtures.data, *extra)
        assertEquals(ExitCode.OK, run.exit, run.stderr)
        return run
    }

    private fun sha256(file: Path): String =
        MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") {
            "%02x".format(it)
        }

    private fun cards(dir: Path): List<JsonObject> =
        dir.listDirectoryEntries()
            .filter { GradingPackets.isCardFile(it.name) }
            .sortedBy { it.name }
            .flatMap { Json.parseToJsonElement(it.readText()).jsonArray.map { e -> e.jsonObject } }

    private fun promptFile(text: String): Path {
        val file = temp.resolve("candidate.md")
        Files.writeString(file, text)
        return file
    }

    private val candidate = "# Candidate prompt\n\nGrade each card for this set's sealed pools.\n"

    /**
     * The SHA-256 of every file of the `trk` and `syn` packets as SPA-033's code wrote them, built
     * by `spa grade-packet <code> --out <dir> --data fixtures` at commit 1725a50, before SPA-036.
     */
    private val spa033 =
        mapOf(
            "trk" to
                mapOf(
                    "instructions.md" to
                        "64c92c00f5879a0a5415ebbc36facee919427b30d65cc1884f24ca4c3c2e67ad",
                    "response.schema.json" to
                        "aec8719df32da655f99f64ff0b61a0ea260dbf909e14a11b3384195812085b6c",
                    "cards-01.json" to
                        "383420617266c39e2f0dd80ce5de28c591876b7a869a1e1bcb462daf49860129",
                    "cards-02.json" to
                        "8a8f9c3d3b7edb08c3f42d2a1619ad16576777d3d99a0186a780d604531b6a4b",
                    "cards-03.json" to
                        "5fa37e278faf3e937b721ffda359fa013f26bbdbc4121c0bf558cae765d09377",
                    "cards-04.json" to
                        "2d0763bae079d69ab0d4fafb689b76f479bb603dc318b72e532427ea063b5bf1",
                    "cards-05.json" to
                        "a53b9f61549d777eb1dfcfe076c5af4b756cf235d7a151c13470a5fc96f24a40",
                    "cards-06.json" to
                        "f78f6c91410f56425d9d15971a6f885f9bcaa98861dbbb4da0638bd2536d422d",
                    "cards-07.json" to
                        "a583bb07fa14135472426b01dda44fa40af116ca82a06bb7852924446c0d2468",
                ),
            "syn" to
                mapOf(
                    "instructions.md" to
                        "2458af495002e2c00073edbf2bd73f4d89db1ca5f69de4f169468c12103cc20b",
                    "response.schema.json" to
                        "aec8719df32da655f99f64ff0b61a0ea260dbf909e14a11b3384195812085b6c",
                    "cards-01.json" to
                        "2644b1827a720181e3f5219e8abc7649b64c7526882dc7fb8e4f0327eaa1dc18",
                ),
        )

    // Criterion 1

    @Test
    fun `without the new options, trk and syn packets are byte-identical to SPA-033's`() {
        spa033.forEach { (set, digests) ->
            val out = temp.resolve(set)
            packet(set, out)
            assertEquals(digests.keys.sorted(), out.listDirectoryEntries().map { it.name }.sorted())
            digests.forEach { (name, digest) ->
                assertEquals(digest, sha256(out.resolve(name)), "$set $name")
            }
        }
    }

    @Test
    fun `without --themes, no theme appears anywhere in the packet`() {
        val out = temp.resolve("trk")
        packet("trk", out, "--prompt", promptFile(candidate).toString())

        assertFalse("Themes in this set" in out.resolve("instructions.md").readText())
        assertTrue(cards(out).none { "themes" in it })
    }

    // Criterion 2

    @Test
    fun `with --prompt, the instructions start with the file's text and the rest is unchanged`() {
        val plain = temp.resolve("plain")
        val custom = temp.resolve("custom")
        packet("trk", plain)
        packet("trk", custom, "--prompt", promptFile(candidate).toString())

        val before = plain.resolve("instructions.md").readText()
        val after = custom.resolve("instructions.md").readText()
        assertTrue(after.startsWith(candidate), after.take(200))
        assertFalse(GradingPackets.promptText.trimEnd() in after, "prompt v1 is replaced")
        assertEquals(
            before.removePrefix(GradingPackets.promptText.trimEnd()),
            after.removePrefix(candidate.trimEnd()),
        )
        plain
            .listDirectoryEntries()
            .filter { it.name != "instructions.md" }
            .forEach {
                assertContentEquals(it.readBytes(), custom.resolve(it.name).readBytes(), it.name)
            }
    }

    @Test
    fun `a prompt file with CRLF line ends is read with line feeds`() {
        val out = temp.resolve("crlf")
        packet("syn", out, "--prompt", promptFile(candidate.replace("\n", "\r\n")).toString())

        val instructions = out.resolve("instructions.md").readText()
        assertTrue(instructions.startsWith(candidate), instructions.take(200))
        assertFalse('\r' in instructions)
    }

    @Test
    fun `a missing prompt file prints one line and exits 1`() {
        val missing = temp.resolve("nope.md")
        val out = temp.resolve("o")

        val run =
            runCli(
                "grade-packet",
                "syn",
                "--out",
                out.toString(),
                "--data",
                Fixtures.data,
                "--prompt",
                missing.toString(),
            )

        assertEquals(ExitCode.ERROR, run.exit)
        assertEquals(listOf("spa: prompt not found: $missing"), run.stderrLines())
        assertTrue(Files.notExists(out))
    }

    @Test
    fun `an empty prompt file prints one line and exits 1`() {
        val empty = promptFile(" \n\n")
        val out = temp.resolve("o")

        val run =
            runCli(
                "grade-packet",
                "syn",
                "--out",
                out.toString(),
                "--data",
                Fixtures.data,
                "--prompt",
                empty.toString(),
            )

        assertEquals(ExitCode.ERROR, run.exit)
        assertEquals(listOf("spa: prompt $empty is empty"), run.stderrLines())
        assertTrue(Files.notExists(out))
    }

    // Criterion 3

    private fun themesSection(instructions: String): List<String> {
        val start = instructions.indexOf("## Themes in this set")
        assertTrue(start >= 0, "no themes section")
        val end = instructions.indexOf("\n## ", start + 1)
        return instructions.substring(start, if (end < 0) instructions.length else end).lines()
    }

    @Test
    fun `with --themes, trk's instructions hold one line per kept theme after the brief`() {
        val plain = temp.resolve("plain")
        val out = temp.resolve("themes")
        packet("trk", plain)
        packet("trk", out, "--themes")

        val instructions = out.resolve("instructions.md").readText()
        val section = themesSection(instructions)
        val spacecraft = section.single { it.startsWith("- Spacecraft (type):") }
        assertTrue(spacecraft.startsWith("- Spacecraft (type): has 17, refers 11;"), spacecraft)
        assertTrue(
            Regex("colours W=\\d+ U=\\d+ B=\\d+ R=\\d+ G=\\d+ M=\\d+ C=\\d+$")
                .containsMatchIn(spacecraft),
            spacecraft,
        )
        val station = section.single { it.startsWith("- Station (keyword):") }
        assertTrue(station.startsWith("- Station (keyword): has 17, refers 1;"), station)
        val index = CardIndex.load(Fixtures.root.resolve("scryfall"), "trk")
        val brief = BriefLoader.load(Fixtures.root.resolve("briefs/trk.json"))
        val kept =
            PoolThemes.count(
                Fixtures.root.resolve("scryfall"),
                index,
                brief.mechanics.map { it.name },
            )
        assertEquals(kept.size, section.count { it.startsWith("- ") })

        val brief033 = plain.resolve("instructions.md").readText()
        val themesAt = instructions.indexOf("## Themes in this set")
        assertTrue(themesAt > instructions.indexOf("### Archetypes"), "after the format brief")
        assertTrue(
            themesAt < instructions.indexOf("## The files in this packet"),
            "before the files",
        )
        val withoutSection =
            instructions.substring(0, themesAt) +
                instructions.substring(instructions.indexOf("## The files in this packet"))
        assertEquals(brief033, withoutSection, "the section is the only addition")
    }

    private fun themesOf(card: JsonObject): List<Pair<String, String>> =
        assertNotNull(card["themes"] as? JsonArray, "${card["id"]} has no themes").map {
            it.jsonObject["name"]!!.jsonPrimitive.content to
                it.jsonObject["role"]!!.jsonPrimitive.content
        }

    @Test
    fun `with --themes, every trk card lists its themes with roles`() {
        val out = temp.resolve("themes")
        packet("trk", out, "--themes")
        val cards = cards(out)

        assertEquals(336, cards.size)
        cards.forEach { themesOf(it) }
        val spacecraft = cards.filter { "Spacecraft" in it["typeLine"]!!.jsonPrimitive.content }
        assertEquals(17, spacecraft.size)
        spacecraft.forEach { assertTrue("Spacecraft" to "has" in themesOf(it), it.toString()) }
        val referring = cards.filter { "Spacecraft" to "refers" in themesOf(it) }
        assertEquals(11, referring.size)
        val text = out.resolve(cardFileOf(out, spacecraft.first())).readText()
        assertTrue(
            "{\n        \"name\": \"Spacecraft\",\n        \"role\": \"has\"\n      }" in text
        )
        assertTrue(cards.all { card -> themesOf(card).let { it.distinct() == it } })
    }

    private fun cardFileOf(dir: Path, card: JsonObject): String =
        dir.listDirectoryEntries()
            .filter { GradingPackets.isCardFile(it.name) }
            .first { "\"id\": ${card["id"]}" in it.readText() }
            .name

    // Criterion 4

    @Test
    fun `a trk packet built with both options passes the blind check`() {
        val out = temp.resolve("both")
        packet("trk", out, "--themes", "--prompt", promptFile(candidate).toString())

        assertTrue(out.resolve("instructions.md").readText().startsWith(candidate))
        assertTrue("## Themes in this set" in out.resolve("instructions.md").readText())
        assertEquals(emptyList(), BlindCheck.forbiddenTextHits(out))
        val fields = BlindCheck.winRateFields() - BlindCheck.packetFields
        assertTrue("gihwr" in fields && "games" in fields, "the check is not empty: $fields")
        assertEquals(emptyList(), BlindCheck.fieldNameHits(out, fields))
    }

    @Test
    fun `grade-import accepts responses to a trk packet built with both options`() {
        val out = temp.resolve("both")
        packet("trk", out, "--themes", "--prompt", promptFile(candidate).toString())
        val cardFiles = out.listDirectoryEntries().filter { GradingPackets.isCardFile(it.name) }
        cardFiles.forEach { file ->
            val entries =
                Json.parseToJsonElement(file.readText()).jsonArray.joinToString(",\n") {
                    "{\"id\": ${it.jsonObject["id"]}, \"score\": 2.5, \"confidence\": 0.5}"
                }
            Files.writeString(out.resolve(GradingPackets.responseFileName(file.name)), "[$entries]")
        }
        val index = CardIndex.load(Fixtures.root.resolve("scryfall"), "trk")

        val result = GradeImport.run(out, index, "trk", "test", "2026-11-04T00:00:00Z")

        assertEquals(emptyList(), result.problems)
        assertEquals(336, assertNotNull(result.file).grades.size)
    }

    @Test
    fun `the help names both options`() {
        val run = runCli("--help")
        val line = run.stdoutLines().single { "grade-packet" in it }
        assertTrue("[--prompt <file>]" in line && "[--themes]" in line, line)
    }
}
