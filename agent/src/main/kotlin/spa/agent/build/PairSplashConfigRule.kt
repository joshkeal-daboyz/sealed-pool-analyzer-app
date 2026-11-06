package spa.agent.build

import spa.core.text.Color

/**
 * Each two-colour pair with one splash colour and, up to `maxSplashColours` (at most
 * [BuildConfig.MAX_SPLASH]), with more (SPA-045).
 */
object PairSplashConfigRule : ConfigRule {
    override val key = "pairSplash"

    override val parameters = listOf("maxSplashColours")

    private const val PAIR = 2

    override fun configs(pool: List<Candidate>, params: ConfigParams): List<BuildConfig> {
        val most = minOf(params.whole("maxSplashColours"), BuildConfig.MAX_SPLASH)
        return ConfigRules.colourSets(PAIR).flatMap { pair ->
            val others = Color.entries.filter { it !in pair }
            (1..most).flatMap { size ->
                ConfigRules.colourSets(size, others).map { BuildConfig(pair, it) }
            }
        }
    }
}
