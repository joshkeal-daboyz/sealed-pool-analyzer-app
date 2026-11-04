package spa.app.pool

import android.content.ContentResolver
import android.net.Uri
import spa.app.FileImporter
import spa.app.importing.ImportOutcome
import spa.app.importing.PoolImporter
import spa.app.importing.PoolSource

/** Reads picked documents through [resolver]. Skeleton for the red tests: not implemented yet. */
class ContentFileImporter(
    private val resolver: ContentResolver,
    private val importer: PoolImporter,
) : FileImporter {
    override fun displayName(uri: Uri): String = TODO("SPA-017: $resolver")

    override suspend fun import(uri: Uri, source: PoolSource): ImportOutcome =
        TODO("SPA-017: $importer")
}
