package spa.agent.tags

import spa.agent.facts.CardFacts
import spa.agent.grader.GradingPackets
import spa.agent.grader.GradingPool
import spa.core.cards.CardIndex

/** The text [TagPackets] writes besides the prompt and brief: the axes, file lists and schemas. */
object TagPacketText {
    /** The `## The set's synergy axes` section: one line per axis, id, name and definition. */
    fun renderAxes(axes: List<Axis>): String = buildString {
        appendLine("## The set's synergy axes")
        appendLine()
        appendLine("Use these axes only, each by its id.")
        appendLine()
        axes.forEach { appendLine("- `${it.id}`, ${it.name}: ${it.definition}${supply(it)}") }
    }

    /** An axis's supply as pass 2 shows it, or nothing for an axis without `suppliedBy` (v1). */
    private fun supply(axis: Axis): String {
        val supply = axis.suppliedBy ?: return ""
        return if (supply.isEmpty()) " Supplied by: no fact."
        else " Supplied by: " + supply.joinToString(", ") { "`${it.fact}` ${it.strength}" } + "."
    }

    /**
     * The `## Card facts` section of pass 1: each fact of [facts] (card id to its facts) with the
     * number of cards that have it, most first, then by name ([CardFacts.counts]).
     */
    fun renderFacts(facts: Map<String, Set<String>>): String = buildString {
        appendLine("## Card facts")
        appendLine()
        appendLine(
            "Each fact a card of the set has, with the number of cards that have it, most " +
                "first. `${TagPackets.FACTS}` gives each card's facts by its id."
        )
        appendLine()
        CardFacts.counts(facts).forEach { appendLine("- `${it.fact}`: ${it.cards}") }
    }

    /**
     * The files section of pass 1, for card files named [cardFiles], naming `facts.json` when the
     * packet has it ([withFacts]).
     */
    fun describeAxesFiles(
        index: CardIndex,
        cardFiles: List<String>,
        withFacts: Boolean = false,
    ): String = buildString {
        appendLine("## The files in this folder")
        appendLine()
        appendLine("- `${TagPackets.INSTRUCTIONS}`: this file.")
        appendLine(cardsLine(index, cardFiles))
        if (withFacts) appendLine(FACTS_LINE)
        appendLine("- `${TagPackets.AXES_SCHEMA}`: the JSON schema of your response.")
        appendLine("- You write `${TagPackets.AXES_RESPONSE}`.")
    }

    /**
     * The files section of pass 2, for card files named [cardFiles], naming `facts.json` when the
     * packet has it ([withFacts]).
     */
    fun describeRolesFiles(
        index: CardIndex,
        cardFiles: List<String>,
        withFacts: Boolean = false,
    ): String = buildString {
        val responses = cardFiles.map(GradingPackets::responseFileName)
        appendLine("## The files in this folder")
        appendLine()
        appendLine("- `${TagPackets.INSTRUCTIONS}`: this file.")
        appendLine(cardsLine(index, cardFiles))
        if (withFacts) appendLine(FACTS_LINE)
        appendLine("- `${TagPackets.AXES}`: the axes above, as JSON.")
        appendLine("- `${TagPackets.RESPONSE_SCHEMA}`: the JSON schema of one response file.")
        appendLine("- You write ${range(responses)}, one beside each card file.")
    }

    internal fun cardsLine(index: CardIndex, cardFiles: List<String>): String {
        val count = GradingPool.printings(index).size
        return "- ${range(cardFiles)}: the $count cards of the set, " +
            "${GradingPackets.CARDS_PER_FILE} per file except the last, in collector order. " +
            "Each card has `id`, `name`, `manaCost`, `typeLine`, `rarity`, `oracleText`, " +
            "`power` and `toughness`. A card with two faces has both faces in each field, " +
            "separated by ` // `."
    }

    internal fun range(names: List<String>): String =
        if (names.size <= 1) "`${names.firstOrNull() ?: GradingPackets.cardFileName(1)}`"
        else "`${names.first()}` to `${names.last()}`"

    private const val FACTS_LINE = "- `${TagPackets.FACTS}`: each card's facts, by card id."

    /** The schema of `axes.response.json` for tags prompt v1 ([AxesFormat.PLAIN]). */
    val axesSchema: String
        get() = axesSchema(AxesFormat.PLAIN)

    /**
     * The schema of `axes.response.json` in [format]: for [AxesFormat.SUPPLIED], up to 20 axes,
     * each with a required `suppliedBy`.
     */
    fun axesSchema(format: AxesFormat): String {
        val max = format.maxAxes
        val required =
            if (format.supplied) "\"id\", \"name\", \"definition\", \"suppliedBy\""
            else "\"id\", \"name\", \"definition\""
        val supplied =
            if (!format.supplied) ""
            else
                """
                |,
                |      "suppliedBy": {
                |        "description": "The card facts that supply the axis just by a card having them, each a fact some card of the set has, at most once, with a strength from 1 to 3; empty when no fact does.",
                |        "type": "array",
                |        "items": {
                |          "type": "object",
                |          "additionalProperties": false,
                |          "required": ["fact", "strength"],
                |          "properties": {
                |            "fact": { "type": "string" },
                |            "strength": { "enum": [1, 2, 3] }
                |          }
                |        }
                |      }
                """
                    .trimMargin()
        return """
        |{
        |  "${'$'}schema": "https://json-schema.org/draft/2020-12/schema",
        |  "title": "Synergy axes response",
        |  "description": "axes.response.json: the set's synergy axes, ${AxesFile.MIN_AXES} to $max of them, with distinct ids.",
        |  "type": "array",
        |  "minItems": ${AxesFile.MIN_AXES},
        |  "maxItems": $max,
        |  "items": {
        |    "type": "object",
        |    "additionalProperties": false,
        |    "required": [$required],
        |    "properties": {
        |      "id": {
        |        "description": "Lowercase words joined by hyphens, distinct from every other axis's id.",
        |        "type": "string",
        |        "pattern": "^[a-z0-9-]+${'$'}"
        |      },
        |      "name": {
        |        "description": "A short name.",
        |        "type": "string",
        |        "minLength": 1
        |      },
        |      "definition": {
        |        "description": "One line: what counts as supplying the axis.",
        |        "type": "string",
        |        "minLength": 1
        |      }$supplied
        |    }
        |  }
        |}
        |"""
            .trimMargin()
    }

    /** The schema of one `cards-NN.response.json`, with each role's axis one of [axes]. */
    fun responseSchema(axes: List<Axis>): String {
        val axisIds = axes.joinToString(", ") { "\"${it.id}\"" }
        val max = TagPackets.MAX_PARTNERS
        return """
        |{
        |  "${'$'}schema": "https://json-schema.org/draft/2020-12/schema",
        |  "title": "Synergy tagging response",
        |  "description": "One response file, cards-NN.response.json: exactly one entry for each card id in cards-NN.json, and no other.",
        |  "type": "array",
        |  "items": {
        |    "type": "object",
        |    "additionalProperties": false,
        |    "required": ["id", "roles", "partners"],
        |    "properties": {
        |      "id": {
        |        "description": "The card's id, copied exactly from the card file.",
        |        "type": "string"
        |      },
        |      "roles": {
        |        "description": "The card's role on each axis it touches, at most once per axis and role; empty when it touches none.",
        |        "type": "array",
        |        "items": {
        |          "type": "object",
        |          "additionalProperties": false,
        |          "required": ["axis", "role", "strength"],
        |          "properties": {
        |            "axis": { "enum": [$axisIds] },
        |            "role": { "enum": ["enabler", "payoff"] },
        |            "strength": { "enum": [1, 2, 3] }
        |          }
        |        }
        |      },
        |      "partners": {
        |        "description": "Up to $max named partners, each a card id from the card files (this card's own id allowed), none twice; empty when there are none.",
        |        "type": "array",
        |        "maxItems": $max,
        |        "items": {
        |          "type": "object",
        |          "additionalProperties": false,
        |          "required": ["id", "strength"],
        |          "properties": {
        |            "id": { "type": "string" },
        |            "strength": { "enum": [1, 2, 3] }
        |          }
        |        }
        |      }
        |    }
        |  }
        |}
        |"""
            .trimMargin()
    }
}
