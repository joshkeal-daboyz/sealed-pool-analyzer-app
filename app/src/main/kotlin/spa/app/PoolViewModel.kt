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
) : ViewModel() {
    private val mutableState = MutableStateFlow<PoolUiState>(PoolUiState.NoPool)
    private val mutablePool = MutableStateFlow<PoolUiState.Loaded?>(null)
    private val mutableText = MutableStateFlow("")
    private val mutableFormat = MutableStateFlow<PoolFormat?>(null)
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

    fun onTextChange(text: String) {
        mutableText.value = text
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

    private fun run(source: PoolSource, import: suspend (PoolSource) -> ImportOutcome) {
        val id = ++latest
        running?.cancel()
        mutableState.value = PoolUiState.Loading(source)
        running = viewModelScope.launch {
            val outcome = import(source)
            if (id == latest) {
                val state = stateOf(outcome)
                if (state is PoolUiState.Loaded) mutablePool.value = state
                mutableState.value = state
            }
        }
    }

    companion object {
        fun factory(
            importer: Importer,
            files: FileImporter = FileImporter.None,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { PoolViewModel(importer, files) }
        }

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
