package spa.app

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
import spa.app.importing.ImportOutcome
import spa.app.importing.PoolSource
import spa.core.intake.PoolFormat

/** One import: the app passes [spa.app.importing.PoolImporter.import]; tests pass fakes. */
fun interface Importer {
    suspend fun import(text: String, format: PoolFormat?, source: PoolSource): ImportOutcome
}

/** Holds the pool's [state] and the Pool screen's pasted text, and runs imports. */
class PoolViewModel(private val importer: Importer) : ViewModel() {
    private val mutableState = MutableStateFlow<PoolUiState>(PoolUiState.NoPool)
    private val mutableText = MutableStateFlow("")
    private var running: Job? = null
    private var latest = 0L

    val state: StateFlow<PoolUiState> = mutableState.asStateFlow()

    /** The text in the paste box. Kept across a failed import; cleared by [startNewPool]. */
    val pastedText: StateFlow<String> = mutableText.asStateFlow()

    fun onTextChange(text: String) {
        mutableText.value = text
    }

    /** "New pool": the paste box starts empty, not filled with the current pool. */
    fun startNewPool() {
        mutableText.value = ""
    }

    /**
     * Imports pasted [text] as [format], or auto-detected when null. Moves to
     * [PoolUiState.Loading], then to the import's state. The latest import wins: an earlier one
     * still running is cancelled, and its result is dropped even if it finishes anyway.
     */
    fun analyze(text: String, format: PoolFormat?) {
        val source = PoolSource.Pasted
        val id = ++latest
        running?.cancel()
        mutableState.value = PoolUiState.Loading(source)
        running = viewModelScope.launch {
            val outcome = importer.import(text, format, source)
            if (id == latest) mutableState.value = stateOf(outcome)
        }
    }

    companion object {
        fun factory(importer: Importer): ViewModelProvider.Factory = viewModelFactory {
            initializer { PoolViewModel(importer) }
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
