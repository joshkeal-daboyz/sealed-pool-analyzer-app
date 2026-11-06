package spa.cli

/**
 * Hand-built builds of set `syn` for `spa score-backtest` (SPA-061), on top of [BacktestSyn]: four
 * scored builds whose set grade sums (baseline scores, from the `syn` model grades by hand) rise
 * with their buckets, and one build for each reason a build is not scored.
 *
 * Grades: Mind Stone 1.0, Gravedigger 2.5, Delver of Secrets 3.0, Air Elemental 3.5, Counterspell
 * 4.0, Doom Blade 4.0, Murderous Rider 4.0.
 */
internal object ScoreSyn {
    /** Eight Islands and nine Swamps: 17 lands. */
    val LANDS = mapOf("Island" to 8, "Swamp" to 9)

    @Suppress("LongParameterList")
    fun build(
        draft: String,
        bucket: String,
        spells: Map<String, Int>,
        lands: Map<String, Int> = LANDS,
        main: String = "UB",
        splash: String = "",
        side: Map<String, Int> = emptyMap(),
    ): String = BacktestSyn.row(draft, main, splash, bucket, deck = spells + lands, side = side)

    /** 23 Mind Stone: 23.0. */
    val A = build("a", "0.30", mapOf("Mind Stone" to 23))

    /** 23 Gravedigger: 57.5. */
    val B = build("b", "0.50", mapOf("Gravedigger" to 23))

    /** 12 Air Elemental and 11 Delver of Secrets: 42 + 33 = 75.0. */
    val C = build("c", "0.65", mapOf("Air Elemental" to 12, "Delver of Secrets" to 11))

    /** 12 Counterspell and 11 Doom Blade: 48 + 44 = 92.0. */
    val D = build("d", "0.80", mapOf("Counterspell" to 12, "Doom Blade" to 11))

    /** The four scored builds, in file order, and their baseline scores. */
    val SCORED = listOf(A, B, C, D)
    val BASELINE_SCORES = listOf(23.0, 57.5, 75.0, 92.0)

    private val SPELLS = mapOf("Air Elemental" to 23)

    val ONE_COLOUR = build("one", "0.55", SPELLS, main = "U")
    val FIVE_COLOUR = build("five", "0.55", SPELLS, main = "WUBRG")
    val UNRESOLVED = build("unres", "0.55", SPELLS, side = mapOf("Bonus Reprint" to 1))
    val NO_CHOICE =
        BacktestSyn.row(
            "nochoice",
            "UB",
            bucket = "0.55",
            deck = mapOf("Island" to 17),
            side = mapOf("Serra Angel" to 4),
        )

    /** Splash colour B is also a main colour. */
    val NO_CONFIG = build("noconfig", "0.55", SPELLS, splash = "B")

    /** 23 spells and 16 lands. */
    val NOT_40 = build("not40", "0.55", SPELLS, lands = mapOf("Island" to 8, "Swamp" to 8))

    /** One Serra Angel, a white card, in a blue and black deck. */
    val OFF_COLOUR = build("offcolour", "0.55", mapOf("Air Elemental" to 22, "Serra Angel" to 1))

    /** A blank bucket: no build at all for the read line. */
    val BLANK = build("blank", "", SPELLS)

    /** Every kind of build, in a mixed file order. */
    val MIXED =
        listOf(
            A,
            B,
            ONE_COLOUR,
            C,
            NO_CONFIG,
            UNRESOLVED,
            D,
            NOT_40,
            NO_CHOICE,
            OFF_COLOUR,
            FIVE_COLOUR,
            BLANK,
        )
}
