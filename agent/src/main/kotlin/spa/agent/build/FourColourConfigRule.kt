package spa.agent.build

/**
 * The four-colour sets as main colours, each only when the pool holds at least `minFixing` cards
 * (lands or spells, counted by copy) that supply two or more of the set's colours (SPA-045).
 */
object FourColourConfigRule : ConfigRule {
    override val key = "fourColour"

    override val parameters = listOf("minFixing")

    private const val FOUR = 4
    private const val SUPPLIES = 2

    override fun configs(pool: List<Candidate>, params: ConfigParams): List<BuildConfig> {
        val minimum = params.whole("minFixing")
        return ConfigRules.colourSets(FOUR)
            .filter { colours ->
                pool
                    .filter { c ->
                        (c.card.fixing?.colours?.count { it in colours } ?: 0) >= SUPPLIES
                    }
                    .sumOf { it.quantity } >= minimum
            }
            .map { BuildConfig(it) }
    }
}
