package spa.cli

import kotlin.test.assertEquals
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import spa.core.metrics.ColorBucket
import spa.core.metrics.ColorPair
import spa.core.metrics.PoolMetrics
import spa.core.text.Color

/**
 * Three fixture pools whose key numbers were counted by hand from the pool file and the Scryfall
 * cache (`fixtures/scryfall/trk/cards.json`, plus `sds` for the bonus sheet), not copied from the
 * engine's output. Each group of assertions has the cards it counts listed above it, so the counts
 * can be redone without running code.
 *
 * How the counts are made, following `PoolMetrics`:
 * - Counts are by copies. Rows or lines for the same card add up.
 * - A color bucket holds every nonland card of that color, multicolor cards included; MULTI holds
 *   cards of two or more colors; COLORLESS holds nonland cards of no color. Lands are in no bucket.
 *   A hybrid cost such as {R/W} makes the card both colors.
 * - A pair's cards are the nonland cards whose colors are inside the pair: colorless cards, both
 *   mono-color lists, and the multicolor cards of exactly that pair.
 * - Removal is oracle text that destroys or exiles a target creature or permanent not your own,
 *   deals damage to any target or to such a target creature, gives a target creature -2/-2 or
 *   worse, or fights or bites a target creature. "Destroy all", "each opponent sacrifices",
 *   tapping, stun counters, bounce, and counterspells are not removal.
 * - The land recommendation splits 17 lands by the colored pips of the pair's cards: a plain pip is
 *   1 and a hybrid pip is 1/2 to each color. Each color gets the floor of 17 x its share, the land
 *   left over goes to the larger fraction, and each color keeps at least 6.
 */
class HandCheckedPoolsTest {
    private fun metrics(file: String, format: String): PoolMetrics {
        val run =
            runCli(
                "analyze",
                Fixtures.pool(file),
                "--set",
                "trk",
                "--format",
                format,
                "--data",
                Fixtures.data,
            )
        assertEquals(ExitCode.OK, run.exit, run.stderr)
        return Json.decodeFromJsonElement(
            PoolMetrics.serializer(),
            run.json().getValue("metrics"),
        )
    }

    private fun PoolMetrics.cards(bucket: ColorBucket): Int = byColor.getValue(bucket).cards.total

    private fun PoolMetrics.cards(pair: ColorPair): Int = pairs.getValue(pair).cards

    private fun PoolMetrics.creatures(pair: ColorPair): Int = pairs.getValue(pair).creatures

    private fun PoolMetrics.lands(pair: ColorPair): Map<Color, Int> =
        pairs.getValue(pair).landRecommendation

    /**
     * `trk-manabox-01.csv`, 85 cards. Cloistered Telepath, Guidance Failure, and Final Frontier
     * each have a normal and a foil row of 1. Tatyova is the `sds` bonus-sheet card.
     */
    @Nested
    inner class ManaBox01 {
        private val m = metrics("trk-manabox-01.csv", "manabox")

        // Mono W (7): Command Decision x2, Federation Field Medic, Pure Energy, Rutherford,
        //   Upgraded Ensign, Tactical Officer, Una Chin-Riley, Number One.
        // Mono U (18): Anthropological Mission, Cetacean Ops x2, Cloistered Telepath x2, Ensign
        //   Pavel Chekov, Enterprise NX-01, Federation Probe, Guidance Failure x2, Humpback Whales,
        //   Melkotian Adjudicator, Starfleet Spanner Jockey x3, Suurok-Class Science Vessel x2,
        //   Suus Mahna Practitioner.
        // Mono B (10): Automated Warfare System, Battle-Scarred Survivalist, Collective Drone,
        //   Commander Raffi Musiker, He's Dead, Jim, Impossible Choice, Kai Winn, Faithless
        //   Leader, Khan, Vengeful Heart, Tal Shiar Operative, U.S.S. Reliant, From Hell's Heart.
        // Mono R (7): Boarding Action, Cantankerous Captain, Captain's Tutelage, Exploratory
        //   Mission, Planetary Bombardment, Plasma Cascade, Sarcophagus, Ship of the Dead.
        // Mono G (13): Adversary by Algorithm, Common Goal, Dathon and Picard at El-Adrel,
        //   Dominion Supervisor, Dr. Joseph M'Benga, Final Frontier x2, First Contact, I'm a
        //   Doctor, Not a . . ., Moopsy, Saru, Unthreatened, Species 8472, Xenobotanist.
        // Multicolor (9): Hemmer, Fixing What Is Broken (WU); Locutus, the One Who Speaks x2
        //   (WB); Lore, Positronic Provocateur (UB, hybrid); Gray and Adira, Forever Joined (UR,
        //   hybrid); Tatyova, Benthic Druid (UG); L'Rell, Fierce Visionary (BR); Cleveland
        //   Booker, Empath (RG, hybrid); Dal R'El, Augment Orphan (RG).
        // Colorless (7): Crystalline Entity, Cybernetic Specialist, Tea, Earl Grey, Hot x2,
        //   Type-2 Phaser, Warp Core x2.
        // W = 7 + Hemmer + Locutus x2 = 10. U = 18 + Hemmer, Lore, Gray and Adira, Tatyova = 22.
        // B = 10 + Locutus x2, Lore, L'Rell = 14. R = 7 + Gray and Adira, L'Rell, Cleveland
        // Booker, Dal R'El = 11. G = 13 + Tatyova, Cleveland Booker, Dal R'El = 16.
        // Basic lands (5): Forest, Mountain, Swamp x3.
        // Nonbasic lands (9): Bajor, Cardassia Prime, Ferenginar, Ni'Var Science Academy,
        //   Q Continuum, Quark's Bar, Romulus, Unexplored Territory x2.
        @Test
        fun `cards per color bucket and lands`() {
            assertEquals(10, m.cards(ColorBucket.W), "W")
            assertEquals(22, m.cards(ColorBucket.U), "U")
            assertEquals(14, m.cards(ColorBucket.B), "B")
            assertEquals(11, m.cards(ColorBucket.R), "R")
            assertEquals(16, m.cards(ColorBucket.G), "G")
            assertEquals(9, m.cards(ColorBucket.MULTI), "MULTI")
            assertEquals(7, m.cards(ColorBucket.COLORLESS), "COLORLESS")
            assertEquals(5, m.basicLands, "basic lands")
            assertEquals(9, m.nonbasicLands, "nonbasic lands")
            assertEquals(71, m.nonlandCards, "nonland cards")
        }

        // Removal (9): He's Dead, Jim (destroy target creature); Impossible Choice (destroy
        // target creature); Command Decision x2 (destroy target creature with power 4 or
        // greater); Common Goal (your creatures deal damage equal to their power to target
        // creature an opponent controls); Planetary Bombardment (2 damage to target creature);
        // Plasma Cascade (3 damage to any target); Locutus x2 (deals 2 damage to target creature).
        // Not removal: Crystalline Entity (destroy all), Type-2 Phaser and Enterprise NX-01 (tap
        // and stun), Guidance Failure (top or bottom of library), Tea, Earl Grey, Hot (damage to
        // an opponent), Commander Raffi Musiker (exiles a card from a graveyard), Quark's Bar
        // (a land).
        @Test
        fun `removal total`() {
            assertEquals(9, m.removalTotal)
        }

        // Creatures. Colorless (2): Crystalline Entity, Cybernetic Specialist.
        // W (4): Federation Field Medic, Rutherford, Tactical Officer, Una Chin-Riley.
        // U (12): Cetacean Ops x2, Cloistered Telepath x2, Ensign Pavel Chekov, Federation Probe,
        //   Humpback Whales, Melkotian Adjudicator, Starfleet Spanner Jockey x3, Suus Mahna
        //   Practitioner.
        // B (7): Automated Warfare System, Battle-Scarred Survivalist, Collective Drone,
        //   Commander Raffi Musiker, Kai Winn, Khan, Tal Shiar Operative. (U.S.S. Reliant is a
        //   Spacecraft, not a creature.)
        // R (1): Cantankerous Captain.
        // G (6): Dominion Supervisor, Dr. Joseph M'Benga, Moopsy, Saru, Species 8472,
        //   Xenobotanist.
        // Multicolor: Hemmer (WU), Locutus x2 (WB), Lore (UB), Gray and Adira (UR), Tatyova (UG),
        //   L'Rell (BR), Cleveland Booker and Dal R'El (RG). Every multicolor card is a creature.
        // WU 2+4+12+1 = 19, WB 2+4+7+2 = 15, WR 2+4+1 = 7, WG 2+4+6 = 12, UB 2+12+7+1 = 22,
        // UR 2+12+1+1 = 16, UG 2+12+6+1 = 21, BR 2+7+1+1 = 11, BG 2+7+6 = 15, RG 2+1+6+2 = 11.
        @Test
        fun `creatures per pair`() {
            assertEquals(19, m.creatures(ColorPair.WU), "WU")
            assertEquals(15, m.creatures(ColorPair.WB), "WB")
            assertEquals(7, m.creatures(ColorPair.WR), "WR")
            assertEquals(12, m.creatures(ColorPair.WG), "WG")
            assertEquals(22, m.creatures(ColorPair.UB), "UB")
            assertEquals(16, m.creatures(ColorPair.UR), "UR")
            assertEquals(21, m.creatures(ColorPair.UG), "UG")
            assertEquals(11, m.creatures(ColorPair.BR), "BR")
            assertEquals(15, m.creatures(ColorPair.BG), "BG")
            assertEquals(11, m.creatures(ColorPair.RG), "RG")
        }

        // Cards per pair: colorless 7 + both mono lists + that pair's multicolor cards.
        // WU 7+7+18+1 = 33, WB 7+7+10+2 = 26, WR 7+7+7 = 21, WG 7+7+13 = 27, UB 7+18+10+1 = 36,
        // UR 7+18+7+1 = 33, UG 7+18+13+1 = 39, BR 7+10+7+1 = 25, BG 7+10+13 = 30,
        // RG 7+7+13+2 = 29. The two largest are UG (39) and UB (36).
        @Test
        fun `cards per pair`() {
            assertEquals(33, m.cards(ColorPair.WU), "WU")
            assertEquals(26, m.cards(ColorPair.WB), "WB")
            assertEquals(21, m.cards(ColorPair.WR), "WR")
            assertEquals(27, m.cards(ColorPair.WG), "WG")
            assertEquals(36, m.cards(ColorPair.UB), "UB")
            assertEquals(33, m.cards(ColorPair.UR), "UR")
            assertEquals(39, m.cards(ColorPair.UG), "UG")
            assertEquals(25, m.cards(ColorPair.BR), "BR")
            assertEquals(30, m.cards(ColorPair.BG), "BG")
            assertEquals(29, m.cards(ColorPair.RG), "RG")
        }

        // Pips. Mono U (19): Humpback Whales 2, Cetacean Ops 1x2, Cloistered Telepath 1x2,
        //   Guidance Failure 1x2, Starfleet Spanner Jockey 1x3, Suurok-Class Science Vessel 1x2,
        //   and 1 each for Anthropological Mission, Ensign Pavel Chekov, Enterprise NX-01,
        //   Federation Probe, Melkotian Adjudicator, Suus Mahna Practitioner.
        // Mono G (15): Dr. Joseph M'Benga 2, Species 8472 2, Final Frontier 1x2, and 1 each for
        //   Adversary by Algorithm, Common Goal, Dathon and Picard, Dominion Supervisor, First
        //   Contact, I'm a Doctor, Moopsy, Saru, Xenobotanist.
        // Mono B (12): Khan 2, U.S.S. Reliant 2, and 1 each for Automated Warfare System,
        //   Battle-Scarred Survivalist, Collective Drone, Commander Raffi Musiker, He's Dead, Jim,
        //   Impossible Choice, Kai Winn, Tal Shiar Operative.
        // UG: U 19 + Tatyova 1 = 20, G 15 + Tatyova 1 = 16. 17x20/36 = 9.44, 17x16/36 = 7.56;
        //   floors 9 and 7, the 17th land to G (0.56 > 0.44): U 9, G 8.
        // UB: U 19 + Lore 1 (two U/B halves) = 20, B 12 + Lore 1 = 13. 17x20/33 = 10.30,
        //   17x13/33 = 6.70; floors 10 and 6, the 17th land to B: U 10, B 7.
        @Test
        fun `land recommendation for the two largest pairs`() {
            assertEquals(9, m.lands(ColorPair.UG)[Color.U], "UG U")
            assertEquals(8, m.lands(ColorPair.UG)[Color.G], "UG G")
            assertEquals(10, m.lands(ColorPair.UB)[Color.U], "UB U")
            assertEquals(7, m.lands(ColorPair.UB)[Color.B], "UB B")
        }
    }

    /** `trk-text-01.txt`, 85 cards, one line per card name. */
    @Nested
    inner class Text01 {
        private val m = metrics("trk-text-01.txt", "text")

        // Mono W (17): Command Subadjunct, Dr. McCoy, Surly Surgeon, Emergency Medical Hologram,
        //   Federation Field Medic, Federation Resupply, Free Borg Revolutionaries, Ghost of
        //   Illyria, Hologram Janeway, Pet Sehlat x2, Pure Energy, Set Phasers to . . ., Shields
        //   Up! x2, Shuttle Ace, Sulu, Space Swashbuckler, Vexing Drone.
        // Mono U (11): Cetacean Ops, Cloistered Telepath, Emergency Holographic Crew, Federation
        //   Probe, Guidance Failure, Highly Illogical, Humpback Whales, Interstellar Effigy,
        //   Marooned, Melkotian Adjudicator, Zefram Cochrane, Warp Pioneer.
        // Mono B (13): A Good Day to Die, Assault Drone x2, Cha'DIch Investigator, D'Ghor
        //   Bloodletter, He's Dead, Jim, In the Pale Moonlight, Overflow Submatrix, Pattern Buffer
        //   Preservation, Resistance Is Futile, Stray from History, Tell Me What You See,
        //   Tenacious Drone.
        // Mono R (12): Boarding Action x2, Cantankerous Captain, Erica Ortegas, Plucky Pilot,
        //   Genesis Effect, Horta, La'An Noonien-Singh, Security, Plasma Cascade, Quark, Humble
        //   Proprietor, Red Alert!, Savvy Profiteer, Targ Wrangler.
        // Mono G (11): Amok Time, Astute Terraformer, Branching Timeline, Dathon and Picard at
        //   El-Adrel, Dominion Assault Force, Dominion Supervisor x2, Dr. Leonard "Bones" McCoy,
        //   First Contact, Neelix, Moral Support, Saurian Explorer.
        // Multicolor (1): Worf, Honor-Bound Warrior (WR, hybrid {R/W}{R/W}).
        // Colorless (7): Combadge, Crystalline Entity, Cybernetic Specialist, Isolinear
        //   Processor, Tea, Earl Grey, Hot, Type-2 Phaser, U.S.S. Cerritos, Following Up.
        // W = 17 + Worf = 18. U = 11. B = 13. R = 12 + Worf = 13. G = 11.
        // Basic lands (5): Forest, Mountain, Plains, Swamp x2.
        // Nonbasic lands (8): Andoria, Cardassia Prime, Overgrown Tomb (not Basic), Qo'noS,
        //   Romulus, Spatial Anomaly, Temporal Anomaly, Unexplored Territory.
        @Test
        fun `cards per color bucket and lands`() {
            assertEquals(18, m.cards(ColorBucket.W), "W")
            assertEquals(11, m.cards(ColorBucket.U), "U")
            assertEquals(13, m.cards(ColorBucket.B), "B")
            assertEquals(13, m.cards(ColorBucket.R), "R")
            assertEquals(11, m.cards(ColorBucket.G), "G")
            assertEquals(1, m.cards(ColorBucket.MULTI), "MULTI")
            assertEquals(7, m.cards(ColorBucket.COLORLESS), "COLORLESS")
            assertEquals(5, m.basicLands, "basic lands")
            assertEquals(8, m.nonbasicLands, "nonbasic lands")
            assertEquals(72, m.nonlandCards, "nonland cards")
        }

        // Removal (6): He's Dead, Jim (destroy target creature); Resistance Is Futile (destroy
        // target nonartifact creature); Amok Time (target creature you control fights up to one
        // target creature); Genesis Effect (6 damage to target creature); Plasma Cascade (3
        // damage to any target); Set Phasers to . . . (3 damage to target attacking creature).
        // Not removal: Crystalline Entity (destroy all), D'Ghor Bloodletter (-1/-1 to all, or
        // each player sacrifices), In the Pale Moonlight (each opponent sacrifices), Interstellar
        // Effigy (bounce), Highly Illogical (counter), Guidance Failure, Vexing Drone and
        // Type-2 Phaser (tap), Ghost of Illyria (exiles your own permanent), Dominion Assault
        // Force (exiles a card from a graveyard), Spatial Anomaly (a land).
        @Test
        fun `removal total`() {
            assertEquals(6, m.removalTotal)
        }

        // Creatures. Colorless (2): Crystalline Entity, Cybernetic Specialist.
        // W (12): Command Subadjunct, Dr. McCoy, Surly Surgeon, Emergency Medical Hologram,
        //   Federation Field Medic, Free Borg Revolutionaries, Ghost of Illyria, Hologram
        //   Janeway, Pet Sehlat x2, Shuttle Ace, Sulu, Vexing Drone.
        // U (8): Cetacean Ops, Cloistered Telepath, Emergency Holographic Crew, Federation Probe,
        //   Humpback Whales, Interstellar Effigy, Melkotian Adjudicator, Zefram Cochrane.
        // B (6): Assault Drone x2, Cha'DIch Investigator, D'Ghor Bloodletter, Overflow
        //   Submatrix, Tenacious Drone.
        // R (7): Cantankerous Captain, Erica Ortegas, Horta, La'An Noonien-Singh, Quark, Savvy
        //   Profiteer, Targ Wrangler.
        // G (7): Astute Terraformer, Dominion Assault Force, Dominion Supervisor x2, Dr. Leonard
        //   "Bones" McCoy, Neelix, Saurian Explorer.
        // Multicolor: Worf (WR).
        // WU 2+12+8 = 22, WB 2+12+6 = 20, WR 2+12+7+1 = 22, WG 2+12+7 = 21, UB 2+8+6 = 16,
        // UR 2+8+7 = 17, UG 2+8+7 = 17, BR 2+6+7 = 15, BG 2+6+7 = 15, RG 2+7+7 = 16.
        @Test
        fun `creatures per pair`() {
            assertEquals(22, m.creatures(ColorPair.WU), "WU")
            assertEquals(20, m.creatures(ColorPair.WB), "WB")
            assertEquals(22, m.creatures(ColorPair.WR), "WR")
            assertEquals(21, m.creatures(ColorPair.WG), "WG")
            assertEquals(16, m.creatures(ColorPair.UB), "UB")
            assertEquals(17, m.creatures(ColorPair.UR), "UR")
            assertEquals(17, m.creatures(ColorPair.UG), "UG")
            assertEquals(15, m.creatures(ColorPair.BR), "BR")
            assertEquals(15, m.creatures(ColorPair.BG), "BG")
            assertEquals(16, m.creatures(ColorPair.RG), "RG")
        }

        // Cards per pair: colorless 7 + both mono lists + that pair's multicolor cards.
        // WU 7+17+11 = 35, WB 7+17+13 = 37, WR 7+17+12+1 = 37, WG 7+17+11 = 35, UB 7+11+13 = 31,
        // UR 7+11+12 = 30, UG 7+11+11 = 29, BR 7+13+12 = 32, BG 7+13+11 = 31, RG 7+12+11 = 30.
        // The two largest are WB and WR (37 each); the next is 35.
        @Test
        fun `cards per pair`() {
            assertEquals(35, m.cards(ColorPair.WU), "WU")
            assertEquals(37, m.cards(ColorPair.WB), "WB")
            assertEquals(37, m.cards(ColorPair.WR), "WR")
            assertEquals(35, m.cards(ColorPair.WG), "WG")
            assertEquals(31, m.cards(ColorPair.UB), "UB")
            assertEquals(30, m.cards(ColorPair.UR), "UR")
            assertEquals(29, m.cards(ColorPair.UG), "UG")
            assertEquals(32, m.cards(ColorPair.BR), "BR")
            assertEquals(31, m.cards(ColorPair.BG), "BG")
            assertEquals(30, m.cards(ColorPair.RG), "RG")
        }

        // Pips. Mono W (19): Dr. McCoy, Surly Surgeon 2, Free Borg Revolutionaries 2, Pet Sehlat
        //   1x2, Shields Up! 1x2, and 1 each for Command Subadjunct, Emergency Medical Hologram,
        //   Federation Field Medic, Federation Resupply, Ghost of Illyria, Hologram Janeway, Pure
        //   Energy, Set Phasers to . . ., Shuttle Ace, Sulu, Vexing Drone.
        // Mono B (14): Assault Drone 1x2, In the Pale Moonlight 2, and 1 each for A Good Day to
        //   Die, Cha'DIch Investigator, D'Ghor Bloodletter, He's Dead, Jim, Overflow Submatrix,
        //   Pattern Buffer Preservation, Resistance Is Futile, Stray from History, Tell Me What
        //   You See, Tenacious Drone.
        // Mono R (14): Boarding Action 1x2, Horta 2, La'An Noonien-Singh 2, and 1 each for
        //   Cantankerous Captain, Erica Ortegas, Genesis Effect, Plasma Cascade, Quark, Red
        //   Alert!, Savvy Profiteer, Targ Wrangler.
        // WB: W 19, B 14. 17x19/33 = 9.79, 17x14/33 = 7.21; floors 9 and 7, the 17th land to W:
        //   W 10, B 7.
        // WR: W 19 + Worf 1 (two R/W halves) = 20, R 14 + Worf 1 = 15. 17x20/35 = 9.71,
        //   17x15/35 = 7.29; floors 9 and 7, the 17th land to W: W 10, R 7.
        @Test
        fun `land recommendation for the two largest pairs`() {
            assertEquals(10, m.lands(ColorPair.WB)[Color.W], "WB W")
            assertEquals(7, m.lands(ColorPair.WB)[Color.B], "WB B")
            assertEquals(10, m.lands(ColorPair.WR)[Color.W], "WR W")
            assertEquals(7, m.lands(ColorPair.WR)[Color.R], "WR R")
        }
    }

    /** `trk-collector-01.txt`, 85 cards as `TRK <collector number>`; numbers are in brackets. */
    @Nested
    inner class Collector01 {
        private val m = metrics("trk-collector-01.txt", "collector")

        // Mono W (14): Andromedan Automatons [2], Command Decision [8], Ghost of Illyria [20] x3,
        //   Hologram Janeway [21], Pure Energy [31], Rutherford, Upgraded Ensign [32], Saavik,
        //   Stoic Student [33] x2, Shields Up! [35] x2, U.S.S. Excelsior, Cutting Edge [44], Una
        //   Chin-Riley, Number One [42].
        // Mono U (16): Cloak and Phaser [49] x2, Consider the Prime Directive [51], Emergency
        //   Holographic Crew [55], Federation Probe [59], Guidance Failure [60], Humpback Whales
        //   [65], Interstellar Effigy [66] x2, Marooned [72], Nyota Uhura, Communications [75],
        //   Open Communications [76] x2, Starfleet Spanner Jockey [81], Suus Mahna Practitioner
        //   [84] x2.
        // Mono B (10): Automated Warfare System [93], Battle-Scarred Survivalist [95], Cha'DIch
        //   Investigator [97] x2, Fugitive Listener [107] x2, Relentless Drednok [124], Salt
        //   Vampire [127], Tenacious Drone [133], U.S.S. Reliant, From Hell's Heart [135].
        // Mono R (13): Dahar Master [146] x2, Exploratory Mission [150], The Naked Time [159],
        //   Perils of the Past [161], Planetary Patrol [163], Q, Stacking the Deck [165], Reckless
        //   Impulse [168] x2, Savvy Profiteer [171], Starfleet Security [173], Test of Identity
        //   [177], Transporter Malfunction [178].
        // Mono G (10): Astute Terraformer [184], Culber and Stamets, Star-Crossed [191], Dathon
        //   and Picard at El-Adrel [192], Dominion Assault Force [194], I'm a Doctor, Not a . . .
        //   [203], Moopsy [207] x2, Photosynthetic Physician [210], Temporal Conqueror [219],
        //   Xindi Surveyors [225].
        // Multicolor (6): Daniels, Temporal Agent [232] x2 (BG, hybrid); Lore, Positronic
        //   Provocateur [246] (UB, hybrid); Mirror Universe Intrusion [248] (UB); Spock, Always
        //   Your Friend [253] (UR); Year of Hell [257] (UR).
        // Colorless (6): Cybernetic Specialist [262], Engineered Explosives [264], Sickbay [268],
        //   Tea, Earl Grey, Hot [269], U.S.S. Cerritos, Following Up [272], Warp Core [274].
        // W = 14. U = 16 + Lore, Mirror Universe Intrusion, Spock, Year of Hell = 20.
        // B = 10 + Daniels x2, Lore, Mirror Universe Intrusion = 14. R = 13 + Spock, Year of
        // Hell = 15. G = 10 + Daniels x2 = 12.
        // Basic lands (6): Mountain [310] x4, Swamp [309], Forest [311].
        // Nonbasic lands (4): Bajor [277], Cardassia Prime [280], Hallowed Fountain [286] (not
        //   Basic), Risa [293].
        @Test
        fun `cards per color bucket and lands`() {
            assertEquals(14, m.cards(ColorBucket.W), "W")
            assertEquals(20, m.cards(ColorBucket.U), "U")
            assertEquals(14, m.cards(ColorBucket.B), "B")
            assertEquals(15, m.cards(ColorBucket.R), "R")
            assertEquals(12, m.cards(ColorBucket.G), "G")
            assertEquals(6, m.cards(ColorBucket.MULTI), "MULTI")
            assertEquals(6, m.cards(ColorBucket.COLORLESS), "COLORLESS")
            assertEquals(6, m.basicLands, "basic lands")
            assertEquals(4, m.nonbasicLands, "nonbasic lands")
            assertEquals(75, m.nonlandCards, "nonland cards")
        }

        // Removal (7): Dahar Master x2 (2 damage to any target); Transporter Malfunction (3
        // damage to any target); Command Decision (destroy target creature with power 4 or
        // greater); Saavik x2 (X damage to target attacking or blocking creature an opponent
        // controls); U.S.S. Excelsior (destroy target tapped creature an opponent controls).
        // Not removal: Engineered Explosives (destroy each), Perils of the Past (destroy target
        // artifact), Year of Hell (damage to each player), Tea, Earl Grey, Hot (damage to an
        // opponent), Interstellar Effigy (bounce), Consider the Prime Directive (counter, or tap
        // and stun), Guidance Failure, Ghost of Illyria (exiles your own permanent), Salt Vampire
        // (exiles a card from a hand), The Naked Time (you sacrifice a land).
        @Test
        fun `removal total`() {
            assertEquals(7, m.removalTotal)
        }

        // Creatures. Colorless (1): Cybernetic Specialist.
        // W (9): Andromedan Automatons, Ghost of Illyria x3, Hologram Janeway, Rutherford, Saavik
        //   x2, Una Chin-Riley.
        // U (9): Emergency Holographic Crew, Federation Probe, Humpback Whales, Interstellar
        //   Effigy x2, Nyota Uhura, Starfleet Spanner Jockey, Suus Mahna Practitioner x2.
        // B (9): Automated Warfare System, Battle-Scarred Survivalist, Cha'DIch Investigator x2,
        //   Fugitive Listener x2, Relentless Drednok, Salt Vampire, Tenacious Drone.
        // R (6): Dahar Master x2, Planetary Patrol, Q, Stacking the Deck, Savvy Profiteer,
        //   Starfleet Security.
        // G (8): Astute Terraformer, Culber and Stamets, Dominion Assault Force, Moopsy x2,
        //   Photosynthetic Physician, Temporal Conqueror, Xindi Surveyors.
        // Multicolor creatures: Daniels x2 (BG), Lore (UB), Spock (UR). Mirror Universe Intrusion
        //   is a sorcery and Year of Hell a Saga.
        // WU 1+9+9 = 19, WB 1+9+9 = 19, WR 1+9+6 = 16, WG 1+9+8 = 18, UB 1+9+9+1 = 20,
        // UR 1+9+6+1 = 17, UG 1+9+8 = 18, BR 1+9+6 = 16, BG 1+9+8+2 = 20, RG 1+6+8 = 15.
        @Test
        fun `creatures per pair`() {
            assertEquals(19, m.creatures(ColorPair.WU), "WU")
            assertEquals(19, m.creatures(ColorPair.WB), "WB")
            assertEquals(16, m.creatures(ColorPair.WR), "WR")
            assertEquals(18, m.creatures(ColorPair.WG), "WG")
            assertEquals(20, m.creatures(ColorPair.UB), "UB")
            assertEquals(17, m.creatures(ColorPair.UR), "UR")
            assertEquals(18, m.creatures(ColorPair.UG), "UG")
            assertEquals(16, m.creatures(ColorPair.BR), "BR")
            assertEquals(20, m.creatures(ColorPair.BG), "BG")
            assertEquals(15, m.creatures(ColorPair.RG), "RG")
        }

        // Cards per pair: colorless 6 + both mono lists + that pair's multicolor cards.
        // WU 6+14+16 = 36, WB 6+14+10 = 30, WR 6+14+13 = 33, WG 6+14+10 = 30, UB 6+16+10+2 = 34,
        // UR 6+16+13+2 = 37, UG 6+16+10 = 32, BR 6+10+13 = 29, BG 6+10+10+2 = 28,
        // RG 6+13+10 = 29. The two largest are UR (37) and WU (36).
        @Test
        fun `cards per pair`() {
            assertEquals(36, m.cards(ColorPair.WU), "WU")
            assertEquals(30, m.cards(ColorPair.WB), "WB")
            assertEquals(33, m.cards(ColorPair.WR), "WR")
            assertEquals(30, m.cards(ColorPair.WG), "WG")
            assertEquals(34, m.cards(ColorPair.UB), "UB")
            assertEquals(37, m.cards(ColorPair.UR), "UR")
            assertEquals(32, m.cards(ColorPair.UG), "UG")
            assertEquals(29, m.cards(ColorPair.BR), "BR")
            assertEquals(28, m.cards(ColorPair.BG), "BG")
            assertEquals(29, m.cards(ColorPair.RG), "RG")
        }

        // Pips. Mono U (17): Cloak and Phaser 1x2, Humpback Whales 2, Interstellar Effigy 1x2,
        //   Open Communications 1x2, Suus Mahna Practitioner 1x2, and 1 each for Consider the
        //   Prime Directive, Emergency Holographic Crew, Federation Probe, Guidance Failure,
        //   Marooned, Nyota Uhura, Starfleet Spanner Jockey.
        // Mono R (15): Dahar Master 1x2, Exploratory Mission 2, Q, Stacking the Deck 2, Reckless
        //   Impulse 1x2, and 1 each for Perils of the Past, Planetary Patrol, Savvy Profiteer,
        //   Starfleet Security, Test of Identity, The Naked Time, Transporter Malfunction.
        // Mono W (14): Ghost of Illyria 1x3, Saavik 1x2, Shields Up! 1x2, and 1 each for
        //   Andromedan Automatons, Command Decision, Hologram Janeway, Pure Energy, Rutherford,
        //   U.S.S. Excelsior, Una Chin-Riley.
        // UR: U 17 + Spock 1 + Year of Hell 1 = 19, R 15 + 1 + 1 = 17. 17x19/36 = 8.97,
        //   17x17/36 = 8.03; floors 8 and 8, the 17th land to U: U 9, R 8.
        // WU: W 14, U 17. 17x14/31 = 7.68, 17x17/31 = 9.32; floors 7 and 9, the 17th land to W:
        //   W 8, U 9.
        @Test
        fun `land recommendation for the two largest pairs`() {
            assertEquals(9, m.lands(ColorPair.UR)[Color.U], "UR U")
            assertEquals(8, m.lands(ColorPair.UR)[Color.R], "UR R")
            assertEquals(8, m.lands(ColorPair.WU)[Color.W], "WU W")
            assertEquals(9, m.lands(ColorPair.WU)[Color.U], "WU U")
        }
    }
}
