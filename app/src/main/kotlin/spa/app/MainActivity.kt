package spa.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

/** The app's one Activity. Until SPA-016 adds the app shell it shows the placeholder screen. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PlaceholderScreen() }
    }
}
