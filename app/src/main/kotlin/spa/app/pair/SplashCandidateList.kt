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
import spa.app.names.ColorNames
import spa.app.results.DisplayedValue
import spa.app.results.ROW_GAP
import spa.app.results.ROW_PADDING
import spa.app.results.SectionHeading
import spa.app.results.ValueLine
import spa.app.results.rowCard
import spa.app.ui.theme.LocalSpaColors
import spa.core.metrics.SplashCandidate
import spa.core.metrics.SplashReason

/**
 * Splash candidates: the heading, "<count> splash candidates, typical ...", then one
 * [SplashCandidateItem] per candidate in [candidates]' order, or "No splash candidates".
 */
@Composable
fun SplashCandidateList(
    count: DisplayedValue,
    candidates: List<SplashCandidate>,
    modifier: Modifier = Modifier,
) {
    val colors = LocalSpaColors.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(ROW_GAP.dp)) {
        SectionHeading(stringResource(R.string.pair_splash))
        ValueLine(count) { value, typical ->
            pluralStringResource(R.plurals.pair_splash_count, value, value, typical)
        }
        if (candidates.isEmpty()) {
            Text(
                stringResource(R.string.pair_no_splash),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurfaceVariant,
            )
        }
        candidates.forEach { SplashCandidateItem(it) }
    }
}

/** One candidate as one merged node: its name, then its colour as a name and its reason in words. */
@Composable
fun SplashCandidateItem(candidate: SplashCandidate, modifier: Modifier = Modifier) {
    val colors = LocalSpaColors.current
    Column(
        modifier =
            modifier
                .rowCard(colors.surface)
                .semantics(mergeDescendants = true) {}
                .padding(ROW_PADDING.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(candidate.name, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
        Text(
            stringResource(
                R.string.pair_splash_details,
                ColorNames.of(candidate.color),
                stringResource(reasonWords(candidate.reason)),
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurfaceVariant,
        )
    }
}

/** The reason in words: "removal" or "rare or mythic". */
private fun reasonWords(reason: SplashReason): Int =
    when (reason) {
        SplashReason.REMOVAL -> R.string.pair_reason_removal
        SplashReason.RARE_OR_MYTHIC -> R.string.pair_reason_rare_or_mythic
    }
