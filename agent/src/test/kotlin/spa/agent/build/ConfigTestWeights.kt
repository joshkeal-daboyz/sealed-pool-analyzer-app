package spa.agent.build

/** The deck weights file's text with a chosen `configs` section, for the configuration tests. */
internal object ConfigTestWeights {
    /** The default `configs` section (SPA-045). */
    const val DEFAULT =
        """
        "configs": {
          "pairs": { "enabled": true },
          "pairSplash": { "enabled": true, "maxSplashColours": 2 },
          "triples": { "enabled": true, "tripleSplash": true },
          "fourColour": { "enabled": true, "minFixing": 4 }
        }
        """

    fun text(configs: String = DEFAULT): String =
        """
        {
          "version": 1,
          "weights": { "grades": 1 },
          "params": {},
          "lands": { "lowCurve": 2.6, "highCurve": 3.4, "low": 16, "normal": 17, "high": 18 },
          "splash": { "maxCards": 3, "maxPips": 1 },
          $configs
        }
        """

    fun parse(
        configs: String = DEFAULT,
        rules: List<ConfigRule> = ConfigRules.ALL,
    ): ConfigWeights = DeckWeights.parse(text(configs), DeckParts.ALL, rules).configs
}
