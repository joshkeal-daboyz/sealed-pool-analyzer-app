package spa.app

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import spa.app.importing.ImportFailureKind
import spa.app.importing.ImportOutcome
import spa.app.importing.PoolSource
import spa.app.store.PoolStore
import spa.app.store.SavedPool
import spa.core.intake.PoolFormat

/** One import: the app passes [spa.app.importing.PoolImporter.import]; tests pass fakes. */
fun interface Importer {
    suspend fun import(text: String, format: PoolFormat?, source: PoolSource): ImportOutcome
}

/**
 * Imports a document the system picker gave. The app passes [spa.app.pool.ContentFileImporter],
 * which reads through [spa.app.importing.PoolImporter.importStream]; tests pass fakes.
 */
interface FileImporter {
    /** The document's display name, the import's source. */
    fun displayName(uri: Uri): String

    /** Imports the document at [uri], always with its format detected. */
    suspend fun import(uri: Uri, source: PoolSource): ImportOutcome

    /** For a ViewModel built with no way to read files: every file is unreadable. */
    object None : FileImporter {
        override fun displayName(uri: Uri): String = uri.lastPathSegment ?: uri.toString()

        override suspend fun import(uri: Uri, source: PoolSource): ImportOutcome =
            ImportOutcome.Failure(ImportFailureKind.UNREADABLE, source, emptyList())
    }
}

/**
 * Holds the pool's [state], the current pool, and the Pool screen's pasted text and chosen format,
 * and runs imports.
 */
class PoolViewModel(
    private val importer: Importer,
    private val files: FileImporter = FileImporter.None,
    private val store: PoolStore? = null,
) : ViewModel() {
    private val mutableState = MutableStateFlow<PoolUiState>(PoolUiState.NoPool)
    private val mutablePool = MutableStateFlow<PoolUiState.Loaded?>(null)
    private val mutableText = MutableStateFlow("")
    private val mutableFormat = MutableStateFlow<PoolFormat?>(null)
    private val mutableIncoming = MutableStateFlow<Long?>(null)
    private var running: Job? = null
    private var latest = 0L

    /** The latest import's state: what Results shows while loading, and Pool's error card. */
    val state: StateFlow<PoolUiState> = mutableState.asStateFlow()

    /** The last pool that imported successfully. A failed import never changes it (decision 5). */
    val currentPool: StateFlow<PoolUiState.Loaded?> = mutablePool.asStateFlow()

    /** The text in the paste box. Kept across a failed import; cleared by [startNewPool]. */
    val pastedText: StateFlow<String> = mutableText.asStateFlow()

    /**
     * The format chosen by hand for pasted text, or null for "Auto". Kept across a failed import.
     */
    val chosenFormat: StateFlow<PoolFormat?> = mutableFormat.asStateFlow()

    /**
     * Whether this ViewModel started by re-reading a remembered pool, so launch opens Results in
     * Loading (decision 1). A pool file too damaged to re-read goes straight to the saved-pool
     * unreadable error instead, and this is false.
     */
    val startsWithSavedPool: Boolean = restore()

    /**
     * "Clear pool": deletes the pool file and forgets the current pool, dropping any import still
     * running; the Pool screen is back to Empty, with an empty paste box on "Auto".
     */
    fun clearPool() {
        latest++
        running?.cancel()
        store?.clear()
        mutablePool.value = null
        mutableState.value = PoolUiState.NoPool
        mutableText.value = ""
        mutableFormat.value = null
    }

    /**
     * Re-imports the remembered pool, if any, with its stored text, format, and source, and says
     * whether it did.
     */
    private fun restore(): Boolean =
        when (val saved = store?.read() ?: SavedPool.None) {
            SavedPool.None -> false
            is SavedPool.Unreadable -> {
                mutableState.value = savedPoolUnreadable(saved.source ?: UNNAMED_SAVED_POOL)
                false
            }
            is SavedPool.Pool -> {
                run(saved.source, restoring = true) {
                    importer.import(saved.text, saved.format, it)
                }
                true
            }
        }

    fun onTextChange(text: String) {
        mutableText.value = text
    }

    /**
     * The id of the latest share or "Open with" import that the navigation has not moved to yet, or
     * null when there is none.
     */
    val incomingImport: StateFlow<Long?> = mutableIncoming.asStateFlow()

    /**
     * A share or "Open with" handed the app the file at [uri], or no file at all when null: imports
     * it as a picked file, and marks it in [incomingImport] for the navigation. With no file, the
     * import is unreadable at once ("Couldn't read this file").
     */
    fun importIncoming(uri: Uri?) {
        if (uri != null) {
            analyzeFile(uri)
        } else {
            run(PoolSource.File(NO_FILE_NAME)) {
                ImportOutcome.Failure(ImportFailureKind.UNREADABLE, it, emptyList())
            }
        }
        mutableIncoming.value = latest
    }

    /** The navigation moved to the incoming import [id]: unmarks it, unless a newer one came. */
    fun onIncomingShown(id: Long) {
        mutableIncoming.compareAndSet(id, null)
    }

    /** A chip was tapped: [format], or null for "Auto". */
    fun onFormatChoose(format: PoolFormat?) {
        mutableFormat.value = format
    }

    /**
     * "New pool": the paste box starts empty, not filled with the current pool, the chooser is back
     * on "Auto", and an earlier failed import's error card is gone.
     */
    fun startNewPool() {
        mutableText.value = ""
        mutableFormat.value = null
        if (mutableState.value is PoolUiState.ImportFailed) {
            mutableState.value = mutablePool.value ?: PoolUiState.NoPool
        }
    }

    /**
     * Imports pasted [text] as [format], or auto-detected when null. Moves to
     * [PoolUiState.Loading], then to the import's state. The latest import wins: an earlier one
     * still running is cancelled, and its result is dropped even if it finishes anyway.
     */
    fun analyze(text: String, format: PoolFormat?) {
        run(PoolSource.Pasted) { importer.import(text, format, it) }
    }

    /** Imports the picked document at [uri], with its display name as the source. */
    fun analyzeFile(uri: Uri) {
        run(PoolSource.File(files.displayName(uri))) { files.import(uri, it) }
    }

    /**
     * Runs [import] from [source]. A success replaces the pool file (decision 5: a failure never
     * touches it), unless it is the remembered pool being [restoring], whose failure is the
     * saved-pool unreadable error.
     */
    private fun run(
        source: PoolSource,
        restoring: Boolean = false,
        import: suspend (PoolSource) -> ImportOutcome,
    ) {
        val id = ++latest
        running?.cancel()
        mutableState.value = PoolUiState.Loading(source)
        running = viewModelScope.launch {
            val outcome = import(source)
            if (id == latest) {
                val state =
                    if (restoring && outcome is ImportOutcome.Failure) savedPoolUnreadable(source)
                    else stateOf(outcome)
                if (state is PoolUiState.Loaded) {
                    // The file is small; written here so it is on disk once the pool shows.
                    if (!restoring) store?.save(state.result.text, state.format, state.source)
                    mutablePool.value = state
                }
                mutableState.value = state
            }
        }
    }

    companion object {
        /**
         * The source of a share or "Open with" that carried no file, so the "Couldn't read this
         * file" card has a name to give.
         */
        const val NO_FILE_NAME = "The shared file"

        /**
         * The source of a remembered pool whose file is too damaged to name one: a file with no
         * name, which the "Your last pool couldn't be read" card calls "Your pool".
         */
        val UNNAMED_SAVED_POOL: PoolSource = PoolSource.File("")

        fun factory(
            importer: Importer,
            files: FileImporter = FileImporter.None,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                // The pool file lives in the app's private storage.
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                PoolViewModel(importer, files, app?.let { PoolStore(it.filesDir) })
            }
        }

        private fun savedPoolUnreadable(source: PoolSource): PoolUiState =
            PoolUiState.ImportFailed(ImportFailureKind.SAVED_POOL_UNREADABLE, source, emptyList())

        private fun stateOf(outcome: ImportOutcome): PoolUiState =
            when (outcome) {
                is ImportOutcome.Success ->
                    PoolUiState.Loaded(outcome, outcome.source, outcome.format)
                is ImportOutcome.Failure ->
                    PoolUiState.ImportFailed(outcome.kind, outcome.source, outcome.unresolved)
                is ImportOutcome.DataMissing -> PoolUiState.DataMissing
            }
    }
}
