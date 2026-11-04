package spa.app

import androidx.annotation.StringRes
import spa.app.importing.ImportFailureKind
import spa.app.importing.ImportOutcome
import spa.app.importing.PoolSource
import spa.core.intake.PoolFormat
import spa.core.intake.UnresolvedLine

/** What the app holds about the pool; screens are drawn from it. */
sealed interface PoolUiState {
    /** No pool yet: launch shows Pool. */
    data object NoPool : PoolUiState

    /** An import from [source] is running. */
    data class Loading(val source: PoolSource) : PoolUiState

    /** An import from [source] succeeded, read as [format]. */
    data class Loaded(
        val result: ImportOutcome.Success,
        val source: PoolSource,
        val format: PoolFormat,
    ) : PoolUiState

    /** An import from [source] gave no pool, for [kind]; [lines] are its unresolved lines. */
    data class ImportFailed(
        val kind: ImportFailureKind,
        val source: PoolSource,
        val lines: List<UnresolvedLine>,
    ) : PoolUiState

    /** The bundled set data did not load. */
    data object DataMissing : PoolUiState
}

/** The spec's name for this format: "ManaBox CSV", "List with counts", "Collector numbers". */
@StringRes
fun PoolFormat.nameRes(): Int =
    when (this) {
        PoolFormat.MANABOX -> R.string.format_manabox
        PoolFormat.TEXT -> R.string.format_text
        PoolFormat.COLLECTOR -> R.string.format_collector
    }
