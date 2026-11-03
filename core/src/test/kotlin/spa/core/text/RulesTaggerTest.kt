package spa.core.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import spa.core.text.Cards as C

class RulesTaggerTest {
    /** For each pattern name: real cards that must hit it, and near-misses that must not. */
    private val hits: Map<String, List<OracleCard>> =
        mapOf(
            "destroy" to
                listOf(C.murder, C.herosDownfall, C.vindicate, C.plummet, C.ravenousChupacabra),
            "exile" to listOf(C.swordsToPlowshares, C.journeyToNowhere),
            "damage" to listOf(C.lightningBolt, C.shock, C.flameSlash, C.rabidBite),
            "shrink" to listOf(C.disfigure, C.grimHireling, C.blackSunsTwilight),
            "fight" to listOf(C.preyUpon),
            "bite" to listOf(C.rabidBite),
            "sacrifice" to listOf(C.mercyKilling),
            "counter" to listOf(C.cancel, C.essenceScatter, C.negate, C.stifle, C.spellSwindle),
            "return-to-hand" to listOf(C.unsummon, C.intoTheRoil),
            "top-or-bottom" to listOf(C.griptide, C.theSpotsPortal),
            "instant-pump" to listOf(C.giantGrowth, C.rebelliousStrike, C.rangersGuile),
            "flash-pump" to listOf(C.briarpackAlpha),
        )

    private val nearMisses: Map<String, List<OracleCard>> =
        mapOf(
            "destroy" to listOf(C.shatter, C.naturalize),
            "exile" to listOf(C.cloudshift),
            "damage" to listOf(C.lavaSpike, C.graviticPunch),
            "shrink" to listOf(C.whisperOfTheDross),
            "fight" to listOf(C.hansEriksson),
            "bite" to listOf(C.graviticPunch),
            "sacrifice" to listOf(C.cauldronDance),
            "counter" to listOf(C.carnageTyrant),
            "return-to-hand" to listOf(C.raiseDead, C.gravedigger),
            "top-or-bottom" to listOf(C.junktroller),
            "instant-pump" to listOf(C.compelledDuel, C.huntersProwess, C.yevasForcemage),
            "flash-pump" to listOf(C.yevasForcemage, C.ambushViper, C.compelledDuel),
        )

    private fun patternNames(card: OracleCard): List<String> =
        RulesTagger.matchingPatterns(card.typeLine, card.oracleText).map { it.name }

    private fun tags(card: OracleCard): Set<Tag> = RulesTagger.tag(card.typeLine, card.oracleText)

    @Test
    fun `every pattern has at least one real hit and one near-miss`() {
        val names = RulesTagger.patterns.map { it.name }
        assertEquals(names.distinct(), names, "pattern names are unique")
        assertEquals(names.toSet(), hits.keys, "hit table covers exactly the patterns")
        assertEquals(names.toSet(), nearMisses.keys, "near-miss table covers exactly the patterns")
        hits.values.forEach { assertTrue(it.isNotEmpty()) }
        nearMisses.values.forEach { assertTrue(it.isNotEmpty()) }
    }

    @Test
    fun `each hit matches its pattern`() {
        for ((pattern, cards) in hits) {
            for (card in cards) {
                assertTrue(pattern in patternNames(card), "${card.name} should hit '$pattern'")
            }
        }
    }

    @Test
    fun `each near-miss does not match its pattern`() {
        for ((pattern, cards) in nearMisses) {
            for (card in cards) {
                assertFalse(pattern in patternNames(card), "${card.name} should miss '$pattern'")
            }
        }
    }

    @Test
    fun `removal is set for destroy, exile, damage, shrink, fight, bite, and sacrifice`() {
        listOf(
                C.murder,
                C.herosDownfall,
                C.vindicate,
                C.swordsToPlowshares,
                C.lightningBolt,
                C.flameSlash,
                C.disfigure,
                C.blackSunsTwilight,
                C.preyUpon,
                C.rabidBite,
                C.mercyKilling,
            )
            .forEach { assertTrue(Tag.REMOVAL in tags(it), "${it.name} is removal") }
    }

    @Test
    fun `removal is not set for artifact or enchantment removal, player damage, -1-1, or flicker`() {
        listOf(
                C.shatter,
                C.naturalize,
                C.lavaSpike,
                C.graviticPunch,
                C.whisperOfTheDross,
                C.cloudshift,
                C.cauldronDance,
                C.hansEriksson,
            )
            .forEach { assertFalse(Tag.REMOVAL in tags(it), "${it.name} is not removal") }
    }

    @Test
    fun `counter is set for spells and abilities but not for can't be countered`() {
        listOf(C.cancel, C.essenceScatter, C.negate, C.stifle).forEach {
            assertEquals(setOf(Tag.COUNTER), tags(it), it.name)
        }
        assertEquals(emptySet(), tags(C.carnageTyrant))
    }

    @Test
    fun `bounce is set for hand, top, and bottom but not for graveyard returns`() {
        listOf(C.unsummon, C.intoTheRoil, C.griptide, C.theSpotsPortal).forEach {
            assertEquals(setOf(Tag.BOUNCE), tags(it), it.name)
        }
        listOf(C.raiseDead, C.gravedigger, C.junktroller).forEach {
            assertFalse(Tag.BOUNCE in tags(it), "${it.name} is not bounce")
        }
    }

    @Test
    fun `trick is set for instant pumps and flash pumps but not sorceries or plain creatures`() {
        listOf(C.giantGrowth, C.rebelliousStrike, C.rangersGuile, C.briarpackAlpha).forEach {
            assertEquals(setOf(Tag.TRICK), tags(it), it.name)
        }
        listOf(C.compelledDuel, C.huntersProwess, C.yevasForcemage, C.ambushViper).forEach {
            assertFalse(Tag.TRICK in tags(it), "${it.name} is not a trick")
        }
    }

    @Test
    fun `trick needs an instant type line, not the word instant in the text`() {
        // Briarpack Alpha's reminder text says "instant"; without Flash it is not a trick.
        val withoutFlash = C.briarpackAlpha.oracleText.replace("Flash ", "")
        assertEquals(emptySet(), RulesTagger.tag(C.briarpackAlpha.typeLine, withoutFlash))
    }

    @Test
    fun `fixing comes from mana production of two or more colors`() {
        assertEquals(setOf(Tag.FIXING), tags(C.selesnyaGuildgate))
        assertEquals(setOf(Tag.FIXING), tags(C.birdsOfParadise))
        assertEquals(setOf(Tag.FIXING), tags(C.prosperousInnkeeper))
        assertEquals(setOf(Tag.COUNTER, Tag.FIXING), tags(C.spellSwindle))
        assertEquals(emptySet(), tags(C.llanowarElves))
        assertEquals(emptySet(), tags(C.forest))
    }

    @Test
    fun `a card can carry several tags, returned in enum order`() {
        // Grim Hireling makes Treasure and shrinks a creature.
        assertEquals(listOf(Tag.REMOVAL, Tag.FIXING), tags(C.grimHireling).toList())
    }

    @Test
    fun `tagging is deterministic on repeated calls`() {
        val cards = hits.values.flatten() + nearMisses.values.flatten()
        val first = cards.map { tags(it).toList() }
        repeat(3) { assertEquals(first, cards.map { card -> tags(card).toList() }) }
    }

    @Test
    fun `text inside parentheses is ignored`() {
        val type = C.murder.typeLine
        assertEquals(setOf(Tag.REMOVAL), RulesTagger.tag(type, C.murder.oracleText))
        assertEquals(emptySet(), RulesTagger.tag(type, "(${C.murder.oracleText})"))
        assertEquals(
            emptySet(),
            RulesTagger.tag(type, "Flying (Reminder (${C.cancel.oracleText}).)"),
        )
        // Prey Upon's reminder text would otherwise read as a bite.
        assertEquals(listOf("fight"), patternNames(C.preyUpon))
    }

    @Test
    fun `matching ignores case`() {
        assertEquals(setOf(Tag.REMOVAL), RulesTagger.tag("INSTANT", "DESTROY TARGET CREATURE."))
    }

    @Test
    fun `interaction is counter, bounce, and trick`() {
        assertEquals(
            setOf(Tag.COUNTER, Tag.BOUNCE, Tag.TRICK),
            Tag.entries.filter { it.isInteraction }.toSet(),
        )
    }

    @Test
    fun `normalizing removes nested reminder text and maps Unicode minus and apostrophe`() {
        assertEquals(
            "target creature gets -2/-2. owner's",
            normalizeRulesText("Target creature gets −2/−2. (a (b) c)Owner’s"),
        )
    }
}
