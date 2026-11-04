package spa.app.nav

/** The routes of the spec's screen inventory. */
object Routes {
    const val POOL = "pool"
    const val RESULTS = "results"
    const val PAIR_ARG = "pair"
    const val PAIR = "pair/{$PAIR_ARG}"
    const val ABOUT = "about"

    val ALL = listOf(POOL, RESULTS, PAIR, ABOUT)

    /** The route of Pair detail for [code], `WU` to `RG`. */
    fun pair(code: String): String = "pair/$code"
}
