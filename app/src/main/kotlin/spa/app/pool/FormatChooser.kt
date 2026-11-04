package spa.app.pool

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import spa.app.R
import spa.app.nameRes
import spa.app.ui.theme.LocalSpaColors
import spa.core.intake.PoolFormat

/** The chips in order: "Auto" (null), then the three formats. */
private val CHOICES: List<PoolFormat?> =
    listOf(null, PoolFormat.MANABOX, PoolFormat.TEXT, PoolFormat.COLLECTOR)

/**
 * The format chooser: one chip per choice, single choice, each exposing its selected state (P2).
 * [selected] is the chosen format, or null for "Auto". Wraps at large font.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FormatChooser(
    selected: PoolFormat?,
    onSelect: (PoolFormat?) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = LocalSpaColors.current
    FlowRow(
        modifier = modifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(CHIP_GAP.dp),
        verticalArrangement = Arrangement.spacedBy(CHIP_GAP.dp),
    ) {
        CHOICES.forEach { choice ->
            val isSelected = choice == selected
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(choice) },
                enabled = enabled,
                label = {
                    Text(
                        stringResource(choice?.nameRes() ?: R.string.pool_format_auto),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                },
                modifier = Modifier.heightIn(min = CHIP_HEIGHT.dp),
                shape = CircleShape,
                colors =
                    FilterChipDefaults.filterChipColors(
                        containerColor = colors.ground,
                        labelColor = colors.onSurface,
                        selectedContainerColor = colors.selectedChip,
                        selectedLabelColor = colors.accentText,
                    ),
                border =
                    BorderStroke(
                        if (isSelected) SELECTED_BORDER.dp else 1.dp,
                        if (isSelected) colors.accent else colors.outline,
                    ),
            )
        }
    }
}

/**
 * The line under the chooser: "Reading as <format> (chosen)" when [chosen] is set, else "Detected:
 * <format>" for [detected]. Nothing when neither is known.
 */
@Composable
fun DetectedFormatLine(chosen: PoolFormat?, detected: PoolFormat?, modifier: Modifier = Modifier) {
    val text =
        when {
            chosen != null -> stringResource(R.string.pool_chosen, stringResource(chosen.nameRes()))
            detected != null ->
                stringResource(R.string.pool_detected, stringResource(detected.nameRes()))
            else -> return
        }
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.bodyLarge,
        color = LocalSpaColors.current.onSurfaceVariant,
    )
}

private const val CHIP_GAP = 8
private const val CHIP_HEIGHT = 48
private const val SELECTED_BORDER = 2
