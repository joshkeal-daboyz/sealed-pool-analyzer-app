package spa.agent.poolgrade

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/**
 * The pool adjustment's weights: the most each part moves a grade, by the part's key
 * ([CardPart.key]), and the [cap] on their sum, in grade points. A part with no weight has weight 0
 * and is off, so a weight of 0 and a missing weight are the same weights.
 */
class Weights(weights: Map<String, Double>, val cap: Double) {
    private val byKey: Map<String, Double> = weights.filterValues { it != 0.0 }

    init {
        (weights + (CAP to cap)).forEach { (key, value) ->
            require(value.isFinite() && value >= 0.0) { "'$key' must be a number, 0 or more" }
        }
    }

    /** G-004's three parts and the cap, with [tagSynergy] (SPA-048's part) 0 unless given. */
    constructor(
        synergy: Double,
        curve: Double,
        colour: Double,
        cap: Double,
        tagSynergy: Double = 0.0,
    ) : this(
        mapOf(
            SynergyCardPart.KEY to synergy,
            CurveCardPart.KEY to curve,
            ColourCardPart.KEY to colour,
            TagSynergyCardPart.KEY to tagSynergy,
        ),
        cap,
    )

    /** The weight of the part with [key]: 0 when it has none. */
    operator fun get(key: String): Double = byKey[key] ?: 0.0

    val synergy: Double
        get() = this[SynergyCardPart.KEY]

    val curve: Double
        get() = this[CurveCardPart.KEY]

    val colour: Double
        get() = this[ColourCardPart.KEY]

    val tagSynergy: Double
        get() = this[TagSynergyCardPart.KEY]

    override fun equals(other: Any?): Boolean =
        other is Weights && other.cap == cap && other.byKey == byKey

    override fun hashCode(): Int = HASH * cap.hashCode() + byKey.hashCode()

    override fun toString(): String =
        (byKey.toSortedMap().map { (key, value) -> "$key=$value" } + "cap=$cap").joinToString(
            ", ",
            "Weights(",
            ")",
        )

    companion object {
        /** `fixtures/weights/default.json`. */
        val DEFAULT = Weights(synergy = 0.5, curve = 0.25, colour = 0.25, cap = 1.0)

        private const val CAP = "cap"
        private const val HASH = 31

        /**
         * The weights in [text], a JSON object of numbers, each finite and not negative: `cap`, and
         * any of the keys of [parts]. A part missing from the file has weight 0. Anything else, an
         * unknown key among them, is an [IllegalArgumentException] naming it.
         */
        fun parse(text: String, parts: List<CardPart> = CardParts.ALL): Weights {
            val element =
                try {
                    Json.parseToJsonElement(text)
                } catch (e: SerializationException) {
                    throw IllegalArgumentException("not JSON: ${e.message}", e)
                }
            require(element is JsonObject) { "expected a JSON object" }
            val keys = parts.map { it.key }
            val unknown = element.keys.sorted().firstOrNull { it != CAP && it !in keys }
            require(unknown == null) {
                "unknown key '$unknown': expected $CAP and any of ${keys.joinToString(", ")}"
            }
            val values = element.keys.filter { it != CAP }.associateWith { number(element, it) }
            return Weights(values, number(element, CAP))
        }

        private fun number(element: JsonObject, key: String): Double {
            val primitive = element[key] as? JsonPrimitive
            val value = primitive?.takeUnless { it.isString }?.doubleOrNull
            require(value != null && value.isFinite() && value >= 0.0) {
                "'$key' must be a number, 0 or more"
            }
            return value
        }
    }
}
