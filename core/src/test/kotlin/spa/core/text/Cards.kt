package spa.core.text

/**
 * A card's Scryfall `type_line` and `oracle_text`, copied verbatim from Scryfall on 2026-11-03.
 * Card data from Scryfall (https://scryfall.com).
 */
data class OracleCard(val name: String, val typeLine: String, val oracleText: String)

/** Real oracle texts shared by the text tests. */
object Cards {
    val murder = OracleCard("Murder", "Instant", "Destroy target creature.")
    val herosDownfall =
        OracleCard("Hero's Downfall", "Instant", "Destroy target creature or planeswalker.")
    val vindicate = OracleCard("Vindicate", "Sorcery", "Destroy target permanent.")
    val plummet = OracleCard("Plummet", "Instant", "Destroy target creature with flying.")
    val ravenousChupacabra =
        OracleCard(
            "Ravenous Chupacabra",
            "Creature — Beast Horror",
            "When this creature enters, destroy target creature an opponent controls.",
        )
    val shatter = OracleCard("Shatter", "Instant", "Destroy target artifact.")
    val naturalize = OracleCard("Naturalize", "Instant", "Destroy target artifact or enchantment.")

    val swordsToPlowshares =
        OracleCard(
            "Swords to Plowshares",
            "Instant",
            "Exile target creature. Its controller gains life equal to its power.",
        )
    val journeyToNowhere =
        OracleCard(
            "Journey to Nowhere",
            "Enchantment",
            "When this enchantment enters, exile target creature.\nWhen this enchantment leaves " +
                "the battlefield, return the exiled card to the battlefield under its owner's control.",
        )
    val cloudshift =
        OracleCard(
            "Cloudshift",
            "Instant",
            "Exile target creature you control, then return that card to the battlefield under " +
                "your control.",
        )

    val lightningBolt =
        OracleCard("Lightning Bolt", "Instant", "Lightning Bolt deals 3 damage to any target.")
    val shock = OracleCard("Shock", "Instant", "Shock deals 2 damage to any target.")
    val flameSlash =
        OracleCard("Flame Slash", "Sorcery", "Flame Slash deals 4 damage to target creature.")
    val lavaSpike =
        OracleCard(
            "Lava Spike",
            "Sorcery — Arcane",
            "Lava Spike deals 3 damage to target player or planeswalker.",
        )

    val disfigure =
        OracleCard("Disfigure", "Instant", "Target creature gets -2/-2 until end of turn.")
    val grimHireling =
        OracleCard(
            "Grim Hireling",
            "Creature — Tiefling Rogue",
            "Whenever one or more creatures you control deal combat damage to a player, create " +
                "two Treasure tokens.\n{B}, Sacrifice X Treasures: Target creature gets -X/-X " +
                "until end of turn. Activate only as a sorcery.",
        )
    val blackSunsTwilight =
        OracleCard(
            "Black Sun's Twilight",
            "Instant",
            "Up to one target creature gets -X/-X until end of turn. If X is 5 or more, return a " +
                "creature card with mana value X or less from your graveyard to the battlefield " +
                "tapped.",
        )
    val whisperOfTheDross =
        OracleCard(
            "Whisper of the Dross",
            "Instant",
            "Target creature gets -1/-1 until end of turn. Proliferate. (Choose any number of " +
                "permanents and/or players, then give each another counter of each kind already " +
                "there.)",
        )

    val preyUpon =
        OracleCard(
            "Prey Upon",
            "Sorcery",
            "Target creature you control fights target creature you don't control. (Each deals " +
                "damage equal to its power to the other.)",
        )
    val hansEriksson =
        OracleCard(
            "Hans Eriksson",
            "Legendary Creature — Human Scout",
            "Whenever Hans Eriksson attacks, reveal the top card of your library. If it's a " +
                "creature card, put it onto the battlefield tapped and attacking defending player " +
                "or a planeswalker they control. Otherwise, put that card into your hand. When " +
                "you put a creature card onto the battlefield this way, it fights Hans Eriksson.",
        )
    val rabidBite =
        OracleCard(
            "Rabid Bite",
            "Sorcery",
            "Target creature you control deals damage equal to its power to target creature you " +
                "don't control.",
        )
    val graviticPunch =
        OracleCard(
            "Gravitic Punch",
            "Sorcery",
            "Target creature you control deals damage equal to its power to target player.\n" +
                "Jump-start (You may cast this card from your graveyard by discarding a card in " +
                "addition to paying its other costs. Then exile this card.)",
        )
    val mercyKilling =
        OracleCard(
            "Mercy Killing",
            "Instant",
            "Target creature's controller sacrifices it, then creates X 1/1 green and white Elf " +
                "Warrior creature tokens, where X is that creature's power.",
        )
    val cauldronDance =
        OracleCard(
            "Cauldron Dance",
            "Instant",
            "Cast this spell only during combat.\nReturn target creature card from your graveyard " +
                "to the battlefield. That creature gains haste. Return it to your hand at the " +
                "beginning of the next end step.\nYou may put a creature card from your hand onto " +
                "the battlefield. That creature gains haste. Its controller sacrifices it at the " +
                "beginning of the next end step.",
        )

    val cancel = OracleCard("Cancel", "Instant", "Counter target spell.")
    val essenceScatter = OracleCard("Essence Scatter", "Instant", "Counter target creature spell.")
    val negate = OracleCard("Negate", "Instant", "Counter target noncreature spell.")
    val stifle =
        OracleCard(
            "Stifle",
            "Instant",
            "Counter target activated or triggered ability. (Mana abilities can't be targeted.)",
        )
    val carnageTyrant =
        OracleCard(
            "Carnage Tyrant",
            "Creature — Dinosaur",
            "This spell can't be countered.\nTrample, hexproof",
        )

    val unsummon = OracleCard("Unsummon", "Instant", "Return target creature to its owner's hand.")
    val intoTheRoil =
        OracleCard(
            "Into the Roil",
            "Instant",
            "Kicker {1}{U} (You may pay an additional {1}{U} as you cast this spell.)\nReturn " +
                "target nonland permanent to its owner's hand. If this spell was kicked, draw a card.",
        )
    val raiseDead =
        OracleCard(
            "Raise Dead",
            "Sorcery",
            "Return target creature card from your graveyard to your hand.",
        )
    val gravedigger =
        OracleCard(
            "Gravedigger",
            "Creature — Zombie",
            "When this creature enters, you may return target creature card from your graveyard " +
                "to your hand.",
        )
    val griptide =
        OracleCard("Griptide", "Instant", "Put target creature on top of its owner's library.")
    val theSpotsPortal =
        OracleCard(
            "The Spot's Portal",
            "Instant",
            "Put target creature on the bottom of its owner's library. You lose 2 life unless you " +
                "control a Villain.",
        )
    val junktroller =
        OracleCard(
            "Junktroller",
            "Artifact Creature — Golem",
            "Defender\n{T}: Put target card from a graveyard on the bottom of its owner's library.",
        )

    val giantGrowth =
        OracleCard("Giant Growth", "Instant", "Target creature gets +3/+3 until end of turn.")
    val rebelliousStrike =
        OracleCard(
            "Rebellious Strike",
            "Instant",
            "Target creature gets +3/+0 until end of turn.\nDraw a card.",
        )
    val rangersGuile =
        OracleCard(
            "Ranger's Guile",
            "Instant",
            "Target creature you control gets +1/+1 and gains hexproof until end of turn. (It " +
                "can't be the target of spells or abilities your opponents control.)",
        )
    val compelledDuel =
        OracleCard(
            "Compelled Duel",
            "Sorcery",
            "Target creature gets +3/+3 until end of turn and must be blocked this turn if able.",
        )
    val huntersProwess =
        OracleCard(
            "Hunter's Prowess",
            "Sorcery",
            "Until end of turn, target creature gets +3/+3 and gains trample and \"Whenever this " +
                "creature deals combat damage to a player, draw that many cards.\"",
        )
    val briarpackAlpha =
        OracleCard(
            "Briarpack Alpha",
            "Creature — Wolf",
            "Flash (You may cast this spell any time you could cast an instant.)\nWhen this " +
                "creature enters, target creature gets +2/+2 until end of turn.",
        )
    val yevasForcemage =
        OracleCard(
            "Yeva's Forcemage",
            "Creature — Elf Shaman",
            "When this creature enters, target creature gets +2/+2 until end of turn.",
        )
    val ambushViper = OracleCard("Ambush Viper", "Creature — Snake", "Flash\nDeathtouch")

    val forest = OracleCard("Forest", "Basic Land — Forest", "({T}: Add {G}.)")
    val breedingPool =
        OracleCard(
            "Breeding Pool",
            "Land — Forest Island",
            "({T}: Add {G} or {U}.)\nAs this land enters, you may pay 2 life. If you don't, it " +
                "enters tapped.",
        )
    val selesnyaGuildgate =
        OracleCard(
            "Selesnya Guildgate",
            "Land — Gate",
            "This land enters tapped.\n{T}: Add {G} or {W}.",
        )
    val llanowarElves = OracleCard("Llanowar Elves", "Creature — Elf Druid", "{T}: Add {G}.")
    val birdsOfParadise =
        OracleCard(
            "Birds of Paradise",
            "Creature — Bird",
            "Flying\n{T}: Add one mana of any color.",
        )
    val lotusCobra =
        OracleCard(
            "Lotus Cobra",
            "Creature — Snake",
            "Landfall — Whenever a land you control enters, add one mana of any color.",
        )
    val golgariSignet = OracleCard("Golgari Signet", "Artifact", "{1}, {T}: Add {B}{G}.")
    val solRing = OracleCard("Sol Ring", "Artifact", "{T}: Add {C}{C}.")
    val ornithopter = OracleCard("Ornithopter", "Artifact Creature — Thopter", "Flying")
    val prosperousInnkeeper =
        OracleCard(
            "Prosperous Innkeeper",
            "Creature — Halfling Citizen",
            "When this creature enters, create a Treasure token. (It's an artifact with \"{T}, " +
                "Sacrifice this token: Add one mana of any color.\")\nWhenever another creature " +
                "you control enters, you gain 1 life.",
        )
    val bigScore =
        OracleCard(
            "Big Score",
            "Instant",
            "As an additional cost to cast this spell, discard a card.\nDraw two cards and " +
                "create two Treasure tokens. (They're artifacts with \"{T}, Sacrifice this token: " +
                "Add one mana of any color.\")",
        )
    val spellSwindle =
        OracleCard(
            "Spell Swindle",
            "Instant",
            "Counter target spell. Create X Treasure tokens, where X is that spell's mana value. " +
                "(They're artifacts with \"{T}, Sacrifice this token: Add one mana of any color.\")",
        )
    val treasureVault =
        OracleCard(
            "Treasure Vault",
            "Artifact Land",
            "{T}: Add {C}.\n{X}{X}, {T}, Sacrifice this land: Create X Treasure tokens.",
        )
}
