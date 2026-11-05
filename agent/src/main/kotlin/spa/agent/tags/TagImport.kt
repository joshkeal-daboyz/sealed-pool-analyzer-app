package spa.agent.tags

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import spa.agent.grader.GradingPackets
import spa.agent.grader.GradingPool
import spa.core.cards.CardIndex
import spa.core.cards.Printing

/** What an import gives: the tag [file] when there are no [problems], else null. */
data class TagImportResult(val file: TagFile?, val problems: List<String>)

/**
 * Turns a pass-2 tagging packet's responses into a tag file (see [TagPackets]).
 *
 * The packet's card files must hold exactly the set's [GradingPool], and its `axes.json` must be a
 * valid axes file. Each card file needs its `cards-NN.response.json` beside it, well formed (see
 * [TagResponses]). Then every [TagCheck] in [checks] runs on all the responses together. Every
 * problem is one line; on any problem there is no file.
 */
object TagImport {
    fun run(
        packetDir: Path,
        index: CardIndex,
        setCode: String,
        provenance: String,
        checks: List<TagCheck> = TagChecks.ALL,
    ): TagImportResult {
        val pool = GradingPool.printings(index)
        val packet = readPacket(packetDir, pool, setCode)
        // The checks run only on a packet that reads whole.
        val problems =
            packet.problems.ifEmpty {
                val input = TagCheckInput(packet.cardFiles, packet.axes, packet.responses)
                checks.flatMap { it.problems(input) }
            }
        if (problems.isNotEmpty()) return TagImportResult(null, problems)

        val byId = packet.responses.associateBy { it.id }
        val cards = pool.map { tagCard(it, byId.getValue(GradingPool.id(it))) }
        val file = TagFile(setCode, provenance, packet.axes, cards)
        return try {
            TagImportResult(TagFiles.parse(TagFiles.write(file)), emptyList())
        } catch (e: TagFileException) {
            TagImportResult(null, e.problems.map { "tag file: $it" })
        }
    }

    /** A packet as read: its card files, axes and well-formed responses, or what is wrong. */
    private class Packet(
        val cardFiles: List<CardFileIds>,
        val axes: List<Axis>,
        val responses: List<ResponseCard>,
        val problems: List<String>,
    )

    private fun readPacket(dir: Path, pool: List<Printing>, setCode: String): Packet {
        val again = "run 'spa tags-packet $setCode --axes <file>' to write the packet"
        val problems = mutableListOf<String>()
        val axesPath = dir.resolve(TagPackets.AXES)
        val axes =
            if (Files.isRegularFile(axesPath)) {
                val result = AxesFile.parse(Files.readString(axesPath), TagPackets.AXES)
                problems += result.problems
                result.axes.orEmpty()
            } else {
                problems += "${TagPackets.AXES}: missing; $again"
                emptyList()
            }
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
        val poolIds = pool.map(GradingPool::id)
        if (paths.isNotEmpty() && packed.sorted() != poolIds.sorted()) {
            problems += "the card files do not hold the sealed pool of $setCode; $again"
        }
        val responses = paths.map(TagResponses::read)
        problems += responses.flatMap { it.problems }
        return Packet(cardFiles, axes, responses.flatMap { it.cards }, problems)
    }

    /** The ids in a card file, or null when it is not a JSON array of objects with a string id. */
    private fun cardIds(path: Path): List<String>? {
        val cards = TagJson.parse(Files.readString(path)).getOrNull() as? JsonArray
        val ids = cards?.map { TagJson.string((it as? JsonObject)?.get("id")) }
        return if (ids != null && ids.all { it != null }) ids.filterNotNull() else null
    }

    private fun tagCard(printing: Printing, response: ResponseCard) =
        TagCard(
            id = GradingPool.id(printing),
            name = printing.card.name,
            roles = response.roles.map { TagRole(it.axis, it.role, it.strength.toInt()) },
            partners = response.partners.map { TagPartner(it.id, it.strength.toInt()) },
        )
}
