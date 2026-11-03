package spa.core.sim

import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import spa.core.intake.PoolFormat

/** `fixtures/pools/manifest.json`: every pool fixture and what parsing it must give. */
@Serializable
data class FixtureManifest(val fixtures: List<FixtureEntry>) {
    /** The manifest as JSON with two-space indents and a final line break. */
    fun encode(): String = json.encodeToString(serializer(), this) + "\n"

    companion object {
        const val FILE_NAME = "manifest.json"

        @OptIn(ExperimentalSerializationApi::class)
        private val json = Json {
            prettyPrint = true
            prettyPrintIndent = "  "
        }

        fun decode(text: String): FixtureManifest = json.decodeFromString(serializer(), text)

        /** The manifest in [poolsDir], or an empty one when there is none yet. */
        fun load(poolsDir: Path): FixtureManifest {
            val file = poolsDir.resolve(FILE_NAME)
            return if (Files.exists(file)) decode(Files.readString(file))
            else FixtureManifest(emptyList())
        }
    }
}

/**
 * One fixture: its [file] name, the [set] index and [format] it parses with, the [seed] it was
 * sampled from or null when it was edited by hand, the total count of the parsed pool
 * [expectedCards], and every line that must stay unresolved.
 */
@Serializable
data class FixtureEntry(
    val file: String,
    val set: String,
    val format: String,
    val seed: Long?,
    val expectedCards: Int,
    val expectedUnresolved: List<ExpectedUnresolved>,
) {
    /** [format] as the parser's format. */
    val poolFormat: PoolFormat
        get() = PoolFormat.valueOf(format.uppercase(Locale.ROOT))

    companion object {
        /** The manifest's name for [format]: `manabox`, `text`, or `collector`. */
        fun formatName(format: PoolFormat): String = format.name.lowercase(Locale.ROOT)
    }
}

/** A line that must not resolve: its 1-based [line] and the parser's [reason] code. */
@Serializable data class ExpectedUnresolved(val line: Int, val reason: String)
