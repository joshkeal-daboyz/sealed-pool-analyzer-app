# Win-rate grade tables

Each file is a grade file (`agent/src/main/resources/spa/agent/grades.schema.json`) with source `WIN_RATE`, written by `spa winrates` from a 17Lands public Premier Draft game data file. Play data from [17Lands](https://www.17lands.com), free for non-commercial use. The downloads are not committed.

A card's GIH WR is its win rate over the games where it was in the opening hand or drawn (`opening_hand_<name> + drawn_<name> >= 1`). Each card with at least 500 such games has a grade: `score = 2.5 + (gihwr - m) / s`, clamped to 0 to 5 and rounded to the nearest 0.5, with `m` and `s` the mean and population standard deviation of GIH WR over the set's matched cards with 500 or more games, and `confidence = games / (games + 2000)` to 3 places. Every entry keeps its `gihwr` (to 6 places) and `games`.

| File | Source file | Last-Modified | Games | Names in the data | Unmatched | Graded | m | s |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `msh.json` | `game_data_public.MSH.PremierDraft.csv.gz` | 2026-07-27 | 377,514 | 339 | 58 | 278 | 0.5620 | 0.0391 |
| `hob.json` | `game_data_public.HOB.PremierDraft.csv.gz` | 2026-10-01 | 323,114 | 193 | 0 | 184 | 0.5587 | 0.0379 |
| `fra.json` | `game_data_public.FRA.PremierDraft.csv.gz` | 2026-10-19 | 297,964 | 295 | 10 | 271 | 0.5593 | 0.0390 |

Unmatched names are cards in the 17Lands data that are not in the set's Scryfall cache (for `msh` and `fra`, reprints from a bonus sheet the cache does not hold). They are listed by the command and skipped, and are not part of `m` and `s`. A double-faced card appears in the data under its front-face name and is graded under its full name.

## Rebuilding

Download each file from `https://17lands-public.s3.amazonaws.com/analysis_data/game_data/game_data_public.<SET>.PremierDraft.csv.gz` to a folder outside the repository, note its `Last-Modified` header, and run from the repository root:

```bash
./gradlew :cli:installDist
cli/build/install/spa/bin/spa winrates <dir>/game_data_public.MSH.PremierDraft.csv.gz --set msh --dataset-date 2026-07-27
cli/build/install/spa/bin/spa winrates <dir>/game_data_public.HOB.PremierDraft.csv.gz --set hob --dataset-date 2026-10-01
cli/build/install/spa/bin/spa winrates <dir>/game_data_public.FRA.PremierDraft.csv.gz --set fra --dataset-date 2026-10-19
```

Only a ticket rebuilds a table. A newer download or a refreshed Scryfall cache changes it.
