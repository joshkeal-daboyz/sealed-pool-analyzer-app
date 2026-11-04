package spa.app.pool

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.performClick
import androidx.core.app.ActivityOptionsCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.ConcurrentHashMap
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import spa.app.Importer
import spa.app.PoolViewModel
import spa.app.SpaApp
import spa.app.testing.RecordingSink
import spa.app.testing.TestImports
import spa.app.timing.SpaTiming

/**
 * Documents served to the app under test through a real content URI, as the system picker gives
 * them: [put] registers bytes under a display name, [throwing] a document whose stream cannot be
 * opened. Call [install] before each test; Robolectric forgets providers between tests.
 */
class TestDocuments : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        if (nameOf(uri).startsWith(FORBIDDEN)) throw SecurityException("No access to $uri")
        return MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)).apply {
            addRow(arrayOf<Any>(nameOf(uri), docs[nameOf(uri)]?.size ?: 0))
        }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val bytes = docs[nameOf(uri)] ?: throw FileNotFoundException("No document ${nameOf(uri)}")
        val file = File.createTempFile("doc", ".bin").apply { writeBytes(bytes) }
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
        const val AUTHORITY = "spa.app.test.documents"
        private val docs = ConcurrentHashMap<String, ByteArray>()

        fun install() {
            docs.clear()
            Robolectric.buildContentProvider(TestDocuments::class.java).create(AUTHORITY)
        }

        /** A document named [name] holding [bytes]. */
        fun put(name: String, bytes: ByteArray): Uri {
            docs[name] = bytes
            return uriOf(name)
        }

        /** `fixtures/pools/<file>`, under its own name. */
        fun pool(file: String): Uri = put(file, TestImports.pool(file).toByteArray())

        /** A document named [name] whose stream throws when opened. */
        fun throwing(name: String): Uri {
            docs.remove(name)
            return uriOf(name)
        }

        /** A document whose provider refuses the name query; its URI ends in [name]. */
        fun refusingName(name: String, bytes: ByteArray): Uri = put(FORBIDDEN + name, bytes)

        private const val FORBIDDEN = "forbidden-"

        private fun uriOf(name: String): Uri = Uri.parse("content://$AUTHORITY/doc/$name")

        private fun nameOf(uri: Uri): String = uri.lastPathSegment.orEmpty()
    }
}

/**
 * Stands in for the system picker: each launch records its input (the MIME types) and returns
 * [next] at once; null is a cancelled picker.
 */
class FakePicker : ActivityResultRegistry() {
    var next: Uri? = null
    val launches = mutableListOf<Any?>()

    override fun <I, O> onLaunch(
        requestCode: Int,
        contract: ActivityResultContract<I, O>,
        input: I,
        options: ActivityOptionsCompat?,
    ) {
        launches += input
        dispatchResult(requestCode, next)
    }
}

/** The whole app under test, with the [FakePicker] in place of the system picker. */
class PoolApp(private val compose: ComposeContentTestRule) {
    val picker = FakePicker()
    val sink = RecordingSink()
    lateinit var nav: NavHostController
    lateinit var viewModel: PoolViewModel

    /** The app's real import paths over the fixtures' set data, and [TestDocuments]. */
    fun realViewModel(importer: Importer = TestImports.importer) =
        PoolViewModel(
            importer,
            ContentFileImporter(RuntimeEnvironment.getApplication().contentResolver, poolImporter),
        )

    fun launch(vm: PoolViewModel = realViewModel()) {
        viewModel = vm
        val owner =
            object : ActivityResultRegistryOwner {
                override val activityResultRegistry: ActivityResultRegistry = picker
            }
        compose.setContent {
            nav = rememberNavController()
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                SpaApp(vm, SpaTiming(sink), nav)
            }
        }
        compose.waitForIdle()
    }

    /** Picks [uri] with "Open file", or cancels the picker when null. */
    fun pick(uri: Uri?) {
        picker.next = uri
        compose.onNode(hasText("Open file") and hasClickAction()).performClick()
        compose.waitForIdle()
    }

    /** Waits for [text] to appear anywhere, as an import runs off the main thread. */
    fun waitForText(text: String, substring: Boolean = false) {
        compose.waitUntil(TIMEOUT) {
            compose
                .onAllNodes(hasText(text, substring = substring))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private val poolImporter
        get() = TestImports.poolImporter

    companion object {
        const val TIMEOUT = 15_000L
    }
}

/** The paste box, found by its label (P1). */
fun ComposeContentTestRule.pasteBox() = onNode(hasSetTextAction() and hasText("Paste your pool"))

/** A node that is a semantics heading (A6). */
val isHeading: SemanticsMatcher = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)

/** Ten lines of prose: a pasted "not a pool" with 10 unread lines. */
val PROSE_LINES =
    listOf(
        "Dear diary, today we went to the prerelease.",
        "The store was full of people opening boxes.",
        "I sat near the window with a cup of tea.",
        "My friend brought snacks for the whole table.",
        "We talked about the new set for an hour.",
        "Nobody could agree on the best colour pair.",
        "Then the judge called the start of round one.",
        "I lost the first game but won the next two.",
        "After that we went home on the late bus.",
        "Next week we will do it all over again.",
    )

/** [PROSE_LINES] as one text. */
val PROSE_TEXT: String = PROSE_LINES.joinToString("\n")
