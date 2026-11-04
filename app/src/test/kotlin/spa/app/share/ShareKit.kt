package spa.app.share

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ActivityController
import spa.app.MainActivity
import spa.app.testing.TestImports

/**
 * Files another app shares with this one, served through a real content URI as Files or ManaBox
 * would: [pool] serves a fixture under its own name, [put] any bytes, [missing] a URI whose stream
 * cannot be opened. [hold] makes a document's stream wait until [release], so a test can share a
 * second file before the first import completes. Call [install] before each test; Robolectric
 * forgets providers between tests.
 */
class SharedDocuments : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor =
        MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)).apply {
            addRow(arrayOf<Any>(nameOf(uri), docs[nameOf(uri)]?.size ?: 0))
        }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val name = nameOf(uri)
        gates[name]?.await(GATE_SECONDS, TimeUnit.SECONDS)
        val bytes = docs[name] ?: throw FileNotFoundException("No document $name")
        val file = File.createTempFile("shared", ".csv").apply { writeBytes(bytes) }
        file.deleteOnExit()
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String = "text/csv"

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ) = 0

    companion object {
        const val AUTHORITY = "spa.app.test.shared"
        private const val GATE_SECONDS = 30L
        private val docs = ConcurrentHashMap<String, ByteArray>()
        private val gates = ConcurrentHashMap<String, CountDownLatch>()

        fun install() {
            docs.clear()
            gates.values.forEach { it.countDown() }
            gates.clear()
            Robolectric.buildContentProvider(SharedDocuments::class.java).create(AUTHORITY)
        }

        fun put(name: String, bytes: ByteArray): Uri {
            docs[name] = bytes
            return uriOf(name)
        }

        /** `fixtures/pools/<file>`, under its own name. */
        fun pool(file: String): Uri = put(file, TestImports.pool(file).toByteArray())

        /** A URI named [name] that no document stands behind: opening it throws. */
        fun missing(name: String): Uri {
            docs.remove(name)
            return uriOf(name)
        }

        /** Holds [name]'s stream closed until [release]. */
        fun hold(name: String) {
            gates[name] = CountDownLatch(1)
        }

        fun release(name: String) {
            gates.remove(name)?.countDown()
        }

        private fun uriOf(name: String): Uri = Uri.parse("content://$AUTHORITY/doc/$name")

        private fun nameOf(uri: Uri): String = uri.lastPathSegment.orEmpty()
    }
}

/** The intents another app sends, addressed to [MainActivity]. */
object Shares {
    private val context
        get() = RuntimeEnvironment.getApplication()

    /** A share, as Files' share sheet sends it: the file as `EXTRA_STREAM`, or none when null. */
    fun send(uri: Uri?, type: String = "text/csv"): Intent =
        Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_SEND
            this.type = type
            if (uri != null) putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    /** An "Open with": the file as the intent's data, or none when null. */
    fun view(uri: Uri?, type: String = "text/csv"): Intent =
        Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            if (uri != null) setDataAndType(uri, type) else this.type = type
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    /** The app's launcher intent. */
    fun launcher(): Intent =
        Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
}

/**
 * The real [MainActivity], driven as Android drives it: [launch] starts it with an intent,
 * [deliver] hands it a new one while it is open (pausing it around `onNewIntent`, as the system
 * does), and [recreate] re-creates it as a rotation would.
 */
class SharedApp private constructor(private var controller: ActivityController<MainActivity>) {
    /**
     * Presses system back once and says whether that left the app: true only when the screen on
     * show was the whole back stack.
     */
    fun backLeavesTheApp(): Boolean {
        val activity = controller.get()
        activity.onBackPressedDispatcher.onBackPressed()
        return activity.isFinishing
    }

    /** Hands [intent] to the running Activity as Android does when the app is already open. */
    fun deliver(intent: Intent) {
        controller.newIntent(intent)
    }

    fun recreate() {
        controller = controller.recreate()
    }

    fun close() {
        if (!controller.get().isDestroyed) controller.pause().stop().destroy()
    }

    companion object {
        fun launch(intent: Intent): SharedApp =
            SharedApp(Robolectric.buildActivity(MainActivity::class.java, intent).setup())
    }
}

/** Waits for [text] to appear anywhere, as an import runs off the main thread. */
fun ComposeTestRule.waitForText(text: String, substring: Boolean = false) {
    waitUntil(TIMEOUT) {
        onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty()
    }
}

/** Whether [text] is shown anywhere. */
fun ComposeTestRule.shows(text: String, substring: Boolean = false): Boolean =
    onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty()

/** Real imports read the bundled set data the first time, which can take a while. */
const val TIMEOUT = 30_000L
