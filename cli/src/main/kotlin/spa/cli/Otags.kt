package spa.cli

/** A set's Scryfall Tagger tags by packet id, and the cares tags they give by packet id. */
internal class OtagInputs(
    val otags: Map<String, List<String>>,
    val cares: Map<String, Map<String, Int>>,
)

/** The `--otags` option the synergy commands and `facts` share (SPA-054). */
internal object Otags {
    /** `--otags none`: no otags, as for a set with no otags file. */
    const val NONE = "none"

    /** The otags of [set]: [given], else `<data>/otags/<code>.json` when it exists; else null. */
    fun load(data: DataDir, set: String, given: String?): OtagInputs? = null
}
