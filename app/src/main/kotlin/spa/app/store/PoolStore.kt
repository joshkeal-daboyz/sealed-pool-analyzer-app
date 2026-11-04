package spa.app.store

import android.util.Log
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.json.JSONException
import org.json.JSONObject
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

/**
 * The one remembered pool (deliverable 7): `last-pool.json` in [dir], the app's `filesDir`, holding
 * exactly `text`, `format` (the [PoolFormat] name), and `source` (the file name, or `pasted`).
 * Written atomically: a temporary file beside it, then a rename over it.
 */
class PoolStore(dir: File) {
    /** The pool file. */
    val file: File = File(dir, FILE_NAME)

    private val temporary = File(dir, "$FILE_NAME.tmp")

    /** Replaces the pool file with [text], [format], and [source]. */
    fun save(text: String, format: PoolFormat, source: PoolSource) {
        val json =
            JSONObject()
                .put(TEXT, text)
                .put(FORMAT, format.name)
                .put(SOURCE, sourceName(source))
                .toString()
        try {
            temporary.writeText(json, Charsets.UTF_8)
            Files.move(
                temporary.toPath(),
                file.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (e: IOException) {
            // A full disk: the pool shows now, but is not remembered; the old file is untouched.
            Log.w(TAG, "Could not save the pool", e)
            temporary.delete()
        }
    }

    /** What the pool file holds. */
    fun read(): SavedPool {
        if (!file.exists()) return SavedPool.None
        val json =
            try {
                JSONObject(file.readText(Charsets.UTF_8))
            } catch (e: JSONException) {
                return SavedPool.Unreadable(null)
            } catch (e: IOException) {
                Log.w(TAG, "Could not read the saved pool", e)
                return SavedPool.Unreadable(null)
            }
        val source = json.stringOrNull(SOURCE)?.let(::sourceOf)
        val text = json.stringOrNull(TEXT)
        val format =
            json.stringOrNull(FORMAT)?.let { name ->
                PoolFormat.entries.firstOrNull { it.name == name }
            }
        return if (source != null && text != null && format != null) {
            SavedPool.Pool(text, format, source)
        } else {
            SavedPool.Unreadable(source)
        }
    }

    /** Deletes the pool file. */
    fun clear() {
        file.delete()
    }

    companion object {
        const val FILE_NAME = "last-pool.json"

        private const val TAG = "SpaStore"
        private const val TEXT = "text"
        private const val FORMAT = "format"
        private const val SOURCE = "source"

        /** How a pasted pool's source is written. */
        private const val PASTED = "pasted"

        private fun sourceName(source: PoolSource): String =
            when (source) {
                is PoolSource.File -> source.name
                PoolSource.Pasted -> PASTED
            }

        /** The string at [key], or null when it is missing or not a string. */
        private fun JSONObject.stringOrNull(key: String): String? = opt(key) as? String

        private fun sourceOf(name: String): PoolSource =
            if (name == PASTED) PoolSource.Pasted else PoolSource.File(name)
    }
}
