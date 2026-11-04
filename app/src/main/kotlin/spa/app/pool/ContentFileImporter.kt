package spa.app.pool

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import java.io.FileNotFoundException
import spa.app.FileImporter
import spa.app.importing.ImportOutcome
import spa.app.importing.PoolImporter
import spa.app.importing.PoolSource

/**
 * Reads picked documents through [resolver]: the name is the document's
 * [OpenableColumns.DISPLAY_NAME], and the bytes go through [PoolImporter.importStream], which turns
 * any failure to read them into an unreadable import.
 */
class ContentFileImporter(
    private val resolver: ContentResolver,
    private val importer: PoolImporter,
) : FileImporter {
    override fun displayName(uri: Uri): String =
        queryDisplayName(uri) ?: uri.lastPathSegment ?: uri.toString()

    override suspend fun import(uri: Uri, source: PoolSource): ImportOutcome =
        importer.importStream(
            { resolver.openInputStream(uri) ?: throw FileNotFoundException(uri.toString()) },
            source,
        )

    /** The provider's display name, or null when it gives none or refuses the query. */
    private fun queryDisplayName(uri: Uri): String? =
        try {
            resolver
                .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (column >= 0 && cursor.moveToFirst()) cursor.getString(column) else null
                }
                ?.takeIf { it.isNotBlank() }
        } catch (e: SecurityException) {
            // A document whose permission was revoked: the import itself will say it's unreadable.
            Log.w(TAG, "No display name for $uri", e)
            null
        }

    private companion object {
        const val TAG = "SpaImport"
    }
}
