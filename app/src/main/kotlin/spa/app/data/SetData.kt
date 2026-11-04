package spa.app.data

import android.content.Context
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.DateTimeException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import spa.core.baseline.Baseline
import spa.core.brief.BriefLoader
import spa.core.brief.FormatBrief
import spa.core.cards.CardIndex

/** Opens one bundled file by its path below the `setdata/` assets folder. */
fun interface AssetSource {
    /** The file at [path], such as `scryfall/trk/cards.json`. Throws [IOException] if absent. */
    @Throws(IOException::class) fun open(path: String): InputStream
}

/** The set data the app analyzes pools with: the card index, the brief, and the baseline. */
data class LoadedSet(val index: CardIndex, val brief: FormatBrief, val baseline: Baseline)

/** The bundled set data could not be copied or loaded. */
class SetDataException(message: String, cause: Throwable) : Exception(message, cause)

/**
 * The bundled `trk` set data. Core's loaders read paths and assets are not paths, so the first
 * [load] copies the bundled files to [root] (under `Context.filesDir`), then loads them through
 * core. The copy is redone only when [stamp] changes, which [forContext] ties to the app's install
 * time, so an updated APK refreshes it. The loaded data is cached for the life of this object.
 */
class SetData(
    private val assets: AssetSource,
    private val root: File,
    private val stamp: String,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutex = Mutex()
    private var cached: LoadedSet? = null

    /** Test seam: how many times this object copied the bundled files to [root]. */
    internal var copies = 0
        private set

    /** Test seam: how many times this object read the copied files through core. */
    internal var reads = 0
        private set

    /**
     * The loaded set data, copied and read on the first call and cached after it. Runs on the IO
     * dispatcher. Throws [SetDataException] when a bundled file is missing or does not load.
     */
    suspend fun load(): LoadedSet =
        withContext(dispatcher) { mutex.withLock { cached ?: readFresh().also { cached = it } } }

    private fun readFresh(): LoadedSet =
        try {
            if (!isCopied()) copyAll()
            reads++
            LoadedSet(
                index = CardIndex.load(root.resolve(SCRYFALL).toPath(), SET),
                brief = BriefLoader.load(root.resolve(BRIEF).toPath()),
                baseline = Baseline.decode(root.resolve(BASELINE).readText()),
            )
        } catch (e: IOException) {
            throw SetDataException("cannot load the bundled $SET set data", e)
        } catch (e: IllegalArgumentException) {
            throw SetDataException("cannot load the bundled $SET set data", e)
        } catch (e: DateTimeException) {
            throw SetDataException("cannot load the bundled $SET set data", e)
        }

    private fun isCopied(): Boolean {
        val marker = root.resolve(MARKER)
        return marker.isFile && marker.readText() == stamp
    }

    /** Copies every file into a fresh folder beside [root], then swaps it in, marker last. */
    private fun copyAll() {
        val staging = File(root.parentFile, root.name + ".tmp")
        staging.deleteRecursively()
        FILES.forEach { path ->
            val target = staging.resolve(path)
            target.parentFile?.mkdirs()
            assets.open(path).use { input ->
                Files.copy(input, target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }
        staging.resolve(MARKER).writeText(stamp)
        root.deleteRecursively()
        if (!staging.renameTo(root)) throw IOException("cannot move $staging to $root")
        copies++
    }

    companion object {
        /** The set the app reads. */
        const val SET = "trk"

        /** The assets folder the build copies the set data into. */
        const val ASSET_DIR = "setdata"

        private const val SCRYFALL = "scryfall"
        private const val BRIEF = "briefs/$SET.json"
        private const val BASELINE = "baselines/$SET.json"
        private const val MARKER = "copied.txt"
        private val SCRYFALL_FILES =
            listOf("cards.json", "companions.json", "set.json", "fetched-at.txt")

        /** Every bundled file, relative to [ASSET_DIR]. */
        val FILES: List<String> =
            listOf(SET, "sds").flatMap { set -> SCRYFALL_FILES.map { "$SCRYFALL/$set/$it" } } +
                listOf(BRIEF, BASELINE)

        /** The set data bundled in this app's APK, copied under `filesDir/setdata`. */
        fun forContext(context: Context): SetData {
            val app = context.applicationContext
            val installed = app.packageManager.getPackageInfo(app.packageName, 0).lastUpdateTime
            return SetData(
                assets = { path -> app.assets.open("$ASSET_DIR/$path") },
                root = app.filesDir.resolve(ASSET_DIR),
                stamp = installed.toString(),
            )
        }
    }
}
