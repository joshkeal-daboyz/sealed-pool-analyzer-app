---
id: G-005
title: Build the deck, ranked decks for a pool
status: draft
owner: human
target_set_code: trk
companion_set_codes: [sds]
depends_on: [G-004]
---

# G-005: Build the deck, ranked decks for a pool

The owner asked for this goal on 2026-11-05 ("Go ahead and start five"), right after closing G-004. This is the step a player sees: give the app a pool, get back the 40-card decks it could build, ranked, each with its lands and a reason for every card. Drafted by the Architect from the design brief (Deck Builder, Mana Engineer, the backtest protocol) and from what G-004 measured.

## Why this goal next

- The roadmap orders it. Grades exist (G-003) and move with the pool (G-004). The design brief's Deck Builder is the next consumer, and the Judge and Explainer need its candidates.
- It is measurable, with data we already hold. 17Lands' public Sealed data (G-004) gives each strong player's whole pool and the deck they registered. The brief's own backtest metrics apply directly: does the top deck use the player's colour pair, how many of their spells it shares, and is its land count within one.
- G-004 left one thing for here. Counted synergy hurt at every weight, and the owner moved synergy judgement by a model reading the pool into this goal (G-004, 2026-11-05).

## Deliverables

1. **The deck builder** (search, no model call, as the brief decided on 2026-11-03). For each colour pair, it picks the 22 or 23 spells with the best total score and fills the rest with lands. The score is the sum of the cards' pool-adjusted grades (G-004's tuned weights), minus penalties for curve gaps and too few creatures, plus a bonus for removal. The search is a greedy fill followed by local swaps. It is deterministic, and every included card carries a numeric reason.
2. **Mana.** A land count from the deck's curve (open question 3), and basics split by the deck's coloured mana symbols. The builder plays the nonbasic lands from the pool that make the pair's colours.
3. **`spa build <pool-file> --set <code> [--pair <XY>] [--top <n>]`.** It prints the ranked decks, by default the top three pairs. Each deck shows its 40 cards by mana value, its land split, its score broken into parts, and its sideboard. Each card has a short reason (its grade, plus anything the curve, creature count or removal added).
4. **The deck backtest: `spa deck-backtest --set <code>`.** For each strong player's build in a 17Lands Sealed file (G-004 Decided 2), the builder runs on that player's pool and reports four numbers:
   - **Pair agreement:** the top deck's pair is the player's pair.
   - **Top-three rate:** the player's pair is in the top three.
   - **Main-deck overlap:** how many of the player's registered nonland cards the builder's deck for the player's pair also plays.
   - **Land count:** the builder's land count is within one of the player's.

   It also reports the same numbers for a baseline builder that uses set grades alone.
5. **Synergy judgement by a model** (open question 4). A Fable agent reads one pool's cards in one pair and names the synergy groups it sees, each naming the pool's cards that make it work. The builder turns each named group into a score bonus. It is measured on a sample of held-out builds, against the builder without it.
6. **The Star Trek grade sheet page** gets a "Decks" view: paste a pool, see the ranked decks (open question 1). The Architect updates it; it is not a repo ticket.

## Goal-level acceptance criteria

1. **Gate.** Green on `main` at goal close, locally and in CI. No test calls a model or the network; the synergy agent's tests replay recorded responses.
2. **`core` unchanged** unless a ticket names a `core` change the owner approved.
3. **Legal decks.** Every deck the builder emits has exactly 40 cards, only pool cards plus basics, and no card above its pool quantity. Property tests check this over every fixture pool and every pair, and the deck backtest checks it over every build it runs.
4. **Explained.** Each deck's printed score equals the sum of its listed parts, and every card line has a reason. A test checks this over every line `spa build` prints for the trk sample pool.
5. **Tuned and held out.** The builder's penalties and bonuses are tuned on `msh` and `hob` only, then frozen and measured on `fra`. The tuning and the held-out result are recorded in the goal file.
6. **Targets on `fra`** (open question 2). Pair agreement, top-three rate, main-deck overlap and land count each meet their target, and the builder beats the set-grades-only baseline on main-deck overlap. A miss goes to the owner with the numbers, as in G-003 and G-004.
7. **Synergy agent, measured** (if open question 4 keeps it). On a fixed sample of held-out `fra` builds, main-deck overlap with the synergy bonus is reported against the builder without it. The bonus ships switched on only if it helps. Each finding names only cards in the pool; the import step drops any finding that names a card the pool lacks.
8. **Usage.** Each Fable run records its tokens and the weekly Fable percentage before and after.

## Constraints

- **Deterministic before probabilistic.** The builder, mana and backtest are code. The only model step is the synergy agent, on the subscription (G-003 Decided 5), with no API key, through recorded files as in G-003's grading.
- **No tuning on the test.** `msh` and `hob` are for tuning; `fra` is held out; `trk` is held out for everything (SPA-040's data, when it comes).
- **Every set is different** (owner, 2026-11-04). Land counts, creature targets and curve shape come from each set's own baseline, never from fixed numbers that suit one set.
- **17Lands public datasets only.** Downloads stay out of the repository.
- **One Developer at a time** on this PC.

## Where this departs from the design brief

- The brief prunes ten pairs to three using analysis-agent findings, then builds. Here the builder builds all ten (it is instant) and ranks them by deck score. The analysis agents can shape that ranking later.
- The brief's Synergy Detector reads parsed keywords. G-004 showed that counting them lowers deck-pick accuracy, so here it is a model reading the cards, measured before it is used.
- Splashes (a second variant when a splash is viable) are open question 5.

## Out of scope for this goal

The Red Team, the Sideboard Planner's swap plans, the Judge, and the Explainer's written rationale. The other analysis agents. Any `core` change. Editing grades or pinning cards in a UI. Native photo capture. The Arena-inspired redesign.

## Metrics to report when the goal closes

The same as G-004, plus: the four deck-backtest numbers on `fra` for the builder and for the set-grades baseline; the synergy agent's sample result and its Fable usage; and the time `spa build` takes on a pool.

## Open questions, each with its default

1. **Where you see the decks.** Default: the command line plus a "Decks" view on the Star Trek grade sheet page. The phone app gets the deck screens with the Arena-inspired redesign, in a later goal, since that is a visual review of its own.
2. **Targets on `fra`.** Default: pair agreement 60% or more, top-three rate 90% or more, main-deck overlap 17 or more of the player's spells on average, land count within one 90% or more of the time, and better overlap than the set-grades baseline. The brief's targets (80% and 18) stay the long-run aim; this goal has no Judge or analysis agents yet.
3. **Land count.** Default: 17 lands with 23 spells. 16 lands with 24 spells when the deck's mean mana value is at or below a cutoff tuned on `msh` and `hob`.
4. **Synergy agent in this goal.** Default: yes, as the last ticket, run on 60 held-out `fra` builds (about 60 Fable calls on the subscription), and kept only if it raises main-deck overlap.
5. **Splashes.** Default: no splashes in this goal; every deck is two colours plus colourless cards. A splash variant comes with the Mana Engineer's odds, in a later goal.
