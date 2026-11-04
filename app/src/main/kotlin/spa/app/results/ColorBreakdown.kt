package spa.app.results

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import spa.app.R
import spa.app.names.ColorNames
import spa.app.ui.theme.LocalSpaColors

/** Cards per colour: the heading and one [ColorRow] per bucket, in [colors]' order. */
@Composable
fun ColorBreakdown(colors: List<ColorUi>, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(ROW_GAP.dp)) {
        SectionHeading(stringResource(R.string.results_colours))
        colors.forEach { ColorRow(it) }
    }
}

/**
 * One bucket as one button that expands in place (R4). Collapsed: its cards and playables.
 * Expanded: also creatures, removal, interaction, and the [RarityBreakdown]. The state is exposed
 * as "Collapsed" or "Expanded".
 */
@Composable
fun ColorRow(color: ColorUi, modifier: Modifier = Modifier) {
    val colors = LocalSpaColors.current
    var expanded by rememberSaveable(color.bucket) { mutableStateOf(false) }
    val state =
        stringResource(if (expanded) R.string.results_expanded else R.string.results_collapsed)
    Column(
        modifier =
            modifier
                .rowCard(colors.surface)
                .testTag("color/${color.bucket}")
                .clickable(role = Role.Button) { expanded = !expanded }
                .semantics { stateDescription = state }
                .padding(ROW_PADDING.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                ColorNames.of(color.bucket),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface,
            )
            Icon(
                if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = colors.muted,
            )
        }
        Line(color.cards, R.string.results_colour_cards)
        Line(color.playables, R.string.results_colour_playables)
        if (expanded) {
            Line(color.creatures, R.string.results_colour_creatures)
            Line(color.removal, R.string.results_colour_removal)
            Line(color.interaction, R.string.results_colour_interaction)
            RarityBreakdown(color)
        }
    }
}

/**
 * Cards, then playables, by rarity (common, uncommon, rare, mythic), each with its typical value.
 */
@Composable
fun RarityBreakdown(color: ColorUi) {
    val words = RARITY_WORDS.map { stringResource(it) }
    Column(Modifier.padding(top = ITEM_GAP.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        color.cardsByRarity.forEachIndexed { at, value ->
            ValueLine(value) { n, typical ->
                stringResource(R.string.results_rarity_cards, words[at], n, typical)
            }
        }
        color.playablesByRarity.forEachIndexed { at, value ->
            ValueLine(value) { n, typical ->
                stringResource(R.string.results_rarity_playables, words[at], n, typical)
            }
        }
    }
}

@Composable
private fun Line(value: DisplayedValue, @StringRes format: Int) {
    ValueLine(value) { n, typical -> stringResource(format, n, typical) }
}

/** The rarity words, in [ResultsUi.RARITIES]' order. */
private val RARITY_WORDS =
    listOf(
        R.string.results_rarity_common,
        R.string.results_rarity_uncommon,
        R.string.results_rarity_rare,
        R.string.results_rarity_mythic,
    )
