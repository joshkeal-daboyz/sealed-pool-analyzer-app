package spa.gate

import java.io.File

/** The few git queries the gate needs. Every query returns null when git fails. */
internal class Git(private val root: File) {
    fun shortHead(): String = run("rev-parse", "--short", "HEAD")?.trim() ?: "unknown"

    /** The current branch name, or null when HEAD is detached. */
    fun branch(): String? =
        run("rev-parse", "--abbrev-ref", "HEAD")?.trim()?.takeIf { it != "HEAD" }

    /** `git merge-base origin/main HEAD`, falling back to local `main`. */
    fun mergeBase(): String? =
        listOf("origin/main", "main").firstNotNullOfOrNull { ref ->
            run("merge-base", ref, "HEAD")?.trim()?.takeIf { it.isNotEmpty() }
        }

    /** Files changed between [base] and the working tree, plus untracked files. */
    fun changedSince(base: String?): List<String> {
        val tracked = run("diff", "--name-only", base ?: "HEAD").orEmpty().lines()
        val untracked = run("ls-files", "--others", "--exclude-standard").orEmpty().lines()
        return (tracked + untracked).filter { it.isNotBlank() }
    }

    /** The contents of [path] at [revision], or null if it did not exist there. */
    fun show(revision: String, path: String): String? = run("show", "$revision:$path")

    private fun run(vararg args: String): String? {
        val process =
            ProcessBuilder(listOf("git") + args)
                .directory(root)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
        val output = process.inputStream.bufferedReader().readText()
        return if (process.waitFor() == 0) output else null
    }
}
