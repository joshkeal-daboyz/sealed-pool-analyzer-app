---
id: G-003
title: Card grades, the first model call
status: closed
owner: human
target_set_code: trk
companion_set_codes: [sds]
depends_on: [G-001, G-002]
---

# G-003: Card grades, the first model call

Build step 5 of the design brief, the card grading layer: a per-card limited grade on one shared scale, relative to the card's own set, with a confidence and a source on every grade. Grades come from 17Lands win rates when a set has play data, and from a Claude grade (rules text, rarity, format brief, set baseline) when it doesn't. A Claude grade is the only kind `trk` can have on prerelease day. Prove the Claude grade the way the roadmap says: grade three past sets blind and measure the rank correlation against their 17Lands win rates.

This is the first goal that calls a model, and the first step of the roadmap's Intelligence phase. As with G-001, it is built and tested on the JVM and reached through `spa`. The app does not change in this goal (open question 1).

Drafted by the Architect on 2026-11-04 and approved by the owner the same day with every default ("Approve with defaults"); the answers are in "Decided" at the end.

## Why this goal next

- The roadmap orders it: Foundation (G-001) and the metrics half of the Interface (G-002) are done. The brief calls the Card Grader "the single biggest risk to recommendation quality", and every analysis agent, the Deck Builder's score and the Judge read grades. Nothing in the Intelligence or Product phases can start without them.
- It is the smallest goal that sets up the agent layer: the module that alone may call a model, the API key handling, recorded responses that keep the gate deterministic, and cost reporting. Every later agent reuses this.
- It is measurable. Grade correlation is the brief's own metric, and 17Lands' public data makes it free to compute.

## Before this goal starts

Done by the owner, outside the repository:

1. **No API key is needed** (owner decision 2026-11-04, Decided 5). Grading runs on Claude Fable 5.1 through the owner's Claude Code subscription.
2. **Nothing else to install.** The JDK, Gradle wrapper and Android SDK from G-001 and G-002 are enough. The Anthropic Java SDK comes through Gradle. The 17Lands public datasets download over HTTPS with no account.

## Deliverables

1. **An `agent` module.** Pure Kotlin on the JVM, depending on `core`, and the only module allowed to call a model. It uses the official Anthropic Java SDK (`com.anthropic`), reads the key only from the environment (`ANTHROPIC_API_KEY`), and fails with one clear line when the key is missing. `core`'s metric engine stays model-free, as CLAUDE.md requires.
2. **The grade model.** `CardGrade(score, confidence, source, provenance)`: `score` on the shared scale (open question 2), `confidence` 0 to 1, `source` either `WIN_RATE` or `MODEL`, and `provenance` naming the dataset or the model id, prompt version and date. A `grade(card, set)` lookup returns it, preferring `WIN_RATE` when it exists.
3. **The Claude grader.** Grades every card in a set's sealed pool (the set plus its companions) once, from rules text, mana cost, type, rarity and the set's format brief, never from win rates or published reviews. `spa grade-packet <set>` writes a deterministic grading packet (instructions, the brief, the cards in groups, a response schema); a Claude Fable 5.1 agent grades it in Claude Code; `spa grade-import <set>` validates the responses (every card exactly once, on the scale) and writes the committed file `fixtures/grades/<set>.json`, with the model, prompt version and date in its header. (Changed 2026-11-04 by owner decision from the Batches API to the subscription route.)
4. **The win-rate grade.** From a 17Lands public dataset, a per-card game-in-hand win rate (GIH WR), mapped onto the same scale within its set, with confidence from the sample size. A script derives a small per-card table, committed under `fixtures/winrates/<set>.json` with the dataset's name and date; the raw download is never committed.
5. **The blind backtest.** `spa grade-backtest <set>` reports the Spearman rank correlation between the Claude grade and GIH WR, over the cards with enough games (open question 4), overall and by rarity. It runs on three past sets (open question 3), and the report goes into the goal file at close.
6. **`trk` grades.** The Claude grade file for `trk` and `sds`, made with the prompt the backtest measured. `spa grades <file> --set trk` prints a pool's cards with grade, confidence and source, sorted by grade. The `trk` win-rate table follows in a later ticket once 17Lands publishes `trk` data (release is 2026-11-13).
7. **Real pools from the 11/6 prerelease** (G-002 Decided 7, carried). If the owner brings back ManaBox-format CSVs, adding them to `fixtures/pools/` with their goldens is a ticket in this goal.

## Goal-level acceptance criteria

1. **Gate.** The gate is green on `main` at goal close, in CI and locally. The gate never calls the API: every test that touches the model reads recorded responses committed under `agent/src/test/resources/`.
2. **No key anywhere.** `git grep -nE "sk-ant-"` on `main` finds nothing outside this goal file, CI has no Anthropic secret, and no grading step uses an API key.
3. **`core` is unchanged.** `git diff <G-003 start> main -- core/src/main fixtures/golden` lists no file, and `core`'s runtime dependencies still hold no Anthropic or HTTP client.
4. **One grade per card.** For each graded set, the grade file holds exactly one grade for every card in the sealed pool (set plus companions, basics excluded), every score is on the scale, every confidence is in [0, 1], and the file validates against its JSON schema. A test pins this on the committed `trk` file.
5. **Blind means blind.** The grading packet for a set contains no 17Lands field, no win rate and no review text (a test inspects a generated packet), and each grading agent is told to read only its packet and use no web tool.
6. **Backtest.** On each of the three past sets, `spa grade-backtest` prints the Spearman correlation and its card count. The mean over the three meets the target in open question 4. If it misses, the result goes to the owner with the numbers and one proposal; the goal does not tune the prompt on the backtest sets (that would fit the test set).
7. **Usage.** Each grading run records its token count and the weekly Fable plan percentage before and after, and the goal total is recorded at close.
8. **Win rates.** For each backtest set, the win-rate table's GIH WR for five cards picked by the Acceptance Tester equals an independent recomputation from the same public 17Lands game data file. (Amended 2026-11-04, SPA-031 re-plan 1: 17Lands' card ratings endpoint no longer returns full-window numbers, and the endpoint that does is marked for use on 17Lands.com only.)
9. **Grades in a pool.** `spa grades fixtures/pools/trk-manabox-01.csv --set trk` lists all 71 entries with grade, confidence and source `MODEL`, highest first, and exits 0.

## Constraints

- **Model:** Claude Fable 5.1, run as Claude Code subagents on the owner's subscription (Decided 5). The packet and the import are deterministic code; only the grading step is the model.
- **Usage, not dollars:** grading runs on the owner's Max plan (extra usage off, so nothing is billed beyond the plan). Each run's token count and the plan's weekly Fable percentage before and after are recorded.
- **No API call anywhere in this goal.** The gate and CI never call a model. Grading runs are pipeline steps: the Architect dispatches Fable grading agents in Claude Code, each reading only a grading packet and writing response files.
- **Training-data leakage.** The model may have seen grades or win rates for sets released before its training cutoff, which would inflate the backtest. The backtest prefers sets released after the cutoff (open question 3) and reports each set's release date beside its number.
- **Data terms.** 17Lands data is non-commercial, and the tool is free. The derived tables credit 17Lands in the README beside Scryfall. Published set reviews are never sent to the model or committed (brief, Data sources).
- **One Developer at a time** on this PC, as in G-002.
- New dependencies (the Anthropic Java SDK, a CSV reader if the 17Lands parser needs one) go in `gradle/libs.versions.toml` and the ticket's scope.

## Where this draft departs from the design brief

- **One grade file per set, not one call per pool.** The brief batches the Card Grader "into one call per pool". A grade is per card and per set, so this goal grades each set once and ships the file. Every user then sees the same grades at no cost, and the app can show grades with no key. A per-pool call (grading in the context of the pool) can come later if the backtest says it helps.
- **No set-review source.** The brief lists published reviews as a grade source "when available". Its own Data sources table says they are copyrighted and only a development cross-check, so this goal leaves them out.
- **Editable grades in the UI** (brief, Agent roster) come with the app goal that shows grades.

## Out of scope for this goal

The seven analysis agents, the Deck Builder, Judge and Explainer. Any app change: grades on screen, key entry, the Android Keystore. Per-pool grading, prompt tuning against the backtest sets, and user-recorded results. The look-and-feel redesign (Owner decision 2026-11-04, G-002: after the functionality, inspired by MTG Arena). Fuzzy name matching, Arena and MTGO import, and more sets in the app.

## Metrics to report when the goal closes

The same as G-002 so the goals compare, minus the UI-only ones: gate green on first push, bounces per merged ticket by source, re-plans, escalations to the human, lead time per ticket, and share of criteria proven by deterministic tests. Add for the first model goal: the backtest correlations, dollars spent on model calls in total and per graded set, and how many recorded responses the gate replays.

## Decided

The owner approved every default on 2026-11-04. Each item keeps the question and its answer.

1. **Show grades in the app in this goal?** Default: no. Grades stay in `spa` for this goal; the app shows them in the next app goal, together with editing.
2. **The scale.** Default: 0.0 to 5.0 in steps of 0.5, with the usual limited letter grades (F to A+) as display labels, relative to the set (a 3.0 is a solid playable in this set).
3. **Which three past sets for the backtest?** Default: the three most recent sets that have complete 17Lands Premier Draft datasets, preferring sets released after the model's training cutoff (June 2026). GIH WR comes from Premier Draft, whose sample is far larger than Sealed's; grades are about card quality in the format either way. The first backtest ticket names the sets and their release dates.
4. **The correlation target.** Default: a mean Spearman of 0.45 or more over the three sets, counting cards with at least 500 games in hand. A miss goes to the owner, not into prompt tuning.
5. **The model.** Default was Claude Opus 5.5 through the API. **Owner decision 2026-11-04: Claude Fable 5.1 through the owner's Claude Code subscription, no API key.** A Fable probe (no tools) reported a training cutoff of about June 2026 and no card-level or Limited knowledge of `msh`, `hob`, `fra` or `sos`, so the backtest stays blind.
6. **The spend limit.** Superseded 2026-11-04: no API key is used in this goal (item 5), so there is no spend. Grading uses the owner's Max plan usage.
7. **Pools from the 11/6 prerelease.** Default: if you photograph pools at the event, give the photos to Claude and keep the ManaBox-format CSVs it returns; the first ticket of this goal adds them as fixtures. If none arrive, deliverable 7 drops.

## Architect notes (2026-11-04)

- G-003 start commit for the "unchanged" criterion (3): 06c306c.
- Backtest sets, settled from data (Decided 3): `msh` Marvel Super Heroes (released 2026-06-26), `hob` The Hobbit (2026-08-14), and `fra` Reality Fracture (2026-10-02), the three most recent expansions before `trk`, all at or after the model's June 2026 training cutoff. 17Lands publishes Premier Draft game data for each (`game_data_public.<SET>.PremierDraft.csv.gz`, 22 to 31 MB, checked 2026-11-04). Its card ratings endpoint (`/card_ratings/data?expansion=<SET>&format=PremierDraft`) gives `ever_drawn_win_rate`, the GIH WR criterion 8 checks against.
- The backtest sets have no format brief or Scryfall cache yet. A data ticket fetches the caches with `spa fetch-set` and drafts each brief from Scryfall data alone (mechanics from rules text, signposts from gold uncommons), never from 17Lands or reviews, so the grade stays blind. These briefs exist only for the backtest, so the design brief's human review of each brief (which is for briefs the product ships) is not required; the owner may read them.
- The Claude grader reads the card data and the format brief. It does not read the set baseline: the baseline holds pool-level averages (removal per pool, curve), which say nothing about one card, and the backtest sets have none. Deliverable 3's "and its baseline" is dropped on that reasoning.
- The `agent` module must be added to the gate's coverage source roots (`build-logic/src/main/kotlin/spa/gate/Coverage.kt`, line 25), the same trap as SPA-001's ui-tests step.
- Anthropic Java SDK: `com.anthropic:anthropic-java` 3.0.0 (published 2026-10-21, the newest at least two weeks old).
- The API key is not set on this PC yet. Tickets that need no live call run first: SPA-029 (module and grade model), SPA-030 (backtest set data), SPA-031 (win-rate tables), SPA-032 (backtest command). SPA-033 (the Claude grader, first live run on `msh`) and SPA-034 (remaining runs, backtest report, `trk` grades) wait for the key.
- Order, one Developer at a time: SPA-029, 030, 031, 032, 033, 034. Each touches `cli` commands or the build, so all are serialized.
- 2026-11-04 SPA-029 integrated (PR #34) on attempt 1: the agent module, CardGrade and grade files, GradeLookup, and the replayable model client. 1 of 6 tickets integrated. Next: SPA-030.
- 2026-11-04 Known limit, raised by the owner: the format brief schema (`core`, `format-brief.schema.json` and `BriefRules`) requires exactly ten two-color archetypes. Sets built on five two-color factions (`hob`), three-color factions (wedges or shards), or four-color factions don't fit. G-003's sets are handled without a schema change (`msh`, `fra` and `trk` have ten pairs; `hob` gets labelled placeholders, SPA-030 re-plan 1). A schema that allows any number of archetypes of one to five colors is a `core` change for the owner, and belongs in the multi-set goal, before any such set is a target.
- 2026-11-04 SPA-030 integrated (PR #35) on attempt 2 after re-plan 1: Scryfall caches and blind briefs for msh, hob (five factions plus five labelled placeholders) and fra. 2 of 6 tickets integrated. Next: SPA-031 (win-rate tables).
- 2026-11-04 Known gap, found by SPA-031: the Scryfall caches for `msh` and `fra` miss cards that appear in their 17Lands games, 58 reprints for `msh` (Counterspell, Path to Exile, Rancor, Sword of Fire and Ice and others, likely a bonus sheet) and 10 for `fra`. SPA-030 read Scryfall's `booster` flag, which is false for every card in all four sets, so it found no companions. The backtest compares only cards in both files, so it is unaffected; real pools from these sets would not resolve those cards. Finding a set's bonus sheet from Scryfall belongs with the flexible-brief work in the multi-set goal (every set differs).
- 2026-11-04 SPA-031 integrated (PR #36) on attempt 2 after re-plan 1: win-rate grade tables for msh (278 cards), hob (184) and fra (271) from 17Lands public game data, every entry matched by an independent recomputation. 3 of 6 tickets integrated. Next: SPA-032 (backtest command). SPA-033 and SPA-034 wait for the API key.
- 2026-11-04 SPA-032 integrated (PR #37) on attempt 2 after one inspector bounce (win rates ranked by gihwr, a ticket ambiguity): `spa grade-backtest`. 4 of 6 tickets integrated. Next: SPA-033, the Claude grader, blocked until the owner sets ANTHROPIC_API_KEY.
- 2026-11-04 Owner decision: grade with Claude Fable 5.1 through the Claude Code subscription instead of the API (no key, no extra cost). Usage then: Max plan, 5-hour 18%, weekly all models 9%, weekly Fable 0%, extra usage off. SPA-033 and SPA-034 rewritten before dispatch: SPA-033 builds `grade-packet` and `grade-import` and the prompt; SPA-034 is the grading runs (the Architect dispatches Fable graders per set) plus import, `spa grades`, and the backtest. The `AnthropicModelClient` from SPA-029 stays for the later app goal, unused here.
- 2026-11-04 Owner: the prerelease is Friday 2026-11-06 at 3:00 p.m. Deadline: `trk` grades and a phone-readable grade sheet before then. Fable graders start as soon as SPA-033 is green (packets from its branch, compared after merge), all four sets in parallel, `trk` not waiting on the backtest.
- 2026-11-04 SPA-033 integrated (PR #38): grading packet, import, prompt v1. Fable graded msh, hob, fra and trk in parallel (5% of the weekly Fable allowance). Preview backtest mean 0.405 (msh 0.385, hob 0.359, fra 0.472), below the 0.45 target: strong on rares and mythics, weak on commons and uncommons. Star Trek grade sheet published for the owner. 5 of 6 tickets integrated. Next: SPA-034 (import, spa grades, backtest record).
- 2026-11-04 SPA-034 integrated (PR #39) on attempt 2 after re-plan 1 (basic lands): the four grade files, `spa grades`, and the backtest record. **6 of 6 tickets integrated.** Goal criterion 6: backtest mean 0.405 (msh 0.385, hob 0.359, fra 0.472) against the 0.45 target, a miss. Reported to the owner with one proposal: after the prerelease, a prompt v2 developed on msh, hob and fra (now spent as test sets) and both versions measured on `trk` once 17Lands publishes its data, the clean held-out test. Default if no answer: that plan. The goal closes when the owner rules on the miss.
- 2026-11-04 Owner direction for the next goal: layered grading. Layer 1, the set grade (this goal). Then grades that adjust for synergy with the other cards in your pool and for cost. Architect's sketch: set-level synergy counts added to the grading packet (layer 2); pool adjustments for synergy, curve and colour depth, deterministic from the metric engine where possible and a model only for synergy judgement (layer 3); adjusted grades feed the deck builder. To be drafted as G-004 after the Friday prerelease, together with the prompt v2 proposal.
- 2026-11-04 Owner approved G-004 with defaults, which takes the proposal on the criterion 6 miss: prompt v2 developed on `msh`, `hob` and `fra`, and v1 and v2 measured on `trk` once its data exists. G-003 closed with all 6 tickets integrated.

### Metrics (2026-11-04)

| Metric | G-003 |
| --- | --- |
| Tickets integrated | 6 (SPA-029 to SPA-034), PRs #34 to #39 |
| Gate green on first push | 6 of 6 pushed heads (local red runs before a push: 5, all detekt or coverage) |
| Bounces by source | Developer to Architect before code: 3 (SPA-030 hob factions, SPA-031 schema and endpoints, SPA-034 basic lands). Inspector: 1 (SPA-032, win rates ranked by score, a ticket ambiguity). Acceptance: 0. All four were gaps in the tickets, none code defects |
| Re-plans | 3 (SPA-030, 031, 034, one each); plus one ruling (SPA-033 criterion 2) and one clarification (SPA-032) |
| Escalations to the human | 0 from limits. Owner decisions: grade with Fable on the subscription; the backtest miss (via G-004) |
| Lead time | About 7 hours, SPA-029 dispatched 16:20 to SPA-034 merged, 2026-11-04 |
| Criteria proven by deterministic tests | 5 of 9 (1, 2, 3, 4, 9); 3 by data checks or reruns (5, 8, 6's numbers); 7 by usage records |
| Backtest (criterion 6) | msh 0.385, hob 0.359, fra 0.472, mean 0.405 against 0.45: a miss. Strong on rares and mythics (0.49 to 0.81), weak on commons and uncommons (0.15 to 0.40) |
| Model usage | Four Fable graders: 600,319 tokens in total; weekly Fable allowance 0% to 5%; no API key, no spend |
| Recorded responses replayed by the gate | 2 (SPA-029's hand-written replay recordings) |
| Delivered beyond the tickets | The Star Trek grade sheet page for the 6 Nov prerelease |
