package spa.app

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.core.view.WindowCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Criterion 11 (decision 8, always dark): the system bars keep light icons on the dark ground, even
 * when the system is in light mode.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "+notnight")
class SystemBarsTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun statusBarIconsAreLightInSystemLightMode() {
        compose.waitForIdle()
        val window = compose.activity.window
        val bars = WindowCompat.getInsetsController(window, window.decorView)
        assertFalse("light-status-bar appearance is on", bars.isAppearanceLightStatusBars)
    }

    @Test
    fun navigationBarIconsAreLightInSystemLightMode() {
        compose.waitForIdle()
        val window = compose.activity.window
        val bars = WindowCompat.getInsetsController(window, window.decorView)
        assertFalse("light-navigation-bar appearance is on", bars.isAppearanceLightNavigationBars)
    }
}
