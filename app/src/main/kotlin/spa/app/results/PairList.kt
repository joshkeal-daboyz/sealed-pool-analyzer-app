package spa.app.results

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import spa.app.R
import spa.app.names.PairNames
import spa.app.ui.theme.LocalSpaColors
import spa.core.metrics.ColorPair

/**
 * Colour pairs: the heading, "Sorted by cards in the pair" so the order does not read as a
 * recommendation (decision 4), and one [PairRow] per pair, in [pairs]' order.
 */
@Composable
fun PairList(pairs: List<PairUi>, onPair: (ColorPair) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(ROW_GAP.dp)) {
        SectionHeading(stringResource(R.string.results_pairs))
        Text(
            stringResource(R.string.results_pairs_sorted),
            style = MaterialTheme.typography.bodyLarge,
            color = LocalSpaColors.current.onSurfaceVariant,
        )
        pairs.forEach { PairRow(it, onClick = { onPair(it.pair) }) }
    }
}

/**
 * One pair as one button (R3): the card count large on the left (drawn only; the cards line says
 * it), then the pair's name, its cards line, its creatures line, and a chevron.
 */
@Composable
fun PairRow(pair: PairUi, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalSpaColors.current
    val type = MaterialTheme.typography
    Row(
        modifier =
            modifier
                .rowCard(colors.surface)
                .testTag("pair/${pair.pair}")
                .clickable(role = Role.Button, onClick = onClick)
                .padding(ROW_PADDING.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ROW_PADDING.dp),
    ) {
        Text(
            "${pair.cards.value}",
            modifier = Modifier.widthIn(min = PAIR_VALUE_MIN_WIDTH.dp).clearAndSetSemantics {},
            style = type.displayMedium,
            color = colors.onSurface,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(PairNames.of(pair.pair), style = type.titleMedium, color = colors.onSurface)
            ValueLine(pair.cards) { value, typical ->
                pluralStringResource(R.plurals.results_pair_cards, value, value, typical)
            }
            ValueLine(pair.creatures) { value, typical ->
                pluralStringResource(R.plurals.results_pair_creatures, value, value, typical)
            }
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.muted,
        )
    }
}

private const val PAIR_VALUE_MIN_WIDTH = 56
