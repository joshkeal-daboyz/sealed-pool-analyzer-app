package spa.cli

import java.nio.file.Path
import java.util.Locale
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * SPA-033 criterion 2's blind check, as ruled, on any packet directory: no file names win rates or
 * 17Lands in any case, and no file uses a field name of the win-rate tables under
 * `fixtures/winrates/` other than those the packet must carry ([packetFields]). A field name counts
 * as a key in a JSON file, or quoted or in backticks in `instructions.md`.
 */
internal object BlindCheck {
    /** Field names SPA-033 requires in the packet's cards and responses. */
    val packetFields =
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

    val forbiddenText =
        listOf(
            "winrate",
            "gihwr",
            "17lands",
            "win rate",
            "gih wr",
            "game in hand",
            "win %",
            "17 lands",
        )

    fun files(packet: Path): List<Path> = packet.listDirectoryEntries().sortedBy { it.name }

    fun keys(element: JsonElement): Set<String> =
        when (element) {
            is JsonObject -> element.keys + element.values.flatMap(::keys)
            is JsonArray -> element.flatMap(::keys).toSet()
            else -> emptySet()
        }

    /** Every field name used in the committed win-rate tables. */
    fun winRateFields(): Set<String> =
        Fixtures.root
            .resolve("winrates")
            .listDirectoryEntries("*.json")
            .flatMap { keys(Json.parseToJsonElement(it.readText())) }
            .toSet()

    /** `<file>: <text>` for each forbidden text found in a file of [packet]. */
    fun forbiddenTextHits(packet: Path): List<String> =
        files(packet).flatMap { file ->
            val text = file.readText().lowercase(Locale.ROOT)
            forbiddenText.filter { it in text }.map { "${file.name}: $it" }
        }

    /** `<file>: <field>` for each win-rate field name used in a file of [packet]. */
    fun fieldNameHits(packet: Path, fields: Set<String> = winRateFields() - packetFields) =
        files(packet).flatMap { file ->
            val text = file.readText()
            val hits =
                if (file.name.endsWith(".json")) {
                    keys(Json.parseToJsonElement(text)).intersect(fields)
                } else {
                    fields.filter { "\"$it\"" in text || "`$it`" in text }
                }
            hits.map { "${file.name}: $it" }
        }
}
