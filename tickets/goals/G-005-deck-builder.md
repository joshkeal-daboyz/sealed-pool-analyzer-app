---
id: G-005
title: Build the deck, ranked decks for a pool
status: closed
owner: human
target_set_code: trk
companion_set_codes: [sds]
depends_on: [G-004]
---

# G-005: Build the deck, ranked decks for a pool

The owner asked for this goal on 2026-11-05 ("Go ahead and start five"), right after closing G-004. This is the step a player sees: give the app a pool, get back the 40-card decks it could build, ranked, each with its lands and a reason for every card. Drafted by the Architect from the design brief (Deck Builder, Mana Engineer, the backtest protocol) and from what G-004 measured. Approved the same day with changes to questions 2 and 5; the answers are in "Decided" at the end.

## Why this goal next

- The roadmap orders it. Grades exist (G-003) and move with the pool (G-004). The design brief's Deck Builder is the next consumer, and the Judge and Explainer need its candidates.
- It is measurable, with data we already hold. 17Lands' public Sealed data (G-004) gives each strong player's whole pool and the deck they registered.
- Two-colour decks are not the norm. Among strong players' builds (win-rate bucket 0.60 or higher), a straight two-colour deck is 27% in `msh`, 58% in `hob` and 28% in `fra`. The rest splash or play three or more main colours: three or more main colours are 27% in `msh`, 10% in `hob` and 35% in `fra` (Architect count, 2026-11-05, scratchpad `tools/cs2.js`). A builder that only knows pairs misses most real decks in two of the three sets.
- G-004 left one thing for here. Counted synergy hurt at every weight, and the owner moved synergy judgement by a model reading the pool into this goal (G-004, 2026-11-05).

## Deliverables

1. **Fixing tags** (no `core` change; `core`'s FIXING tag covers only cards that produce two or more colours). In `agent`, each card in a set's sealed pool is classed as fixing, or not, by its Scryfall data: lands that make two or more colours, basic landcycling and land-type cycling (such as Plainscycling), cards that search for a basic land, Treasure makers, and mana producers of two or more colours. `spa fixing <pool-file> --set <code>` prints the pool's fixing, each card with the colours it can make.
2. **The deck builder** (search, no model call, as the brief decided on 2026-11-03). For a given set of colours, it picks the 22 to 24 spells with the best total score and fills the rest with lands. The score is the sum of the cards' pool-adjusted grades (G-004's tuned weights), minus penalties for curve gaps and too few creatures, plus a bonus for removal, minus a castability penalty for each card whose colours the mana base supports poorly. The search is a greedy fill followed by local swaps. It is deterministic, and every included card carries a numeric reason.
3. **Colour configurations.** The builder tries every two-colour pair; every pair with a splash of one or two colours; every three-colour set; and four-colour sets when the pool's fixing supports them. It ranks the resulting decks by score, so a three-colour deck wins when the pairs are weak and the fixing is there.
4. **Mana.** The land count comes from the deck's curve and its number of colours (Decided 3). Basics are split by the deck's coloured mana symbols. The pool's fixing lands that make the deck's colours are played. Castability uses the hypergeometric odds of having each card's colours by its mana value, counting basics and fixing. **Fixing is worth more the more colours a deck needs** (owner, 2026-11-05): in a two-colour deck a fixer is scored on its grade alone; with a splash, three colours or four, it gets a bonus that rises with the colours it supplies that the deck needs.
5. **`spa build <pool-file> --set <code> [--colours <WUB>] [--top <n>]`.** It prints the ranked decks, three by default. Each deck shows its colours (main and splash), its 40 cards by mana value, its land split and fixing, its score broken into parts, and its sideboard. Each card has a short reason (its grade, plus anything the curve, creature count, removal, castability or fixing added).
6. **The deck backtest: `spa deck-backtest --set <code>`.** For each strong player's build in a 17Lands Sealed file (G-004 Decided 2), of any colour count, the builder runs on that player's pool and reports these numbers, overall and by the player's deck shape (two colours, two plus a splash, three or more main colours):
   - **Colour agreement:** the top deck's main colours are the player's main colours.
   - **Top-three rate:** the player's main colours are among the top three decks'.
   - **Splash agreement:** the top deck splashes exactly when the player did.
   - **Main-deck overlap:** how many of the player's registered nonland cards the builder's deck in the player's colours also plays.
   - **Land count:** the builder's land count is within one of the player's.

   It also reports the same numbers for a baseline builder that uses set grades alone and two-colour pairs only.
7. **Synergy tags for every card in the set** (owner, 2026-11-05; replaces the per-pool synergy agent of Decided 4). Once per set, Fable reads the whole set, blind, as in G-003's grading, in two passes:
   - **The set's synergy axes.** It names the set's synergy axes from the cards themselves (for example: sacrifice, tokens, +1/+1 counters, artifacts, spells cast, Spacecraft and Station, a tribe), each with a one-line definition.
   - **Card roles and named partners.** It tags each card with its role on each axis it touches: an **enabler** (it supplies the thing), a **payoff** (it rewards having the thing), or both, each with a strength of 1 to 3. It also lists **named partners**: specific cards in the set the card works with beyond its axes, such as a card that names another, or a two-card combo, each with a strength of 1 to 3.

   The tags are validated by `spa tags-import` (every card exactly once, only the set's own axes, partners only cards in the set) and committed as `fixtures/tags/<code>.json`, with the model, prompt version and date.

   **A synergy score for every pair of cards in the set** (owner, 2026-11-05: "each card in the set should have a score associated with each other card in the set to identify synergy"). Code derives the score for each pair, a and b, from the tags:
   - for each shared axis, a's enabler strength times b's payoff strength, plus b's enabler strength times a's payoff strength;
   - plus any named-partner strength, in either direction.

   **A card with itself** (owner, 2026-11-05: "Synergy scoring also needs to relate to the card itself because you can have doubles"). The table includes each card paired with itself, from the same formula, so a card that both supplies and rewards a theme, or names its own copies, scores with its second copy. Named partners may name the card itself.

   The result is a symmetric set-by-set table, diagonal included, `spa synergy-table --set <code>`, with most pairs at 0. It is computed from the tag file rather than stored, so a change to the formula needs no new model run.

   **A card's synergy in your pool** (owner, 2026-11-05: "synergy should be scored based on the card and the cards in your pool") is the sum of its pair scores with every other copy in the pool, in the deck's colours: each other card's copies through the pair score, and its own extra copies through its score with itself. It is compared with the same sum over a typical pool of the set, so a card is raised only when your pool backs it more than usual. A deck's synergy is the sum over its cards. Both are pluggable and weighted like every other part, and the formula's numbers live in the weights file. Because the tags are a file, synergy works offline on the phone, costs nothing per pool, and is measured over every backtest build, not a sample. `trk` is tagged before any 17Lands `trk` data exists, so its later test stays blind.
8. **The Star Trek grade sheet page** gets a "Decks" view: paste a pool, see the ranked decks (Decided 1). The Architect updates it; it is not a repo ticket.

## Goal-level acceptance criteria

1. **Gate.** Green on `main` at goal close, locally and in CI. No test calls a model or the network; the synergy agent's tests replay recorded responses.
2. **`core` unchanged** unless a ticket names a `core` change the owner approved.
3. **Legal decks.** Every deck the builder emits has exactly 40 cards, only pool cards plus basics, and no card above its pool quantity. Property tests check this over every fixture pool and every colour configuration, and the deck backtest checks it over every build it runs.
4. **Explained.** Each deck's printed score equals the sum of its listed parts, and every card line has a reason. A test checks this over every line `spa build` prints for the trk sample pool.
5. **Fixing pinned.** For each of `msh`, `hob`, `fra` and `trk`, the fixing class of every sealed-pool card equals an independent script's, and a test pins five named cards per set, including at least one basic landcycler where the set has one.
6. **Tuned and held out.** The builder's penalties, bonuses and land rules are tuned on `msh` and `hob` only, then frozen and measured on `fra`. The tuning and the held-out result are recorded in the goal file.
7. **Targets on `fra`** (Decided 2). Over all strong builds: colour agreement 50% or more, top-three rate 80% or more, main-deck overlap 16 or more of the player's nonland cards on average, land count within one 85% or more of the time. On builds with three or more main colours, colour agreement 35% or more. The builder beats the two-colour, set-grades baseline on main-deck overlap overall and on colour agreement for three-colour builds. A miss goes to the owner with the numbers, as in G-003 and G-004.
8. **Synergy tags, measured.** Tag files for `msh`, `hob`, `fra` and `trk` are committed and validate. The `trk` file is dated before the first 17Lands `trk` data file's `Last-Modified`, and the tagging packet holds no win-rate or review text. The synergy part's weight is tuned on `msh` and `hob`. On held-out `fra`, the deck backtest reports main-deck overlap and colour agreement with the part on against off. It ships switched on only if it helps.
9. **Usage.** Each Fable run records its tokens and the weekly Fable percentage before and after.

## Constraints

- **Easy to change how cards and decks are scored** (owner, 2026-11-05: "We are going to be regularly updating the way that cards are evaluated so make sure that the modules used to evaluate cards and the deck itself are easily alterable"). Concretely, for every ticket in this goal:
  - Each scoring rule (a card adjustment such as curve or colour depth; a deck part such as the curve penalty, creature count, removal bonus, castability or fixing bonus; the synergy bonus) is its own class behind one small interface, in its own file, with its own tests.
  - The builder, ranking and printing code see only that interface and a list of parts. Adding, removing or reordering a part touches the list and the part's own file, nothing else.
  - Every weight, threshold and cutoff comes from a versioned JSON file under `fixtures/weights/` (as G-004's `tuned.json` does), selectable with `--weights`, never a constant in code. A part with weight 0 is switched off.
  - Each part writes its own reason line, so a new part explains itself with no change to the printer.
  - The deck backtest runs any weights file, so a change is measured against the last one with one command.
  - A test proves the seam: a test-only part, added to the list, changes the score and prints its reason with no other code change.
- **Deterministic before probabilistic.** Fixing tags, the builder, mana and backtest are code. The only model step is tagging each set once, on the subscription (G-003 Decided 5), with no API key, through recorded files as in G-003's grading.
- **No tuning on the test.** `msh` and `hob` are for tuning; `fra` is held out; `trk` is held out for everything (SPA-040's data, when it comes).
- **Every set is different** (owner, 2026-11-04). Land counts, creature targets, curve shape and how much fixing a set has come from each set's own data, never from fixed numbers that suit one set. `hob` builds are mostly two colours and `fra` builds are often three; the builder must learn that from the pools, not from a set flag.
- **17Lands public datasets only.** Downloads stay out of the repository.
- **One Developer at a time** on this PC.

## Where this departs from the design brief

- The brief prunes ten pairs to three using analysis-agent findings, then builds. Here the builder builds every colour configuration (it is search, and fast) and ranks them by deck score. The analysis agents can shape that ranking later.
- The brief builds "one candidate per pair plus a second variant when a splash is viable". Here splashes and three- and four-colour decks are configurations in their own right (owner, 2026-11-05).
- The brief's Synergy Detector reads parsed keywords. G-004 showed that counting them lowers deck-pick accuracy: a keyword can't tell a card that makes tokens from one that rewards them. Here a model reads each card once per set and tags its role, and code scores synergy from those tags, measured before it is used.

## Out of scope for this goal

The Red Team, the Sideboard Planner's swap plans, the Judge, and the Explainer's written rationale. The other analysis agents. Any `core` change. Editing grades or pinning cards in a UI. Native photo capture. The Arena-inspired redesign. Five-colour decks.

## Metrics to report when the goal closes

The same as G-004, plus: the deck-backtest numbers on `fra`, overall and by deck shape, for the builder and for the baseline; the synergy part on against off, and the tagging runs' Fable usage; and the time `spa build` takes on a pool.

## Decided

The owner answered on 2026-11-05: "Approve all except 2 and 5. Agents are going to have to pick up on the fact that sometimes all of the two color pairs are weak and 3-4 color decks must be formed in order to reach maximum potential. Basic land cycling and color fixing should be considered a higher priority when this is a necessity."

1. **Where you see the decks.** Approved default: the command line plus a "Decks" view on the Star Trek grade sheet page. The phone app gets the deck screens with the Arena-inspired redesign, in a later goal.
2. **Targets on `fra`.** The default (pair agreement 60%, top-three 90%, overlap 17, land count 90%) assumed two-colour decks, and the owner did not approve it. The Architect re-derived it for decks of any colour count (criterion 7): colour agreement 50%, top-three 80%, overlap 16, land count within one 85%, colour agreement 35% on three-or-more-colour builds, and beating the two-colour baseline. The bars are lower than the draft's because matching the exact colour set among two, three and four colours, with or without a splash, is harder than matching one of ten pairs. The owner may set other numbers; until then these stand.
3. **Land count.** Approved default, widened for more colours: 17 lands with 23 spells, 16 with 24 when the deck's mean mana value is at or below a cutoff, and 18 with 22 allowed for decks of three or more colours. The cutoffs are tuned on `msh` and `hob`.
4. **Synergy agent in this goal.** Approved default: yes, run on 60 held-out `fra` builds and kept only if it helps. **Changed by the owner the same day:** "I think that perhaps you should apply tagging to cards in the set, so that way the tags can be leveraged to identify synergy." Synergy now comes from per-set role tags made once by Fable (deliverable 7, criterion 8), not from a model call per pool. That is cheaper (four set runs instead of a call per pool), works offline on the phone, and is measured on every build.
5. **Splashes and more colours.** The default (two colours only) was not approved. Decided by the owner: the builder must build splash, three-colour and four-colour decks when the two-colour pairs are weak, and basic landcycling and colour fixing are valued more when a deck needs them (deliverables 1, 3 and 4).

## Architect notes (2026-11-05)

- G-005 start commit for criterion 2: 2962b0f.
- G-004's deck-pick test counted only two-colour builds (`main_colors` of length 2), which are 27% to 58% of strong builds. Its numbers stand for those builds only; this goal's backtest covers every colour count.
- 17Lands encodes a build's colours as `main_colors` (for example `WUB`) and `splash_colors` (for example `R`). Strong builds per set: `msh` 4,995, `hob` 5,206, `fra` 5,321.
- G-004's `PoolGrader` hard-codes its three adjustments (a sealed `Part` interface) and a fixed `Weights` class, so it does not meet the alterable-scoring constraint. SPA-043 makes it pluggable with no change to any output.
- Order (owner, 2026-11-05: "synergy scoring should come after tagging and we should implement it into the build"). One Developer at a time, all serialized, because they share `agent`, `cli` and the builder:
  1. SPA-042: fixing tags and `spa fixing`.
  2. SPA-047: synergy tags, meaning the tagging packet, `spa tags-import` and the tag schema. Then the Architect's Fable runs on the four sets, `trk` first, before 17Lands publishes its data (expected around 2026-11-20).
  3. SPA-048: synergy scoring from the tags. It covers:
     - the pair table for every two cards in the set (`spa synergy-table`);
     - each card's synergy with the rest of a pool in given colours, against a typical pool;
     - `spa synergy <pool-file> --set <code> [--colours <XY>]`, which prints each card's synergy and its strongest partners in the pool, with reasons.

     It is a pluggable part, for both the card adjustment and the deck score.
  4. SPA-043: pluggable card adjustment parts and weights files. It is a refactor whose `pool-grades` and `deck-pick` outputs stay byte-identical.
  5. SPA-044: the builder and mana for a given colour set, with pluggable deck parts. Synergy (SPA-048) is one of its parts from the first version, not added later.
  6. SPA-045: colour configurations, ranking and `spa build`.
  7. SPA-046: the deck backtest and tuning of every part, synergy included, on `msh` and `hob`, held out on `fra`, with synergy on against off.

  The Architect completes each ticket, with script-pinned numbers, before dispatching it.
- 2026-11-05 Owner's tagging examples (Star Trek): "Andromedan Automatons are a great example of a card that synergizes with itself." "Creatures with ETBs should be recognized as synergizing with 'beam me up'." Checked against the cards:
  - Andromedan Automatons' enters ability searches for "any number of cards named Andromedan Automatons".
  - Beam me up (10 cards in trk) is cast from the graveyard by returning a creature you control to its owner's hand. That replays a creature's enters ability, and trk has 57 creatures with one.
- Owner, the same day: "I don't want you to add these examples to the pipeline, I want these to be leveraged as independent tests to ensure synergy is working properly." So:
  - the tagging prompt stays v1 as written in SPA-047, with no rule drawn from these examples;
  - no self-name rule goes into the code;
  - the examples become **owner synergy checks**: a held-out test of the tags and the pair table that Fable never sees.
- SPA-050 (after SPA-048): `fixtures/synergy-checks/<code>.json` holds the owner's checks, and `spa synergy-check --set <code>` reports each check as pass or fail, with its numbers. The first file, `trk`, holds two checks:
  - **Andromedan Automatons with itself:** its self-pair score in the table is above 0.
  - **Enters abilities with beam me up:** over every pair of one of trk's 57 creatures with an enters ability and one of its 10 beam me up cards, more than half score above 0. Both card lists are frozen in the check file, made by a script from the card text, so the check does not depend on any tagging rule.

  New examples from the owner are added as new checks in the same file.
- **Keeping them independent.** No check file, card list or check wording goes into a packet, a prompt or a scoring rule. If a check fails:
  - the Architect reports it to the owner with the numbers;
  - any fix is a general change to the prompt or the formula, made without looking at which pairs failed, and the owner approves it;
  - after a fix, the failed check has been used once and is marked "seen". Only checks the fix never saw still count as independent.
- Checks run on the committed `trk` tags as soon as SPA-048 merges, and the result goes in this file.
- 2026-11-05 Architect: trk tagged with Fable 5.1 on the subscription, tags prompt v1, as two fresh agents that could read only their packet folder.
  - Pass 1 named 15 axes. Pass 2 tagged all 336 cards: 814 roles, 377 named partners (2 of them a card with itself), 7 cards with no tag.
  - Usage: 136,385 and 196,257 tokens; weekly Fable allowance 22% to 26%.
  - Committed as `fixtures/tags/trk.json` while 17Lands still returns 403 for `game_data_public.TRK.PremierDraft.csv.gz`, which is criterion 8's blind date.
  - The owner's synergy checks were not run and not seen by either agent; they run after SPA-048.
- 2026-11-05 Architect: msh, hob and fra tagged the same way (tags prompt v1, fresh Fable agents per pass, each reading only its packet folder). These sets are for tuning (msh, hob) and the held-out test (fra).
  - msh: 13 axes, 276 cards, 703 roles, 657 partners, 16 untagged; 126,547 and 182,426 tokens.
  - hob: 15 axes, 188 cards, 585 roles, 382 partners, 4 untagged; 106,567 and 155,636 tokens.
  - fra: 14 axes, 280 cards, 825 roles, 833 partners, 6 untagged; 127,130 and 196,305 tokens.
  - Weekly Fable allowance 26% to 33%; all four sets together, 22% to 33%.
- 2026-11-05 Architect: the owner's checks were scored on the committed trk tags by an independent script, before SPA-050's command exists:
  - Andromedan Automatons with itself scores 3 (Fable named it its own partner): pass.
  - 524 of the 590 pairs of the 59 creatures with their own enters trigger and the 10 beam me up cards score above 0 (share 0.888, mean 1.70): pass.

  The enters list is 59 cards, not 57; the first count missed names written with a period. Both checks stay `independent`, because nothing was changed after seeing them.
- 2026-11-05 Owner's second batch of examples: "Lands should have synergy with landfall cards." "Moopsy should have synergy with cards that care about creatures dying." "Star Trek has loads of legendary permanents." "Lands also have synergy with the set's unique land mechanics." Added as four checks to `fixtures/synergy-checks/trk.json`, with lists built from card text (`tools/checklists2.js`). Results on the committed trk tags (independent script):
  - lands (36) with landfall cards (11): 77 of 396 pairs score at least 1, share 0.194. **Fail.**
  - lands with Strange new worlds cards (14): 504 of 504, share 1.000. **Pass.**
  - Moopsy with death payoffs (5): 0 of 5. **Fail.** Moopsy has no tags at all.
  - legendary permanents (130) with legendary payoffs (4, all sds bonus-sheet cards): 22 of 516, share 0.043. **Fail.** trk has no legendary axis; pass 1 named exactly 15 axes, the cap.
  - Running total: 3 of 6 checks pass.
  - The Architect saw the first, third and fourth cases before they were checks, while listing untagged cards for the owner. Nothing was changed because of that.
- 2026-11-05 Owner approved ("Yes do it") the fix the Architect proposed for the three failing checks, which also carries out the owner's point: "There should be tags associated with the cards that assist in identifying synergy."
  - **Card facts:** a deterministic tag layer made by code from card data: card types, legendary, creature types, keywords, cheap (mana value 2 or less), and its own enters, dies and token-making triggers.
  - **Axis supply:** in tags prompt v2, pass 1 names which facts supply each axis, with a strength (for example, landfall from any land, legendary payoffs from any legendary permanent, creature deaths from any creature, more for cheap deathtouch ones). A card's enabler strength on an axis becomes the larger of its own role and its strongest supplying fact.
  - **More axes:** the axis cap rises from 15 to 20.
  - All four sets are retagged with v2. Expected cost about 11% of the weekly Fable allowance.
  - The three failing checks (lands with landfall, Moopsy with death payoffs, legendary permanents with legendary payoffs) are marked `seen`; the three passing ones stay `independent`.
  - Caveat: the "enters trigger" fact uses nearly the same text rule as the enters-with-beam-me-up check's list. If v2 supplies the enters axis from that fact, that check passes partly by construction. It passed under v1, before any fact existed, and that v1 result stands as its independent evidence.
- 2026-11-05 Owner: "Perhaps we should leverage Fable to look over the cards and ensure the proper tags are applied to each card." Planned as **SPA-052, a review pass (pass 3)** after each set's v2 tagging.
  - **What the reviewer gets.** A fresh Fable agent reads a packet holding:
    - the cards;
    - the set's axes with their `suppliedBy` facts;
    - every card's facts;
    - the pass-2 roles and partners.
  - **What it writes.** Corrections only: add, remove or change a role or partner, each with a one-line reason. It also writes suggestions for new fact rules, for cases like Moopsy's self-granted reach. These are reported to the Architect and never applied automatically, since facts are code.
  - **The import.** `spa tags-review-import` validates the corrections against the same checks as pass 2, applies them, and records each one with its reason in the tag file. The provenance names both passes.
  - **Blind.** Like grading: no win rates, no reviews, and none of the owner's synergy checks.
  - **Cost.** About 4% of the weekly Fable allowance per set, so 16% for four sets. With the v2 retag that comes to roughly 60% of the week by the 2026-11-11 reset.
  - **Order.** SPA-050 merges, then SPA-051, then SPA-052. Each set is then tagged with v2, reviewed, and its owner checks run.
- 2026-11-05 Architect: all four sets retagged with tags prompt v2 and reviewed (review v1) by fresh Fable agents. Each agent read only its packet folder, and none saw the owner's checks. trk was committed while 17Lands still returns 403 for TRK data.

  | Set | Axes | Supply links | Roles | Partners | Untagged | Review corrections |
  | --- | --- | --- | --- | --- | --- | --- |
  | trk | 17 | 66 | 456 | 407 | 70 | 7 |
  | msh | 17 | 37 | 369 | 243 | 41 | 4 |
  | hob | 20 | 36 | 280 | 242 | 26 | 23 |
  | fra | 17 | 34 | 399 | 561 | 36 | 8 |

  - There are fewer hand-tagged roles than under v1, and more untagged cards, because facts now supply what roles used to. trk's pair table: 15,333 nonzero pairs (v1: 7,259), maximum 30.
  - Weekly Fable allowance: 33% to 52%. The 5-hour limit is at 79%.
- **Owner checks on v2 trk tags: 5 of 6 pass.**
  - automatons-with-itself (independent): pass, score 3.
  - enters-with-beam-me-up (independent): pass, 494 of 590, share 0.837 (v1: 0.888).
  - lands-with-land-mechanics (independent): pass, 504 of 504.
  - lands-with-landfall (seen): pass, 396 of 396 (v1: 0.194).
  - moopsy-with-death-payoffs (seen): pass, 5 of 5 (v1: 0 of 5).
  - legendary-permanents-with-legendary-payoffs (seen): **fail**, 71 of 516, share 0.138 (v1: 0.043). trk v2 still has no legendary axis: pass 1 judged the four legendary payoffs, all sds bonus-sheet cards, too few. This is reported as is; nothing is changed to chase it.
- **Fact suggestions** from the four reviews are kept in scratchpad `tagging-v2/suggestions/`, for a later fact ticket; none was applied. Suggestions made by two or more sets:
  - draws-card;
  - a sacrifice outlet;
  - puts +1/+1 counters;
  - adds mana;
  - makes a creature token only (makes-token now also counts Treasure, Food and Planet tokens, and tokens given to the opponent);
  - a "for the opponent" marker (a gift Treasure, "its controller amasses").

  trk alone also suggested `modal` (unnamed "Choose one"), beam me up as a fact, a Planet token as a land entering, and a noncreature-artifact fact.
- 2026-11-05 Owner, on Moopsy: "It is how good the card is and not the synergy; remove this as a test of synergy." The check `moopsy-with-death-payoffs` is removed from `fixtures/synergy-checks/trk.json`, leaving 5 checks. Moopsy's value as a cheap creature that trades up belongs to its set grade. The fact-combination supply the Architect floated for it (for example creature with deathtouch) is dropped from SPA-054's plan.
- 2026-11-05 Owner, on tags: "Cares about legendary is different from legendary itself", and the tags carry plain names (`legendary`, `creature`, `land`). Planned as SPA-054, after SPA-043:
  - each card's plain facts (code) are matched against `cares:<fact>` tags that Fable gives each card in a new card pass, with a strength of 1 to 3. A fact needs no theme and no minimum card count to be cared about;
  - the pair score gains a fact term: one card's fact times the other card's `cares:` strength for that fact;
  - new facts from the reviews: `draws-card` and `noncreature`;
  - only trk is retagged and reviewed now (owner: the other sets cost time and are needed only for the backtest). msh, hob and fra are retagged just before SPA-046;
  - same-name cards (Andromedan Automatons) stay on named partners, so that independent check stays unseen.
- 2026-11-05 Owner: "Yes, don't reinvent the wheel." Scryfall publishes its Tagger tags as a bulk file, Oracle Tags (6 MB, 4,601 tags with parents and taggings by oracle id). It covers all 336 trk cards, about 14 tags each with parents.
  - SPA-054 turns these tags into facts, and Scryfall's "matters" and "synergy" tags into `cares:` tags through `fixtures/otags/cares.json` (Architect-curated data).
  - The planned Fable cares pass is replaced by this.
  - Owner: "Fable needs to fill in the gaps where the Scryfall tags haven't been applied." That is SPA-055, after SPA-054: a Fable pass, trk only for now, that adds `cares:` tags only where Scryfall gives none for that idea.
  - The Moopsy check's removal waits for SPA-054, which frees `SynergyV1UnchangedTest` from the live check file.
- 2026-11-05 Owner: mutation probes only at a major release (goal close); per ticket, the Test Inspector runs only the affected test classes. `pipeline/roles/test-inspector.md` updated. G-005's close includes a release inspection with probes.
- 2026-11-05 Owner approved dropping the per-ticket Test Inspector. CLAUDE.md principles 2 and 6, the standing authorizations, and the architect and test-inspector briefs are updated. Per ticket: the Acceptance Tester, plus the Architect's tests-first order check. At G-005's close: one Test Inspector release inspection with probes. A gate step for the commit-order check is planned as a ticket.
- 2026-11-06 Architect: SPA-056 merged. Owner checks on main: 5 of 5 pass (2 independent: automatons-with-itself, lands-with-land-mechanics). enters-with-beam-me-up went from 0.837 to 0.907 (535 of 590). It becomes `seen` once SPA-055 moves SynergyCheckCommandTest onto the frozen check copy (the status change failed that test, which reads the live file). The remaining gap is mostly Transporter Malfunction, which has no enters-triggers payoff tag; SPA-055's Fable gap pass never sees the checks.
- 2026-11-06 Architect (fresh session): SPA-057 recorded as integrated; SPA-055 merged (6804f70) and SPA-044 merged (fd80298), each with a green gate, an Acceptance Tester PASS and a verified tests-first order. The Fable cares pass on trk ran once: Claude Fable 5.1 on the subscription, cares-gaps prompt v1, a fresh agent reading only its packet folder. 105 cards gained cares tags (180 additions); 12 suggestions for `cares.json` were printed and not applied (kept outside the repository for a later ticket). Usage: 249,751 subagent tokens; weekly Fable allowance 57% to 59%. The result is held at `fixtures/otags/trk.fable.json`, not yet committed: `CaresScoringTest` (line 184) and `OtagsFixturesTest` (line 84) pin trk as having no Fable file, so SPA-058 freezes them first (lesson SPA-053 again; the gate refused the push, 2 failures). SPA-045 is next, then SPA-058, then SPA-046; msh, hob and fra get their cares pass just before SPA-046.
- 2026-11-06 Architect status (handoff, context past 300K; start a fresh Architect from `pipeline/prompts/architect-kickoff.md`): SPA-055, SPA-044 and SPA-045 are integrated (PRs 58, 59, 60), each with a green gate, an Acceptance Tester PASS and a verified tests-first order. No Developer is running. Next, in order: (1) dispatch SPA-058 (queued, ticket complete): it freezes two trk tests so `fixtures/otags/trk.fable.json` can be committed; the held file and its SHA-256 are in the ticket. (2) Write SPA-046 (deck backtest, tuning of every part on msh and hob, held out on fra, synergy on against off, plus the `trk` blind date) from deliverable 6 and criteria 6 to 9; before it, tag msh, hob and fra with the Fable cares pass (the packet and import commands exist; only trk is done). Carry into SPA-046: on `trk-manabox-01` the default weights rank WUB/G, BRG/W, WBG/R first, and the `fixing` part may favour configurations that name extra splash colours; `grades` uses set grades, not pool-adjusted ones; `fourColour.minFixing` counts card copies; 12 `cares.json` suggestions from the trk run wait in `.cache/otags/hold/cares-table-suggestions.json`. (3) The Decks view on the Star Trek grade sheet page is the Architect's, not a repo ticket. (4) At close: the Test Inspector release inspection from `pipeline/prompts/inspector-dispatch.md`, then set the goal closed and archive G-005's tickets. Time: `spa build` on `trk-manabox-01` takes 5.2 s. Housekeeping: persistent tester checkout is `D:/spa-acceptance`; old worktrees under `.claude/worktrees/` (SPA-045 mine, agent-* and design-* older) may be removed once idle; two stray scratch files `/tmp_new.txt` and `/tmp_bs.kt` may sit at the root of the drive.
- 2026-11-06 Architect: SPA-058, SPA-059 and SPA-046 integrated (PRs 61, 62, 63). All four sets now have committed Fable cares files (trk 105 cards, msh 91, hob 77 and fra 115 gained tags; tokens 211K, 181K, 242K for msh, hob, fra; weekly Fable 60% to 66%). `spa deck-backtest` works on real data: about 3 s per build, 0.63 builds/s on 2 threads. First numbers on hob (200 builds, default untuned weights): colour agreement 15.5% against the baseline 37.0%, top3 40.0% against 66.5%. Open question for after tuning: 1,119 msh and 583 fra strong builds (22% and 11%) are dropped as unresolved names, none on hob; the cause is not yet known (checked: the name resolver is `CardIndex.byName` on the set pool). SPA-060 (tuner) is next; then the tuning run on msh and hob, the fra held-out, synergy on against off.
- 2026-11-06 Owner: "We are running out of time, rather than 200+ samples per set do 50 from here on." Every remaining measurement run in this goal (the fra held-out backtest, synergy on against off, the score-against-results check) uses --limit 50 builds per set. The tuning run already in progress uses 60 per set and finishes as started. At 50 builds a rate has a standard error of about 7 percentage points, so the goal's criterion 7 and 8 numbers are reported with that caveat and cannot separate differences of a few points.
- 2026-11-06 Owner, revising the line above: "Use 30 then we can always fine tune this afterwards." Remaining measurement runs use --limit 30 builds per set, not 50. At 30 builds a rate has a standard error of about 9 percentage points; the numbers are a first reading to be refined later with larger samples, not a pass or fail on the goal's targets.
- 2026-11-06 Owner: fra is a very unique set, skip tuning on it (it was never tuned on: the tuner refuses it), then "Drop it and move forward": the held-out fra measurement is dropped. Consequences recorded by the Architect: goal criteria 7 (the fra targets) and 8 (synergy on against off, measured on fra) are not measured at close; the tuned weights are frozen from msh and hob only; fra stays untouched and held out, so a later run with a larger sample can still use it. The goal closes on its other criteria. The tuning run on msh and hob (60 builds per set, 2 rounds) finishes as started.
- 2026-11-06 Owner, on what the agreement numbers mean: the builder is meant to judge cards objectively; players choose by taste and playstyle, and a different route is often good. So colour agreement, top-three and overlap with the player are reported at close as how often the builder's choice differs from the player's, not as a pass or miss against criterion 7's targets. The measure of whether a deck is strong is results: whether higher builder scores go with higher win rates (a score-against-results check on held-out builds, a small ticket if time remains). One open worry the agreement numbers cannot settle: msh colour agreement is 0 of 13 for plain two-colour builds, which may be taste or a builder habit (for example the fixing part favouring extra colours); only the results check can tell.
- 2026-11-06 Architect: Test Inspector release inspection of G-005 (SPA-054, 055, 044, 045, 046, 060; Claude Sonnet 5.5, effort high, 40 of 80 tool calls, 8 of 8 mutation probes killed): PASS on all six; verdicts are in each ticket's history. The inspector's brief was narrowed to inspect-only that day (owner). It once ran a checkout in the main repository by mistake and restored it; the main repository was verified clean. Tuned weights frozen in `fixtures/weights/deck-tuned.json`.
- 2026-11-06 Architect status (handoff to a fresh Architect, owner asked for a new session to conserve context; owner deadline 3PM 2026-11-06): every G-005 ticket is integrated (SPA-042 to SPA-060 except SPA-057's note above; no Developer is running). The Test Inspector release inspection passed (see above). Tuned weights are frozen in `fixtures/weights/deck-tuned.json`. Owner decisions today: remaining measurement runs use 30 builds per set; the fra held-out measurement is dropped (criteria 7 and 8's fra numbers are not measured); agreement with players is reported as how often the builder differs, not as a pass or miss; the Test Inspector only inspects. Left to do, in order: (1) confirm CI on `main` is green (the last runs were in progress); (2) decide deliverable 8, the Decks view on the Star Trek Grade Sheet artifact (https://claude.ai/artifact/YB3b4YQXjr6Y79j7jWLjZv): a paste-a-pool view needs the builder ported to JavaScript, which is a project of its own, so the default is to leave it for a later goal, or add a tab showing the real `spa build` output for the sample pool `fixtures/pools/trk-manabox-01.csv` with `--weights fixtures/weights/deck-tuned.json`; (3) close G-005: set `status: closed`, `git mv` its integrated tickets (every ticket with `goal: G-005` and status integrated) into `tickets/archive/` in the same commit, per the Goal close section of the architect brief; (4) if time remains, a small ticket for the score-against-results check (does a higher builder score go with a higher win rate, held-out builds, --limit 30). Housekeeping: persistent tester checkout `D:/spa-acceptance`, inspector checkout `D:/spa-inspection`, CLI copy for tuning `D:/spa-tune-bin`, data under `D:/sealed-pool-analyzer-app/.cache/` (17lands, otags, tune), old worktrees under `.claude/worktrees/` can be removed once idle; two stray files `/tmp_new.txt` and `/tmp_bs.kt` may sit at the drive root.
- 2026-11-06 Architect (fresh session), goal close:
  - **Deliverable 8 (Decks view).** Pasting a pool and getting decks on the grade sheet page needs the builder ported to JavaScript, a project of its own: left for a later goal. The page's new "Decks" tab instead shows the real `spa build` output (top three decks, tuned weights) for the sample pool `fixtures/pools/trk-manabox-01.csv`, with a note that it is a preview of one pool. Syntax-checked; not viewed live (the built-in browser was not signed in to claude.ai).
  - **Not measured:** criterion 7 (fra targets) and criterion 8 (synergy on against off on fra), per the owner's 2026-11-06 decisions above. fra stays held out.
  - **Open worries for a later goal:** msh colour agreement is 0 of 13 for plain two-colour builds (taste or a builder habit, such as the fixing part favouring extra colours; only a score-against-results check can tell); 22% of msh and 11% of fra strong builds drop as unresolved card names (cause not found).
  - **Next:** the score-against-results check (does a higher builder score go with a higher win rate on held-out builds, `--limit 30`), then SPA-040 (G-004, queued, left in place).
  - Goal closed, its integrated tickets (SPA-042 to SPA-060) moved to `tickets/archive/`.
- 2026-11-06 Architect, after close: SPA-061 (`spa score-backtest`, PR #65, merged 3857d31, archived) answers the owner's results question. On every build with a bucket, scoring the deck the player registered with the tuned builder weights goes with a higher win rate bucket, weakly: Spearman 0.089 (msh), 0.129 (hob), 0.093 (fra), `se` about 0.015; top quarter minus bottom quarter 0.04 to 0.05 of win rate. **Grades alone do as well or better** (0.112, 0.167, 0.122; gaps 0.045, 0.070, 0.051), so the builder's extra parts add nothing to predicting results on registered decks. Caveats: a bucket is the player's win rate, so skill is in it; 45% to 55% of builds were skipped (`off-colour`: a registered card outside the setup's candidates, cause not found; unresolved names 22% msh and 11% fra; not-40), so the numbers describe the other builds; fra was used here for the first time (a check, not tuning). Next, for a later goal: find why half the builds are `off-colour`, then re-check; and ask whether the parts are tuned for agreement with players rather than results.
