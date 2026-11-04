package spa.app.names

import spa.core.metrics.ColorPair

/** A colour pair's name as the app shows it, such as "White–Blue (WU)". */
object PairNames {
    /** "<first colour>–<second colour> (<code>)", with an en dash between the colours. */
    fun of(pair: ColorPair): String =
        "${ColorNames.of(pair.first)}–${ColorNames.of(pair.second)} (${pair.name})"
}
