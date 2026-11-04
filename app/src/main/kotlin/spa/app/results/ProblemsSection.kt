package spa.app.results

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import spa.app.R
import spa.app.ui.theme.LocalSpaColors

/**
 * Problems, first under the pool header, always open (decision 2). A clean pool gets the one
 * all-clear line. Otherwise: the summary of unread lines, the another-set note, "Skip to numbers"
 * when there are more than [LONG_LIST] items, then each unread line and each kit warning.
 */
@Composable
fun ProblemsSection(ui: ResultsUi, onSkipToNumbers: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalSpaColors.current
    val type = MaterialTheme.typography
    val clean = ui.problemCount == 0
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .border(
                    if (clean) BorderStroke(1.dp, colors.outline)
                    else BorderStroke(PROBLEM_BORDER.dp, colors.accent),
                    RoundedCornerShape(SECTION_RADIUS.dp),
                )
                .padding(ROW_PADDING.dp),
        verticalArrangement = Arrangement.spacedBy(ROW_GAP.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ICON_GAP.dp),
        ) {
            if (!clean) Icon(Icons.Filled.Warning, contentDescription = null, tint = colors.accent)
            SectionHeading(stringResource(R.string.results_problems), top = 0)
        }
        if (clean) {
            Text(
                stringResource(R.string.results_all_clear),
                style = type.bodyLarge,
                color = colors.onSurface,
            )
        }
        if (ui.unresolved.isNotEmpty()) {
            Text(
                pluralStringResource(
                    R.plurals.results_lines_not_read,
                    ui.unresolved.size,
                    ui.unresolved.size,
                ),
                style = type.bodyLarge.copy(fontWeight = FontWeight.Bold),
                color = colors.accentText,
            )
        }
        if (ui.someFromAnotherSet) {
            Text(
                stringResource(R.string.results_wrong_set_note),
                style = type.bodyLarge,
                color = colors.onSurface,
            )
        }
        if (ui.problemCount > LONG_LIST) SkipToNumbersButton(onSkipToNumbers)
        ui.unresolved.forEach { UnresolvedLineItem(it) }
        ui.kitWarnings.forEach { KitWarningItem(it) }
    }
}

/** "Skip to numbers": an outlined pill, 52 dp tall. */
@Composable
private fun SkipToNumbersButton(onClick: () -> Unit) {
    val colors = LocalSpaColors.current
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.heightIn(min = OUTLINED_HEIGHT.dp),
        shape = CircleShape,
        border = BorderStroke(1.dp, colors.outline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.onSurface),
    ) {
        Text(
            stringResource(R.string.results_skip_to_numbers),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

/** More items than this in Problems adds "Skip to numbers". */
const val LONG_LIST = 5

private const val PROBLEM_BORDER = 2
private const val SECTION_RADIUS = 18
private const val ICON_GAP = 10
private const val OUTLINED_HEIGHT = 52
