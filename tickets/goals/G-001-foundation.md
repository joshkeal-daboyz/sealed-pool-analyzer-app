---
id: G-001
title: Foundation phase of the Sealed Pool Analyzer
status: open
owner: human
target_set_code: trk
companion_set_codes: [sds]
---

# G-001: Foundation phase

Build the deterministic, LLM-free foundation of the Sealed Pool Analyzer as a pure-Kotlin core library tested on the JVM: card data for one set, a format brief schema, pool intake with ManaBox CSV as the primary format, and a metric engine that reports every number against a set baseline. This is steps 1 to 4 of the development pipeline in the design brief, and it has no user interface, so it exercises every pipeline role except the Designer.

## Why this goal first

It has no UI, so the gate, the Testing agents, and the Architect's ticket-writing are all exercised without an emulator. Its output is needed for the first real pools, collected at the prerelease on 2026-11-06 as ManaBox-format CSVs.

## Deliverables

1. **Toolchain and gate.** A Gradle build with a `core` module (Kotlin, JVM, no Android dependencies), the gate script in `pipeline/gate/`, a pre-push hook that runs it, and a CI workflow that runs the same script. Green on an empty module before anything else is dispatched.
2. **Card data.** A loader that fetches one set's cards from Scryfall by set code (the paginated search endpoint, cached locally as JSON), a normalized card model that handles double-faced, adventure, and split cards, and a `getSet(setCode)` query returning every card legal in that set's sealed pool. Scryfall attribution in the README.
3. **Format brief.** A JSON schema for the per-set format brief (mechanics with rules text, the ten designed two-color archetypes with signpost uncommons, the generic archetypes, the kit template, expected format speed) and a loader for it. A drafting script that fills what Scryfall data can fill is a later goal; for G-001 the human writes the first brief by hand.
4. **Pool intake.** Parsers for ManaBox CSV exports (resolving by Scryfall ID, exact), plain text with counts, and collector-number lists, all resolving against one set only, reporting unresolved lines rather than guessing, and validating the pool against the kit template with a warning, not an error.
5. **Metric engine.** `PoolMetrics` and ten `ColorPairMetrics`: cards and playables per color by rarity, mana curve per pair, creature counts, removal and interaction tags from rules-text patterns, fixing sources, splash candidates, hypergeometric land math, and a set baseline computed by simulating kits from the set's rarity and slot distribution, so every count is reported beside the typical value for a pool from this set.
6. **Command-line entry point.** `analyze <pool-file> --set <code>` prints the metrics as JSON. This is the black-box surface the Acceptance Tester uses until there is an app.

## Goal-level acceptance criteria

1. `./gradlew check` runs the whole gate green on a fresh clone, and the CI workflow shows the same result on the pull request.
2. `getSet(code)` for the target set returns a card count equal to the set's `card_count` reported by Scryfall, and unit tests cover a double-faced card, an adventure card, and a split card.
3. A hand-written format brief for the target set validates against the schema and loads.
4. Every pool in `fixtures/pools/` resolves with zero silent errors. The fixture set holds at least twenty pools, at least five of them ManaBox exports, and includes misspelled names and double-faced card names. Synthetic fixtures are acceptable until real ones arrive from the event.
5. Golden-file tests pin the metric engine's output for every fixture pool, and three fixtures have their key counts (cards per color, removal count, creature count per pair, land recommendation) checked by hand and recorded in the test.
6. The set baseline for the target set is reproducible: the simulation is seeded, and its output is pinned by a golden file.
7. `analyze` on any fixture pool exits 0 and prints JSON that parses.

## Constraints

- Kotlin only in `core/`. No Android dependencies there.
- No model calls anywhere in this goal.
- The development machine is Windows 11 with Python 3.12 and git, and **no JDK, Gradle, or Android SDK installed**. The toolchain ticket must either install what it needs (a JDK 17 or later and the Gradle wrapper) or escalate to the human to install them. Record what was installed in the ticket's change note.
- Scryfall's API asks for a `User-Agent` header and a modest request rate; the loader respects both and caches so tests never hit the network.
- `target_set_code` above is `TBD` until the human fills it in. The Architect's first action is to ask for it if it is still `TBD`.

## Out of scope for this goal

The card grading layer, any agent, the deck builder, the Android app, photo capture, and the 17Lands backtest. Those are later goals.

## Metrics to report when the goal closes

Gate green on first push, bounces per merged ticket by source, tokens per merged ticket by role, re-plans, escalations to human, lead time per ticket, and share of criteria proven by deterministic tests. Append them here as a short table.

## Architect notes (2026-11-03)

- `target_set_code` set to `trk` (Star Trek, expansion, releases 2026-11-13, 581 printings and 311 distinct cards on Scryfall on 2026-11-03). The human also named `sds` (Stardates, 30-card bonus sheet, `parent_set_code: trk`), so the sealed-pool card index for `trk` covers `trk` and `sds`. The other children of `trk` on Scryfall (`trc` Commander, `ttrk` tokens, `atrk` art series) are not in a sealed pool and are excluded.
- Scryfall has no double-faced, adventure, or split cards in `trk`. Criterion 2's layout tests and criterion 4's double-faced-card-name fixtures therefore use a small synthetic set `syn`, assembled from real Scryfall card objects of other sets (SPA-002).
- Scryfall's `booster` flag is false on every `trk` card until release, so sealed legality is not derived from it. Every printing of the set and its companion set is indexed; metrics count distinct cards by `oracle_id`.
- Decomposed into SPA-001 to SPA-010. The hand-written format brief `fixtures/briefs/trk.json` is a human deliverable, needed before SPA-007 and SPA-008 dispatch; SPA-003 provides the schema and a complete example to copy from.
- `serialize_with` follows shared files: SPA-002, SPA-003, SPA-007, and SPA-008 all edit `core/build.gradle.kts`; SPA-009 and SPA-010 both edit `cli/build.gradle.kts`. SPA-001 is omitted from every list because every ticket depends on it. Wave 2 is therefore SPA-002 with SPA-004, then SPA-003.
- 2026-11-03 Re-plan: SPA-004 criteria 6 and 7 carried hypergeometric values the Architect computed wrongly. The Developer caught them before writing code. The pins were recomputed by script, and two ambiguities were settled (re-plan 1 of 2 for SPA-004).
- 2026-11-03 Format brief: on the human's instruction, the Architect built `fixtures/briefs/trk.json` instead of the human writing it by hand. Sources: the human's summaries of the Star Trek release notes and prerelease guide (archetype plans rewritten in the Architect's words), plus the cached Scryfall data (signposts are each pair's two two-color uncommons; mechanics' reminder text is from the cards). Battlefront and Seek out appear on no trk card, so they are left out. Surveil is added because it is the set's most common keyword. On the human's word, speed is a medium placeholder that notes it varies by archetype. Generic archetypes are empty. It loads through BriefLoader.
- 2026-11-03 Human decision: the metric engine does not read the format brief, neither its archetypes nor the cards it names. Metrics describe the pool against the set baseline. Archetype fit and card quality belong to the later grading and deck-building goals. In this goal, the brief is used only for its kit template (SPA-007, SPA-008) and for the `validate-brief` name check (SPA-009).
- 2026-11-03 Re-plan: SPA-008 criterion 5 pinned 0 basic lands per trk kit, from a misreading of basicLandsSupplied. The Developer caught it before the gate ran. It is now an exact Binomial(6, 4/5) pin, median 5 (re-plan 1 of 2 for SPA-008).
- 2026-11-03 Re-plan: added SPA-011, which corrects the kit card-count warning. SPA-005's criterion 6 left booster basics out of the count, so every sampled pool was warned as short. It runs after SPA-009 and before SPA-010, and G-001 now has 11 tickets.
- 2026-11-03 Re-plan: added SPA-012. It derives the rare-count range from the kit template, because SPA-005's fixed range flagged 14.1 percent of genuine kits, and exempts basics from the high-count warning. It runs after SPA-011 and before SPA-010, and G-001 now has 12 tickets. Remaining known issues for a later cleanup ticket are fetch-set's multi-line Scryfall errors and its "null" network message.

## Closing report (2026-11-03, Architect)

All 12 tickets are integrated (SPA-001 to SPA-012; SPA-011 and SPA-012 were added during the goal). The goal-level criteria were verified on a fresh clone of `main` at 6ad6856:

| Goal criterion | Result on main |
| --- | --- |
| 1. Gate green on a fresh clone and in CI | Pass. All seven steps green locally; CI green on 6ad6856. |
| 2. Card count equals Scryfall's card_count; layout tests | Pass. trk 581 of 581, sds 30 of 30. Layout tests are in SPA-002. |
| 3. Hand-written brief validates and loads | Pass. `spa validate-brief fixtures/briefs/trk.json --set trk` prints OK. The brief was built by the Architect at the human's instruction. |
| 4. Every fixture resolves, with no silent errors | Pass. 26 pools (11 ManaBox), totals and unresolved lines matching the manifest. |
| 5. Goldens for every pool, three hand-checked | Pass. 26 goldens; 105 hand-derived literals over three pools, recounted by both testers. |
| 6. Baseline seeded and pinned | Pass. Rebuilding with seed 20261106 and 2000 kits leaves the tree unchanged. |
| 7. `analyze` exits 0 with JSON on every pool | Pass. 26 of 26. |

Metrics:

| Ticket | Attempts | Bounces (source) | Re-plans | Inspector tests added | Dispatch to merge |
| --- | --- | --- | --- | --- | --- |
| SPA-001 | 1 | 0 | 0 | 0 | 52 min |
| SPA-002 | 1 | 0 | 0 | 3 | 27 min |
| SPA-003 | 1 | 0 | 0 | 16 cases | 28 min |
| SPA-004 | 1 | 1 (Developer, ticket defect) | 1 | 0 | 53 min |
| SPA-005 | 2 | 0 | 0 | 0 | 21 min |
| SPA-006 | 2 | 0 (1 ruling) | 0 | 2 | 23 min |
| SPA-007 | 1 | 0 | 0 | 0 | 26 min |
| SPA-008 | 1 | 1 (Developer, ticket defect) | 1 | 0 | 21 min |
| SPA-009 | 2 | 0 | 0 | 0 | 24 min |
| SPA-011 | 1 | 0 | 0 | 0 | 11 min |
| SPA-012 | 2 | 0 | 0 | 0 | 12 min |
| SPA-010 | 1 | 0 | 0 | 0 | 16 min |

- Gate green on first push: every pushed head passed CI on its first run (12 of 12). Counting local gate runs, 7 of 12 tickets were green on the first run; the five red first runs were four lint failures and one compile error, each fixed in one attempt.
- Bounces: 2, both Developers sending a defective ticket back to the Architect before writing code. Test Inspector bounces 0. Acceptance Tester bounces 0.
- Re-plans: 2 (SPA-004, SPA-008). Rulings on a ticket's own contradiction: 1 (SPA-006). Tickets added to fix defects found downstream: 2 (SPA-011, SPA-012, both from SPA-005's kit-warning criteria).
- Escalations to the human: 0. Human decisions: the target set, building the brief, metrics not reading the brief, one hold.
- Tokens by role: Test Inspector 1,239,648 total (about 103k per ticket); Acceptance Tester 1,320,940 total (about 110k per ticket), from the agents' completion notices. Developer and Architect token counts aren't available from the session tools, so they are not reported.
- Share of criteria proven by deterministic tests: about 66 of 87 acceptance criteria (76 percent) have a unit, golden, or property test as their test-plan proof. The rest are Acceptance Tester scenarios (network, installed command, git hook, CI) or document reads.
- Tests: 415 at close, plus 26 golden files.

Known issues for a later cleanup ticket (none blocks G-001):
- The tagger doesn't tag Guidance Failure's "target creature or Spacecraft ... top or bottom of their library" as BOUNCE. A fix changes goldens.
- "Add ... one mana of the chosen color" (Thriving lands) isn't FIXING. No trk card uses it.
- A typed collector number is matched case-sensitively after one lowercase retry, so `1701d` misses `1701D`.
- `fetch-set` embeds Scryfall's multi-line error body, against the README's one-line errors, and a network failure's message ends in "null".
- No unit test exercises the default `--data fixtures` path or an unreadable-file IOException.

The Architect recommends the human close this goal.
