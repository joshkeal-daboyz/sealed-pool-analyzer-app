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
import spa.app.names.ReasonText
import spa.app.ui.theme.LocalSpaColors
import spa.app.ui.theme.SpaFonts
import spa.core.intake.UnresolvedLine

/**
 * One unread line, as a `surface` card read as one item: "Line <n>", the line's text in full in
 * Atkinson Hyperlegible Mono (wrapping, never cut), and the reason in words with its code.
 */
@Composable
fun UnresolvedLineItem(line: UnresolvedLine, modifier: Modifier = Modifier) {
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
        Text(
            stringResource(R.string.results_line_number, line.lineNumber),
            style = type.titleSmall,
            color = colors.onSurface,
        )
        Text(
            line.rawLine,
            style = type.bodyLarge.copy(fontFamily = SpaFonts.mono),
            color = colors.onSurface,
        )
        Text(ReasonText.of(line.reason), style = type.bodyMedium, color = colors.onSurfaceVariant)
    }
}

internal const val ITEM_GAP = 4
