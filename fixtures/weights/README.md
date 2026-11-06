# Pool adjustment weights

`spa pool-grades` reads its weights from `default.json` here unless `--weights <file>` names another file. A weights file is one JSON object of numbers, each finite and 0 or more: `cap`, and one key per card part (`CardParts.ALL` in `spa.agent.poolgrade`):

| Key | Default | Meaning |
| --- | --- | --- |
| `synergy` | 0.5 | The most the synergy part moves a grade, up or down. |
| `curve` | 0.25 | The most the curve part moves a grade. |
| `colour` | 0.25 | The most the colour depth part moves a grade. |
| `tagSynergy` | 0 | The most the tag synergy part (SPA-048, through SPA-043) moves a grade. The tag file is read only when this is above 0. |
| `cap` | 1.0 | The most the parts together move a grade (G-004 Decided 6). Required. |

A part missing from the file has weight 0, which switches it off. The defaults are G-004's starting weights (SPA-038), untuned; `default.json` has no `tagSynergy`, so it is 0. The weights tuned on `msh` and `hob` in SPA-039 are `tuned.json`, below. An unknown key, a missing `cap`, a negative number or a value that is not a number makes the command print one line, naming the key, and exit 1.

## `tag-synergy.json`

`spa synergy` reads the tag synergy part's weight from `tag-synergy.json` here unless `--weights <file>` names another file: `{ "weight": 0.5 }`, one JSON object with exactly the number `weight`, 0 or more. It is the most the part moves a card, up or down; weight 0 switches the part off. 0.5 is SPA-048's starting weight, untuned (SPA-046 tunes it on `msh` and `hob`). Any other key, a missing key, a negative number or a value that is not a number makes the command print one line and exit 1.

## `tuned.json`

The grid winner of SPA-039's tuning, frozen: `{ "synergy": 0.0, "curve": 0.25, "colour": 0.5, "cap": 1.0 }`, in the same four-key format as `default.json`, so `spa pool-grades --weights fixtures/weights/tuned.json` and `spa deck-pick --weights` both read it. `deck-pick --tune` writes `synergy`, `curve`, `colour` and `cap`, with `tagSynergy` before `cap` only when it is above 0; its grid keeps `tagSynergy` at 0.

It was written by `spa deck-pick MSH.Sealed.csv.gz HOB.Sealed.csv.gz --set msh,hob --tune --out fixtures/weights/tuned.json` on 17Lands' public Sealed game data (`game_data_public.<SET>.Sealed.csv.gz`, not committed) with the v2 grade files and the committed baselines:

- Grid: synergy {0, 0.25, 0.5, 0.75, 1.0}, curve {0, 0.25, 0.5}, colour {0, 0.25, 0.5}, cap 1.0: 45 combinations.
- Sets: `msh` (2,815 builds) and `hob` (4,697 builds), 7,512 builds pooled. `fra` is held out and `trk` was not used.
- Pick: the highest mean adjusted overlap over the pooled builds, ties to the smaller total weight. The winner's pooled overlap is 0.7583, against 0.7519 with every weight 0 (set grades alone) and 0.7514 with the default weights.

## `deck-default.json`

`spa build` reads the deck builder's weights from `deck-default.json` here unless `--weights <file>` names another file (SPA-044). It is versioned (`"version": 1`) and holds `weights`, `params`, `lands`, `splash` and `configs`. These are starting values, untuned; SPA-046 tunes them. Each rule is its own class in `spa.agent.build` (`DeckParts.ALL`), reads its weight by key, and a part with weight 0, or missing from `weights`, is off.

| Section | Key | Default | Meaning |
| --- | --- | --- | --- |
| `weights` | `grades` | 1.0 | Times the sum of the spells' set grades. |
| `weights` | `curve` | 1.0 | Minus this per spell short of a mana value target, 1 to 6 or more. |
| `weights` | `creatures` | 1.0 | Minus this per creature spell below `minCreatures`. |
| `weights` | `removal` | 1.0 | Per removal spell, up to `cap`. |
| `weights` | `castability` | 1.0 | Minus this times grade times the chance a spell is not castable on curve. |
| `weights` | `fixing` | 1.0 | Times `(k - 2)` times the share of the deck's `k` colours a fixing card supplies, summed over fixing cards (0 at two colours). |
| `weights` | `synergy` | 0.1 | Times the sum of every pair of spell copies' synergy score, over the spells. The tag file is read only when this is above 0. |
| `params.curve` | `target1` to `target6` | 1, 5, 4, 3, 2, 1 | The spells wanted at each mana value; `target6` is 6 or more. |
| `params.creatures` | `minCreatures` | 14 | The creature spells wanted. |
| `params.removal` | `cap` | 6 | The most removal spells that count. |
| `lands` | `lowCurve`, `highCurve` | 2.6, 3.4 | The mean mana value cutoffs. |
| `lands` | `low`, `normal`, `high` | 16, 17, 18 | Lands when the mean is at or below `lowCurve`; otherwise; and when the deck has three or more colours and the mean is at or above `highCurve` (G-005 Decided 3). |
| `splash` | `maxCards`, `maxPips` | 3, 1 | At most this many splash cards, each with at most this many pips of splash colours. |
| `configs.pairs` | `enabled` | true | The 10 two-colour pairs, no splash. |
| `configs.pairSplash` | `enabled`, `maxSplashColours` | true, 2 | Each pair with 1 splash colour and, when `maxSplashColours` is 2, with 2 (at most 2). |
| `configs.triples` | `enabled`, `tripleSplash` | true, true | The 10 three-colour sets, and with `tripleSplash` each also with 1 splash colour. |
| `configs.fourColour` | `enabled`, `minFixing` | true, 4 | A four-colour set, only when the pool holds at least `minFixing` copies of cards (lands or spells) that supply two or more of its colours. |

The `configs` section is optional: without it no configuration is tried, so `spa build` needs `--colours`. A rule missing from it is off; `enabled` defaults to true; every other key a rule names must be given. Adding a configuration rule adds its key here. SPA-046 tunes the starting values.

Adding a part adds its key to `weights` (and its numbers to `params` if it reads any). An unknown key anywhere, a missing number, a negative number, a count out of order or a wrong `version` makes the command print one line naming it and exit 1.

## `deck-baseline.json`

`spa deck-backtest` measures a baseline builder with `deck-baseline.json` here unless `--baseline <file>` names another deck weights file (SPA-046). It is in the format of `deck-default.json`: set grades alone and two-colour pairs only. Its `grades` weight is 1 and every other weight is 0, so a deck scores the sum of its spells' set grades; its `configs` enable `pairs` and switch `pairSplash`, `triples` and `fourColour` off (their numbers are given, as the format asks), so every ranked deck is a pair with no splash. The `lands` and `splash` rules are `deck-default.json`'s: they decide the land count and do not enter the score.

## `deck-grid.json`

`spa deck-tune` searches the grid in `deck-grid.json` here unless `--grid <file>` names another (SPA-060). It is one JSON object of knobs. A key is a path into the deck weights JSON (`weights.curve`, `params.creatures.minCreatures`, `configs.fourColour.minFixing`), and its value is the list of numbers to try there, in the order tried:

| Path | Values |
| --- | --- |
| `weights.curve`, `weights.creatures`, `weights.removal`, `weights.castability`, `weights.fixing` | 0, 0.5, 1, 2 |
| `weights.synergy` | 0, 0.05, 0.1, 0.2, 0.4 |
| `params.creatures.minCreatures` | 12, 13, 14, 15 |
| `params.removal.cap` | 4, 6, 8 |
| `lands.lowCurve` | 2.4, 2.6, 2.8 |
| `lands.highCurve` | 3.2, 3.4, 3.6 |
| `splash.maxCards` | 2, 3, 4 |
| `splash.maxPips` | 1, 2 |
| `configs.fourColour.minFixing` | 2, 4, 6 |

`weights.grades` stays 1, since it sets the scale. These are starting choices: the grid may be revised after the first run, as G-004's was. Before any run the tuner checks that the start file (`--start`, default `deck-default.json`) parses as deck weights and that every value, put at its path in the start file, still parses. A path whose first part is not `weights`, `params`, `lands`, `splash` or `configs`, a path or value that does not parse, an empty list, or a value that is not a number makes the command print one line naming the knob and exit 1. Only numbers can be tuned; the `enabled` and `tripleSplash` switches of `configs` are not.

The tuner's own output, `deck-tuned.json` (written by the Architect's run on `msh` and `hob`, not by SPA-060), is in the format of `deck-default.json`, so `spa build --weights` and `spa deck-backtest --weights` read it.

## `deck-tuned.json`

The deck weights `spa deck-tune` found on `msh` and `hob` (SPA-060), frozen on 2026-11-06: `weights.curve` 0 (was 1), `weights.creatures` 2 (was 1), `params.creatures.minCreatures` 15 (was 14), `lands.lowCurve` 2.8 (was 2.6); every other value is `deck-default.json`'s. `fra` and `trk` were not used.

It was written by `spa deck-tune MSH.Sealed.csv.gz HOB.Sealed.csv.gz --set msh,hob --limit 60 --threads 3 --rounds 2` on 17Lands' public Sealed data (not committed) with `fixtures/weights/deck-grid.json`:

- Objective (mean over the two sets of mean main-deck overlap share plus colour agreement): 0.796 at the start, 0.851 at the end, after 67 evaluations in 9,226 s.
- On the 60 builds per set it tuned on, the builder agrees with the player's main colours in 3.3% of `msh` builds and 33.3% of `hob` builds, has the player's colours in its top three in 16.7% and 51.7%, and its deck shares 15.69 and 15.32 of the player's 23.22 nonland cards on average. These are well short of G-005's targets (colour agreement 50%, top three 80%, overlap 16). Agreement with strong players is a sanity check, not a measure of how good a deck is (owner, 2026-11-06).
- The held-out `fra` measurement was dropped by the owner, so the weights have no measurement outside the two tuning sets.
