package spa.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** The colour tokens of the spec's Visual direction ("B · Low light"). There is one scheme. */
@Immutable
data class SpaColors(
    /** Background. */
    val ground: Color,
    /** Cards, rows, the text field. */
    val surface: Color,
    /** Primary text. */
    val onSurface: Color,
    /** Secondary text, typical values. */
    val onSurfaceVariant: Color,
    /** The "or" divider text, chevrons. */
    val muted: Color,
    /** Button, chip, and text-field borders. */
    val outline: Color,
    /** Decorative only. */
    val divider: Color,
    /** Primary button fill, the Problems border and icon, the selected chip's border. */
    val accent: Color,
    /** Text on the accent. */
    val onAccent: Color,
    /** The Problems summary line, outside-typical words, selected-chip text. */
    val accentText: Color,
    /** Selected chip fill. */
    val selectedChip: Color,
) {
    companion object {
        /** The app's only scheme: always dark, whatever the system setting. */
        val Dark =
            SpaColors(
                ground = Color(0xFF121417),
                surface = Color(0xFF1C2026),
                onSurface = Color(0xFFECEFF3),
                onSurfaceVariant = Color(0xFFB8C0CA),
                muted = Color(0xFF8C96A3),
                outline = Color(0xFF6B7480),
                divider = Color(0xFF2E343C),
                accent = Color(0xFFF2B544),
                onAccent = Color(0xFF1A1406),
                accentText = Color(0xFFFFE2A3),
                selectedChip = Color(0xFF3A2E12),
            )
    }
}

/** The [SpaColors] in effect; [SpaTheme] provides [SpaColors.Dark]. */
val LocalSpaColors = staticCompositionLocalOf { SpaColors.Dark }
