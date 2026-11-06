package spa.agent.build

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import spa.core.math.LandMath

/**
 * The land count rule (G-005 Decided 3), every number from the weights file: [low] lands when the
 * spells' mean mana value is at or below [lowCurve]; [high] when the deck has three or more colours
 * and the mean is at or above [highCurve]; otherwise [normal].
 */
data class LandRule(
    val lowCurve: Double,
    val highCurve: Double,
    val low: Int,
    val normal: Int,
    val high: Int,
) {
    /** The land count for spells of mean mana value [mean] in a deck of [colours] colours. */
    fun count(mean: Double, colours: Int): Int =
        when {
            mean <= lowCurve -> low
            colours >= HIGH_COLOURS && mean >= highCurve -> high
            else -> normal
        }

    /**
     * 0 when [count] gives [landCount] for this deck, else above 1 and larger the further the mean
     * is from the range where it would.
     */
    fun distance(mean: Double, colours: Int, landCount: Int): Double {
        if (count(mean, colours) == landCount) return 0.0
        val away =
            when {
                landCount == low -> mean - lowCurve
                landCount == high && colours >= HIGH_COLOURS -> highCurve - mean
                landCount == high -> UNREACHABLE
                mean <= lowCurve -> lowCurve - mean
                else -> mean - highCurve
            }
        return 1.0 + away
    }

    private companion object {
        const val HIGH_COLOURS = 3
        const val UNREACHABLE = 1000.0
    }
}

/** The splash limits: at most [maxCards] splash cards, each with at most [maxPips] splash pips. */
data class SplashRule(val maxCards: Int, val maxPips: Double)

/**
 * The deck builder's weights file (`fixtures/weights/deck-default.json`, SPA-044): the [weights] of
 * each part by its key (a part with no weight has weight 0 and is off), each part's numbers in
 * [partParams], and the [lands] and [splash] rules. Nothing here is a constant in code.
 */
data class DeckWeights(
    val weights: Map<String, Double>,
    val partParams: Map<String, Map<String, Double>>,
    val lands: LandRule,
    val splash: SplashRule,
) {
    /** The weight of the part with [key]: 0 when it has none. */
    fun weight(key: String): Double = weights[key] ?: 0.0

    /** [part]'s numbers. */
    fun params(part: DeckPart): PartParams = PartParams(partParams[part.key].orEmpty())

    companion object {
        /** The only file version there is. */
        const val VERSION = 1

        private val TOP = listOf("version", "weights", "params", "lands", "splash")
        private val LAND_KEYS = listOf("lowCurve", "highCurve", "low", "normal", "high")
        private val SPLASH_KEYS = listOf("maxCards", "maxPips")

        /**
         * The weights in [text]: `version` (1), `weights` (a number, 0 or more, per key of
         * [parts]), `params` (each part's numbers, by [DeckPart.parameters]), `lands` and `splash`.
         * Anything else, an unknown key among them, is an [IllegalArgumentException] naming it.
         */
        fun parse(text: String, parts: List<DeckPart> = DeckParts.ALL): DeckWeights {
            val root = Fields.obj(Fields.parseJson(text), "the file")
            Fields.unknown(root, TOP, "the file")
            val version = (root["version"] as? JsonPrimitive)?.doubleOrNull
            require(version == VERSION.toDouble()) { "unsupported version: expected $VERSION" }
            val weights = numbers(Fields.section(root, "weights"), parts.map { it.key }, "weights")
            val params = partParams(Fields.section(root, "params"), parts, weights)
            return DeckWeights(
                weights,
                params,
                lands(Fields.section(root, "lands")),
                splash(Fields.section(root, "splash")),
            )
        }

        private fun numbers(
            element: JsonObject,
            allowed: List<String>,
            where: String,
        ): Map<String, Double> {
            Fields.unknown(element, allowed, "'$where'")
            return element.keys.associateWith { Fields.number(element, it) }
        }

        private fun partParams(
            element: JsonObject,
            parts: List<DeckPart>,
            weights: Map<String, Double>,
        ): Map<String, Map<String, Double>> {
            val withParams = parts.filter { it.parameters.isNotEmpty() }
            Fields.unknown(element, withParams.map { it.key }, "'params'")
            return withParams
                .mapNotNull { part ->
                    val values = element[part.key]
                    if (values == null) {
                        require((weights[part.key] ?: 0.0) == 0.0) {
                            "missing params for '${part.key}'"
                        }
                        null
                    } else {
                        val own = Fields.obj(values, "'${part.key}'")
                        Fields.unknown(own, part.parameters, "'${part.key}' of 'params'")
                        part.parameters.forEach {
                            require(it in own) { "missing parameter '$it' of '${part.key}'" }
                        }
                        part.key to own.keys.associateWith { Fields.number(own, it) }
                    }
                }
                .toMap()
        }

        private fun lands(element: JsonObject): LandRule {
            Fields.unknown(element, LAND_KEYS, "'lands'")
            LAND_KEYS.forEach { require(it in element) { "missing '$it' in 'lands'" } }
            val rule =
                LandRule(
                    Fields.number(element, "lowCurve"),
                    Fields.number(element, "highCurve"),
                    Fields.whole(element, "low"),
                    Fields.whole(element, "normal"),
                    Fields.whole(element, "high"),
                )
            require(rule.low <= rule.normal && rule.normal <= rule.high) {
                "land counts must be in order: low <= normal <= high"
            }
            require(rule.high <= LandMath.DEFAULT_DECK_SIZE) {
                "'high' must be at most ${LandMath.DEFAULT_DECK_SIZE}"
            }
            return rule
        }

        private fun splash(element: JsonObject): SplashRule {
            Fields.unknown(element, SPLASH_KEYS, "'splash'")
            SPLASH_KEYS.forEach { require(it in element) { "missing '$it' in 'splash'" } }
            return SplashRule(Fields.whole(element, "maxCards"), Fields.number(element, "maxPips"))
        }
    }
}

/** The JSON reading steps the weights file shares, each failing with a one line message. */
private object Fields {
    fun parseJson(text: String): JsonElement =
        try {
            Json.parseToJsonElement(text)
        } catch (e: SerializationException) {
            throw IllegalArgumentException("not JSON: ${e.message}", e)
        }

    fun obj(element: JsonElement?, what: String): JsonObject {
        require(element is JsonObject) { "expected $what to be a JSON object" }
        return element
    }

    fun section(root: JsonObject, name: String): JsonObject {
        require(name in root) { "missing '$name'" }
        return obj(root[name], "'$name'")
    }

    fun unknown(element: JsonObject, allowed: List<String>, where: String) {
        val bad = element.keys.sorted().firstOrNull { it !in allowed }
        require(bad == null) {
            "unknown key '$bad' in $where: expected ${allowed.joinToString(", ")}"
        }
    }

    fun number(element: JsonObject, key: String): Double {
        val primitive = element[key] as? JsonPrimitive
        val value = primitive?.takeUnless { it.isString }?.doubleOrNull
        require(value != null && value.isFinite() && value >= 0.0) {
            "'$key' must be a number, 0 or more"
        }
        return value
    }

    fun whole(element: JsonObject, key: String): Int {
        val value = number(element, key)
        require(value == Math.floor(value) && value <= Int.MAX_VALUE) {
            "'$key' must be a whole number, 0 or more"
        }
        return value.toInt()
    }
}
