package spa.core.metrics

import kotlin.test.Test
import kotlin.test.assertEquals
import spa.core.text.Color.B
import spa.core.text.Color.G
import spa.core.text.Color.R
import spa.core.text.Color.U
import spa.core.text.Color.W

/**
 * Criteria 1, 2 and 5: one hand-built pool of 17 syn cards (19 copies), every expected number
 * worked out by hand from the cards.
 *
 * The cards, with what the engine should read from each (colors; type; mana value; rarity; tags;
 * pips from the face named in the ticket):
 * - Grizzly Bears x2: G; creature; 2; common; none; G1
 * - Serra Angel: W; creature; 5; uncommon; none; W2
 * - Counterspell: U; instant; 2; uncommon; COUNTER; U2
 * - Doom Blade: B; instant; 2; common; REMOVAL; B1
 * - Giant Growth: G; instant; 1; common; TRICK; G1
 * - Kitchen Finks: GW (hybrid); creature; 3; uncommon; none; G1 W1 (two {G/W} at 0.5 each)
 * - Lightning Helix: RW; instant; 2; uncommon; REMOVAL ("3 damage to any target"); R1 W1
 * - Huntmaster of the Fells (transform): RG from the front; creature; 4; rare; REMOVAL from the
 *   back face only (Ravager deals damage to "up to one target creature"); R1 G1
 * - Agadeem's Awakening (modal_dfc): B; front is a sorcery so it is not a land; 3 ({X}{B}{B}{B});
 *   mythic; none (the back is a one-color land, so no FIXING); B3
 * - Bonecrusher Giant (adventure): R; creature; 3; rare; REMOVAL from Stomp only; R1 from the
 *   creature face {2}{R}
 * - Murderous Rider (adventure): B; creature; 3; rare; REMOVAL from Swift End only; B2 from
 *   {1}{B}{B}
 * - Fire // Ice (split): UR; instant; 2 (smaller face); uncommon; none (Fire divides damage among
 *   "targets" with no "to", Ice taps); R1 from Fire, the first of two faces that both cost 2
 * - Wear // Tear (split): RW; instant; 1 (Tear); uncommon; none (artifact and enchantment are not
 *   creature or permanent targets); W1 from Tear, the cheaper face
 * - Mind Stone: colorless; artifact; 2; common; none ({C} is not a color); no pips
 * - Temple of Mystery: nonbasic land, rare; produces G and U, so FIXING and a fixing source
 * - Evolving Wilds: nonbasic land, common; produces nothing, so not fixing
 * - Forest x2: basic land
 */
class HandBuiltPoolTest {
    private val pool =
        poolOf(
            entry(Syn.GRIZZLY_BEARS, 2),
            entry(Syn.SERRA_ANGEL),
            entry(Syn.COUNTERSPELL),
            entry(Syn.DOOM_BLADE),
            entry(Syn.GIANT_GROWTH),
            entry(Syn.KITCHEN_FINKS),
            entry(Syn.LIGHTNING_HELIX),
            entry(Syn.HUNTMASTER),
            entry(Syn.AGADEEMS_AWAKENING),
            entry(Syn.BONECRUSHER_GIANT),
            entry(Syn.MURDEROUS_RIDER),
            entry(Syn.FIRE_ICE),
            entry(Syn.WEAR_TEAR),
            entry(Syn.MIND_STONE),
            entry(Syn.TEMPLE_OF_MYSTERY),
            entry(Syn.EVOLVING_WILDS),
            entry(Syn.FOREST, 2),
        )
    private val metrics = MetricEngine.compute(pool)

    @Test
    fun `shape has every bucket and all ten pairs in enum order`() {
        assertEquals(ColorBucket.entries, metrics.byColor.keys.toList())
        assertEquals(
            listOf("WU", "WB", "WR", "WG", "UB", "UR", "UG", "BR", "BG", "RG"),
            metrics.pairs.keys.map { it.name },
        )
        metrics.pairs.values.forEach { assertEquals(Curve.TOP + 1, it.curve.buckets.size) }
    }

    @Test
    fun `pool totals`() {
        // 17 entries, Grizzly Bears and Forest at 2 copies each: 15 x 1 + 2 + 2.
        assertEquals(19, metrics.totalCards)
        // Everything but Temple of Mystery, Evolving Wilds, and the two Forests. Agadeem's
        // Awakening counts here: its front face is a sorcery.
        assertEquals(15, metrics.nonlandCards)
        // Forest x2.
        assertEquals(2, metrics.basicLands)
        // Temple of Mystery and Evolving Wilds.
        assertEquals(2, metrics.nonbasicLands)
        // Temple of Mystery adds {G} or {U}; nothing else in the pool makes two colors.
        assertEquals(
            listOf(FixingSource("Temple of Mystery", setOf(G, U), 1)),
            metrics.fixingSources,
        )
        // Doom Blade, Lightning Helix, Huntmaster, Bonecrusher Giant, Murderous Rider.
        assertEquals(5, metrics.removalTotal)
        // Counterspell (COUNTER) and Giant Growth (TRICK).
        assertEquals(2, metrics.interactionTotal)
    }

    @Test
    fun `white bucket`() {
        assertEquals(
            ColorMetrics(
                // Serra Angel, Kitchen Finks, Lightning Helix, Wear // Tear: all uncommon.
                cards = rarities(uncommon = 4),
                // Only Serra Angel is mono-white.
                playables = rarities(uncommon = 1),
                // Serra Angel and Kitchen Finks.
                creatures = 2,
                // Lightning Helix.
                removal = 1,
                // No white card counters, bounces, or pumps.
                interaction = 0,
            ),
            metrics.byColor.getValue(ColorBucket.W),
        )
    }

    @Test
    fun `blue bucket`() {
        assertEquals(
            ColorMetrics(
                // Counterspell and Fire // Ice, both uncommon.
                cards = rarities(uncommon = 2),
                // Counterspell; Fire // Ice is blue-red.
                playables = rarities(uncommon = 1),
                // Neither is a creature.
                creatures = 0,
                // Neither is removal.
                removal = 0,
                // Counterspell.
                interaction = 1,
            ),
            metrics.byColor.getValue(ColorBucket.U),
        )
    }

    @Test
    fun `black bucket`() {
        assertEquals(
            ColorMetrics(
                // Doom Blade (common), Murderous Rider (rare), Agadeem's Awakening (mythic).
                cards = rarities(common = 1, rare = 1, mythic = 1),
                // All three are mono-black.
                playables = rarities(common = 1, rare = 1, mythic = 1),
                // Murderous Rider.
                creatures = 1,
                // Doom Blade and Murderous Rider (Swift End).
                removal = 2,
                interaction = 0,
            ),
            metrics.byColor.getValue(ColorBucket.B),
        )
    }

    @Test
    fun `red bucket`() {
        assertEquals(
            ColorMetrics(
                // Lightning Helix, Fire // Ice, Wear // Tear (uncommon); Huntmaster, Bonecrusher
                // Giant (rare).
                cards = rarities(uncommon = 3, rare = 2),
                // Bonecrusher Giant is the only mono-red card.
                playables = rarities(rare = 1),
                // Huntmaster and Bonecrusher Giant.
                creatures = 2,
                // Lightning Helix, Huntmaster (back face), Bonecrusher Giant (Stomp).
                removal = 3,
                interaction = 0,
            ),
            metrics.byColor.getValue(ColorBucket.R),
        )
    }

    @Test
    fun `green bucket`() {
        assertEquals(
            ColorMetrics(
                // Grizzly Bears x2 and Giant Growth (common); Kitchen Finks (uncommon); Huntmaster
                // (rare).
                cards = rarities(common = 3, uncommon = 1, rare = 1),
                // Grizzly Bears x2 and Giant Growth are mono-green.
                playables = rarities(common = 3),
                // Grizzly Bears x2, Kitchen Finks, Huntmaster.
                creatures = 4,
                // Huntmaster.
                removal = 1,
                // Giant Growth.
                interaction = 1,
            ),
            metrics.byColor.getValue(ColorBucket.G),
        )
    }

    @Test
    fun `multicolor and colorless buckets`() {
        assertEquals(
            ColorMetrics(
                // Kitchen Finks, Lightning Helix, Fire // Ice, Wear // Tear (uncommon); Huntmaster
                // (rare).
                cards = rarities(uncommon = 4, rare = 1),
                // Playables equal cards for the multicolor bucket.
                playables = rarities(uncommon = 4, rare = 1),
                // Kitchen Finks and Huntmaster.
                creatures = 2,
                // Lightning Helix and Huntmaster.
                removal = 2,
                interaction = 0,
            ),
            metrics.byColor.getValue(ColorBucket.MULTI),
        )
        assertEquals(
            ColorMetrics(
                // Mind Stone, common. The colorless lands are in no bucket.
                cards = rarities(common = 1),
                playables = rarities(common = 1),
                creatures = 0,
                removal = 0,
                interaction = 0,
            ),
            metrics.byColor.getValue(ColorBucket.COLORLESS),
        )
    }

    @Test
    fun `red-green pair`() {
        val rg = metrics.pairs.getValue(ColorPair.RG)

        // Castable: Grizzly Bears x2, Giant Growth, Huntmaster, Bonecrusher Giant, Mind Stone.
        assertEquals(6, rg.cards)
        // Grizzly Bears x2, Huntmaster, Bonecrusher Giant.
        assertEquals(4, rg.creatures)
        // Giant Growth and Mind Stone.
        assertEquals(2, rg.noncreatures)
        assertEquals(
            curveOf(
                // 1: Giant Growth.
                CurveBucket(1, creatures = 0, noncreatures = 1),
                // 2: Grizzly Bears x2; Mind Stone.
                CurveBucket(2, creatures = 2, noncreatures = 1),
                // 3: Bonecrusher Giant.
                CurveBucket(3, creatures = 1, noncreatures = 0),
                // 4: Huntmaster.
                CurveBucket(4, creatures = 1, noncreatures = 0),
            ),
            rg.curve,
        )
        // Huntmaster and Bonecrusher Giant.
        assertEquals(2, rg.removal)
        // Giant Growth.
        assertEquals(1, rg.interaction)
        // Temple of Mystery makes G.
        assertEquals(1, rg.fixingSources)
        assertEquals(
            listOf(
                // B is the one off color, Doom Blade costs {1}{B}, and it is removal.
                SplashCandidate("Doom Blade", B, SplashReason.REMOVAL),
                // W is the one off color, {R}{W} has one W pip, and it is removal. Kitchen Finks
                // and Wear // Tear also need one W but are uncommon and not removal; Murderous
                // Rider and Agadeem's Awakening need more than one B; Fire // Ice has no U pip.
                SplashCandidate("Lightning Helix", W, SplashReason.REMOVAL),
            ),
            rg.splashCandidates,
        )
        // Pips: G 2 (Bears x2) + 1 (Giant Growth) + 1 (Huntmaster) = 4; R 1 (Huntmaster) +
        // 1 (Bonecrusher) = 2. Shares of 17: G 11.33, R 5.67; floors 11 + 5, the spare land goes to
        // R's larger fraction: G 11, R 6, and R already meets the minimum of 6.
        assertEquals(mapOf(R to 6, G to 11), rg.landRecommendation)
        // R: 6 lands, no fixing source makes R: 1 - C(34,9)/C(40,9) = 0.80818 -> 0.8082.
        // G: 11 lands + Temple of Mystery = 12: 1 - C(28,9)/C(40,9) = 0.97474 -> 0.9747.
        assertEquals(mapOf(R to 0.8082, G to 0.9747), rg.castProbability)
    }

    @Test
    fun `white-blue pair`() {
        val wu = metrics.pairs.getValue(ColorPair.WU)

        // Castable: Serra Angel, Counterspell, Mind Stone. Kitchen Finks needs G, Lightning Helix
        // and Wear // Tear need R, Fire // Ice needs R.
        assertEquals(3, wu.cards)
        // Serra Angel.
        assertEquals(1, wu.creatures)
        // Counterspell and Mind Stone.
        assertEquals(2, wu.noncreatures)
        assertEquals(
            curveOf(
                // 2: Counterspell and Mind Stone.
                CurveBucket(2, creatures = 0, noncreatures = 2),
                // 5: Serra Angel.
                CurveBucket(5, creatures = 1, noncreatures = 0),
            ),
            wu.curve,
        )
        // Nothing castable in WU is removal.
        assertEquals(0, wu.removal)
        // Counterspell.
        assertEquals(1, wu.interaction)
        // Temple of Mystery makes U.
        assertEquals(1, wu.fixingSources)
        assertEquals(
            listOf(
                // Off color R, creature face {2}{R}, removal through Stomp (and rare).
                SplashCandidate("Bonecrusher Giant // Stomp", R, SplashReason.REMOVAL),
                // Off color B, {1}{B}, removal.
                SplashCandidate("Doom Blade", B, SplashReason.REMOVAL),
                // Off color R, {R}{W}, removal. Fire // Ice also needs one R (Fire's {1}{R}) but
                // is uncommon and not removal; Wear // Tear's pips are Tear's {W}, so no R pip;
                // Huntmaster has two off colors.
                SplashCandidate("Lightning Helix", R, SplashReason.REMOVAL),
            ),
            wu.splashCandidates,
        )
        // Pips: W 2 (Serra Angel), U 2 (Counterspell). Shares 8.5 each, floors 8 + 8, the spare
        // land goes to W on the tie (enum order): W 9, U 8.
        assertEquals(mapOf(W to 9, U to 8), wu.landRecommendation)
        // W: 9 lands. U: 8 lands + Temple of Mystery = 9. Both 1 - C(31,9)/C(40,9) = 0.92627.
        assertEquals(mapOf(W to 0.9263, U to 0.9263), wu.castProbability)
    }

    @Test
    fun `white-red pair uses the cheaper split face for pips`() {
        val wr = metrics.pairs.getValue(ColorPair.WR)

        // Castable: Serra Angel, Lightning Helix, Bonecrusher Giant, Wear // Tear, Mind Stone.
        assertEquals(5, wr.cards)
        // Serra Angel and Bonecrusher Giant.
        assertEquals(2, wr.creatures)
        // Lightning Helix, Wear // Tear, Mind Stone.
        assertEquals(3, wr.noncreatures)
        assertEquals(
            curveOf(
                // 1: Wear // Tear (Tear costs {W}).
                CurveBucket(1, creatures = 0, noncreatures = 1),
                // 2: Lightning Helix and Mind Stone.
                CurveBucket(2, creatures = 0, noncreatures = 2),
                // 3: Bonecrusher Giant.
                CurveBucket(3, creatures = 1, noncreatures = 0),
                // 5: Serra Angel.
                CurveBucket(5, creatures = 1, noncreatures = 0),
            ),
            wr.curve,
        )
        // Lightning Helix and Bonecrusher Giant.
        assertEquals(2, wr.removal)
        // No castable card counters, bounces, or pumps.
        assertEquals(0, wr.interaction)
        // Temple of Mystery makes only G and U.
        assertEquals(0, wr.fixingSources)
        assertEquals(
            listOf(
                // Off color B, {1}{B}, removal.
                SplashCandidate("Doom Blade", B, SplashReason.REMOVAL),
                // Off color G, {2}{R}{G} has one G pip, removal from the back face.
                SplashCandidate(
                    "Huntmaster of the Fells // Ravager of the Fells",
                    G,
                    SplashReason.REMOVAL,
                ),
            ),
            wr.splashCandidates,
        )
        // Pips: W 2 (Serra) + 1 (Helix) + 1 (Tear) = 4; R 1 (Helix) + 1 (Bonecrusher) = 2. Using
        // Wear's {1}{R} instead would give W 3, R 3. Shares of 17: W 11.33, R 5.67 -> W 11, R 6.
        assertEquals(mapOf(W to 11, R to 6), wr.landRecommendation)
        // W: 11 lands: 1 - C(29,9)/C(40,9) = 0.96337 -> 0.9634. R: 6 lands -> 0.8082.
        assertEquals(mapOf(W to 0.9634, R to 0.8082), wr.castProbability)
    }
}
