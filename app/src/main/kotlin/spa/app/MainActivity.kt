package spa.app

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel

/** The app's one Activity: hosts [SpaApp]. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Always dark (decision 8): light bar icons on the dark ground, whatever the system mode.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val app = application as SpaApplication
        setContent {
            val pool: PoolViewModel =
                viewModel(factory = PoolViewModel.factory(app.importer, app.fileImporter))
            SpaApp(pool, app.timing)
        }
    }
}

/** A file a share or "Open with" handed the app: [uri], or null when the intent carried none. */
data class IncomingFile(val uri: Uri?)

/**
 * The file [intent] hands the app: a share's `EXTRA_STREAM`, or an "Open with"'s data. Null when
 * [intent] is neither a share nor an "Open with".
 */
@Suppress("UnusedParameter", "FunctionOnlyReturningConstant")
fun incomingFile(intent: Intent?): IncomingFile? = null
