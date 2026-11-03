package spa.core.baseline

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * The typical value of every flattened metric for a pool from one set, over [kits] sampled kits.
 *
 * @property setCode the set the kits were sampled from.
 * @property briefVersion the version of the brief whose kit template was sampled.
 * @property cacheFetchedAt the latest fetch time of the Scryfall caches the index was loaded from.
 * @property seed the first kit's seed; kit `i`, counted from 0, uses `seed + i`.
 * @property kits how many kits were sampled.
 * @property stats one [Stat] per [MetricFlattener] pointer, sorted by pointer.
 */
@Serializable
data class Baseline(
    val setCode: String,
    val briefVersion: Int,
    val cacheFetchedAt: String,
    val seed: Long,
    val kits: Int,
    val stats: Map<String, Stat>,
) {
    /** The baseline as pretty-printed JSON with every object's keys sorted, ending in a newline. */
    fun encode(): String =
        pretty.encodeToString(
            JsonElement.serializer(),
            sortKeys(Json.encodeToJsonElement(serializer(), this)),
        ) + "\n"

    companion object {
        private val pretty = Json { prettyPrint = true }

        /** The baseline [encode] wrote. */
        fun decode(text: String): Baseline = Json.decodeFromString(serializer(), text)

        private fun sortKeys(element: JsonElement): JsonElement =
            when (element) {
                is JsonObject ->
                    JsonObject(
                        element.entries
                            .sortedBy { it.key }
                            .associate { it.key to sortKeys(it.value) }
                    )
                is JsonArray -> JsonArray(element.map(::sortKeys))
                else -> element
            }
    }
}
