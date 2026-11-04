package spa.app.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import spa.app.R
import spa.app.ui.SpaTopBar
import spa.app.ui.theme.LocalSpaColors

/**
 * About: the app's name, version, and card data snapshot date, then the "Card data" heading and the
 * attribution, in one vertical scroll. Everything on it is bundled at build time, so it has one
 * state.
 */
@Composable
fun AboutScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val info = remember(context) { AboutInfo.read(context) }
    val colors = LocalSpaColors.current
    Scaffold(
        modifier = modifier,
        topBar = {
            SpaTopBar(title = stringResource(R.string.shell_about), onAbout = null, onBack = onBack)
        },
        containerColor = colors.ground,
    ) { padding ->
        Column(
            modifier =
                Modifier.padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = SIDE.dp, vertical = TOP.dp),
            verticalArrangement = Arrangement.spacedBy(GAP.dp),
        ) {
            AppInfoRows(info)
            Text(
                stringResource(R.string.about_card_data),
                modifier = Modifier.padding(top = HEADING_GAP.dp).semantics { heading() },
                style = MaterialTheme.typography.headlineSmall,
                color = colors.onSurface,
            )
            AttributionText(Modifier.fillMaxWidth())
        }
    }
}

private const val SIDE = 16
private const val TOP = 12
private const val GAP = 12
private const val HEADING_GAP = 12
