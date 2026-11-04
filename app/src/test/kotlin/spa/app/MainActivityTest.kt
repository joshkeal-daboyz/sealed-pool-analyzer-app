package spa.app

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Criterion 3: the real Activity, with no pool, launches on Pool. */
@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun withNoPoolLaunchShowsPool() {
        compose.onNodeWithText("Sealed Pool Analyzer").assertIsDisplayed()
        compose.onNode(hasSetTextAction() and hasText("Paste your pool")).assertIsDisplayed()
        compose.onNodeWithContentDescription("About").assertHasClickAction()
    }

    @Test
    fun fillsThe411By914DpScreen() {
        compose.onRoot().assertWidthIsEqualTo(411.dp).assertHeightIsEqualTo(914.dp)
    }
}
