package spa.app.pool

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import spa.app.R
import spa.app.ui.SpaTopBar
import spa.app.ui.theme.LocalSpaColors
import spa.app.ui.theme.SpaFonts

/**
 * The Pool screen, minimal: the paste box and "Analyze pool". [onBack] is set when Pool was opened
 * with "New pool". SPA-017 adds the rest of the screen.
 */
@Composable
fun PoolEntryScreen(
    text: String,
    onTextChange: (String) -> Unit,
    onAnalyze: () -> Unit,
    onAbout: () -> Unit,
    onBack: (() -> Unit)?,
) {
    Scaffold(
        topBar = {
            SpaTopBar(title = stringResource(R.string.app_name), onAbout = onAbout, onBack = onBack)
        },
        containerColor = LocalSpaColors.current.ground,
    ) { padding ->
        Column(
            modifier =
                Modifier.padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PasteBox(text, onTextChange)
            AnalyzeButton(enabled = text.isNotBlank(), onClick = onAnalyze)
        }
    }
}

/** The multi-line paste box, with the visible label "Paste your pool" (P1). */
@Composable
fun PasteBox(text: String, onTextChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalSpaColors.current
    OutlinedTextField(
        value = text,
        onValueChange = onTextChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.pool_paste_label)) },
        placeholder = { Text(stringResource(R.string.pool_paste_hint)) },
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = SpaFonts.mono),
        minLines = MIN_LINES,
        maxLines = MAX_LINES,
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedContainerColor = colors.surface,
                unfocusedContainerColor = colors.surface,
                focusedBorderColor = colors.accent,
                unfocusedBorderColor = colors.outline,
                focusedLabelColor = colors.accentText,
                unfocusedLabelColor = colors.onSurfaceVariant,
                focusedPlaceholderColor = colors.onSurfaceVariant,
                unfocusedPlaceholderColor = colors.onSurfaceVariant,
                cursorColor = colors.accent,
            ),
    )
}

/** "Analyze pool": not enabled while the box holds only spaces and blank lines (P3). */
@Composable
fun AnalyzeButton(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalSpaColors.current
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = PRIMARY_HEIGHT.dp),
        shape = RoundedCornerShape(PRIMARY_RADIUS.dp),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = colors.accent,
                contentColor = colors.onAccent,
            ),
    ) {
        Text(stringResource(R.string.pool_analyze), style = MaterialTheme.typography.labelLarge)
    }
}

private const val MIN_LINES = 3
private const val MAX_LINES = 8
private const val PRIMARY_HEIGHT = 64
private const val PRIMARY_RADIUS = 16
