# Pool fixtures

Pools for tests, in the three formats the intake parsers read. **Every pool here is synthetic.** None was opened at a real event: each was sampled from a set's prerelease kit template (`kitTemplate` in `fixtures/briefs/<set>.json`) over that set's Scryfall cache, then written by `spa.core.sim.PoolWriters`.

## What is here

| Files | Set | Format | How it was made |
| --- | --- | --- | --- |
| `trk-manabox-01.csv` to `-10` | `trk` | ManaBox CSV | Sampled |
| `trk-text-01.txt` to `-05` | `trk` | Text list | Sampled |
| `trk-collector-01.txt` to `-05` | `trk` | Collector list | Sampled |
| `syn-text-01.txt`, `-02` | `syn` | Text list | Sampled; every second multi-face card is written by its front face only |
| `syn-collector-01.txt` | `syn` | Collector list | Sampled |
| `trk-text-misspelled-01.txt` | `trk` | Text list | Hand-edited copy of `trk-text-01.txt` with 3 misspelled names |
| `trk-manabox-wrong-set-01.csv` | `trk` | ManaBox CSV | Hand-edited copy of `trk-manabox-01.csv`: 2 rows carry set code `TRC` and made-up Scryfall ids |
| `trk-collector-bad-number-01.txt` | `trk` | Collector list | Hand-edited copy of `trk-collector-01.txt` with 2 collector numbers that do not exist |

`trk` kits use the `trk` template, whose bonus-sheet slot draws from `sds`, so some `trk` pools hold `sds` cards (`trk-manabox-01.csv` and `trk-collector-05.txt` do).

`manifest.json` lists every fixture with its `file`, `set`, `format`, `seed` (`null` for a hand-edited file), `expectedCards` (the total count of the parsed pool), and `expectedUnresolved` (each `{ line, reason }` that must not resolve). `FixturePoolsTest` parses every listed file and checks both, and fails if a file here is not in the manifest.

## The ManaBox header

The ManaBox files use the best-known export layout: `Name, Set code, Set name, Collector number, Foil, Rarity, Quantity, ManaBox ID, Scryfall ID, Purchase price, Misprint, Altered, Condition, Language, Purchase date, Purchase price currency`. The ManaBox IDs, prices, and purchase date are stand-ins. This layout is provisional until a real export from the 2026-11-06 event replaces it.

## Regenerating

```bash
./gradlew :core:generateFixtures
```

This rewrites every sampled file and `manifest.json` from the seeds in `FixtureGenerator.plan` (`core/src/main/kotlin/spa/core/sim/FixtureGeneratorMain.kt`), which the manifest also records. It is deterministic: running it twice leaves the tree unchanged. It keeps hand-edited files, and their manifest entries (the ones with a `null` seed), as they are.

Replacing fixtures or the manifest, by regenerating with a changed plan, template, or cache, or by editing a file by hand, is a ticket, not a side effect of other work. Tests depend on these exact files.
