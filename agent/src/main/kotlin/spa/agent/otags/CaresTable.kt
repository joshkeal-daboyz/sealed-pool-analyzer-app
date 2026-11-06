package spa.agent.otags

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import spa.agent.facts.FactRules
import spa.agent.facts.OtagFactRule

/** One fact a cares tag cares about, with its [strength], 1 to 3. */
data class CaresEntry(val fact: String, val strength: Int)

/**
 * The cares table, `fixtures/otags/cares.json` (SPA-054): Scryfall's "cares about" tags, each with
 * the facts it cares about. A card with such a tag gets `cares:<fact>` at the strength given, for
 * every fact listed; when two of its tags give the same fact, the largest strength is kept. The
 * table is data: a new tag is a new entry, with no code change.
 */
class CaresTable(val cares: Map<String, List<CaresEntry>>) {
    /** The cares tags of a card with [otags]: each fact it cares about, with its strength. */
    fun of(otags: Collection<String>): Map<String, Int> {
        val strengths = mutableMapOf<String, Int>()
        for (entry in otags.flatMap { cares[it].orEmpty() }) {
            strengths.merge(entry.fact, entry.strength, ::maxOf)
        }
        return strengths
    }

    /** [of] for each card of [otags], by id. */
    fun pool(otags: Map<String, List<String>>): Map<String, Map<String, Int>> = otags.mapValues {
        of(it.value)
    }

    /**
     * One line for each fact of the table that is not a known fact name: a code fact family
     * ([FactRules.knows]) or `otag:<slug>` of a tag in the bulk file, whose slugs are [bulkSlugs].
     */
    fun unknown(bulkSlugs: Set<String>): List<String> = cares.flatMap { (tag, entries) ->
        entries
            .filterNot { known(it.fact, bulkSlugs) }
            .map { "tag '$tag': unknown fact '${it.fact}'" }
    }

    private fun known(fact: String, bulkSlugs: Set<String>): Boolean =
        FactRules.knows(fact) ||
            (fact.startsWith(OtagFactRule.PREFIX) &&
                fact.removePrefix(OtagFactRule.PREFIX) in bulkSlugs)

    companion object {
        val EMPTY = CaresTable(emptyMap())

        private const val MAX_STRENGTH = 3

        /**
         * The table in [text]: a JSON object whose `cares` maps each tag's slug to a list of `{
         * "fact", "strength" }`, strength 1 to 3; other keys (a `comment`) are ignored. Anything
         * else is an [IllegalArgumentException].
         */
        fun parse(text: String): CaresTable {
            val root =
                try {
                    Json.parseToJsonElement(text)
                } catch (e: SerializationException) {
                    throw IllegalArgumentException("not JSON: ${e.message}", e)
                }
            val cares = (root as? JsonObject)?.get("cares")
            require(cares is JsonObject) { "expected an object with a 'cares' object" }
            return CaresTable(cares.mapValues { (tag, entries) -> entries(tag, entries) })
        }

        private fun entries(tag: String, element: JsonElement): List<CaresEntry> {
            require(element is JsonArray) { "tag '$tag': expected a list of facts" }
            return element.map { entry ->
                val fields = entry as? JsonObject
                val fact = (fields?.get("fact") as? JsonPrimitive)?.takeIf { it.isString }
                val strength =
                    (fields?.get("strength") as? JsonPrimitive)
                        ?.takeUnless { it.isString }
                        ?.intOrNull
                require(fact != null && strength != null && strength in 1..MAX_STRENGTH) {
                    "tag '$tag': each entry needs a 'fact' and a 'strength' from 1 to 3"
                }
                CaresEntry(fact.content, strength)
            }
        }
    }
}
