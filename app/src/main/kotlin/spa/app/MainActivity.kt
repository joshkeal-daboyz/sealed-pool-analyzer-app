package spa.app

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels

/**
 * The app's one Activity: hosts [SpaApp], and hands the file of a share or "Open with" to the pool,
 * at launch and when one arrives while the app is open.
 */
class MainActivity : ComponentActivity() {
    private val pool: PoolViewModel by viewModels {
        val app = application as SpaApplication
        PoolViewModel.factory(app.importer, app.fileImporter)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Always dark (decision 8): light bar icons on the dark ground, whatever the system mode.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        // A re-created Activity (rotation) still holds its launch intent: imported already.
        if (savedInstanceState == null) importFrom(intent)
        val app = application as SpaApplication
        setContent { SpaApp(pool, app.timing) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        importFrom(intent)
    }

    private fun importFrom(intent: Intent?) {
        incomingFile(intent)?.let { pool.importIncoming(it.uri) }
    }
}

/** A file a share or "Open with" handed the app: [uri], or null when the intent carried none. */
data class IncomingFile(val uri: Uri?)

/**
 * The file [intent] hands the app: a share's `EXTRA_STREAM` (or, failing that, its first clip), or
 * an "Open with"'s data. Null when [intent] is neither a share nor an "Open with".
 */
fun incomingFile(intent: Intent?): IncomingFile? =
    when (intent?.action) {
        Intent.ACTION_SEND ->
            IncomingFile(
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
            )
        Intent.ACTION_VIEW -> IncomingFile(intent.data)
        else -> null
    }
