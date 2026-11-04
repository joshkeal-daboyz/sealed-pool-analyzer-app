package spa.app.pair

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import spa.app.R
import spa.app.names.BasicLandNames
import spa.app.results.OutsideTypicalTag
import spa.app.results.ROW_GAP
import spa.app.results.ROW_PADDING
import spa.app.results.SectionHeading
import spa.app.results.TypicalRange
import spa.app.results.rowCard
import spa.app.ui.theme.LocalSpaColors

/**
 * Lands: the heading, then one card per colour reading "<n> <basic land name>", such as "6 Plains",
 * with its typical line and outside-typical words. Each card is one merged node, tagged with the
 * value's pointer.
 */
@Composable
fun LandRecommendationRows(lands: List<LandUi>, modifier: Modifier = Modifier) {
    val colors = LocalSpaColors.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(ROW_GAP.dp)) {
        SectionHeading(stringResource(R.string.pair_lands))
        lands.forEach { land ->
            val value = land.lands
            Column(
                modifier =
                    Modifier.rowCard(colors.surface)
                        .testTag(value.pointer)
                        .semantics(mergeDescendants = true) {}
                        .padding(ROW_PADDING.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    stringResource(R.string.pair_land, value.value, BasicLandNames.of(land.color)),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                )
                value.typical?.let { TypicalRange(it) }
                OutsideTypicalTag(value.outside)
            }
        }
    }
}
