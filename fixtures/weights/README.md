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
