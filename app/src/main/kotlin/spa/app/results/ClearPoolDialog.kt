package spa.app.results

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import spa.app.R
import spa.app.importing.PoolSource
import spa.app.ui.theme.LocalSpaColors

/**
 * The Clear pool dialog (decision 7: Clear pool asks once): "Clear this pool?", what clearing
 * [source] means, and the buttons "Keep pool" ([onDismiss], as are back and a tap outside) and
 * "Clear pool" ([onConfirm]). Its buttons say what they do, never "OK" or "Cancel" (C1). It is a
 * window of its own, so TalkBack stays inside it (C2).
 */
@Composable
fun ClearPoolDialog(source: PoolSource, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val colors = LocalSpaColors.current
    val type = MaterialTheme.typography
    val name =
        when (source) {
            is PoolSource.File -> source.name
            PoolSource.Pasted -> stringResource(R.string.results_clear_source_pasted)
        }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { DialogButton(stringResource(R.string.results_clear_confirm), onConfirm) },
        dismissButton = { DialogButton(stringResource(R.string.results_clear_keep), onDismiss) },
        title = { Text(stringResource(R.string.results_clear_title), style = type.titleLarge) },
        text = { Text(stringResource(R.string.results_clear_body, name), style = type.bodyLarge) },
        containerColor = colors.surface,
        titleContentColor = colors.onSurface,
        textContentColor = colors.onSurfaceVariant,
    )
}

/** A dialog button, at least 48 dp tall (A1). */
@Composable
private fun DialogButton(label: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.heightIn(min = MIN_TOUCH.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = LocalSpaColors.current.accentText),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

private const val MIN_TOUCH = 48
