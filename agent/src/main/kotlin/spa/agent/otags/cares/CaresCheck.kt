package spa.agent.otags.cares

import spa.agent.tags.CardFileIds

/**
 * What an import check sees: the packet card files ([cardFiles]), the names of the response files
 * that exist ([responseFiles]), every well-formed response entry ([responses]), the fact names of
 * `vocabulary.json` ([vocabulary]), each card id's cares tags from Scryfall ([cares]: fact to
 * strength) and the cares [table] with the suggestions made for it.
 */
class CaresCheckInput(
    val cardFiles: List<CardFileIds>,
    val responseFiles: Set<String>,
    val responses: List<CaresResponseCard>,
    val vocabulary: Set<String>,
    val cares: Map<String, Map<String, Int>>,
    val table: CaresTableInput,
)

/** The cares table as a check sees it: the [tags] it holds and the [suggestions] for it. */
class CaresTableInput(val tags: Set<String>, val suggestions: List<CaresSuggestion>)

/**
 * One rule a cares response must keep. [problems] gives one line per break, naming the file and the
 * card, and none when the responses keep the rule.
 */
fun interface CaresCheck {
    fun problems(input: CaresCheckInput): List<String>
}

/** The checks `spa cares-import` runs, in order. A new check is one file and one entry here. */
object CaresChecks {
    val ALL: List<CaresCheck> =
        listOf(
            CaresResponseFileCheck,
            CaresCardCheck,
            CaresVocabularyCheck,
            CaresStrengthCheck,
            CaresRepeatedFactCheck,
            CaresExistingCheck,
            CaresReasonCheck,
            CaresSuggestionCheck,
        )
}
