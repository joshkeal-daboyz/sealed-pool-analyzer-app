package spa.core.baseline

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import spa.core.metrics.PoolMetrics

/**
 * Turns a [PoolMetrics] into one number per JSON pointer, so the metrics of many pools can be
 * aggregated pointer by pointer.
 */
object MetricFlattener {
    /** The last segment of the entry that keeps a dropped list's length. */
    const val COUNT = "count"

    /**
     * Encodes [metrics] to JSON and walks it: one entry per numeric leaf, keyed by its JSON pointer
     * (for example `/pairs/WU/curve/buckets/2/creatures` or `/byColor/R/cards/common`). A list that
     * holds any non-numeric leaf, or no element at all, is dropped as a whole, names and numbers
     * alike, and its length is kept as `<pointer>/count`, such as `/fixingSources/count` and
     * `/pairs/WU/splashCandidates/count`. Any other non-numeric leaf is dropped.
     */
    fun flatten(metrics: PoolMetrics): Map<String, Double> =
        flatten(Json.encodeToJsonElement(PoolMetrics.serializer(), metrics))

    /** [flatten] for any JSON [element]; the root's pointer is the empty string. */
    internal fun flatten(element: JsonElement): Map<String, Double> {
        val out = linkedMapOf<String, Double>()
        walk(element, "", out)
        return out
    }

    /** [key] escaped as one JSON pointer segment: `~` as `~0`, `/` as `~1`. */
    internal fun escape(key: String): String = key.replace("~", "~0").replace("/", "~1")

    private fun walk(element: JsonElement, pointer: String, out: MutableMap<String, Double>) {
        when (element) {
            is JsonObject ->
                element.forEach { (key, value) -> walk(value, "$pointer/${escape(key)}", out) }
            is JsonArray ->
                if (element.isNotEmpty() && element.all(::isNumeric)) {
                    element.forEachIndexed { at, value -> walk(value, "$pointer/$at", out) }
                } else {
                    out["$pointer/$COUNT"] = element.size.toDouble()
                }
            is JsonPrimitive -> number(element)?.let { out[pointer] = it }
        }
    }

    /** Whether every leaf under [element] is a number. */
    private fun isNumeric(element: JsonElement): Boolean =
        when (element) {
            is JsonObject -> element.values.all(::isNumeric)
            is JsonArray -> element.all(::isNumeric)
            is JsonPrimitive -> number(element) != null
        }

    private fun number(primitive: JsonPrimitive): Double? =
        if (primitive.isString) null else primitive.doubleOrNull
}
