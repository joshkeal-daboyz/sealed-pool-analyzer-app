package spa.app.timing

import android.os.SystemClock
import android.util.Log

/** Where [SpaTiming] writes its lines: logcat in the app, a fake in tests. */
fun interface TimingSink {
    fun log(tag: String, message: String)
}

/**
 * Goal criterion 13's timing lines: `Log.i("SpaTiming", "<event> <elapsedRealtime>")`, once per
 * occurrence of each event. All calls come from the main thread.
 */
class SpaTiming(
    private val sink: TimingSink = TimingSink { tag, message -> Log.i(tag, message) },
    private val clock: () -> Long = SystemClock::elapsedRealtime,
) {
    private var firstScreenLogged = false
    private var lastResults: Any? = null

    /** `SpaApplication.onCreate`. */
    fun appCreated() = log(APP_CREATED)

    /** The process's first screen drew its first frame. Logged once per [SpaTiming]. */
    fun firstScreenShown() {
        if (firstScreenLogged) return
        firstScreenLogged = true
        log(FIRST_SCREEN_SHOWN)
    }

    /** "Analyze pool" was tapped. */
    fun importConfirmed() = log(IMPORT_CONFIRMED)

    /** Results drew in its loaded state for [import]. Logged once per distinct [import]. */
    fun resultsShown(import: Any) {
        if (import === lastResults) return
        lastResults = import
        log(RESULTS_SHOWN)
    }

    private fun log(event: String) = sink.log(TAG, "$event ${clock()}")

    companion object {
        const val TAG = "SpaTiming"
        const val APP_CREATED = "app-created"
        const val FIRST_SCREEN_SHOWN = "first-screen-shown"
        const val IMPORT_CONFIRMED = "import-confirmed"
        const val RESULTS_SHOWN = "results-shown"
    }
}
