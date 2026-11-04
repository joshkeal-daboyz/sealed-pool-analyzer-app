package spa.app.results

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
import spa.app.names.ColorNames
import spa.app.ui.theme.LocalSpaColors
import spa.core.metrics.FixingSource

/**
 * Fixing sources: the heading, then one card per source with its name, the colours it makes as
 * names, and its copies when above 1; or "No fixing sources".
 */
@Composable
fun FixingSourceList(sources: List<FixingSource>, modifier: Modifier = Modifier) {
    val colors = LocalSpaColors.current
    val type = MaterialTheme.typography
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(ROW_GAP.dp)) {
        SectionHeading(stringResource(R.string.results_fixing_sources))
        if (sources.isEmpty()) {
            Text(
                stringResource(R.string.results_no_fixing),
                style = type.bodyLarge,
                color = colors.onSurfaceVariant,
            )
        }
        sources.forEach { source ->
            Column(
                modifier =
                    Modifier.rowCard(colors.surface)
                        .semantics(mergeDescendants = true) {}
                        .padding(ROW_PADDING.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(source.name, style = type.titleMedium, color = colors.onSurface)
                if (source.colors.isNotEmpty()) {
                    Text(
                        ColorNames.list(source.colors),
                        style = type.bodyLarge,
                        color = colors.onSurfaceVariant,
                    )
                }
                if (source.count > 1) {
                    Text(
                        pluralStringResource(
                            R.plurals.results_fixing_copies,
                            source.count,
                            source.count,
                        ),
                        style = type.bodyLarge,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
