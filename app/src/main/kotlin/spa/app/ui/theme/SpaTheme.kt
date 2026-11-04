package spa.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * The app's theme: [SpaColors.Dark] with no dynamic colour, whatever the system's light setting
 * (decision 8), and [SpaTypography].
 */
@Composable
fun SpaTheme(content: @Composable () -> Unit) {
    val c = SpaColors.Dark
    val scheme =
        darkColorScheme(
            primary = c.accent,
            onPrimary = c.onAccent,
            primaryContainer = c.selectedChip,
            onPrimaryContainer = c.accentText,
            secondary = c.accent,
            onSecondary = c.onAccent,
            secondaryContainer = c.selectedChip,
            onSecondaryContainer = c.accentText,
            background = c.ground,
            onBackground = c.onSurface,
            surface = c.surface,
            onSurface = c.onSurface,
            surfaceVariant = c.surface,
            onSurfaceVariant = c.onSurfaceVariant,
            surfaceContainerLowest = c.ground,
            surfaceContainerLow = c.surface,
            surfaceContainer = c.surface,
            surfaceContainerHigh = c.surface,
            surfaceContainerHighest = c.surface,
            outline = c.outline,
            outlineVariant = c.divider,
        )
    CompositionLocalProvider(LocalSpaColors provides c) {
        MaterialTheme(colorScheme = scheme, typography = SpaTypography, content = content)
    }
}
