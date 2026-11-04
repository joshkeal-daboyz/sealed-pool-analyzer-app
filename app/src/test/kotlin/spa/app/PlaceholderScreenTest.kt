package spa.app

import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaceholderScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun showsTheAppName() {
        compose.onNodeWithText("Sealed Pool Analyzer").assertIsDisplayed()
    }

    @Test
    fun fillsThe411By914DpScreen() {
        compose.onRoot().assertWidthIsEqualTo(411.dp).assertHeightIsEqualTo(914.dp)
    }
}
