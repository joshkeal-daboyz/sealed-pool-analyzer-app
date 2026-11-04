---
id: G-003
title: Card grades, the first model call
status: open
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

1. **An Anthropic API key with a spend limit.** Create a key in the Claude Console (console.anthropic.com > API keys) and set a monthly spend limit under Billing (default: $20; this goal's model runs are estimated at under $10 in total, see Constraints). Set it as the user environment variable `ANTHROPIC_API_KEY` on this PC (Settings > System > About > Advanced system settings > Environment Variables > New, under "User variables"), then close and reopen every terminal and Claude Code session. Check it in Git Bash with `test -n "$ANTHROPIC_API_KEY" && echo set` (prints `set`, never the key). The key never goes into the repository, a ticket, CI, or a chat message.
2. **Nothing else to install.** The JDK, Gradle wrapper and Android SDK from G-001 and G-002 are enough. The Anthropic Java SDK comes through Gradle. The 17Lands public datasets download over HTTPS with no account.

## Deliverables

1. **An `agent` module.** Pure Kotlin on the JVM, depending on `core`, and the only module allowed to call a model. It uses the official Anthropic Java SDK (`com.anthropic`), reads the key only from the environment (`ANTHROPIC_API_KEY`), and fails with one clear line when the key is missing. `core`'s metric engine stays model-free, as CLAUDE.md requires.
2. **The grade model.** `CardGrade(score, confidence, source, provenance)`: `score` on the shared scale (open question 2), `confidence` 0 to 1, `source` either `WIN_RATE` or `MODEL`, and `provenance` naming the dataset or the model id, prompt version and date. A `grade(card, set)` lookup returns it, preferring `WIN_RATE` when it exists.
3. **The Claude grader.** Grades every card in a set's sealed pool (the set plus its companions) once, from rules text, mana cost, type, rarity, the set's format brief and its baseline, never from win rates or published reviews. It runs through the Message Batches API (half price) and structured outputs, so each card gets exactly one schema-valid grade. Its output is a committed file, `fixtures/grades/<set>.json`, with the model id, prompt version and date in its header. `spa grade-set <set>` writes it and reports the token use and cost.
4. **The win-rate grade.** From a 17Lands public dataset, a per-card game-in-hand win rate (GIH WR), mapped onto the same scale within its set, with confidence from the sample size. A script derives a small per-card table, committed under `fixtures/winrates/<set>.json` with the dataset's name and date; the raw download is never committed.
5. **The blind backtest.** `spa grade-backtest <set>` reports the Spearman rank correlation between the Claude grade and GIH WR, over the cards with enough games (open question 4), overall and by rarity. It runs on three past sets (open question 3), and the report goes into the goal file at close.
6. **`trk` grades.** The Claude grade file for `trk` and `sds`, made with the prompt the backtest measured. `spa grades <file> --set trk` prints a pool's cards with grade, confidence and source, sorted by grade. The `trk` win-rate table follows in a later ticket once 17Lands publishes `trk` data (release is 2026-11-13).
7. **Real pools from the 11/6 prerelease** (G-002 Decided 7, carried). If the owner brings back ManaBox-format CSVs, adding them to `fixtures/pools/` with their goldens is a ticket in this goal.

## Goal-level acceptance criteria

1. **Gate.** The gate is green on `main` at goal close, in CI and locally. The gate never calls the API: every test that touches the model reads recorded responses committed under `agent/src/test/resources/`.
2. **The key stays out.** `git grep -nE "sk-ant-"` on `main` finds nothing outside this goal file. CI has no Anthropic secret. Running `spa grade-set` with `ANTHROPIC_API_KEY` unset prints one line naming the variable and exits 1, with no stack trace.
3. **`core` is unchanged.** `git diff <G-003 start> main -- core/src/main fixtures/golden` lists no file, and `core`'s runtime dependencies still hold no Anthropic or HTTP client.
4. **One grade per card.** For each graded set, the grade file holds exactly one grade for every card in the sealed pool (set plus companions, basics excluded), every score is on the scale, every confidence is in [0, 1], and the file validates against its JSON schema. A test pins this on the committed `trk` file.
5. **Blind means blind.** The Claude grader's request for a set contains no 17Lands field, no win rate and no review text. A test inspects the recorded request bodies, and the Acceptance Tester reads one live request.
6. **Backtest.** On each of the three past sets, `spa grade-backtest` prints the Spearman correlation and its card count. The mean over the three meets the target in open question 4. If it misses, the result goes to the owner with the numbers and one proposal; the goal does not tune the prompt on the backtest sets (that would fit the test set).
7. **Cost.** Each `spa grade-set` run prints input, output and cached tokens and a dollar figure from the model's published price, and the total for the goal is recorded at close. It stays under the owner's spend limit.
8. **Win rates.** For each backtest set, the win-rate table's GIH WR for five cards picked by the Acceptance Tester matches 17Lands' published card ratings for that set and format within 0.5 percentage points.
9. **Grades in a pool.** `spa grades fixtures/pools/trk-manabox-01.csv --set trk` lists all 71 entries with grade, confidence and source `MODEL`, highest first, and exits 0.

## Constraints

- **Model:** Claude Opus 5.5 (`claude-opus-5-5`), adaptive thinking, structured outputs, through the Message Batches API. A different model is the owner's call (open question 5).
- **Cost estimate:** one set is about 300 cards. One graded set is roughly 100K input tokens and 60K output tokens including thinking: about $1.40 at Opus 5.5's $4 / $20 per million, about $0.70 through Batches. Four sets (three past, plus `trk` and `sds`) come to under $5, and under $10 with one re-run each. The first ticket that calls the API measures one set and reports the cost before the others run.
- **No API call in the gate or CI.** Live runs are manual: the Developer or Acceptance Tester runs them on this PC with the owner's key and records the output in the ticket. Recorded responses are committed so the gate replays them.
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
5. **The model.** Default: Claude Opus 5.5. A cheaper model (Sonnet 5.5 at half the price) is possible if the cost estimate worries you; the backtest would then measure that one instead.
6. **The spend limit.** Default: $20 a month on the Console key. The goal's estimate is under $10.
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
