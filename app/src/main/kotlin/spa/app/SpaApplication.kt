package spa.app

import android.app.Application
import spa.app.data.SetData
import spa.app.importing.PoolImporter
import spa.app.timing.SpaTiming

/** The process: one [SpaTiming], and one [PoolImporter] over one [SetData]. */
class SpaApplication : Application() {
    val timing = SpaTiming()

    /** The app's import path. The set data is copied and read on the first import. */
    val importer: Importer by lazy {
        val poolImporter = PoolImporter(SetData.forContext(this))
        Importer { text, format, source -> poolImporter.import(text, format, source) }
    }

    override fun onCreate() {
        super.onCreate()
        timing.appCreated()
    }
}
