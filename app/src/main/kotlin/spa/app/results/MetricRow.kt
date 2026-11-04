package spa.app.results

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import spa.app.R
import spa.app.ui.theme.LocalSpaColors
import spa.core.baseline.Stat

/**
 * The shared metric row: [value] large on the left, then [label], its [TypicalRange], and the
 * [OutsideTypicalTag] when it applies. One merged semantics node, tagged with the value's pointer.
 */
@Composable
fun MetricRow(label: String, value: DisplayedValue, modifier: Modifier = Modifier) {
    val colors = LocalSpaColors.current
    val type = MaterialTheme.typography
    Row(
        modifier =
            modifier
                .rowCard(colors.surface)
                .testTag(value.pointer)
                .semantics(mergeDescendants = true) {}
                .padding(ROW_PADDING.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ROW_PADDING.dp),
    ) {
        Text(
            "${value.value}",
            modifier = Modifier.widthIn(min = VALUE_MIN_WIDTH.dp),
            style = type.displayMedium,
            color = colors.onSurface,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = type.titleMedium, color = colors.onSurface)
            value.typical?.let { TypicalRange(it) }
            OutsideTypicalTag(value.outside)
        }
    }
}

/** "typical <median> (<p10>–<p90>)", or "typical <median>" when p10 equals p90. */
@Composable
fun TypicalRange(stat: Stat, modifier: Modifier = Modifier) {
    Text(
        typicalText(stat),
        modifier = modifier,
        style = MaterialTheme.typography.bodyLarge,
        color = LocalSpaColors.current.onSurfaceVariant,
    )
}

/** "below typical" or "above typical" in the accent text colour; nothing when [outside] is null. */
@Composable
fun OutsideTypicalTag(outside: Outside?, modifier: Modifier = Modifier) {
    val words =
        when (outside) {
            Outside.BELOW -> R.string.results_below_typical
            Outside.ABOVE -> R.string.results_above_typical
            null -> return
        }
    Text(
        stringResource(words),
        modifier = modifier,
        style = MaterialTheme.typography.bodyLarge,
        color = LocalSpaColors.current.accentText,
    )
}

/** The typical line's text; the baseline's stats outside `castProbability` are whole numbers. */
@Composable
fun typicalText(stat: Stat): String {
    val median = stat.median.roundToInt()
    val p10 = stat.p10.roundToInt()
    val p90 = stat.p90.roundToInt()
    return if (p10 == p90) stringResource(R.string.results_typical_median, median)
    else stringResource(R.string.results_typical_range, median, p10, p90)
}

/**
 * One value written as a line, such as "Cards 17, typical 14 (10–19)", built by [line] from the
 * value and its typical line, with the [OutsideTypicalTag] under it. Tagged with the pointer.
 */
@Composable
fun ValueLine(value: DisplayedValue, line: @Composable (Int, String) -> String) {
    val typical = value.typical?.let { typicalText(it) }.orEmpty()
    Column(Modifier.testTag(value.pointer)) {
        Text(
            line(value.value, typical),
            style = MaterialTheme.typography.bodyLarge,
            color = LocalSpaColors.current.onSurfaceVariant,
        )
        OutsideTypicalTag(value.outside)
    }
}

/** A section heading, 22 sp bold, exposed as a heading (A6), with [top] dp of space above it. */
@Composable
fun SectionHeading(text: String, modifier: Modifier = Modifier, top: Int = HEADING_TOP) {
    Text(
        text,
        modifier = modifier.padding(top = top.dp).semantics { heading() },
        style = MaterialTheme.typography.headlineSmall,
        color = LocalSpaColors.current.onSurface,
    )
}

/** Each row is its own [surface] card, radius 14 dp. */
internal fun Modifier.rowCard(surface: Color): Modifier =
    fillMaxWidth().clip(RoundedCornerShape(ROW_RADIUS.dp)).background(surface)

internal const val ROW_PADDING = 16
internal const val ROW_GAP = 10
private const val ROW_RADIUS = 14
private const val VALUE_MIN_WIDTH = 56
private const val HEADING_TOP = 14
