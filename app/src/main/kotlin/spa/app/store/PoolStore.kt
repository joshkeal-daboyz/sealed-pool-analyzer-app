package spa.app.store

import java.io.File
import spa.app.importing.PoolSource
import spa.core.intake.PoolFormat

/** What [PoolStore.read] found in the pool file. */
sealed interface SavedPool {
    /** There is no pool file. */
    data object None : SavedPool

    /** The pool file cannot be read back; [source] is the source it names, when it names one. */
    data class Unreadable(val source: PoolSource?) : SavedPool

    /** The pool file holds [text], read as [format], from [source]. */
    data class Pool(val text: String, val format: PoolFormat, val source: PoolSource) : SavedPool
}

/** The one remembered pool: `last-pool.json` in [dir], the app's `filesDir`. */
class PoolStore(dir: File) {
    /** The pool file. */
    val file: File = File(dir, FILE_NAME)

    /** Replaces the pool file with [text], [format], and [source]. */
    fun save(text: String, format: PoolFormat, source: PoolSource) {
        TODO("SPA-022")
    }

    /** What the pool file holds. */
    fun read(): SavedPool = TODO("SPA-022")

    /** Deletes the pool file. */
    fun clear() {
        TODO("SPA-022")
    }

    companion object {
        const val FILE_NAME = "last-pool.json"
    }
}
