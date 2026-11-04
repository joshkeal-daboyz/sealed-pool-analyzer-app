package spa.app.pool

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import spa.app.PoolUiState
import spa.app.R
import spa.app.ui.SpaTopBar
import spa.app.ui.theme.LocalSpaColors
import spa.app.ui.theme.SpaFonts
import spa.core.intake.PoolFormat
import spa.core.intake.PoolParser

/**
 * What the Pool screen shows: the pasted [text], the [format] chosen by hand (null for "Auto"), and
 * the pool [state], whose failed imports and missing card data show their error card.
 */
data class PoolEntryModel(
    val text: String,
    val format: PoolFormat? = null,
    val state: PoolUiState = PoolUiState.NoPool,
)

/**
 * What the Pool screen's controls do. A picked file goes to [onFilePicked]; cancelling, nowhere.
 */
class PoolEntryActions(
    val onTextChange: (String) -> Unit,
    val onAnalyze: () -> Unit,
    val onAbout: () -> Unit,
    val onFormatChoose: (PoolFormat?) -> Unit = {},
    val onFilePicked: (Uri) -> Unit = {},
    /** The saved-pool-unreadable card's "Clear pool". */
    val onClearPool: () -> Unit = {},
)

/** How long typing must pause before "Detected: <format>" updates. */
const val DETECT_PAUSE_MS = 300L

/**
 * The Pool screen with its pasted text only, no error, and the chooser on "Auto". [onBack] is set
 * when Pool was opened with "New pool".
 */
@Composable
fun PoolEntryScreen(
    text: String,
    onTextChange: (String) -> Unit,
    onAnalyze: () -> Unit,
    onAbout: () -> Unit,
    onBack: (() -> Unit)?,
) =
    PoolEntryScreen(
        PoolEntryModel(text),
        PoolEntryActions(onTextChange, onAnalyze, onAbout),
        onBack,
    )

/**
 * The Pool screen, by the spec's states: the error card when [model]'s state has one, the heading,
 * "Open file", the "or" divider, the paste box, the format chooser, the detected-format line, and
 * "Analyze pool". With missing card data every input is disabled. [onBack] is set when Pool was
 * opened with "New pool".
 */
@Composable
fun PoolEntryScreen(model: PoolEntryModel, actions: PoolEntryActions, onBack: (() -> Unit)?) {
    val inputsEnabled = model.state !is PoolUiState.DataMissing
    val detected = rememberDetectedFormat(model.text)
    Scaffold(
        topBar = {
            SpaTopBar(
                title = stringResource(R.string.app_name),
                onAbout = actions.onAbout,
                onBack = onBack,
            )
        },
        containerColor = LocalSpaColors.current.ground,
    ) { padding ->
        Column(
            modifier =
                Modifier.padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = SIDE.dp, vertical = TOP.dp),
            verticalArrangement = Arrangement.spacedBy(GAP.dp),
        ) {
            ErrorCardFor(model.state)
            Heading()
            OpenFileButton(enabled = inputsEnabled, onPicked = actions.onFilePicked)
            OrDivider()
            PasteBox(model.text, actions.onTextChange, enabled = inputsEnabled)
            FormatChooser(model.format, actions.onFormatChoose, enabled = inputsEnabled)
            DetectedFormatLine(chosen = model.format, detected = detected)
            AnalyzeButton(
                enabled = inputsEnabled && model.text.isNotBlank(),
                onClick = actions.onAnalyze,
            )
        }
    }
}

/**
 * The format [text] is detected as, updated once typing has paused for [DETECT_PAUSE_MS]; null for
 * blank text. The first value is detected at once, so a screen that comes back shows its line.
 */
@Composable
private fun rememberDetectedFormat(text: String): PoolFormat? {
    var detected by remember { mutableStateOf(detectOrNull(text)) }
    LaunchedEffect(text) {
        if (text.isBlank()) {
            detected = null
        } else {
            delay(DETECT_PAUSE_MS)
            detected = detectOrNull(text)
        }
    }
    return detected
}

private fun detectOrNull(text: String): PoolFormat? =
    if (text.isBlank()) null else PoolParser.detect(text)

@Composable
private fun ErrorCardFor(state: PoolUiState) {
    when (state) {
        is PoolUiState.ImportFailed -> ImportErrorCard(state.kind, state.source, state.lines)
        PoolUiState.DataMissing -> DataMissingCard()
        else -> Unit
    }
}

/** "Add your pool", a heading (A6), and the line under it. */
@Composable
private fun Heading() {
    val colors = LocalSpaColors.current
    Column(verticalArrangement = Arrangement.spacedBy(HEADING_GAP.dp)) {
        Text(
            stringResource(R.string.pool_heading),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.displaySmall,
            color = colors.onSurface,
        )
        Text(
            stringResource(R.string.pool_intro),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurfaceVariant,
        )
    }
}

/** A rule, the word "or", and a rule. */
@Composable
private fun OrDivider() {
    val colors = LocalSpaColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GAP.dp),
    ) {
        HorizontalDivider(Modifier.weight(1f), color = colors.divider)
        Text(
            stringResource(R.string.pool_or),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.muted,
        )
        HorizontalDivider(Modifier.weight(1f), color = colors.divider)
    }
}

/**
 * The multi-line paste box, with the visible label "Paste your pool" (P1) and the hint under it. It
 * grows to about 8 lines, then scrolls inside itself.
 */
@Composable
fun PasteBox(
    text: String,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = LocalSpaColors.current
    OutlinedTextField(
        value = text,
        onValueChange = onTextChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        label = { Text(stringResource(R.string.pool_paste_label)) },
        // Under the box rather than a placeholder, which shows only once the box has focus.
        supportingText = { Text(stringResource(R.string.pool_paste_hint)) },
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = SpaFonts.mono),
        minLines = MIN_LINES,
        maxLines = MAX_LINES,
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedContainerColor = colors.surface,
                unfocusedContainerColor = colors.surface,
                disabledContainerColor = colors.surface,
                focusedBorderColor = colors.accent,
                unfocusedBorderColor = colors.outline,
                focusedLabelColor = colors.accentText,
                unfocusedLabelColor = colors.onSurfaceVariant,
                focusedSupportingTextColor = colors.onSurfaceVariant,
                unfocusedSupportingTextColor = colors.onSurfaceVariant,
                cursorColor = colors.accent,
            ),
    )
}

/** "Analyze pool": not enabled while the box holds only spaces and blank lines (P3). */
@Composable
fun AnalyzeButton(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    PrimaryButton(onClick = onClick, enabled = enabled, modifier = modifier) {
        Text(stringResource(R.string.pool_analyze), style = MaterialTheme.typography.labelLarge)
    }
}

/** The Pool screen's primary button: full width, 64 dp tall, the accent fill. */
@Composable
internal fun PrimaryButton(
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
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
        content = content,
    )
}

private const val SIDE = 16
private const val TOP = 12
private const val GAP = 16
private const val HEADING_GAP = 6
private const val MIN_LINES = 3
private const val MAX_LINES = 8
private const val PRIMARY_HEIGHT = 64
private const val PRIMARY_RADIUS = 16
