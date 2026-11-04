package spa.app.pool

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import spa.app.R

/** The document types the picker offers: CSV under its common names, and plain text. */
val POOL_MIME_TYPES =
    arrayOf("text/csv", "text/comma-separated-values", "application/csv", "text/plain")

/**
 * "Open file": opens the system picker ([ActivityResultContracts.OpenDocument]) for
 * [POOL_MIME_TYPES]. [onPicked] gets the chosen document; a cancelled picker calls nothing.
 */
@Composable
fun OpenFileButton(enabled: Boolean, onPicked: (Uri) -> Unit, modifier: Modifier = Modifier) {
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) onPicked(uri)
        }
    PrimaryButton(
        onClick = { picker.launch(POOL_MIME_TYPES) },
        enabled = enabled,
        modifier = modifier,
    ) {
        Text(stringResource(R.string.pool_open_file), style = MaterialTheme.typography.labelLarge)
    }
}
