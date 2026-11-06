package spa.gate

/**
 * Which modules one gate run checks, from the files the change touched.
 *
 * Documentation paths (under `tickets/` or `design/`, or ending in `.md`) never select a module. A
 * change made only of them is [docsOnly]: `pipeline/gate/run.sh` skips the gate before Gradle
 * starts, and Gradle itself, if run directly, checks everything. [full] runs every module, the app
 * and the `build-logic` tests included.
 */
data class GateScope(val modules: Set<String>, val full: Boolean, val docsOnly: Boolean) {
    val runsApp: Boolean
        get() = APP in modules

    companion object {
        const val CORE = "core"
        const val AGENT = "agent"
        const val CLI = "cli"
        const val APP = "app"
        const val BUILD_LOGIC = "build-logic"

        /** The modules a full run checks, in the order notes list them. */
        val ALL_MODULES = listOf(CORE, AGENT, CLI, APP)

        private val NOTE_ORDER = listOf(CORE, AGENT, CLI, APP, BUILD_LOGIC)
        private val DOC_DIRS = listOf("tickets/", "design/")
        private const val FIXTURES = "fixtures"

        private val FULL = GateScope(ALL_MODULES.toSet(), full = true, docsOnly = false)

        /** A path under `tickets/` or `design/`, or any Markdown file. Matches `run.sh`. */
        fun isDocumentation(path: String): Boolean {
            val p = normalize(path)
            return DOC_DIRS.any { p.startsWith(it) } || p.endsWith(".md")
        }

        /**
         * The scope for [changed] files (repository-relative). [fullFlag] is `--full`;
         * [hasMergeBase] is false when the merge-base with `main` could not be found.
         */
        fun of(changed: List<String>, fullFlag: Boolean, hasMergeBase: Boolean): GateScope {
            if (fullFlag || !hasMergeBase) return FULL
            val code = changed.map(::normalize).filterNot(::isDocumentation)
            if (code.isEmpty()) {
                return if (changed.isEmpty()) FULL else FULL.copy(full = true, docsOnly = true)
            }
            val tops = code.map { it.substringBefore('/', missingDelimiterValue = "") }.toSet()
            if (tops.any { it !in MODULE_DIRS }) return FULL
            val modules = buildSet {
                when {
                    CORE in tops || FIXTURES in tops -> addAll(listOf(CORE, AGENT, CLI))
                    AGENT in tops -> addAll(listOf(AGENT, CLI))
                    CLI in tops -> add(CLI)
                }
                if (APP in tops) add(APP)
            }
            return GateScope(modules, full = false, docsOnly = false)
        }

        /** The unit-tests step's pass note: the modules whose tests ran, in a fixed order. */
        fun unitTestsNote(modules: Collection<String>): String {
            val ordered = modules.distinct().sortedBy { NOTE_ORDER.indexOf(it) }
            return "modules: " + ordered.joinToString(", ").ifEmpty { "none" }
        }

        private val MODULE_DIRS = setOf(CORE, AGENT, CLI, APP, FIXTURES)

        private fun normalize(path: String) = path.replace('\\', '/').removePrefix("./")
    }
}
