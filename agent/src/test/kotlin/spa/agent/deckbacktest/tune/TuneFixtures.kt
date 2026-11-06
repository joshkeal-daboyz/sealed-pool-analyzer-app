package spa.agent.deckbacktest.tune

/** The deck weights the tune tests start from: the default numbers, pairs only. */
internal const val START =
    """
    {
      "version": 1,
      "weights": {
        "grades": 1.0, "curve": 1.0, "creatures": 1.0, "removal": 1.0,
        "castability": 1.0, "fixing": 1.0, "synergy": 0
      },
      "params": {
        "curve": { "target1": 1, "target2": 5, "target3": 4, "target4": 3, "target5": 2, "target6": 1 },
        "creatures": { "minCreatures": 14 },
        "removal": { "cap": 6 }
      },
      "lands": { "lowCurve": 2.6, "highCurve": 3.4, "low": 16, "normal": 17, "high": 18 },
      "splash": { "maxCards": 3, "maxPips": 1 },
      "configs": { "pairs": { "enabled": true } }
    }
    """

/** The grid text of [knobs]: each path with its values. */
internal fun gridText(vararg knobs: Pair<String, List<Number>>): String =
    knobs.joinToString(prefix = "{", postfix = "}") { (path, values) ->
        "\"$path\": [${values.joinToString(", ")}]"
    }
