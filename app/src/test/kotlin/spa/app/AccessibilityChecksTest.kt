package spa.app

import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.test.core.app.ApplicationProvider
import org.robolectric.Shadows.shadowOf
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Compose's accessibility checks (the spec's A4) run under Robolectric, so the gate runs them. */
@RunWith(AndroidJUnit4::class)
class AccessibilityChecksTest {
    @get:Rule val compose = createComposeRule()

    @Before
    fun enableChecks() {
        val manager =
            ApplicationProvider.getApplicationContext<Context>()
                .getSystemService(AccessibilityManager::class.java)
        shadowOf(manager).setEnabled(true)
        shadowOf(manager).setTouchExplorationEnabled(true)
        compose.enableAccessibilityChecks()
    }

    @Test
    fun placeholderScreenPassesTheChecks() {
        compose.setContent { PlaceholderScreen() }
        compose.onRoot().tryPerformAccessibilityChecks()
    }

    @Test
    fun smallUnlabelledClickableFailsTheChecks() {
        compose.setContent { SmallUnlabelledClickable() }
        val failure =
            assertThrows(Throwable::class.java) { compose.onRoot().tryPerformAccessibilityChecks() }
        assertTrue(
            "expected an accessibility check failure, got $failure",
            failure.javaClass.name.contains("Accessibility"),
        )
    }
}

/** Fails two A4 checks on purpose: a 30 x 30 dp touch target, and nothing to read out. */
@Composable
private fun SmallUnlabelledClickable() {
    Box(Modifier.size(30.dp).clickable {})
}
