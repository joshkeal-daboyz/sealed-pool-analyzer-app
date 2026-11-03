# Set baselines

A baseline gives the typical value of every pool metric for a pool from one set. It is computed by sampling many prerelease kits from the set's kit template (`kitTemplate` in `fixtures/briefs/<set>.json`) over the committed Scryfall cache, running the metric engine on each kit, and summarising every number. It reads the brief only through the kit sampler, never for archetypes or card names.

| File | Set | Seed | Kits |
| --- | --- | --- | --- |
| `trk.json` | trk (with its bonus sheet, sds) | 20261106 | 2000 |
| `syn.json` | syn (synthetic test set) | 1 | 500 |

Kit `i`, counted from 0, uses seed `seed + i`.

## Rebuilding

```bash
./gradlew :core:buildBaseline -Pset=trk -Pseed=20261106 -Pkits=2000
./gradlew :core:buildBaseline -Pset=syn -Pseed=1 -Pkits=500
```

Only a ticket rebuilds a baseline. Refreshing the Scryfall cache, changing the brief's kit template, or changing the sampler or the metric engine all change it. A golden test (`BaselineGoldenTest`) rebuilds each file from the seed and kit count stored inside it and fails on any byte of difference. So any of those changes must regenerate the files in the same ticket, and say so in its change note.

## Fields

- `setCode`: the set the kits were sampled from.
- `briefVersion`: the `version` of the brief whose kit template was sampled.
- `cacheFetchedAt`: the latest `fetched-at.txt` among the Scryfall caches loaded for the set (for trk, the later of trk and sds).
- `seed`, `kits`: the first kit's seed and how many kits were sampled.
- `stats`: one entry per metric, keyed by its JSON pointer into the metrics JSON. Each entry has these values, all over the sampled kits and rounded to 4 decimals:
  - `median`, `p10`, `p90`: the 50th, 10th, and 90th percentiles, by nearest rank.
  - `mean`: the average.

## Pointers

Each pointer is a numeric leaf of the pool metrics JSON, for example `/totalCards`, `/byColor/R/cards/common`, or `/pairs/WU/curve/buckets/2/creatures`. A list that holds names is not kept item by item. Only its length is kept, under `<pointer>/count`: `/fixingSources/count` and `/pairs/<pair>/splashCandidates/count`. If a kit has no value for a pointer, it counts as 0 there. For example, a kit with no pips of a color has no land recommendation for it.

## Notes

- A trk pool's typical rare and mythic counts include bonus-sheet (sds) cards, because those count by their own printed rarity, which is mythic.
- `basicLandsSupplied` means basics are also handed out at the event. It does not change the basics sampled inside boosters. In trk, only the Land slot can yield a basic, at 4 in 5 per booster, so `/basicLands` follows Binomial(6, 4/5): median 5, mean 4.8.
- `/totalCards` is constant: 85 for trk, which is 6 boosters of 14 plus the promo.
