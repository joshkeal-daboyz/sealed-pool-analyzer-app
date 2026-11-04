package spa.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel

/** The app's one Activity: hosts [SpaApp]. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Always dark (decision 8): light bar icons on the dark ground, whatever the system mode.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val app = application as SpaApplication
        setContent {
            val pool: PoolViewModel = viewModel(factory = PoolViewModel.factory(app.importer))
            SpaApp(pool, app.timing)
        }
    }
}
