package spa.core.brief

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * The checks JSON Schema cannot express. Each reads the raw document and skips any part whose shape
 * is wrong, since the schema already reports that part.
 */
internal object BriefRules {
    private const val COLORS = "WUBRG"

    /** The ten color pairs, each written in WUBRG order. */
    val colorPairs: Set<String> =
        COLORS.indices
            .flatMap { i -> (i + 1 until COLORS.length).map { j -> "${COLORS[i]}${COLORS[j]}" } }
            .toSet()

    fun check(root: JsonElement): List<BriefError> {
        val doc = root as? JsonObject ?: return emptyList()
        return colorPairErrors(doc["archetypes"] as? JsonArray) +
            slotSumErrors(doc["kitTemplate"] as? JsonObject)
    }

    /** [colors] as a pair in WUBRG order, or null when it is not two distinct WUBRG letters. */
    fun normalizePair(colors: String): String? =
        colors
            .takeIf { it.length == 2 && it[0] != it[1] && it.all { c -> c in COLORS } }
            ?.toList()
            ?.sortedBy { COLORS.indexOf(it) }
            ?.joinToString("")

    /** Each archetype after the first with a given pair, and the pairs no archetype has. */
    private fun colorPairErrors(archetypes: JsonArray?): List<BriefError> {
        if (archetypes == null) return emptyList()
        val firstIndex = mutableMapOf<String, Int>()
        val errors = mutableListOf<BriefError>()
        archetypes.forEachIndexed { index, archetype ->
            val pair = (archetype as? JsonObject)?.get("colors").string()?.let(::normalizePair)
            val first = pair?.let { firstIndex.putIfAbsent(it, index) }
            if (first != null) {
                errors +=
                    BriefError(
                        "/archetypes/$index/colors",
                        "color pair $pair repeats /archetypes/$first/colors; " +
                            "the ten archetypes must cover the ten color pairs once each",
                    )
            }
        }
        val missing = colorPairs - firstIndex.keys
        if (missing.isNotEmpty()) {
            errors +=
                BriefError(
                    "/archetypes",
                    "no archetype for color pair(s) ${missing.joinToString()}",
                )
        }
        return errors
    }

    /** An error when the slots' counts do not sum to cardsPerBooster. */
    private fun slotSumErrors(kit: JsonObject?): List<BriefError> {
        val perBooster = kit?.get("cardsPerBooster").int()
        val counts =
            (kit?.get("slots") as? JsonArray)?.map { (it as? JsonObject)?.get("count").int() }
        if (perBooster == null || counts == null || counts.any { it == null }) return emptyList()
        val sum = counts.sumOf { it ?: 0 }
        return if (sum == perBooster) {
            emptyList()
        } else {
            listOf(
                BriefError(
                    "/kitTemplate/slots",
                    "slot counts sum to $sum but cardsPerBooster is $perBooster",
                )
            )
        }
    }

    private fun JsonElement?.string(): String? =
        (this as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonElement?.int(): Int? =
        (this as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull
}
