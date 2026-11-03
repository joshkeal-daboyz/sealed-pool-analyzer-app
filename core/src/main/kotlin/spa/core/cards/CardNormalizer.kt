package spa.core.cards

import kotlinx.serialization.json.JsonObject
import spa.core.text.Color

/**
 * Turns verbatim Scryfall card objects into [Printing]s and [Card]s.
 *
 * Layout rules for a card's colors, mana value, type line, and creature-ness:
 * - `transform`, `flip`, `meld`: all four from the front face.
 * - `modal_dfc`, `adventure`: from the front face, except colors, the union of the faces'.
 * - `split`: colors the union, mana value the smaller face's, type lines joined with ` // `, and
 *   never a creature.
 * - any other layout: Scryfall's top-level fields.
 *
 * Land and basic land always follow the front face's type line.
 */
object CardNormalizer {
    private val frontFaceLayouts = setOf("transform", "flip", "meld")
    private val unionColorLayouts = setOf("modal_dfc", "adventure")
    private const val SPLIT = "split"
    private const val FACE_SEPARATOR = " // "

    /**
     * Printings for [cards], in order. A card already in [cardsByOracleId] reuses that instance,
     * and every new card is added to it, so printings across calls can share cards.
     */
    fun printings(
        cards: List<JsonObject>,
        cardsByOracleId: MutableMap<String, Card> = mutableMapOf(),
    ): List<Printing> = cards.map { json ->
        val dto = decode(json)
        val card = cardsByOracleId.getOrPut(oracleId(dto)) { card(dto) }
        Printing(
            scryfallId = dto.id,
            setCode = dto.set,
            collectorNumber = dto.collectorNumber,
            rarity = Rarity.fromScryfall(dto.rarity),
            promoTypes = dto.promoTypes,
            card = card,
        )
    }

    /** The [Card] for one Scryfall card object. */
    fun card(json: JsonObject): Card = card(decode(json))

    private fun decode(json: JsonObject): ScryfallCardDto =
        ScryfallJson.reader.decodeFromJsonElement(ScryfallCardDto.serializer(), json)

    private fun card(dto: ScryfallCardDto): Card {
        val faces = faces(dto)
        val front = faces.first()
        val union = Color.setOf(faces.flatMap { face -> face.colors.map { it.name } })
        val shape =
            when (dto.layout) {
                in frontFaceLayouts -> Shape(front.colors, front.manaValue, front.typeLine)
                in unionColorLayouts -> Shape(union, front.manaValue, front.typeLine)
                SPLIT ->
                    Shape(
                        colors = union,
                        manaValue = faces.minOf { it.manaValue },
                        typeLine = faces.joinToString(FACE_SEPARATOR) { it.typeLine },
                        isCreature = false,
                    )
                else ->
                    Shape(
                        colors = Color.setOf(dto.colors.orEmpty()),
                        manaValue = dto.cmc ?: front.manaValue,
                        typeLine = dto.typeLine ?: front.typeLine,
                    )
            }
        val frontTypes = TypeLine(front.typeLine)
        return Card(
            oracleId = oracleId(dto),
            name = dto.name,
            layout = dto.layout,
            faces = faces,
            colors = shape.colors,
            colorIdentity = Color.setOf(dto.colorIdentity),
            manaValue = shape.manaValue,
            typeLine = shape.typeLine,
            isCreature = shape.isCreature,
            isLand = frontTypes.isLand,
            isBasicLand = frontTypes.isBasicLand,
            rarity = Rarity.fromScryfall(dto.rarity),
        )
    }

    private fun oracleId(dto: ScryfallCardDto): String =
        requireNotNull(dto.oracleId ?: dto.cardFaces?.firstNotNullOfOrNull { it.oracleId }) {
            "Card ${dto.id} (${dto.name}) has no oracle_id."
        }

    /** The card's faces; a card without `card_faces` is its own single face. */
    private fun faces(dto: ScryfallCardDto): List<Face> {
        val topColors = Color.setOf(dto.colors.orEmpty())
        val faces = dto.cardFaces.orEmpty()
        if (faces.isEmpty()) {
            val cost = dto.manaCost.orEmpty()
            return listOf(
                Face(
                    name = dto.name,
                    manaCost = cost,
                    manaValue = dto.cmc ?: ManaCost.manaValue(cost),
                    colors = topColors,
                    typeLine = dto.typeLine.orEmpty(),
                    oracleText = dto.oracleText.orEmpty(),
                    power = dto.power,
                    toughness = dto.toughness,
                )
            )
        }
        return faces.map { face ->
            val cost = face.manaCost.orEmpty()
            Face(
                name = face.name,
                manaCost = cost,
                manaValue = face.cmc ?: ManaCost.manaValue(cost),
                colors = faceColors(face, cost, topColors),
                typeLine = face.typeLine.orEmpty(),
                oracleText = face.oracleText.orEmpty(),
                power = face.power,
                toughness = face.toughness,
            )
        }
    }

    /**
     * A face's colors: Scryfall's per-face `colors` where given (transform and modal faces), else
     * its color indicator, else its mana cost's colors (split and adventure halves), else, for a
     * face with no cost at all such as a flip card's bottom half, the card's colors.
     */
    private fun faceColors(
        face: ScryfallFaceDto,
        cost: String,
        cardColors: Set<Color>,
    ): Set<Color> =
        when {
            face.colors != null -> Color.setOf(face.colors)
            face.colorIndicator != null -> Color.setOf(face.colorIndicator)
            cost.isNotBlank() -> ManaCost.colors(cost)
            else -> cardColors
        }

    private class Shape(
        val colors: Set<Color>,
        val manaValue: Double,
        val typeLine: String,
        val isCreature: Boolean = TypeLine(typeLine).isCreature,
    )

    /** The card types and supertypes of a type line: the words before the em dash. */
    private class TypeLine(line: String) {
        private val types = line.substringBefore('—').split(' ').filter { it.isNotBlank() }
        val isCreature = "Creature" in types
        val isLand = "Land" in types
        val isBasicLand = isLand && "Basic" in types
    }
}
