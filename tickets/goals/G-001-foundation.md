---
id: G-001
title: Foundation phase of the Sealed Pool Analyzer
status: open
owner: human
target_set_code: TBD
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
