package spa.cli

import java.nio.file.Path
import java.util.Locale
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import spa.core.brief.BriefLoader

/**
 * Criterion 2: the generated `syn` packet is blind. No file names win rates or 17Lands in any case,
 * and no file uses a field name of the win-rate tables under `fixtures/winrates/`.
 *
 * The win-rate files share some field names with what the ticket puts in the packet: every card has
 * a `name`, and every response a `score` and a `confidence`. Those are left out of the check, since
 * the packet must carry them. The rest (`gihwr`, `games`, `collectorNumber`, `setCode`,
 * `provenance`, `source` and the rest) must not appear as a key in any JSON file, nor quoted or in
 * backticks, the way a field is named, in `instructions.md`. A plain word is not a field name: the
 * `syn` brief's speed notes say "Most games are decided ...", which is format text, not a field.
 */
class BlindPacketTest {
    @TempDir lateinit var temp: Path

    private lateinit var packet: Path

    /** Field names the ticket requires in the packet's cards and responses. */
    private val packetFields =
        setOf(
            "id",
            "name",
            "manaCost",
            "typeLine",
            "rarity",
            "oracleText",
            "power",
            "toughness",
            "score",
            "confidence",
        )

    private val forbiddenText = listOf("winrate", "gihwr", "17lands", "win rate")

    @BeforeEach
    fun generate() {
        packet = temp.resolve("packet")
        val run = runCli("grade-packet", "syn", "--out", packet.toString(), "--data", Fixtures.data)
        assertEquals(ExitCode.OK, run.exit, run.stderr)
    }

    private fun files(): List<Path> = packet.listDirectoryEntries().sortedBy { it.name }

    private fun keys(element: JsonElement): Set<String> =
        when (element) {
            is JsonObject -> element.keys + element.values.flatMap(::keys)
            is JsonArray -> element.flatMap(::keys).toSet()
            else -> emptySet()
        }

    /** Every field name used in the committed win-rate tables. */
    private fun winRateFields(): Set<String> =
        Fixtures.root
            .resolve("winrates")
            .listDirectoryEntries("*.json")
            .flatMap { keys(Json.parseToJsonElement(it.readText())) }
            .toSet()

    @Test
    fun `no packet file mentions win rates or 17Lands in any case`() {
        val found =
            files().flatMap { file ->
                val text = file.readText().lowercase(Locale.ROOT)
                forbiddenText.filter { it in text }.map { "${file.name}: $it" }
            }

        assertEquals(emptyList(), found)
    }

    @Test
    fun `no packet file uses a field name of the win-rate tables`() {
        val fields = winRateFields() - packetFields
        assertTrue("gihwr" in fields && "games" in fields, "the check is not empty: $fields")

        val found =
            files().flatMap { file ->
                val text = file.readText()
                val hits =
                    if (file.name.endsWith(".json")) {
                        keys(Json.parseToJsonElement(text)).intersect(fields)
                    } else {
                        fields.filter { "\"$it\"" in text || "`$it`" in text }
                    }
                hits.map { "${file.name}: $it" }
            }

        assertEquals(emptyList(), found)
    }

    @Test
    fun `the instructions carry the scale, the no-tools rule, and the brief's archetype names`() {
        val instructions = packet.resolve("instructions.md").readText()
        val brief = BriefLoader.load(Fixtures.root.resolve("briefs/syn.json"))

        assertTrue("0.0 to 5.0" in instructions)
        assertTrue("steps of 0.5" in instructions)
        assertTrue("Do not use the web" in instructions)
        assertTrue("only the files in this packet" in instructions)
        val missing = brief.archetypes.map { it.name }.filter { it !in instructions }
        assertEquals(emptyList(), missing)
        assertEquals(10, brief.archetypes.size)
    }
}
