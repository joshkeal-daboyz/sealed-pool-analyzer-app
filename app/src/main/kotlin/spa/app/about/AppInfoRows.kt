package spa.app.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import spa.app.R
import spa.app.ui.theme.LocalSpaColors

/** The app's name, "Version <version>", and "Card data snapshot: <date>". */
@Composable
fun AppInfoRows(info: AboutInfo, modifier: Modifier = Modifier) {
    val colors = LocalSpaColors.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(ROW_GAP.dp)) {
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineSmall,
            color = colors.onSurface,
        )
        Text(
            stringResource(R.string.about_version, info.version),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurfaceVariant,
        )
        Text(
            stringResource(R.string.about_snapshot, info.snapshotDate),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurfaceVariant,
        )
    }
}

private const val ROW_GAP = 4
