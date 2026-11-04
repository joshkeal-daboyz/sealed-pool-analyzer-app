package spa.app.pair

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import spa.app.R
import spa.app.results.ROW_GAP
import spa.app.results.ROW_PADDING
import spa.app.results.SectionHeading
import spa.app.results.ValueLine
import spa.app.results.rowCard
import spa.app.ui.theme.LocalSpaColors

/** Curve: the heading, then one [CurveRow] per bucket. Rows, not a grid, so it reflows. */
@Composable
fun CurveTable(rows: List<CurveRowUi>, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(ROW_GAP.dp)) {
        SectionHeading(stringResource(R.string.pair_curve))
        rows.forEach { CurveRow(it) }
    }
}

/**
 * One bucket as one merged node (D2): "Mana value <mv>" ("Mana value 7+" for the last), then "<n>
 * creatures, typical ..." and "<n> other, typical ...", each with its outside-typical words.
 */
@Composable
fun CurveRow(bucket: CurveRowUi, modifier: Modifier = Modifier) {
    val colors = LocalSpaColors.current
    Column(
        modifier =
            modifier
                .rowCard(colors.surface)
                .semantics(mergeDescendants = true) {}
                .padding(ROW_PADDING.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        val label = if (bucket.isTop) R.string.pair_mana_value_top else R.string.pair_mana_value
        Text(
            stringResource(label, bucket.manaValue),
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurface,
        )
        ValueLine(bucket.creatures) { value, typical ->
            pluralStringResource(R.plurals.pair_curve_creatures, value, value, typical)
        }
        ValueLine(bucket.other) { value, typical ->
            stringResource(R.string.pair_curve_other, value, typical)
        }
    }
}
