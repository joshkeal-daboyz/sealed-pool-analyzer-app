package spa.agent.tags

/** What reading an axes file gives: the [axes] when there are no [problems], else null. */
data class AxesResult(val axes: List<Axis>?, val problems: List<String>)

/** Reads and checks an axes file, `axes.response.json`. */
object AxesFile {
    const val MIN_AXES = 3
    const val MAX_AXES = 15

    fun parse(text: String, fileName: String): AxesResult = TODO("SPA-047 $text $fileName")
}
