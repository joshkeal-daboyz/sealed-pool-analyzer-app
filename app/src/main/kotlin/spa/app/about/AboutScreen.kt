package spa.app.about

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import spa.app.R
import spa.app.ui.SpaTopBar
import spa.app.ui.theme.LocalSpaColors

/** About, a placeholder that shows only its title so the route exists. SPA-020 fills it. */
@Composable
fun AboutScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = {
            SpaTopBar(title = stringResource(R.string.shell_about), onAbout = null, onBack = onBack)
        },
        containerColor = LocalSpaColors.current.ground,
    ) { padding ->
        Box(Modifier.padding(padding))
    }
}
