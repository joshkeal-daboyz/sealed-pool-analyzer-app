package spa.agent.build

import spa.core.text.Color

/**
 * The three-colour sets as main colours, each also with one splash colour when `tripleSplash`
 * (SPA-045).
 */
object TriplesConfigRule : ConfigRule {
    override val key = "triples"

    override val flags = listOf("tripleSplash")

    private const val TRIPLE = 3

    override fun configs(pool: List<Candidate>, params: ConfigParams): List<BuildConfig> {
        val splash = params.flag("tripleSplash")
        return ConfigRules.colourSets(TRIPLE).flatMap { triple ->
            listOf(BuildConfig(triple)) +
                if (splash) {
                    Color.entries.filter { it !in triple }.map { BuildConfig(triple, setOf(it)) }
                } else {
                    emptyList()
                }
        }
    }
}
