# Format briefs

One JSON file per set, `<setCode>.json`, describing the set's limited format for people and for code. The shape is fixed by the schema at `core/src/main/resources/spa/core/brief/format-brief.schema.json` (JSON Schema draft 2020-12). Every object forbids unknown properties, so a misspelled field name is an error, not a silently ignored field.

`syn.json` is the complete example, for the synthetic test set. Its archetype names and card names are invented.

`msh.json` (Marvel Super Heroes) and `fra.json` (Reality Fracture) exist only for the G-003 grade backtest. They are blind: written from the sets' Scryfall caches alone, with no 17Lands data, reviews, or play results, so the Claude grader sees the same kind of brief it would see before a set is played. Neither set has a bonus sheet, so their kit template has seven common slots and no `bonusSheet` weight.

## Writing `trk.json`

1. Copy `syn.json` to `trk.json`.
2. Change these fields:
   - `setCode` to `trk`, and `setName` to the set's full name.
   - `version` stays `1` for the first version. Add 1 whenever you change the brief later.
   - `speed` to `fast`, `medium`, or `slow`, and `speedNotes` to a sentence or two on why: which turn games are usually decided, and what that means for curves.
   - `companionSetCodes` to the codes of any other sets whose cards appear in a `trk` sealed pool (for example a bonus sheet set), or leave it `[]`. Each code is 3 to 5 lowercase letters or digits.
   - `mechanics`: one entry per mechanic, with its `name`, its full `rulesText`, and its `reminderText` if it has one (leave `reminderText` out if not).
   - `archetypes`: exactly ten entries, one per color pair. `colors` is two letters from `WUBRG` in either order (`WU` and `UW` are the same pair; each pair appears once). Give each its `name`, a one- or two-sentence `plan`, the card names of its `signpostUncommons` (at least one), and, optionally, the `keyCommons` it wants.
   - `genericArchetypes`: any decks that are not one color pair, such as five-color or mono-color decks, each with a `name`, a `description`, and optionally `colors` (one to five distinct `WUBRG` letters). This list may be empty.
3. Keep the `kitTemplate` section as it is. It models a current prerelease kit: six 14-card Play Boosters plus one foil promo rare or mythic, with basic lands supplied at the event. Change it only if the set's kit differs.
4. Check it loads: `BriefLoader.load(Path.of("fixtures/briefs/trk.json"))` returns the brief, or throws `BriefValidationException` listing every problem with a JSON pointer to it, such as `/archetypes/7/colors`.

## The kit template

`slots` lists what fills one booster. Each slot has a `name`, a `count` of cards, `foil` (true for a traditional-foil slot; leave it out otherwise), and `weights`: the relative odds of each kind of card filling the slot. Weights need not sum to anything; `{ "rare": 6, "mythic": 1 }` means a mythic one time in seven. The kinds are `common`, `uncommon`, `rare`, `mythic`, `basicLand`, `commonLand` (a common nonbasic land), and `bonusSheet`, and every weight is greater than 0. The slot counts must sum to `cardsPerBooster`. `promo` is the kit's extra card, with the same `weights`, or `null` for a kit without one.

Card names in a brief are not checked against the set here; the `validate-brief` command (a later ticket) warns about names it cannot find.
