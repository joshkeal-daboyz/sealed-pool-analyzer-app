package spa.app.pair

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import spa.app.R
import spa.app.ui.theme.LocalSpaColors

/** "This pool has <n> lines not read; these numbers leave them out." */
@Composable
fun UnreadLinesReminder(count: Int, modifier: Modifier = Modifier) {
    Text(
        pluralStringResource(R.plurals.pair_lines_not_read, count, count),
        modifier = modifier,
        style = MaterialTheme.typography.bodyLarge,
        color = LocalSpaColors.current.onSurface,
    )
}
