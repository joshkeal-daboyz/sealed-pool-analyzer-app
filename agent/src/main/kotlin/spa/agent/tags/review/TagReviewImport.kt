package spa.agent.tags.review

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlinx.serialization.json.Json
import spa.agent.grader.GradingPackets
import spa.agent.tags.CardFileIds
import spa.agent.tags.ResponseCard
import spa.agent.tags.ResponsePartner
import spa.agent.tags.ResponseRole
import spa.agent.tags.TagCard
import spa.agent.tags.TagCheck
import spa.agent.tags.TagCheckInput
import spa.agent.tags.TagChecks
import spa.agent.tags.TagCorrection
import spa.agent.tags.TagFile
import spa.agent.tags.TagFileException
import spa.agent.tags.TagFiles
import spa.agent.tags.TagPartner
import spa.agent.tags.TagReview
import spa.agent.tags.TagRole

/**
 * What a review import gives: the corrected tag [file] and the [suggestions] when there are no
 * [problems], else no file.
 */
data class ReviewImportResult(
    val file: TagFile?,
    val suggestions: List<FactSuggestion>,
    val problems: List<String>,
)

/**
 * Applies a review packet's corrections to the tag file the packet was built from (see
 * [TagReviewPackets]).
 *
 * The packet's `tag-file.json` names that file and the SHA-256 of its bytes; a file that has
 * changed since, or is gone, stops the import with one line. Otherwise every card file needs its
 * review file ([ReviewFiles]), and `fact-suggestions.json` is read when present. The corrections
 * apply through the actions ([ReviewApply]), and then every pass-2 [TagCheck] runs on the corrected
 * tags, with the facts the set's cards have for the supply checks. Every problem is one line, and
 * on any problem there is no file.
 *
 * The corrected file keeps the tag file's set, provenance and axes, and gets a `review` block: the
 * review provenance, the number of applied corrections for each action, and every applied
 * correction with its reason. A review block the tag file already had is replaced.
 */
object TagReviewImport {
    /**
     * Imports the review in [packetDir] for the set [setCode], whose cards have [facts] (card id to
     * its facts), recording [provenance], with the correction [actions] and the pass-2 [checks].
     */
    @Suppress("LongParameterList")
    fun run(
        packetDir: Path,
        setCode: String,
        facts: Map<String, Set<String>>,
        provenance: String,
        actions: List<ReviewAction> = ReviewActions.ALL,
        checks: List<TagCheck> = TagChecks.ALL,
    ): ReviewImportResult =
        try {
            Import(packetDir, setCode, actions).run(facts, provenance, checks)
        } catch (e: Stopped) {
            ReviewImportResult(null, emptyList(), e.problems)
        }

    /** The import stops with [problems]. */
    private class Stopped(val problems: List<String>) : Exception(problems.joinToString("\n"))

    private fun stop(problems: List<String>): Nothing = throw Stopped(problems)

    private fun stop(problem: String): Nothing = stop(listOf(problem))

    /** One import of the packet in [dir] for [setCode], with [actions]. */
    private class Import(val dir: Path, val setCode: String, val actions: List<ReviewAction>) {
        val again = "run 'spa tags-review-packet $setCode' to write the packet"

        fun run(
            facts: Map<String, Set<String>>,
            provenance: String,
            checks: List<TagCheck>,
        ): ReviewImportResult {
            val source = source()
            val tags = tags(source)
            val paths = cardPaths()
            val cardFiles = paths.map { CardFileIds(it.name, cardIds(it)) }
            val reviews = paths.map { ReviewFiles.read(it, actions) }
            val suggestions = ReviewFiles.suggestions(dir)
            val applied =
                ReviewApply.apply(
                    tags.cards.map(::reviewCard),
                    cardFiles,
                    reviews.flatMap { it.corrections },
                    actions,
                )
            val input = checkInput(source, tags, cardFiles, applied, facts)
            val problems =
                reviews.flatMap { it.problems } +
                    suggestions.problems +
                    applied.problems +
                    checks.flatMap { it.problems(input) }
            if (problems.isNotEmpty()) stop(problems)
            val file =
                tags.copy(
                    cards = applied.cards.map(::tagCard),
                    review = review(provenance, applied),
                )
            val checked =
                try {
                    TagFiles.parse(TagFiles.write(file))
                } catch (e: TagFileException) {
                    stop(e.problems.map { "tag file: $it" })
                }
            return ReviewImportResult(checked, suggestions.suggestions, emptyList())
        }

        /** The packet's `tag-file.json`. */
        fun source(): ReviewSource {
            val path = dir.resolve(TagReviewPackets.TAG_FILE)
            if (!Files.isRegularFile(path)) stop("${TagReviewPackets.TAG_FILE}: missing; $again")
            return runCatching {
                Json.decodeFromString(ReviewSource.serializer(), Files.readString(path))
            }
                .getOrNull() ?: stop("${TagReviewPackets.TAG_FILE}: not a tag file record; $again")
        }

        /** The tag file [source] names, unchanged since the packet was built and for [setCode]. */
        fun tags(source: ReviewSource): TagFile {
            val path = Path.of(source.path)
            val name = source.path
            if (!Files.isRegularFile(path)) {
                stop("the tag file $name the packet was built from is missing")
            }
            val bytes = Files.readAllBytes(path)
            if (TagReviewPackets.sha256(bytes) != source.sha256) {
                stop(
                    "the tag file $name changed since the packet was built; run " +
                        "'spa tags-review-packet $setCode' again"
                )
            }
            val file =
                try {
                    TagFiles.parse(bytes.decodeToString())
                } catch (e: TagFileException) {
                    stop(e.problems.map { "tag file $name: $it" })
                }
            if (file.set != setCode) {
                stop("the tag file $name is for set '${file.set}', not '$setCode'")
            }
            return file
        }

        /** The packet's card files, in order; none is a problem. */
        fun cardPaths(): List<Path> {
            val paths =
                dir.listDirectoryEntries()
                    .filter { GradingPackets.isCardFile(it.name) }
                    .sortedBy { it.name }
            if (paths.isEmpty()) stop("$dir: no cards-NN.json files; $again")
            return paths
        }

        /** The ids in the card file at [path]; one that is not a card file is a problem. */
        fun cardIds(path: Path): List<String> =
            runCatching { TagReviewPackets.ids(Files.readString(path)) }.getOrNull()
                ?: stop("${path.name}: not a card file; $again")

        /** The check input for the corrected cards, each named against its review file. */
        fun checkInput(
            source: ReviewSource,
            tags: TagFile,
            cardFiles: List<CardFileIds>,
            applied: ReviewApplied,
            facts: Map<String, Set<String>>,
        ): TagCheckInput {
            val tagFileName = Path.of(source.path).name
            val reviewFileOf =
                cardFiles
                    .flatMap { file ->
                        file.ids.map { it to TagReviewPackets.reviewFileName(file.name) }
                    }
                    .toMap()
            val responses =
                applied.cards.map {
                    ResponseCard(reviewFileOf[it.id] ?: tagFileName, it.id, it.roles, it.partners)
                }
            return TagCheckInput(
                cardFiles,
                tags.axes,
                responses,
                facts.values.flatten().toSet(),
                tagFileName,
            )
        }

        fun review(provenance: String, applied: ReviewApplied) =
            TagReview(
                provenance = provenance,
                counts =
                    actions.associate { action ->
                        action.name to applied.applied.count { it.action == action.name }
                    },
                corrections = applied.applied.map(::record),
            )
    }

    private fun reviewCard(card: TagCard) =
        ReviewCard(
            card.id,
            card.name,
            card.roles.map { ResponseRole(it.axis, it.role, it.strength.toString()) },
            card.partners.map { ResponsePartner(it.id, it.strength.toString()) },
        )

    /** [card] as a tag file entry; a strength that is not a whole number becomes 0, which fails. */
    private fun tagCard(card: ReviewCard) =
        TagCard(
            card.id,
            card.name,
            card.roles.map { TagRole(it.axis, it.role, it.strength.toIntOrNull() ?: 0) },
            card.partners.map { TagPartner(it.id, it.strength.toIntOrNull() ?: 0) },
        )

    private fun record(correction: Correction) =
        TagCorrection(
            id = correction.id,
            action = correction.action,
            axis = correction.axis,
            role = correction.role,
            partner = correction.partner,
            strength = correction.strength?.toIntOrNull(),
            reason = correction.reason,
        )
}
