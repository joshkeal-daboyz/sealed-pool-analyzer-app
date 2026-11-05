---
id: G-004
title: Layered grades, from the set to your pool
status: draft
owner: human
target_set_code: trk
companion_set_codes: [sds]
depends_on: [G-003]
---

# G-004: Layered grades, from the set to your pool

The owner's direction (2026-11-04): grade in layers. G-003 built the first layer, a grade for each card against its whole set. This goal makes that layer better and adds the layers on top: what the set's own synergies say about a card, and how much a card is worth in your pool, given its synergies with your other cards, your curve and your colours. Each layer is measured against what strong players actually did.

Drafted by the Architect on 2026-11-04 for the owner to edit and approve. The open questions are at the end, each with its default.

## Why this goal next

- G-003's backtest averaged 0.41 against a 0.45 target. The grader is good on rares and mythics and weak on commons and uncommons, which make up most of a sealed deck (G-003 Decided 4, the miss).
- A card's worth depends on the pool. A payoff with no enablers in your pool is filler; a 2-drop in a pool short of 2-drops is gold. The design brief's analysis agents and deck builder need that view, and it is most of what a player wants at the table.
- It can be measured. 17Lands' public Sealed data records each player's whole pool and the deck they registered: about 12,000 to 15,000 builds per set for `msh`, `hob` and `fra`, each with a skill bucket (checked 2026-11-04). "Do the adjusted grades pick the cards strong players played?" is a number, not an opinion.

## The layers

1. **Set grade, version 2.** The same blind grading as G-003, with improved instructions and with layer 2's synergy counts in the packet.
2. **Set synergy counts.** Deterministic code tags each card's themes (its keywords, its creature types, the card types and mechanics its rules text refers to, such as Spacecraft or Station) and counts, for each theme, how many cards in the set share it, by colour and rarity. The grader sees these counts, so "this payoff has 14 enablers at common in its colours" is a fact it reads, not a guess.
3. **Pool adjustment.** For a given pool and colour pair, each card's grade moves up or down from its set grade, by deterministic rules, each with a one-line reason:
   - **Synergy:** how many of the pool's other cards in those colours share its themes, compared with a typical pool.
   - **Curve:** whether the pair is short of or crowded at the card's mana value, compared with the set baseline's typical curve.
   - **Colour depth:** how deep the card's colours are in this pool.

   The adjustment is capped, so a card can't move more than a set amount. No model call: this layer is code over the metric engine's numbers, so it is free, instant, and works offline on the phone later.

## Deliverables

1. **Theme tags and set synergy counts** (layer 2), in `agent`, with a `spa themes <set>` command that prints each theme and its counts.
2. **Prompt v2 and v2 grade files** (layer 1). Instructions revised using what G-003 showed on `msh`, `hob` and `fra`, with the synergy counts added to the packet. `trk` is graded with v2 by Claude Fable 5.1 on the subscription **before 17Lands publishes any `trk` data**, and that file is frozen, so the later test on `trk` is blind.
3. **Pool adjustment** (layer 3): `spa pool-grades <pool-file> --set <code> [--pair <XY>]` prints, for each colour pair (or one pair), the pool's cards ranked by adjusted grade, each with its set grade, its adjustment, and the reasons.
4. **The deck-pick test**: `spa deck-pick --set <code>` reads a 17Lands Sealed game data file and, for each build by a strong player (open question 2), ranks that player's pool within the colours they played and measures how many of the top N are cards they actually registered, where N is their deck's nonbasic card count. It reports the mean overlap for set grades alone, for adjusted grades, and for a random ranking.
5. **Win-rate tables and grades for `trk`** once 17Lands publishes them, as G-003's SPA-031 did for the other sets, so the v1 and v2 backtests and the deck-pick test run on `trk`.
6. **The grade sheet page** updated so "My pool" shows the pool-adjusted ranking by colour pair, with the reasons (open question 1).

## Goal-level acceptance criteria

1. **Gate.** Green on `main` at goal close, locally and in CI. No test calls a model or the network.
2. **`core` unchanged** unless a ticket names a `core` change the owner approved. Layers 2 and 3 live in `agent` and `cli`.
3. **Themes.** `spa themes trk` lists each theme with counts by colour and rarity. A test pins the counts for three themes in `trk` against a script that counts them from the Scryfall cache.
4. **Blind v2 on `trk`.** `fixtures/grades/trk.v2.json` is committed with a date before the first 17Lands `trk` data file's `Last-Modified`, and the packet contains no win-rate text, as in G-003.
5. **Pool adjustment explained.** Every adjusted grade `spa pool-grades` prints equals the set grade plus the listed adjustments, each adjustment has a reason line, and the total stays within the cap. Tests pin a hand-worked pool for each rule.
6. **Deck-pick, tuned and held out.** The adjustment's weights are tuned on `msh` and `hob` only, then frozen and measured on `fra`. On `fra`, adjusted grades beat set grades alone by the target in open question 3. The tuning and the held-out result are recorded in the goal file.
7. **`trk`, the clean test.** When `trk` data exists: the v1 and v2 backtests on `trk`, and the deck-pick result on `trk` with the frozen weights, are recorded. v2 beats v1 by the target in open question 3. A miss goes to the owner with the numbers, as in G-003.
8. **Usage.** Each Fable grading run records its tokens and the weekly Fable percentage before and after.

## Constraints

- **Deterministic before probabilistic.** Layers 2 and 3 are code. The only model step is layer 1's grading, on the subscription (G-003 Decided 5), with no API key.
- **No tuning on the test.** `msh` and `hob` are for tuning the adjustment; `fra` is held out for it. `trk` is held out for both the prompt and the adjustment. Prompt v2 may be developed on `msh`, `hob` and `fra`, which G-003 already spent as its prompt test.
- **Every set is different** (owner, 2026-11-04). Themes come from each set's own data; nothing assumes `trk`'s structure.
- **17Lands public datasets only**, never its site-only endpoints (SPA-031). Downloads stay out of the repository; derived tables are small and committed.
- **One Developer at a time** on this PC.

## Where this departs from the design brief

- The brief's Synergy Detector is an analysis agent that reads the pool with a model. Here the pool's synergy is counted by code first. A model can add judgement on top later, once the counted version is measured.
- The brief's Curve Analyst and Color Depth Analyst are "thin wrappers over the metric engine". Layer 3's curve and colour rules are exactly that, minus the sentence a model would write; the reason lines stand in for it.

## Out of scope for this goal

The deck builder, the other analysis agents, the Judge and the Explainer. Any app change (open question 1). Editing grades by hand in a UI. The flexible brief format and bonus-sheet detection (the multi-set goal). Fuzzy name matching.

## Metrics to report when the goal closes

The same as G-003, plus: the deck-pick overlaps (random, set grade, adjusted) on `fra` and `trk`; the v1 and v2 backtests on `trk`; and Fable usage per graded set.

## Open questions

Each has a default. An unanswered question takes its default.

1. **Where you see it.** Default: the command line, plus the Star Trek grade sheet page (updated by the Architect, not a repo ticket) showing your pool's adjusted ranking by colour pair. The phone app gets it with the deck builder, in a later goal.
2. **Who counts as a strong player** in the deck-pick test. Default: 17Lands' `user_game_win_rate_bucket` of 0.60 or higher, which keeps a few thousand builds per set.
3. **Targets.** Default: on `fra` (held out), adjusted grades beat set grades alone by 2 percentage points or more of mean overlap. On `trk`, v2's backtest beats v1's by 0.05 or more, and v2 reaches 0.45.
4. **Waiting for `trk` data.** Default: everything except deliverable 5 and criterion 7 runs now. If 17Lands has not published `trk` data by 2026-12-01, the goal closes on the `fra` result and `trk` becomes a follow-up ticket.
5. **The model.** Default: Claude Fable 5.1 on your subscription, as in G-003.
6. **Adjustment cap.** Default: a card moves at most 1.0 up or down in total from its set grade, so a C card can't jump to an A.
