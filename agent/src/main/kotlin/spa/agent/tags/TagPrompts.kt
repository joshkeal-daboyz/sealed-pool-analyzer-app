package spa.agent.tags

/** The tagging prompts, one resource file per pass and version. */
object TagPrompts {
    const val DEFAULT_VERSION = "v1"

    enum class Pass(val stem: String) {
        AXES("tags-axes"),
        ROLES("tags-roles"),
    }

    fun resource(pass: Pass, version: String): String = "/spa/agent/tags/${pass.stem}-$version.md"

    fun load(pass: Pass, version: String): String? = TODO("SPA-047 $pass $version")
}
