package spa.agent.otags.cares

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import spa.agent.grader.GradingPackets
import spa.agent.tags.CardFileIds
import spa.agent.tags.TagJson

/**
 * What an import gives: the Fable cares [file] when there are no [problems], else null, the number
 * of [additions] and the table [suggestions] (both empty on any problem).
 */
class CaresImportResult(
    val file: FableCaresFile?,
    val problems: List<String>,
    val additions: Int = 0,
    val suggestions: List<CaresSuggestion> = emptyList(),
)

/**
 * The set an import is for: its [code], the card ids of its sealed pool in collector order
 * ([poolIds]) and the tags of its cares table ([tableTags]).
 */
class CaresImportSet(val code: String, val poolIds: List<String>, val tableTags: Set<String>)

/**
 * Turns the responses in a cares packet (see [CaresPackets]) into a Fable cares file (SPA-055).
 *
 * The packet must hold the set's sealed pool in its card files, `vocabulary.json`, and a
 * `cares-NN.json` for each card file. Then every [CaresCheck] runs on all the responses together.
 * Every problem is one line; on any problem there is no file.
 */
object CaresImport {
    private val json = Json

    /**
     * Checks the responses in [packetDir] for [set] with [checks] and, when all pass, gives the
     * Fable cares file with [provenance].
     */
    fun run(
        packetDir: Path,
        set: CaresImportSet,
        provenance: String,
        checks: List<CaresCheck> = CaresChecks.ALL,
    ): CaresImportResult {
        val packet = readPacket(packetDir, set.code, set.poolIds)
        return if (packet.problems.isNotEmpty()) CaresImportResult(null, packet.problems)
        else respond(packetDir, packet, set, provenance, checks)
    }

    private fun respond(
        packetDir: Path,
        packet: Packet,
        set: CaresImportSet,
        provenance: String,
        checks: List<CaresCheck>,
    ): CaresImportResult {
        val (suggestions, suggestionProblems) = CaresResponses.suggestions(packetDir)
        val responses = packet.cardFiles.map { CaresResponses.read(packetDir.resolve(it.name)) }
        val input =
            CaresCheckInput(
                packet.cardFiles,
                present(packet.cardFiles, responses),
                responses.flatMap { it.cards },
                packet.vocabulary,
                packet.cares,
                CaresTableInput(set.tableTags, suggestions),
            )
        val problems =
            responses.flatMap { it.problems } +
                suggestionProblems +
                checks.flatMap { it.problems(input) }
        if (problems.isNotEmpty()) return CaresImportResult(null, problems)

        val adds = input.responses.groupBy({ it.id }, { it.add }).mapValues { it.value.flatten() }
        val cards =
            set.poolIds
                .filter { adds[it].orEmpty().isNotEmpty() }
                .map { id ->
                    FableCard(
                        id,
                        adds.getValue(id).map {
                            FableCare(it.fact, it.strength.toInt(), it.reason)
                        },
                    )
                }
        val file = FableCaresFile(set.code, provenance, cards)
        return CaresImportResult(file, emptyList(), cards.sumOf { it.cares.size }, suggestions)
    }

    /** The names of the response files that exist, one for each card file that has one. */
    private fun present(
        cardFiles: List<CardFileIds>,
        responses: List<CaresFileResult>,
    ): Set<String> =
        cardFiles
            .zip(responses)
            .filter { (_, response) -> response.present }
            .map { (file, _) -> CaresPackets.responseFileName(file.name) }
            .toSet()

    /** A packet as read: its card files, vocabulary and cares, or what is wrong. */
    private class Packet(
        val cardFiles: List<CardFileIds>,
        val vocabulary: Set<String>,
        val cares: Map<String, Map<String, Int>>,
        val problems: List<String>,
    )

    private fun readPacket(dir: Path, setCode: String, pool: List<String>): Packet {
        val again = "run 'spa cares-packet $setCode --out <dir>' to write the packet"
        val problems = mutableListOf<String>()
        val paths =
            dir.listDirectoryEntries()
                .filter { GradingPackets.isCardFile(it.name) }
                .sortedBy { it.name }
        if (paths.isEmpty()) problems += "$dir: no cards-NN.json files; $again"
        val cardFiles = paths.mapNotNull { path ->
            val ids = cardIds(path)
            if (ids == null) problems += "${path.name}: not a card file"
            ids?.let { CardFileIds(path.name, it) }
        }
        val packed = cardFiles.flatMap { it.ids }
        if (paths.isNotEmpty() && packed.sorted() != pool.sorted()) {
            problems += "the card files do not hold the sealed pool of $setCode; $again"
        }
        val vocabulary = vocabulary(dir, again, problems)
        val cares = cardFiles.flatMap { cares(dir, it.name, again, problems) }.toMap()
        return Packet(cardFiles, vocabulary, cares, problems)
    }

    private fun vocabulary(dir: Path, again: String, problems: MutableList<String>): Set<String> {
        val path = dir.resolve(CaresPackets.VOCABULARY)
        if (!Files.isRegularFile(path)) {
            problems += "${CaresPackets.VOCABULARY}: missing; $again"
            return emptySet()
        }
        return try {
            CaresVocabulary.parse(Files.readString(path)).map { it.fact }.toSet()
        } catch (e: IllegalArgumentException) {
            problems += "${CaresPackets.VOCABULARY}: not a vocabulary file: ${firstLine(e)}"
            emptySet()
        }
    }

    private fun cares(
        dir: Path,
        cardFile: String,
        again: String,
        problems: MutableList<String>,
    ): List<Pair<String, Map<String, Int>>> {
        val name = CaresPackets.caresFileName(cardFile)
        val path = dir.resolve(name)
        if (!Files.isRegularFile(path)) {
            problems += "$name: missing; $again"
            return emptyList()
        }
        return try {
            json
                .decodeFromString(
                    ListSerializer(PacketCaresCard.serializer()),
                    Files.readString(path),
                )
                .map { card -> card.id to card.cares.associate { it.fact to it.strength } }
        } catch (e: SerializationException) {
            problems += "$name: not a cares file: ${firstLine(e)}"
            emptyList()
        }
    }

    private fun firstLine(e: Exception): String = e.message?.lineSequence()?.first().orEmpty()

    /** The ids in a card file, or null when it is not a JSON array of objects with a string id. */
    private fun cardIds(path: Path): List<String>? {
        val cards = TagJson.parse(Files.readString(path)).getOrNull() as? JsonArray
        val ids = cards?.map { TagJson.string((it as? JsonObject)?.get("id")) }
        return if (ids != null && ids.all { it != null }) ids.filterNotNull() else null
    }
}
