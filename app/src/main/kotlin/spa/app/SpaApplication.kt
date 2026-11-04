package spa.app

import android.app.Application
import spa.app.data.SetData
import spa.app.importing.PoolImporter
import spa.app.pool.ContentFileImporter
import spa.app.timing.SpaTiming

/** The process: one [SpaTiming], and one [PoolImporter] over one [SetData]. */
class SpaApplication : Application() {
    val timing = SpaTiming()

    /** The app's import path. The set data is copied and read on the first import. */
    private val poolImporter: PoolImporter by lazy { PoolImporter(SetData.forContext(this)) }

    /** Imports pasted text. */
    val importer: Importer by lazy {
        Importer { text, format, source -> poolImporter.import(text, format, source) }
    }

    /** Imports picked documents, through the same [PoolImporter]. */
    val fileImporter: FileImporter by lazy { ContentFileImporter(contentResolver, poolImporter) }

    override fun onCreate() {
        super.onCreate()
        timing.appCreated()
    }
}
