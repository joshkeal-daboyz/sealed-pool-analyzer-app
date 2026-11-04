package spa.agent.grade

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import spa.core.cards.Card
import spa.core.cards.Face
import spa.core.cards.Printing
import spa.core.cards.Rarity

class GradeLookupTest {
    private fun printing(name: String, setCode: String, collectorNumber: String): Printing {
        val face = Face(name, "{1}{W}", 2.0, emptySet(), "Creature", "", "2", "2")
        val card =
            Card(
                oracleId = "oracle-$name",
                name = name,
                layout = "normal",
                faces = listOf(face),
                colors = emptySet(),
                colorIdentity = emptySet(),
                manaValue = 2.0,
                typeLine = "Creature",
                isCreature = true,
                isLand = false,
                isBasicLand = false,
                rarity = Rarity.COMMON,
            )
        return Printing(
            "id-$setCode-$collectorNumber",
            setCode,
            collectorNumber,
            Rarity.COMMON,
            emptyList(),
            card,
        )
    }

    private fun file(
        setCode: String,
        source: GradeSource,
        provenance: String,
        vararg grades: GradeEntry,
    ) =
        GradeFile(
            GradeFileHeader(setCode, source, provenance, "2026-11-04T12:00:00Z"),
            grades.toList(),
        )

    private val both = printing("Graded Twice", "trk", "10")
    private val modelOnly = printing("Model Only", "trk", "11")
    private val ungraded = printing("Never Graded", "trk", "12")
    private val companion = printing("From The Companion", "sds", "3")

    private val lookup =
        GradeLookup(
            listOf(
                file(
                    "trk",
                    GradeSource.MODEL,
                    "claude-opus-5-5, prompt v1, 2026-11-04",
                    GradeEntry("Graded Twice", "10", "trk", 2.0, 0.6),
                    GradeEntry("Model Only", "11", "trk", 3.5, 0.7),
                    GradeEntry("From The Companion", "3", "sds", 4.0, 0.65),
                ),
                file(
                    "trk",
                    GradeSource.WIN_RATE,
                    "17Lands PremierDraft trk 2026-11-20",
                    GradeEntry("Graded Twice", "10", "trk", 4.5, 0.9),
                ),
                file(
                    "fra",
                    GradeSource.WIN_RATE,
                    "17Lands PremierDraft fra 2026-10-30",
                    GradeEntry("Model Only", "11", "trk", 1.0, 0.9),
                ),
            )
        )

    @Test
    fun `a win-rate grade wins over a model grade`() {
        assertEquals(
            CardGrade(4.5, 0.9, GradeSource.WIN_RATE, "17Lands PremierDraft trk 2026-11-20"),
            lookup.grade(both, "trk"),
        )
    }

    @Test
    fun `with no win-rate grade the model grade is returned`() {
        assertEquals(
            CardGrade(3.5, 0.7, GradeSource.MODEL, "claude-opus-5-5, prompt v1, 2026-11-04"),
            lookup.grade(modelOnly, "trk"),
        )
    }

    @Test
    fun `a card with no grade gives null`() {
        assertNull(lookup.grade(ungraded, "trk"))
    }

    @Test
    fun `a companion-set card is graded in the main set's files`() {
        assertEquals(
            CardGrade(4.0, 0.65, GradeSource.MODEL, "claude-opus-5-5, prompt v1, 2026-11-04"),
            lookup.grade(companion, "trk"),
        )
    }

    @Test
    fun `grades for another set's pool are not used`() {
        assertNull(lookup.grade(companion, "sds"))
        assertEquals(GradeSource.MODEL, lookup.grade(modelOnly, "trk")?.source)
        assertEquals(1.0, lookup.grade(modelOnly, "fra")?.score)
    }

    @Test
    fun `another printing of a graded card finds the card's grade by name`() {
        val showcase = printing("Model Only", "trk", "301")
        assertEquals(3.5, lookup.grade(showcase, "trk")?.score)
    }
}
