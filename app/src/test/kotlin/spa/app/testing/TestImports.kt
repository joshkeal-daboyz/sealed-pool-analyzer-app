package spa.app.testing

import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import spa.app.Importer
import spa.app.data.TestFixtures
import spa.app.importing.ImportOutcome
import spa.app.importing.PoolImporter
import spa.app.importing.PoolSource
import spa.core.intake.PoolFormat

/** Real imports for tests, through [PoolImporter] over the set data in `fixtures/`. */
object TestImports {
    private val dir by lazy { Files.createTempDirectory("spa-setdata").toFile() }

    /** One importer per test JVM, so the set data is copied and read once. */
    val poolImporter: PoolImporter by lazy { PoolImporter(TestFixtures.setData(dir)) }

    /** The app's import path, as [spa.app.SpaApplication] wires it. */
    val importer = Importer { text, format, source -> poolImporter.import(text, format, source) }

    /** The text of `fixtures/pools/<file>`. */
    fun pool(file: String): String = TestFixtures.text("pools/$file")

    /** [text] imported as pasted text, which must succeed. */
    fun success(text: String, format: PoolFormat? = null): ImportOutcome.Success = runBlocking {
        poolImporter.import(text, format, PoolSource.Pasted) as ImportOutcome.Success
    }

    /** `trk-manabox-01.csv`, imported as pasted text. */
    val manabox01: ImportOutcome.Success by lazy { success(pool("trk-manabox-01.csv")) }
}
