package spa.app.results

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import spa.app.data.SetData
import spa.core.baseline.Baseline

/**
 * The baseline Results compares against. Null, the default, means the one bundled in the APK; tests
 * provide the fixtures' baseline here.
 */
val LocalBaseline = staticCompositionLocalOf<Baseline?> { null }

/**
 * The baseline bundled in the APK, read once per process from the same asset [SetData] copies.
 * [PoolImporter][spa.app.importing.PoolImporter] does not hand its loaded baseline on, so Results
 * reads the asset itself; a successful import has already proved the file loads.
 */
object BundledBaseline {
    /** The baseline's path below the assets folder, as the build bundles it. */
    private val path =
        "${SetData.ASSET_DIR}/" + SetData.FILES.single { it.startsWith("baselines/") }

    @Volatile private var cached: Baseline? = null

    /** The baseline, once [load] has read it. */
    val loaded: Baseline?
        get() = cached

    /** Reads and decodes the bundled baseline, or returns it when already read. */
    @Throws(IOException::class)
    fun load(context: Context): Baseline =
        cached
            ?: context.assets
                .open(path)
                .use { Baseline.decode(it.readBytes().decodeToString()) }
                .also { cached = it }
}

/**
 * The bundled baseline, read off the main thread: null until it has loaded, or if it cannot be read
 * (then the import that led here would already have failed as card data missing).
 */
@Composable
fun rememberBundledBaseline(): Baseline? {
    val context = LocalContext.current.applicationContext
    val baseline by
        produceState(BundledBaseline.loaded, context) {
            if (value == null) {
                value =
                    withContext(Dispatchers.IO) {
                        try {
                            BundledBaseline.load(context)
                        } catch (e: IOException) {
                            null
                        } catch (e: IllegalArgumentException) {
                            // Not a baseline: kotlinx.serialization's errors are this type.
                            null
                        }
                    }
            }
        }
    return baseline
}
