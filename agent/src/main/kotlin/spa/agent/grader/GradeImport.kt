package spa.agent.grader

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import spa.agent.grade.CardGrade
import spa.agent.grade.GradeEntry
import spa.agent.grade.GradeFile
import spa.agent.grade.GradeFileException
import spa.agent.grade.GradeFileHeader
import spa.agent.grade.GradeFiles
import spa.agent.grade.GradeSource
import spa.core.cards.CardIndex
import spa.core.cards.Printing

/** What an import gives: the grade [file] when there are no [problems], else null. */
data class ImportResult(val file: GradeFile?, val problems: List<String>)

/**
 * Turns a grader's response files into a grade file with source `MODEL`.
 *
 * Every `cards-NN.json` in the packet directory must have its `cards-NN.response.json` beside it,
 * grading each of its ids exactly once and no other, with a score on the scale and a confidence in
 * [0, 1]; and the card files together must hold exactly the set's [GradingPool]. Every problem is
 * one line naming the file and the card.
 */
object GradeImport {
    private val responseFields = setOf("id", "score", "confidence")

    fun run(
        packetDir: Path,
        index: CardIndex,
        setCode: String,
        provenance: String,
        createdAt: String,
    ): ImportResult {
        val cardFiles =
            packetDir
                .listDirectoryEntries()
                .filter { GradingPackets.isCardFile(it.name) }
                .sortedBy { it.name }
        val packet = Packet(setCode, GradingPool.printings(index).associateBy(GradingPool::id))
        if (cardFiles.isEmpty()) {
            packet.problems +=
                "$packetDir: no cards-NN.json files; run 'spa grade-packet $setCode' first"
        } else {
            cardFiles.forEach(packet::read)
            packet.checkAllPacked()
        }
        if (packet.problems.isNotEmpty()) return ImportResult(null, packet.problems)

        val file =
            GradeFile(
                GradeFileHeader(setCode, GradeSource.MODEL, provenance, createdAt),
                packet.grades,
            )
        return try {
            ImportResult(GradeFiles.parse(GradeFiles.write(file)), emptyList())
        } catch (e: GradeFileException) {
            ImportResult(null, e.problems.map { "grade file: $it" })
        }
    }

    /** The card files read so far against the sealed [pool], with their grades and problems. */
    private class Packet(private val setCode: String, private val pool: Map<String, Printing>) {
        val problems = mutableListOf<String>()
        val grades = mutableListOf<GradeEntry>()
        private val packed = mutableSetOf<String>()

        /** Reads one card file and the response file beside it. */
        fun read(cardFile: Path) {
            val ids = cardIds(cardFile)
            if (ids == null) {
                problems += "${cardFile.name}: not a card file"
                return
            }
            packed += ids
            ids.filter { it !in pool }
                .forEach {
                    problems +=
                        "${cardFile.name}: $it is not in the sealed pool of $setCode; " +
                            "run 'spa grade-packet $setCode' again"
                }
            val file = ResponseFile(cardFile, ids)
            file.check()
            problems += file.problems
            if (file.problems.isEmpty()) {
                grades += ids.mapNotNull { id ->
                    pool[id]?.let { entry(it, file.grades.getValue(id)) }
                }
            }
        }

        /** One problem for each pool card that no card file holds. */
        fun checkAllPacked() {
            pool.keys
                .filter { it !in packed }
                .forEach {
                    problems +=
                        "$it is in the sealed pool but in no card file; " +
                            "run 'spa grade-packet $setCode' again"
                }
        }
    }

    private fun entry(printing: Printing, grade: Pair<Double, Double>) =
        GradeEntry(
            name = printing.card.name,
            collectorNumber = printing.collectorNumber,
            setCode = printing.setCode,
            score = grade.first,
            confidence = grade.second,
        )

    /** The ids in a card file, or null when it is not a JSON array of objects with a string id. */
    private fun cardIds(cardFile: Path): List<String>? {
        val cards = parse(Files.readString(cardFile)) as? JsonArray
        val ids = cards?.map { string((it as? JsonObject)?.get("id")) }
        return if (ids != null && ids.all { it != null }) ids.filterNotNull() else null
    }

    private fun parse(text: String): JsonElement? =
        try {
            Json.parseToJsonElement(text)
        } catch (_: SerializationException) {
            null
        }

    private fun string(element: JsonElement?): String? =
        (element as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun number(element: JsonElement?): JsonPrimitive? =
        (element as? JsonPrimitive)?.takeIf { !it.isString && it.content.toDoubleOrNull() != null }

    /** The response file beside one card file, checked against that card file's [ids]. */
    private class ResponseFile(cardFile: Path, private val ids: List<String>) {
        private val path = cardFile.resolveSibling(GradingPackets.responseFileName(cardFile.name))
        private val cardFileName = cardFile.name
        val problems = mutableListOf<String>()

        /** Each well-formed, known id's score and confidence. */
        val grades = mutableMapOf<String, Pair<Double, Double>>()
        private val counts = linkedMapOf<String, Int>()

        private fun problem(message: String) {
            problems += "${path.name}: $message"
        }

        fun check() {
            val element = read() ?: return
            if (element is JsonArray) {
                element.forEachIndexed { i, entry -> entry(i + 1, entry) }
                counts
                    .filterValues { it > 1 }
                    .forEach { (id, n) -> problem("$id is graded $n times") }
                ids.filter { it !in counts }.forEach { problem("$it is not graded") }
            } else {
                problem("not a JSON array of {id, score, confidence}")
            }
        }

        /** The response file's JSON, or null after naming why there is none. */
        private fun read(): JsonElement? {
            if (!Files.isRegularFile(path)) {
                problem("missing; grade every card in $cardFileName")
                return null
            }
            return try {
                Json.parseToJsonElement(Files.readString(path))
            } catch (e: SerializationException) {
                problem("not JSON: ${e.message?.lineSequence()?.first()}")
                null
            }
        }

        private fun entry(position: Int, element: JsonElement) {
            val shape = shapeProblem(element)
            if (shape != null) {
                problem("entry $position: $shape")
                return
            }
            val entry = element as JsonObject
            val id = string(entry["id"])!!
            if (id !in ids) {
                problem("$id is not a card in $cardFileName")
                return
            }
            counts[id] = (counts[id] ?: 0) + 1
            val score = number(entry["score"])!!
            val confidence = number(entry["confidence"])!!
            val scoreValue = score.content.toDouble()
            val confidenceValue = confidence.content.toDouble()
            var ok = true
            if (!CardGrade.isOnScale(scoreValue)) {
                problem("$id score ${score.content} is not 0.0 to 5.0 in steps of 0.5")
                ok = false
            }
            if (confidenceValue !in 0.0..1.0) {
                problem("$id confidence ${confidence.content} is not in 0 to 1")
                ok = false
            }
            if (ok) grades[id] = scoreValue to confidenceValue
        }

        private fun shapeProblem(element: JsonElement): String? {
            val entry = element as? JsonObject ?: return "not an object"
            val extra = entry.keys.firstOrNull { it !in responseFields }
            return when {
                string(entry["id"]) == null -> "id must be a string"
                number(entry["score"]) == null -> "score must be a number"
                number(entry["confidence"]) == null -> "confidence must be a number"
                extra != null -> "unexpected field '$extra'"
                else -> null
            }
        }
    }
}
