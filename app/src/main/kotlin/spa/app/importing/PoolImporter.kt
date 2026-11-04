package spa.app.importing

import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import spa.app.data.SetData
import spa.app.data.SetDataException
import spa.core.intake.ParseResult
import spa.core.intake.PoolFormat
import spa.core.intake.PoolParseException
import spa.core.intake.PoolParser
import spa.core.intake.UnresolvedReason
import spa.core.metrics.MetricEngine

/**
 * Turns pool text into results, the way `spa analyze` does: [PoolParser.parse] against the bundled
 * set data with the brief's kit template, then [MetricEngine.compute]. Never throws for bad input
 * or missing set data; every way an import can end is an [ImportOutcome].
 */
class PoolImporter(
    private val setData: SetData,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    /** The format [PoolParser] would read [text] as. */
    fun detect(text: String): PoolFormat = PoolParser.detect(text)

    /** Imports [text] as [format], or as its detected format when [format] is null. */
    suspend fun import(text: String, format: PoolFormat?, source: PoolSource): ImportOutcome {
        val set =
            try {
                setData.load()
            } catch (e: SetDataException) {
                return ImportOutcome.DataMissing(e)
            }
        return withContext(dispatcher) {
            try {
                outcome(PoolParser.parse(text, set.index, format, set.brief.kitTemplate), source)
            } catch (e: PoolParseException) {
                // A ManaBox header missing a required column: no line can be read.
                ImportOutcome.Failure(ImportFailureKind.NOT_A_POOL, source, emptyList())
            }
        }
    }

    /**
     * Reads the stream [open] gives as strict UTF-8 and imports it with its format detected. A
     * stream that cannot be opened or read, or bytes that are not valid UTF-8, give
     * [ImportFailureKind.UNREADABLE].
     */
    suspend fun importStream(open: () -> InputStream, source: PoolSource): ImportOutcome {
        val text = withContext(Dispatchers.IO) { readText(open) }
        return text.fold(
            onSuccess = { import(it, null, source) },
            onFailure = {
                ImportOutcome.Failure(ImportFailureKind.UNREADABLE, source, emptyList())
            },
        )
    }

    /** The stream's text, or the [IOException] that stopped it. */
    private fun readText(open: () -> InputStream): Result<String> =
        try {
            Result.success(decode(open().use { it.readBytes() }))
        } catch (e: IOException) {
            // CharacterCodingException, for bytes that are not UTF-8, is an IOException.
            Result.failure(e)
        }

    companion object {
        /**
         * [bytes] as UTF-8 text. Malformed input throws a
         * [java.nio.charset.CharacterCodingException] rather than becoming a replacement character.
         */
        internal fun decode(bytes: ByteArray): String =
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()

        /** The kind of a finished parse that resolved no entries, by the Pool screen's rules. */
        internal fun classify(result: ParseResult): ImportFailureKind =
            when {
                result.unresolved.isEmpty() -> ImportFailureKind.EMPTY
                result.unresolved.all { it.reason == UnresolvedReason.WRONG_SET } ->
                    ImportFailureKind.WRONG_SET
                else -> ImportFailureKind.NOT_A_POOL
            }

        private fun outcome(result: ParseResult, source: PoolSource): ImportOutcome =
            if (result.pool.entries.isEmpty()) {
                ImportOutcome.Failure(classify(result), source, result.unresolved)
            } else {
                ImportOutcome.Success(
                    result = result,
                    metrics = MetricEngine.compute(result.pool),
                    format = result.format,
                    source = source,
                )
            }
    }
}
