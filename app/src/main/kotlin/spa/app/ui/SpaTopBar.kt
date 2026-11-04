package spa.app.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import spa.app.R
import spa.app.ui.theme.LocalSpaColors

/**
 * The top bar of every screen: [title], a back arrow ("Back") when [onBack] is given, and the About
 * icon ("About") when [onAbout] is given (About itself has none).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpaTopBar(
    title: String,
    onAbout: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    val colors = LocalSpaColors.current
    TopAppBar(
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        modifier = modifier,
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.size(ICON_TARGET.dp)) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.shell_back),
                    )
                }
            }
        },
        actions = {
            if (onAbout != null) {
                IconButton(onClick = onAbout, modifier = Modifier.size(ICON_TARGET.dp)) {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = stringResource(R.string.shell_about),
                    )
                }
            }
        },
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = colors.ground,
                titleContentColor = colors.onSurface,
                navigationIconContentColor = colors.onSurface,
                actionIconContentColor = colors.onSurface,
            ),
    )
}

/** Icon buttons lay out at 40 dp by default; A1 needs 48 dp. */
private const val ICON_TARGET = 48
