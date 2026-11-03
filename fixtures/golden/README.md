# Golden files

One file per pool in `fixtures/pools/manifest.json`, named `<pool file>.json` (for example `trk-manabox-01.csv.json`). Each holds the full output of

```bash
spa analyze fixtures/pools/<pool file> --set <set> --format <format> --data fixtures
```

with the set and format the manifest declares, minus the two fields that change between machines and builds: `tool.version` and `pool.file`. Everything else is pinned byte for byte (after turning CRLF into LF): the parse report and kit warnings, every metric the engine computes, and the set's baseline.

`cli/src/test/kotlin/spa/cli/GoldenTest.kt` compares the current output with these files. A missing or different file fails the test, and the failure names the command below. The test also fails if a file here has no manifest entry.

## When they change

The goldens are a function of everything `analyze` reads and computes, so they change whenever any of these change:

- the Scryfall cache under `fixtures/scryfall/`
- a format brief under `fixtures/briefs/`
- a baseline under `fixtures/baselines/`
- the pool fixtures or `manifest.json`
- the parsers, kit validator, metric engine, baseline, or output code

## Updating them

```bash
./gradlew :cli:test -PupdateGoldens=true
```

This rewrites every golden file from the current output, and the test then passes. Without the property nothing is written. Run it twice and the tree does not change.

Only a ticket that says so updates the goldens, and its change note says which files changed and why. A golden diff in any other ticket is a failure to explain, not a file to regenerate.
