package spa.app.results

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import spa.app.PoolUiState
import spa.app.R
import spa.app.importing.PoolSource
import spa.app.nameRes
import spa.app.ui.theme.LocalSpaColors

/**
 * The pool header card: the [source], then "Read as <format>", the counts, and the skipped lines
 * when above 0, and "New pool". With no [loaded] pool (Loading), "Reading your pool…" stands in for
 * the counts and there are no buttons. SPA-022 adds Clear pool beside New pool.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PoolHeaderCard(
    source: PoolSource,
    loaded: PoolUiState.Loaded?,
    onNewPool: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onClearPool: (() -> Unit)? = null,
) {
    val colors = LocalSpaColors.current
    val type = MaterialTheme.typography
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(HEADER_RADIUS.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = colors.surface,
                contentColor = colors.onSurface,
            ),
    ) {
        Column(
            modifier = Modifier.padding(CARD_PADDING.dp),
            verticalArrangement = Arrangement.spacedBy(LINE_GAP.dp),
        ) {
            Text(sourceName(source), style = type.titleMedium)
            if (loaded == null) {
                Text(
                    stringResource(R.string.results_reading),
                    style = type.bodyLarge,
                    color = colors.onSurfaceVariant,
                )
            } else {
                HeaderLines(loaded)
            }
            if (onNewPool != null) {
                FlowRow(
                    modifier = Modifier.padding(top = BUTTON_GAP.dp),
                    horizontalArrangement = Arrangement.spacedBy(BUTTON_GAP.dp),
                    verticalArrangement = Arrangement.spacedBy(BUTTON_GAP.dp),
                ) {
                    NewPoolButton(onNewPool)
                }
            }
        }
    }
}

@Composable
private fun HeaderLines(loaded: PoolUiState.Loaded) {
    val result = loaded.result.result
    val totalCards = result.pool.totalCount
    val entryCount = result.pool.entries.size
    val skipped = result.skippedLines
    val colors = LocalSpaColors.current
    val type = MaterialTheme.typography
    Text(
        stringResource(R.string.results_read_as, stringResource(loaded.format.nameRes())),
        style = type.bodyLarge,
        color = colors.onSurfaceVariant,
    )
    val cards = pluralStringResource(R.plurals.results_cards, totalCards, totalCards)
    val entries = pluralStringResource(R.plurals.results_entries, entryCount, entryCount)
    Text(stringResource(R.string.results_counts, cards, entries), style = type.bodyLarge)
    if (skipped > 0) {
        Text(
            pluralStringResource(
                R.plurals.results_skipped_lines,
                skipped,
                skipped,
            ),
            style = type.bodyLarge,
            color = colors.onSurfaceVariant,
        )
    }
}

/** "New pool": an outlined pill, 52 dp tall. */
@Composable
fun NewPoolButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalSpaColors.current
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = OUTLINED_HEIGHT.dp),
        shape = CircleShape,
        border = BorderStroke(1.dp, colors.outline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.onSurface),
    ) {
        Text(
            stringResource(R.string.results_new_pool),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

/** "<file name>" or "Pasted". */
@Composable
private fun sourceName(source: PoolSource): String =
    when (source) {
        is PoolSource.File -> source.name
        PoolSource.Pasted -> stringResource(R.string.results_source_pasted)
    }

private const val HEADER_RADIUS = 18
private const val CARD_PADDING = 20
private const val LINE_GAP = 6
private const val BUTTON_GAP = 10
private const val OUTLINED_HEIGHT = 52
