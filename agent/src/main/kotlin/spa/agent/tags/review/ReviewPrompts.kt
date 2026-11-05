package spa.agent.tags.review

/** The review prompts, `spa/agent/tags/tags-review-<version>.md`. */
object ReviewPrompts {
    const val DEFAULT_VERSION = "v1"

    fun resource(version: String): String = "/spa/agent/tags/tags-review-$version.md"

    fun load(version: String): String? = TODO()
}
