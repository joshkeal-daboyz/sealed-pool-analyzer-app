package spa.app.data

import java.io.File

/** The repository's `fixtures/` folder, which the build passes to app tests. */
object TestFixtures {
    val root: File =
        File(checkNotNull(System.getProperty("spa.fixtures")) { "spa.fixtures is not set" })

    /** The text of `fixtures/<path>`, read as `spa analyze` reads a pool file. */
    fun text(path: String): String = root.resolve(path).readText()

    /** Set data read straight from `fixtures/`, as the build bundles it, copied under [dir]. */
    fun setData(dir: File, stamp: String = "test"): SetData =
        SetData(
            assets = { path -> root.resolve(path).inputStream() },
            root = dir.resolve("setdata"),
            stamp = stamp,
        )
}
