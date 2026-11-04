package spa.app.results

import androidx.compose.runtime.Composable
import spa.app.importing.PoolSource

/**
 * The Clear pool dialog: "Clear this pool?", what clearing [source] means, and the buttons "Keep
 * pool" ([onDismiss], as are back and a tap outside) and "Clear pool" ([onConfirm]).
 */
@Composable
fun ClearPoolDialog(source: PoolSource, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    // SPA-022: stub.
}
