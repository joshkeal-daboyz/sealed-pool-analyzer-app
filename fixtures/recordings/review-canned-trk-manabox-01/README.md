# Canned recordings: `review-canned-trk-manabox-01`

These eight files are the recording of a `spa review fixtures/pools/trk-manabox-01.csv --set trk`
(the seven analysts, `colour-depth` to `synergy`, then `explainer`). **They are canned, not a
model's output.** The answers were written by hand in the test support class `CannedClient`
(`cli/src/test/kotlin/spa/cli/ReviewTestSupport.kt`): each analyst's answer cites real cards of the
pool and keys of its own number table, with the table's values, and the Explainer's answer holds one
disagreement. The requests in the files are the real ones, built from the real prompts, pool bundle
and number tables; only the responses are canned. No file holds a key.

They exist so `--replay` can run end to end offline:

```bash
cli/build/install/spa/bin/spa review fixtures/pools/trk-manabox-01.csv --set trk --replay fixtures/recordings/review-canned-trk-manabox-01
```

A recording answers only a request that is equal to its own in every field, so a change to a
prompt file, the model or caps in `fixtures/agents/review.json`, the deck weights
`fixtures/weights/deck-tuned.json`, the grades, the baseline, the brief or the pool changes the
requests and makes `CannedRecordingsTest` fail. To regenerate the files, run

```bash
./gradlew :cli:test --tests spa.cli.CannedRecordingsTest -PupdateRecordings=true
```

(the Gradle property sets the system property `spa.updateRecordings` to `true`, and the test then
rewrites the files instead of comparing them), read the diff, and commit it.
