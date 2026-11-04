package spa.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel

/** The app's one Activity: hosts [SpaApp]. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = application as SpaApplication
        setContent {
            val pool: PoolViewModel = viewModel(factory = PoolViewModel.factory(app.importer))
            SpaApp(pool, app.timing)
        }
    }
}
