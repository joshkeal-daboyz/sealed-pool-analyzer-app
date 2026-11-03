package spa.gate

import java.net.URI

/** Turns the absolute paths and file URIs that tools print into repository-relative paths. */
object RepoPaths {
    /**
     * [path] relative to [root] with forward slashes; unchanged (but slash-normalized) if outside.
     */
    fun relative(root: String, path: String): String {
        val base = normalize(root).trimEnd('/') + "/"
        val target = normalize(path)
        return if (target.startsWith(base, ignoreCase = true)) target.substring(base.length)
        else target
    }

    /** A `file:///D:/x/y.kt` or `file:/x/y.kt` URI as a plain path. */
    fun fromFileUri(uri: String): String {
        val path = URI(uri).path
        return if (Regex("^/[A-Za-z]:/").containsMatchIn(path)) path.substring(1) else path
    }

    private fun normalize(path: String): String = path.replace('\\', '/')
}
