package spa.app.results

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import spa.app.R
import spa.app.ui.theme.LocalSpaColors
import spa.core.intake.KitWarning
import spa.core.intake.KitWarningCode

/**
 * One kit warning, as a `surface` card read as one item: its title, "<actual>, expected <min> to
 * <max>", and the engine's message verbatim.
 */
@Composable
fun KitWarningItem(warning: KitWarning, modifier: Modifier = Modifier) {
    val colors = LocalSpaColors.current
    val type = MaterialTheme.typography
    Column(
        modifier =
            modifier
                .rowCard(colors.surface)
                .semantics(mergeDescendants = true) {}
                .padding(ROW_PADDING.dp),
        verticalArrangement = Arrangement.spacedBy(ITEM_GAP.dp),
    ) {
        Text(kitWarningTitle(warning.code), style = type.titleSmall, color = colors.onSurface)
        Text(
            stringResource(
                R.string.results_kit_range,
                warning.actual,
                warning.expected.first,
                warning.expected.last,
            ),
            style = type.bodyLarge,
            color = colors.onSurface,
        )
        Text(warning.message, style = type.bodyMedium, color = colors.onSurfaceVariant)
    }
}

/** A kit warning's title by the spec's table; a code the table does not name shows the code. */
fun kitWarningTitle(code: KitWarningCode): String =
    when (code) {
        KitWarningCode.CARD_COUNT -> "Card count"
        KitWarningCode.HIGH_COUNT -> "Too many copies"
        else -> code.code
    }
