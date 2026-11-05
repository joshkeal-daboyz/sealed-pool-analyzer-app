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
 * apply through [actions] ([ReviewApply]), and then every pass-2 [TagCheck] in [checks] runs on the
 * corrected tags, with the facts the set's cards have ([facts], card id to its facts) for the
 * supply checks. Every problem is one line, and on any problem there is no file.
 *
 * The corrected file keeps the tag file's set, provenance and axes, and gets a `review` block: the
 * review [provenance], the number of applied corrections for each action of [actions], and every
 * applied correction with its reason. A review block the tag file already had is replaced.
 */
object TagReviewImport {
    @Suppress("LongParameterList")
    fun run(
        packetDir: Path,
        setCode: String,
        facts: Map<String, Set<String>>,
        provenance: String,
        actions: List<ReviewAction> = ReviewActions.ALL,
        checks: List<TagCheck> = TagChecks.ALL,
    ): ReviewImportResult {
        val again = "run 'spa tags-review-packet $setCode' to write the packet"
        val sourcePath = packetDir.resolve(TagReviewPackets.TAG_FILE)
        if (!Files.isRegularFile(sourcePath)) {
            return failed("${TagReviewPackets.TAG_FILE}: missing; $again")
        }
        val source =
            runCatching {
                Json.decodeFromString(ReviewSource.serializer(), Files.readString(sourcePath))
            }
                .getOrNull()
                ?: return failed("${TagReviewPackets.TAG_FILE}: not a tag file record; $again")
        val tags =
            when (val read = readTags(source, setCode)) {
                is TagsRead.Failed -> return failed(*read.problems.toTypedArray())
                is TagsRead.Read -> read.file
            }
        val paths =
            packetDir
                .listDirectoryEntries()
                .filter { GradingPackets.isCardFile(it.name) }
                .sortedBy { it.name }
        if (paths.isEmpty()) return failed("$packetDir: no cards-NN.json files; $again")
        val ids = paths.associate { it.name to cardIds(it) }
        val unread = ids.filterValues { it == null }.keys
        if (unread.isNotEmpty()) {
            return failed(*unread.map { "$it: not a card file; $again" }.toTypedArray())
        }
        val cardFiles = ids.map { (name, cards) -> CardFileIds(name, cards.orEmpty()) }

        val reviews = paths.map { ReviewFiles.read(it, actions) }
        val suggestions = ReviewFiles.suggestions(packetDir)
        val applied =
            ReviewApply.apply(
                tags.cards.map(::reviewCard),
                cardFiles,
                reviews.flatMap { it.corrections },
                actions,
            )
        val reviewFileOf =
            cardFiles
                .flatMap { file ->
                    file.ids.map { it to TagReviewPackets.reviewFileName(file.name) }
                }
                .toMap()
        val responses =
            applied.cards.map {
                val file = reviewFileOf[it.id] ?: Path.of(source.path).name
                ResponseCard(file, it.id, it.roles, it.partners)
            }
        val input =
            TagCheckInput(
                cardFiles,
                tags.axes,
                responses,
                facts.values.flatten().toSet(),
                Path.of(source.path).name,
            )
        val problems =
            reviews.flatMap { it.problems } +
                suggestions.problems +
                applied.problems +
                checks.flatMap { it.problems(input) }
        if (problems.isNotEmpty()) return failed(*problems.toTypedArray())

        val review =
            TagReview(
                provenance = provenance,
                counts =
                    actions.associate { action ->
                        action.name to applied.applied.count { it.action == action.name }
                    },
                corrections = applied.applied.map(::record),
            )
        val file = tags.copy(cards = applied.cards.map(::tagCard), review = review)
        return try {
            ReviewImportResult(
                TagFiles.parse(TagFiles.write(file)),
                suggestions.suggestions,
                listOf(),
            )
        } catch (e: TagFileException) {
            failed(*e.problems.map { "tag file: $it" }.toTypedArray())
        }
    }

    private fun failed(vararg problems: String) =
        ReviewImportResult(null, emptyList(), problems.toList())

    private sealed interface TagsRead {
        class Read(val file: TagFile) : TagsRead

        class Failed(val problems: List<String>) : TagsRead
    }

    /** The tag file [source] names, unchanged since the packet was built and for [setCode]. */
    private fun readTags(source: ReviewSource, setCode: String): TagsRead {
        val path = Path.of(source.path)
        val name = source.path
        if (!Files.isRegularFile(path)) {
            return TagsRead.Failed(
                listOf("the tag file $name the packet was built from is missing")
            )
        }
        val bytes = Files.readAllBytes(path)
        if (TagReviewPackets.sha256(bytes) != source.sha256) {
            return TagsRead.Failed(
                listOf(
                    "the tag file $name changed since the packet was built; run " +
                        "'spa tags-review-packet $setCode' again"
                )
            )
        }
        val file =
            try {
                TagFiles.parse(bytes.decodeToString())
            } catch (e: TagFileException) {
                return TagsRead.Failed(e.problems.map { "tag file $name: $it" })
            }
        return if (file.set == setCode) TagsRead.Read(file)
        else TagsRead.Failed(listOf("the tag file $name is for set '${file.set}', not '$setCode'"))
    }

    /** The ids in the card file at [path], or null when it is not a card file. */
    private fun cardIds(path: Path): List<String>? = runCatching {
        TagReviewPackets.ids(Files.readString(path))
    }.getOrNull()

    private fun reviewCard(card: TagCard) =
        ReviewCard(
            card.id,
            card.name,
            card.roles.map { ResponseRole(it.axis, it.role, it.strength.toString()) },
            card.partners.map { ResponsePartner(it.id, it.strength.toString()) },
        )

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
