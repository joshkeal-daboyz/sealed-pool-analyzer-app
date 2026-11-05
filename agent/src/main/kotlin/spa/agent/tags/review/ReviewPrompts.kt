package spa.agent.tags.review

/**
 * The tag review prompts, one resource file per version: `spa/agent/tags/tags-review-<version>.md`.
 * A new version is a new file, selected with `--prompt <version>`, and needs no code change.
 */
object ReviewPrompts {
    /** The version used when none is given. */
    const val DEFAULT_VERSION = "v1"

    private val versionPattern = Regex("[a-z0-9]+")

    /** The classpath location of the prompt at [version]. */
    fun resource(version: String): String = "/spa/agent/tags/tags-review-$version.md"

    /**
     * The prompt's text at [version], with line feeds only, or null when there is no such file or
     * [version] is not lowercase letters and digits.
     */
    fun load(version: String): String? {
        if (!versionPattern.matches(version)) return null
        val stream = ReviewPrompts::class.java.getResourceAsStream(resource(version))
        return stream?.use { it.readBytes().decodeToString() }?.replace("\r\n", "\n")
    }
}
