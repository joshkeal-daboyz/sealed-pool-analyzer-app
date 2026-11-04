package spa.app.pool

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import spa.app.R
import spa.app.importing.ImportFailureKind
import spa.app.importing.PoolSource
import spa.app.ui.theme.LocalSpaColors
import spa.app.ui.theme.SpaFonts
import spa.core.intake.UnresolvedLine
import spa.core.intake.UnresolvedReason

/**
 * The error card for a failed import of [kind] from [source]: its title, its body, and for "not a
 * pool" and "wrong set" the first [SHOWN_LINES] of [lines], with "Show all <n> lines" when there
 * are more. Announced politely when it appears (P4).
 */
@Composable
fun ImportErrorCard(
    kind: ImportFailureKind,
    source: PoolSource,
    lines: List<UnresolvedLine>,
    modifier: Modifier = Modifier,
    onClearPool: () -> Unit = {},
) {
    val name = sourceName(source)
    val body =
        when (kind) {
            ImportFailureKind.EMPTY -> stringResource(R.string.pool_error_empty_body, name)
            ImportFailureKind.NOT_A_POOL ->
                if (lines.isEmpty()) {
                    stringResource(R.string.pool_error_not_a_pool_no_lines_body, name)
                } else {
                    stringResource(R.string.pool_error_not_a_pool_body, lines.size, name)
                }
            ImportFailureKind.WRONG_SET -> stringResource(R.string.pool_error_wrong_set_body, name)
            ImportFailureKind.UNREADABLE ->
                stringResource(R.string.pool_error_unreadable_body, name)
            ImportFailureKind.SAVED_POOL_UNREADABLE -> TODO("SPA-022")
        }
    val listed =
        if (kind == ImportFailureKind.NOT_A_POOL || kind == ImportFailureKind.WRONG_SET) lines
        else emptyList()
    // A file name is shown as it is; only "the pasted text" opening a sentence takes a capital.
    val sentence = if (source is PoolSource.Pasted) capitalised(body) else body
    ErrorCard(stringResource(titleOf(kind)), sentence, listed, modifier)
}

/** The "Card data is missing" card: the bundled set data did not load. */
@Composable
fun DataMissingCard(modifier: Modifier = Modifier) {
    ErrorCard(
        title = stringResource(R.string.pool_error_data_missing_title),
        body = stringResource(R.string.pool_error_data_missing_body),
        lines = emptyList(),
        modifier = modifier,
    )
}

@StringRes
private fun titleOf(kind: ImportFailureKind): Int =
    when (kind) {
        ImportFailureKind.EMPTY -> R.string.pool_error_empty_title
        ImportFailureKind.NOT_A_POOL -> R.string.pool_error_not_a_pool_title
        ImportFailureKind.WRONG_SET -> R.string.pool_error_wrong_set_title
        ImportFailureKind.UNREADABLE -> R.string.pool_error_unreadable_title
        ImportFailureKind.SAVED_POOL_UNREADABLE -> TODO("SPA-022")
    }

@Composable
private fun ErrorCard(
    title: String,
    body: String,
    lines: List<UnresolvedLine>,
    modifier: Modifier = Modifier,
) {
    val colors = LocalSpaColors.current
    val type = MaterialTheme.typography
    var showAll by rememberSaveable(lines) { mutableStateOf(false) }
    Card(
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(CARD_RADIUS.dp),
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
            Text(title, style = type.titleMedium)
            Text(body, style = type.bodyLarge, color = colors.onSurfaceVariant)
            (if (showAll) lines else lines.take(SHOWN_LINES)).forEach { UnreadLine(it) }
            if (!showAll && lines.size > SHOWN_LINES) {
                TextButton(
                    onClick = { showAll = true },
                    modifier = Modifier.heightIn(min = MIN_TOUCH.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = colors.accentText),
                ) {
                    Text(
                        stringResource(R.string.pool_error_show_all, lines.size),
                        style = type.labelMedium,
                    )
                }
            }
        }
    }
}

/** One unread line: "Line <n>", its text in full, and the reason in words with its code. */
@Composable
private fun UnreadLine(line: UnresolvedLine) {
    val colors = LocalSpaColors.current
    val type = MaterialTheme.typography
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = LINE_GAP.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(stringResource(R.string.pool_line_number, line.lineNumber), style = type.titleSmall)
        Text(line.rawLine, style = type.bodyLarge.copy(fontFamily = SpaFonts.mono))
        Text(reasonText(line.reason), style = type.bodyMedium, color = colors.onSurfaceVariant)
    }
}

/** The reason in words with its code after it; a code with no words shows the code alone. */
@Composable
private fun reasonText(reason: UnresolvedReason): String {
    val words =
        when (reason) {
            UnresolvedReason.UNKNOWN_NAME -> R.string.pool_reason_unknown_name
            UnresolvedReason.UNKNOWN_COLLECTOR_NUMBER ->
                R.string.pool_reason_unknown_collector_number
            UnresolvedReason.WRONG_SET -> R.string.pool_reason_wrong_set
            else -> return reason.code
        }
    return stringResource(R.string.pool_line_reason, stringResource(words), reason.code)
}

/** "<file name>", or "the pasted text". */
@Composable
private fun sourceName(source: PoolSource): String =
    when (source) {
        is PoolSource.File -> source.name
        PoolSource.Pasted -> stringResource(R.string.pool_source_pasted)
    }

/** [text] with its first letter capitalised, for a body that starts with "the pasted text". */
private fun capitalised(text: String): String = text.replaceFirstChar { it.uppercaseChar() }

/** How many unread lines a card lists before "Show all". */
const val SHOWN_LINES = 3

private const val CARD_RADIUS = 18
private const val CARD_PADDING = 20
private const val LINE_GAP = 6
private const val MIN_TOUCH = 48
