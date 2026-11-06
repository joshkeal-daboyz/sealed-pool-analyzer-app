package spa.agent.build

/** The two-colour pairs, with no splash (SPA-045). */
object PairsConfigRule : ConfigRule {
    override val key = "pairs"

    private const val PAIR = 2

    override fun configs(pool: List<Candidate>, params: ConfigParams): List<BuildConfig> =
        ConfigRules.colourSets(PAIR).map { BuildConfig(it) }
}
