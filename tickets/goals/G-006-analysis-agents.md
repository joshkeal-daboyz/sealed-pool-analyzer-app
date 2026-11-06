---
id: G-006
title: Analysis agents, a pool reviewed from the command line
status: open
owner: human
target_set_code: trk
companion_set_codes: [sds]
depends_on: [G-005]
---

# G-006: Analysis agents, a pool reviewed from the command line

The owner asked for this goal on 2026-11-06: "Let's build out the analysis agents next. That way, even if there is no app, I can hand my pool to Claude and have it reviewed." This is the design brief's step 6 (the specialist analysis agents), delivered so that one command-line call reviews a pool end to end and prints a review a player can read. It is the first goal that calls the Claude API. Drafted by the Architect from the design brief (Development pipeline step 6, Agent roster, Orchestration, Data sources, Evaluation and testing) and from what G-001 to G-005 built. Approved on 2026-11-06 with every default; the answers are in "Decided" at the end.

## Why this goal next

- The owner wants a pool reviewed without the app. Code already does the numbers (metrics, baselines, grades, fixing, synergy, the deck builder); what is missing is the part that reads them and says, in words, what a player should know. The brief's rule: "The number comes from code, the sentence comes from the model."
- The seam exists. SPA-029 (G-003) built `ModelClient`, `AnthropicModelClient` (Anthropic Java SDK 3.0.0), `ReplayModelClient` (answers only from recordings) and `ApiKey` (reads `ANTHROPIC_API_KEY` from the environment). Nothing calls a model in the gate; this goal is the first caller.
- The deck builder (G-005) already ranks every colour configuration by score, so the "Judge" of the brief is code here; the agents explain and probe what the ranking rests on.

## Deliverables

1. **The model seam, ready for a review** (`agent`, package `spa.agent.model`). A request can carry a shared, cacheable context block placed before the agent's own instructions, so the seven analysts share one cached pool bundle. A budget wraps any `ModelClient` and enforces token caps. A recording wrapper writes every exchange as a `Recording` file so a live run becomes a test fixture.
2. **The pool bundle** (`spa.agent.review`). One deterministic text built by code from the resolved pool: every card with quantity, cost, type line, rarity, rules text and set grade; the set's format brief (mechanics, ten archetypes with signposts, speed); the pool's kit warnings. Same input, same bytes (a cache needs that). It holds no other agent's output, no deck and no metric numbers: the metric engine's numbers and the baseline reach each agent through its own number table (deliverable 3), so the cached prefix stays small.
3. **A number table per agent.** Code builds, for each agent, a keyed table of the numbers that agent may quote, each with the pool's value and the set baseline's median, 10th and 90th percentile (from `Baseline.stats`, keyed by the JSON pointers `MetricFlattener` makes). An agent's finding states numbers only by key; the review prints the value from the table, never the model's own digits, and a finding that names a key with a value the table does not hold is dropped.
4. **The analysis agents** (`spa.agent.review.agents`, one class and one prompt file each, listed in `AnalysisAgents.ALL`). Seven, from the brief's roster, each returning the shared output schema:
   - **Colour Depth Analyst**: which colours are deep enough to play, thin-colour warnings, each against the set baseline.
   - **Curve Analyst**: per colour pair, curve shape, creature count, two-drops, top end, gaps.
   - **Mana Engineer**: land count, colour source counts, fixing, which splashes are safe.
   - **Interaction Analyst**: removal and interaction count by type, reach, against the baseline.
   - **Bomb Assessor**: which cards win games alone, and whether they justify a colour commitment or a splash.
   - **Archetype Matcher**: which of the set's designed archetypes the pool supports, signposts present, pieces missing.
   - **Synergy Detector**: which of the set's synergy axes (G-005's tags) have enablers and payoffs in the pool, and the strongest partners.

   Each agent is a thin interpreter of its number table. Agents never see each other's output.
5. **The shared output schema and its checks.** Each agent returns JSON: `agent`, `confidence` (0 to 1), `summary`, and `findings`, each with `claim`, `pair` (a colour pair or null), `severity` (`info`, `warn` or `key`), `cards` (names) and `numbers` (keys with the values the model read). Code validates it: the schema; every card named is in the pool; a finding that cites no card is dropped (brief: "A finding that names no card is dropped before it reaches the Judge"); every number key is in the agent's table and its value equals the table's. Dropped findings are counted per agent and shown.
6. **The runner.** The first analyst runs alone and writes the cache; the other six then run in parallel. An invalid answer gets one retry with the error named; a second failure, a timeout, or a spent budget drops that agent for the run and the review says so. Failure of every agent still gives a review: the decks, with a Missing line.
7. **The Explainer.** One call that reads every surviving finding and the builder's top three decks and writes the verdict, a short note per deck, and a list of disagreements between agents (each naming at least two agents and at least one card), validated the same way. The Judge's ranking is the builder's deck score; the Explainer restates it and does not reorder it.
8. **`spa review <pool-file> --set <code>`** and what it prints (Decided 3). `--dry-run` plans the calls with no key and no network. `--json` prints the structured review. `--record <dir>` writes every exchange as a recording, and `--replay <dir>` answers from recordings with no key and no network (committed canned recordings let the command run end to end offline). `--show-input` prints what the agents are given (the bundle, each number table and facts) with no key and no network. `--model`, `--max-input-tokens`, `--max-output-tokens`, `--config <file>` and `--data` as for the other commands.
9. **A model-free review packet** (owner, 2026-11-06, for the trk prerelease at 3PM): `spa packet <pool-file> --set <code>` writes one text file holding a request for a review, the pool with grades, the pool's numbers beside the set baseline, its fixing, its strongest synergies and the builder's top three decks with their reasons, ready to paste into a Claude chat. It makes no model call and needs no key. It is the first ticket to merge (SPA-071).
10. **A ManaBox-importable deck export** (owner, 2026-11-06: "I can import the deck back into manabox"): `spa build ... --export <dir>` writes each ranked deck as a ManaBox CSV (the owner's export format, basics added as the set's basic lands) and a plain `N Name (SET) number` list as a fallback. Model-free; second to merge (SPA-072).
11. **A versioned config file**, `fixtures/agents/review.json`: the model id, token caps, per-call timeout, parallelism, retries, and for each agent its id, whether it is on, its max output tokens and its prompt version. Prompts are resource files, `agent/src/main/resources/spa/agent/review/<agent>-v1.md`; a new prompt is a new file, as in G-005's tagging.

## Goal-level acceptance criteria

0. **Usable before the agents.** By the trk prerelease (3PM, 2026-11-06), `spa packet` and `spa build --export` are merged on `main`, so a pool exported from ManaBox can be turned into a paste-ready packet and a ManaBox-importable deck with no model and no key.

1. **Gate.** Green on `main` at goal close, locally and in CI. No test calls a model or the network; model calls in tests run against `ReplayModelClient` recordings or a fake `ModelClient`. `core` is unchanged: `git diff --stat 3f19d16..HEAD -- core/` is empty.
2. **No key in the repository.** `spa review` without `ANTHROPIC_API_KEY` and without `--dry-run` prints one line `ANTHROPIC_API_KEY is not set` on stderr and exits 1, before any other work. A search of the repository's tracked files for `sk-ant-` finds nothing.
3. **Dry run.** `spa review fixtures/pools/trk-manabox-01.csv --set trk --dry-run`, with no key and no network, prints the eight planned calls in order (seven analysts, then the Explainer) with the model id, the cached bundle's estimated tokens and each call's estimated input tokens, then the total against the two caps, and exits 0. It makes no call and writes nothing.
4. **A full review on the fake client.** With a fake `ModelClient` that returns a valid answer for every agent, `spa review` on `trk-manabox-01` prints, in this order: header, the top three decks, the Explainer's verdict, one section per analyst in `AnalysisAgents.ALL` order, Disagreements, Missing (`Missing: none`), the usage line and the Scryfall attribution line. Two runs print the same bytes.
5. **Decks are the builder's.** The three decks the review prints have the same colours, scores and 40 cards as the first three decks of `spa build fixtures/pools/trk-manabox-01.csv --set trk --weights fixtures/weights/deck-tuned.json`.
6. **Citations enforced.** A finding that cites no pool card, or a card not in the pool, is dropped and counted; a test feeds both and checks the section and the count.
7. **Numbers enforced.** A finding that names a number key outside its agent's table, or a value different from the table's, is dropped and counted; the printed numbers are the table's. A test feeds both.
8. **Failures degrade, not stop.** An agent that returns invalid JSON twice, times out, or is refused by the budget appears on the Missing line with its reason, and the rest of the review prints. With every agent failing, the review still prints the header, the decks and the Missing line, and exits 0.
9. **Budget.** Calls whose estimated input would pass `--max-input-tokens`, or whose `maxTokens` would pass the output cap, are not sent. The usage line's totals equal the sum of the usage of the calls sent. A test with a tiny cap shows which agents were skipped.
10. **Caching and order.** Every analyst's request carries the same bundle text as its first, cacheable system block, then its own instructions; the first analyst's call is sent and answered before the other six are sent; a unit test on the SDK parameter conversion shows the cache control on the bundle block and none on the rest.
11. **Independence.** No analyst's request contains another agent's output; the Explainer's request contains every surviving finding. A test reads every recorded request.
12. **Alterable.** A test-only agent added to the list gets a request, a section and a Missing or usage entry with no other code change; the model id, caps, retries and which agents run come from `fixtures/agents/review.json` (`--config` selects another), and a part switched off there sends no call.
13. **No set in the prompts.** No prompt file contains a card name or a set code (a test reads every file under `spa/agent/review/` for the names of the trk, msh, hob and fra cards and for the four codes). Every set-specific fact reaches an agent through the bundle.
14. **Live run** (owner's key; Decided 4). `spa review fixtures/pools/trk-manabox-01.csv --set trk --record <dir>` completes within 60 seconds and within the token caps; the usage line is reported in the goal file with the cache-read share. The recording is committed as `fixtures/recordings/review-trk-manabox-01/` and a test replays it to the same review. If `ANTHROPIC_API_KEY` is not set in the Architect's session at close, this and criterion 15 are reported as not measured and left to the owner.
15. **Spot check** (owner). The review of five `trk` fixture pools is printed; the owner rates each agent's section useful, wrong or empty; the rates go in this file as a first reading, not a pass or fail.
16. **Usage.** Each live run records its tokens (input, output, cache read, cache write) and the model, in the goal file.

## Constraints

- **Model calls happen only in the agent layer** (`CLAUDE.md`). `core` is untouched; the metric engine makes no call.
- **The user's key, never ours.** The key is read from `ANTHROPIC_API_KEY` by `ApiKey` and nowhere else; nothing is stored, logged, recorded or printed. A recording holds the request and response only; a test checks that a recording written by the wrapper contains no `x-api-key` and no key value.
- **Tests never use the network.** Fake client or `ReplayModelClient` only. Live runs are manual (the owner's, or the Architect's with a key) and their recordings become fixtures.
- **Deterministic before probabilistic.** Every number, deck and card list is code. The model writes words about numbers it is given, and cites cards it is given. Anything else is dropped.
- **Easy to change.** The same rules as G-005: each agent is its own class behind one small interface, with its own prompt file and tests, listed in `AnalysisAgents.ALL`; the runner, validator and printer see only the interface; adding, removing or reordering an agent touches the list and the agent's own files.
- **Every set is different** (owner, 2026-11-04). No prompt names a set, a card or a number that suits one set. An agent learns the set from the bundle (brief, baseline, tags).
- **Owner examples are held-out checks.** No example card the owner has named goes into a prompt.
- **Attribution.** The review ends with the line "Card data from Scryfall (scryfall.com). Card text and images are property of Wizards of the Coast."
- **One Developer at a time** on this PC; the tickets share `agent` and `cli`, so all are serialized.

## Where this departs from the design brief

- The brief runs the analysts over all ten colour pairs and prunes to three before building. Here the builder already ranks every configuration in about 5 seconds (G-005), so the review builds first and the analysts explain a pool that is already ranked; pruning by findings waits.
- The brief has a model Judge. Here the Judge is the deck score; the Explainer lists where agents disagree and does not reorder the decks. A model Judge waits for a later goal, with the Red Team and the Sideboard Planner.
- The brief's Synergy Detector reads parsed keywords. Here it reads G-005's tag file (axes, roles, pair scores) and says nothing about a set with no tag file (it goes on the Missing line).
- The brief puts the Card Grader behind a model call. Grades here are the recorded grade files of G-003/G-004, read by code and given to the agents.

## Out of scope for this goal

Photo intake (owner, 2026-11-06: "Don't worry about the photo agent. I'll manage the photo and generation of the CSV"): the owner supplies a ManaBox-style CSV or a plain text list. The Red Team, the Sideboard Planner, a model Judge, the Format Scout, reruns with edited grades or forced pairs, the Android app, photo intake, a dollar cost figure (the usage line is tokens), any `core` change, and a held-out backtest of the review (the builder's ranking is already backtested; the agents explain it). Per-pool prompts that name cards from the owner's examples.

## Metrics to report when the goal closes

The live run's usage (tokens in, out, cache read, cache write), seconds and agents answered; findings kept and dropped per agent, by reason (no card, unknown card, bad number key, bad value, schema); the owner's spot-check rates; and the time `spa review --dry-run` takes.

## Decided

The owner answered on 2026-11-06 by approving every default.

1. **Agents in this goal.** The seven analysis agents plus an Explainer. The Judge is the builder's score. The Red Team, Sideboard Planner, a model Judge and Format Scout wait for a later goal; the Card Grader stays the recorded grade files.
2. **Key, model, cost.** `ANTHROPIC_API_KEY` from the environment only. Claude Sonnet 5.5 (`claude-sonnet-5-5`) for every agent, `--model` overrides. Prompt caching on the shared pool bundle; the first analyst runs alone to write the cache, then the other six in parallel. Token caps per review, 300,000 input (cache reads counted in full) and 60,000 output; a call that would pass a cap is skipped and named on the Missing line. `--dry-run` prints the plan and an estimate with no network. A usage line ends every review. No dollar figure.
3. **Output.** Plain text: header (set, pool size, unresolved lines), the builder's top three decks in compact form (colours, score, spells by name and count, lands, one line on why the deck ranks where it does), the Explainer's verdict, one section per analyst (findings with the cited cards and the numbers beside the set baseline), Disagreements, Missing, usage, attribution. `--json` prints the full structured review. The per-card `spa build` listing stays a separate command.
4. **What "reviewed" means.** Structural, tested on every review (schema, citations, number audit, decks equal the builder's); one live run on `trk-manabox-01` with the owner's key, within the caps and 60 seconds, recorded as a fixture; and the owner's spot-check of five `trk` pools, reported as a first reading. If no key is set in the Architect's session at close, the live run and the spot-check are the owner's and are reported as not measured.
5. **Ordering for the 3PM trk prerelease** (owner, 2026-11-06, relayed: "I'm worried about the 3PM deadline. At least we'll know the engine works"): the model-free packet (SPA-071) merges first, the deck export (SPA-072) second, and the model-calling agents follow. SPA-062 was dispatched first and stopped before any commit.
6. **Defaults the Architect set** (all within the standing authorization to derive scope and criteria):
   - Deck weights for the review: `fixtures/weights/deck-tuned.json` (frozen in G-005), `--weights` overrides.
   - One retry per agent on an invalid answer; a per-call timeout of 120 seconds; at most 6 calls in parallel; at most 8 findings per agent; analyst output cap 3,000 tokens, Explainer 6,000.
   - Findings are checked by code as in deliverable 5; free-text numbers in a claim are not checked (a known limit: only keyed numbers are audited).
   - The recording wrapper names files `<label>.json` (the agent id, `<id>-retry1` for a retry, `explainer`), since parallel calls have no stable order.
   - The tests for the agents use hand-built answers; the real answers come only from the live run, whose recording is the one committed fixture of a model's actual output.
   - `trk` is the target set; the command works for any set that has a Scryfall cache, a brief, a baseline and a grade file (the tag file is needed only by the Synergy Detector).

## Architect notes (2026-11-06)

- G-006 start commit for criterion 1: 3f19d16.
- Order (all serialized; the tickets share `agent` and `cli`). The owner's ordering of 2026-11-06 puts SPA-071 and SPA-072 first; the rest follow in id order:
  0. SPA-071: `spa packet`, the paste-ready review packet, no model. Then SPA-072: `spa build --export`, ManaBox-importable decks.
  1. SPA-062: model seam (cached context, budget, recording wrapper).
  2. SPA-063: pool bundle and number tables.
  3. SPA-064: agent interface, output schema, validation, runner, config file, the seam-proof test agent.
  4. SPA-065: Colour Depth, Curve and Mana Engineer agents.
  5. SPA-066: Interaction Analyst and Bomb Assessor.
  6. SPA-067: Archetype Matcher and Synergy Detector.
  7. SPA-068: the Explainer (prompt, schema, checks, runner).
  8. SPA-069: the review assembly (deck summaries, the run, text and JSON rendering).
  9. SPA-070: `spa review` (wiring, dry run, show-input, key, record and replay, canned recordings, README).
  10. SPA-073, only if a live run happens (criterion 14): commit the live recording as `fixtures/recordings/review-trk-manabox-01/` and add the test that replays it. Written when the recording exists.
- Library tests (SPA-062 to 069) build pools by hand, as the other `agent` tests do; the real `trk-manabox-01` numbers are pinned through `spa review --show-input` in SPA-070.
- Criterion 14's "a test replays it to the same review" needs the live recording's requests to match what the code builds now; a later prompt, config or weights change makes it stale and the test names the file to re-record.
- Real numbers for `trk-manabox-01` (from `spa analyze`, 2026-11-06): 71 resolved entries, 85 cards, 71 nonland copies, 5 basic and 9 nonbasic lands, `removalTotal` 9 (baseline median 8, mean 7.938, 2,000 kits), `interactionTotal` 1, 6 fixing sources; pair WU: 33 castable cards, 19 creatures, 2 removal, 0 interaction, 4 fixing sources; the baseline holds 448 pointers.
