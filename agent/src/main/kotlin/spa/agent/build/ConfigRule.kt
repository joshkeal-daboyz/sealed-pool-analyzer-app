package spa.agent.build

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import spa.core.text.Color

/** A rule's numbers and flags from its section of `configs` in the weights file. */
class ConfigParams(
    private val numbers: Map<String, Double> = emptyMap(),
    private val flags: Map<String, Boolean> = emptyMap(),
) {
    /** The whole number [name]; an [IllegalArgumentException] when the file did not give it. */
    fun whole(name: String): Int =
        requireNotNull(numbers[name]) { "the weights file gives no '$name'" }.toInt()

    /** The flag [name]; an [IllegalArgumentException] when the file did not give it. */
    fun flag(name: String): Boolean =
        requireNotNull(flags[name]) { "the weights file gives no '$name'" }
}

/** One rule's section of the weights file: whether it is [enabled], and its [params]. */
class RuleSettings(val enabled: Boolean, val params: ConfigParams)

/**
 * The `configs` section of the deck weights file (SPA-045): each rule's [RuleSettings] by its key.
 * A rule missing from the section is off.
 */
class ConfigWeights(private val rules: Map<String, RuleSettings> = emptyMap()) {
    /** [rule]'s settings, or null when the file has no section for it. */
    fun settings(rule: ConfigRule): RuleSettings? = rules[rule.key]

    companion object {
        private const val ENABLED = "enabled"

        /**
         * The section [element]: an object with one object per rule key of [rules], each with an
         * optional `enabled` (a boolean, true when missing) and every name in the rule's
         * [ConfigRule.parameters] (whole numbers) and [ConfigRule.flags] (booleans). Anything else,
         * an unknown key among them, is an [IllegalArgumentException] naming it.
         */
        fun parse(element: JsonObject, rules: List<ConfigRule>): ConfigWeights {
            Fields.unknown(element, rules.map { it.key }, "'configs'")
            val settings =
                rules
                    .filter { it.key in element }
                    .associate { rule -> rule.key to settings(rule, element.getValue(rule.key)) }
            return ConfigWeights(settings)
        }

        private fun settings(rule: ConfigRule, value: JsonElement) =
            Fields.obj(value, "'${rule.key}'").let { own ->
                val where = "'${rule.key}' of 'configs'"
                Fields.unknown(own, listOf(ENABLED) + rule.parameters + rule.flags, where)
                (rule.parameters + rule.flags).forEach {
                    require(it in own) { "missing '$it' in $where" }
                }
                val enabled = if (ENABLED in own) flag(own, ENABLED) else true
                RuleSettings(
                    enabled,
                    ConfigParams(
                        rule.parameters
                            .associateWith { Fields.whole(own, it) }
                            .mapValues { it.value.toDouble() },
                        rule.flags.associateWith { flag(own, it) },
                    ),
                )
            }

        private fun flag(element: JsonObject, key: String): Boolean {
            val primitive = element[key] as? JsonPrimitive
            val value = primitive?.takeUnless { it.isString }?.booleanOrNull
            require(value != null) { "'$key' must be true or false" }
            return value
        }
    }
}

/**
 * One rule of the colour configurations a pool supports (G-005 deliverable 3), each its own class
 * behind this interface and its own file. The ranking tries every configuration the rules give;
 * adding, removing or reordering a rule touches the list and the rule's file, nothing else.
 *
 * Without the Java-compatibility `DefaultImpls` bridge: every caller is Kotlin.
 */
@JvmDefaultWithoutCompatibility
interface ConfigRule {
    /** The rule's key in the `configs` section of the weights file. */
    val key: String

    /** The names of the whole numbers the rule reads from its section. */
    val parameters: List<String>
        get() = emptyList()

    /** The names of the true or false flags the rule reads from its section. */
    val flags: List<String>
        get() = emptyList()

    /** The configurations the rule gives for the nonbasic cards of a [pool], with [params]. */
    fun configs(pool: List<Candidate>, params: ConfigParams): List<BuildConfig>
}

/** The rules the builder uses by default, and the way their configurations are put together. */
object ConfigRules {
    val ALL: List<ConfigRule> =
        listOf(PairsConfigRule, PairSplashConfigRule, TriplesConfigRule, FourColourConfigRule)

    /**
     * The configurations [rules] give for [pool] under [weights], in rule order, each once. A rule
     * with no section in [weights], or with `enabled` false, gives nothing.
     */
    fun configs(
        pool: List<Candidate>,
        weights: ConfigWeights,
        rules: List<ConfigRule> = ALL,
    ): List<BuildConfig> =
        rules
            .flatMap { rule ->
                val settings = weights.settings(rule)
                if (settings == null || !settings.enabled) emptyList()
                else rule.configs(pool, settings.params)
            }
            .distinct()

    /** Every set of [size] colours, in WUBRG order. */
    internal fun colourSets(size: Int, from: List<Color> = Color.entries): List<Set<Color>> =
        when {
            size == 0 -> listOf(emptySet())
            from.size < size -> emptyList()
            else ->
                colourSets(size - 1, from.drop(1)).map { setOf(from.first()) + it } +
                    colourSets(size, from.drop(1))
        }
}
