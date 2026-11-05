package spa.agent.facts

/**
 * `dies-trigger`: the front face has its own dies trigger, `When` or `Whenever`, then "this
 * creature", its full name or its short name, then "dies".
 */
object DiesTriggerRule : FactRule {
    override fun facts(card: FactCard): Set<String> =
        if (Triggers.has(card, listOf("this creature"), "dies")) setOf("dies-trigger")
        else emptySet()
}
