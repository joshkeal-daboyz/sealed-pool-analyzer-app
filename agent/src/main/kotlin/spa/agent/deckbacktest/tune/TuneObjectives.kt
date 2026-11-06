package spa.agent.deckbacktest.tune

/** The objectives the tuner knows, and the one it uses. */
object TuneObjectives {
    val ALL: List<TuneObjective> = listOf(OverlapAndColour)

    /** The objective `spa deck-tune` maximises. */
    val DEFAULT: TuneObjective = ALL.first()
}
