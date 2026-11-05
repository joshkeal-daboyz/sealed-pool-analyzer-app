# Pool adjustment weights

`spa pool-grades` reads its weights from `default.json` here unless `--weights <file>` names another file. A weights file is one JSON object with exactly these four numbers, each 0 or more:

| Key | Default | Meaning |
| --- | --- | --- |
| `synergy` | 0.5 | The most the synergy part moves a grade, up or down. |
| `curve` | 0.25 | The most the curve part moves a grade. |
| `colour` | 0.25 | The most the colour depth part moves a grade. |
| `cap` | 1.0 | The most the three parts together move a grade (G-004 Decided 6). |

The defaults are G-004's starting weights (SPA-038), untuned. The weights tuned on `msh` and `hob` in SPA-039 are `tuned.json`, below. Any other key, a missing key, a negative number or a value that is not a number makes the command print one line and exit 1.

## `tuned.json`

The grid winner of SPA-039's tuning, frozen: `{ "synergy": 0.0, "curve": 0.25, "colour": 0.5, "cap": 1.0 }`, in the same four-key format as `default.json`, so `spa pool-grades --weights fixtures/weights/tuned.json` and `spa deck-pick --weights` both read it.

It was written by `spa deck-pick MSH.Sealed.csv.gz HOB.Sealed.csv.gz --set msh,hob --tune --out fixtures/weights/tuned.json` on 17Lands' public Sealed game data (`game_data_public.<SET>.Sealed.csv.gz`, not committed) with the v2 grade files and the committed baselines:

- Grid: synergy {0, 0.25, 0.5, 0.75, 1.0}, curve {0, 0.25, 0.5}, colour {0, 0.25, 0.5}, cap 1.0: 45 combinations.
- Sets: `msh` (2,815 builds) and `hob` (4,697 builds), 7,512 builds pooled. `fra` is held out and `trk` was not used.
- Pick: the highest mean adjusted overlap over the pooled builds, ties to the smaller total weight. The winner's pooled overlap is 0.7583, against 0.7519 with every weight 0 (set grades alone) and 0.7514 with the default weights.
