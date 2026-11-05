package spa.agent.tags

/**
 * The tagging prompts, one resource file per pass and version under `spa/agent/tags/`:
 * `tags-axes-<version>.md` and `tags-roles-<version>.md`. A new version is a new pair of files,
 * selected with `--prompt <version>`, and needs no code change.
 */
object TagPrompts {
    /** The version used when none is given. */
    const val DEFAULT_VERSION = "v1"

    private val versionPattern = Regex("[a-z0-9]+")

    /** The two passes: naming the set's axes, then tagging each card's roles and partners. */
    enum class Pass(val stem: String) {
        AXES("tags-axes"),
        ROLES("tags-roles"),
    }

    /** The classpath location of [pass]'s prompt at [version]. */
    fun resource(pass: Pass, version: String): String = "/spa/agent/tags/${pass.stem}-$version.md"

    /**
     * The text of [pass]'s prompt at [version], with line feeds only, or null when there is no such
     * file or [version] is not lowercase letters and digits.
     */
    fun load(pass: Pass, version: String): String? {
        if (!versionPattern.matches(version)) return null
        val stream = TagPrompts::class.java.getResourceAsStream(resource(pass, version))
        return stream?.use { it.readBytes().decodeToString() }?.replace("\r\n", "\n")
    }
}
