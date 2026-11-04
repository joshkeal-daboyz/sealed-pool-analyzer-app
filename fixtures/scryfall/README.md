# Scryfall caches

Card data from [Scryfall](https://scryfall.com), cached so tests and the app never call the network. Each set lives in `<code>/`:

| File | What it is |
| --- | --- |
| `set.json` | The set object from `GET /sets/<code>`, verbatim |
| `cards.json` | Every card object from the set's `search_uri`, verbatim, in search order, one per line |
| `fetched-at.txt` | When the cache was fetched, ISO-8601 UTC |
| `companions.json` | Written by hand: the codes of sets that share this set's sealed pool |

## Sets

| Set | What it is | Cards | Fetched |
| --- | --- | --- | --- |
| `trk` | Star Trek, the target set. Companions: `sds` | 581 | 2026-11-03T18:19:53Z |
| `sds` | Stardates, `trk`'s masterpiece companion | 30 | 2026-11-03T18:20:00Z |
| `syn` | Synthetic test set, built by hand (below) | 25 | 2026-11-03 (source cards) |
| `msh` | Marvel Super Heroes, a G-003 backtest set. No companions | 453 | 2026-11-04T22:00:01Z |
| `hob` | The Hobbit, a G-003 backtest set. No companions | 321 | 2026-11-04T22:00:04Z |
| `fra` | Reality Fracture, a G-003 backtest set. No companions. `cards.json` holds 462 cards against `card_count` 461: Scryfall's count leaves out the variant `101b` (Compel Brutality) | 462 | 2026-11-04T22:00:08Z |

`trc`, `ttrk`, and `atrk` are deliberately not cached: they are not part of the `trk` sealed pool.

The backtest sets' child sets on Scryfall are not cached either: `msc`, `tmsh`, `amsh` (commander, tokens, art series); `hoc`, `thob`, `ahob` (`hoc` is set type `eternal`, new cards for eternal formats, 153 of 158 not Standard-legal, with commons and uncommons, so not a Play Booster bonus sheet); `frc`, `pfra`, `tfra`, `afra`, `yfra` (commander, promo-pack stamped promos, tokens, art series, Alchemy). None is a masterpiece or bonus sheet like `sds`.

## Refreshing

```bash
./gradlew :core:fetchSet -Pset=<code>
```

This rewrites `set.json`, `cards.json`, and `fetched-at.txt` under `fixtures/scryfall/<code>/` and leaves `companions.json` alone. `-PdataDir=<dir>` writes somewhere else instead, for a dry run. The cache is a snapshot: refreshing it changes card data that tests and golden files depend on, so it is done only by a ticket that says so, never as a side effect of other work. Update the fetch date in the table above when you do.

## The synthetic set `syn`

`trk` has no double-faced, adventure, or split cards, so layout tests use `syn`: 25 real Scryfall card objects from other sets, fetched individually on 2026-11-03. Only three fields were rewritten, as raw text: `set` to `syn`, `set_name` to `Synthetic Test Set`, and `collector_number` to 1 to 25 in file order. Every other field, `id` and `oracle_id` included, is Scryfall's. `set.json` is written by hand with `card_count` 25 and no `search_uri`, so `fetchSet` cannot refresh `syn`.

| # | Card | Layout |
| --- | --- | --- |
| 1 to 10 | Grizzly Bears, Serra Angel, Air Elemental, Counterspell, Doom Blade, Gravedigger, Llanowar Elves, Giant Growth, Kitchen Finks, Lightning Helix | normal |
| 11, 12 | Delver of Secrets, Huntmaster of the Fells | transform |
| 13 | Agadeem's Awakening | modal_dfc |
| 14, 15 | Bonecrusher Giant, Murderous Rider | adventure |
| 16, 17 | Fire // Ice, Wear // Tear | split |
| 18 | Mind Stone | normal (colorless artifact) |
| 19, 20 | Evolving Wilds, Temple of Mystery | normal (nonbasic lands) |
| 21 to 25 | Plains, Island, Swamp, Mountain, Forest | normal (basic lands) |
