# Sealed Pool Analyzer

An Android app that takes the cards a player opens at a Magic: The Gathering prerelease and recommends ranked 40-card decks with a written rationale and a sideboard plan.

This repository is built through an agentic development pipeline: a central Architect agent writes tickets from goals, Developer agents implement them in their own worktrees, an automated gate runs formatting, lint, type checks, and tests before any model reads a branch, and two Testing agents inspect what passes. The pipeline's own files live here beside the code they govern.

## Layout

| Path | What it is |
| --- | --- |
| `CLAUDE.md` | The rules every agent reads first |
| `pipeline/` | Role briefs, the gate contract, the ticket template, kickoff prompts |
| `tickets/` | Goals, tickets, and the lessons file; one markdown file per ticket |
| `design/` | Design specs written by the Designer, one per feature with a UI |
| `fixtures/` | Real and synthetic pools used by tests (created by the first goal) |
| `core/`, `app/` | The Kotlin core library and the Android app (created by tickets, not by hand) |
| `agent/` | The agent layer, the only module that calls a model: card grades, the API key from `ANTHROPIC_API_KEY`, and a model client whose tests replay recorded responses |

## Design documents

The product design and the pipeline design live in a separate repository: [sealed-pool-analyzer-designdoc](https://github.com/joshkeal-daboyz/sealed-pool-analyzer-designdoc). Read `docs/design-brief.md` there for what the app is, and `docs/agentic-development-pipeline.md` for how this repository is worked.

## Starting work

Open a Claude Code session in this repository and paste the prompt in `pipeline/prompts/architect-kickoff.md`. The Architect reads the first goal, writes the backlog, and stops for approval before dispatching anything.

## Card data

Card data comes from [Scryfall](https://scryfall.com) and is used under Scryfall's terms. Sealed Pool Analyzer is unofficial Fan Content: it is not produced by, endorsed by, or affiliated with Scryfall or Wizards of the Coast, and Magic: The Gathering is a trademark of Wizards of the Coast. The card data in `fixtures/scryfall/` is a snapshot, refreshed deliberately by a ticket rather than on every build; `fixtures/scryfall/README.md` records when each set was fetched and how to refresh it.

Play data from [17Lands](https://www.17lands.com), free for non-commercial use. The win-rate tables in `fixtures/winrates/` are derived from 17Lands' public game data files; the downloads themselves are not in this repository. `fixtures/winrates/README.md` names each file and its date.

## Command line

The `cli` module builds `spa`, the command line the metric engine is tested through until there is an app. Install it from the repository root (needs the JDK in [Toolchain](#toolchain)):

```bash
./gradlew :cli:installDist
```

This writes `cli/build/install/spa/bin/spa` and, for Windows, `spa.bat`. `spa --help` lists the commands. Every command reads its data from `--data <dir>`, default `fixtures` relative to the current directory, so run it from the repository root or pass `--data`. That directory holds Scryfall caches in `scryfall/<code>/`, briefs in `briefs/<code>.json`, baselines in `baselines/<code>.json`, win-rate tables in `winrates/<code>.json`, and model grade files in `grades/<code>.json`. Exit codes: 0 ok, 1 an error (a missing or unreadable file, set cache, or brief, a missing `--set`, or a network error), 2 a bad command line (no command, an unknown command, or a bad flag). Errors are one line on stderr.

**analyze** parses a pool file and prints one JSON document to stdout: `tool` (name and version), `set`, `pool` (the file as given, the detected or given `format`, `entries` as the count of resolved pool entries, `skippedLines`, every `unresolved` line with its `line`, `text`, and `reason`, and the brief's `kitWarnings`, each with an `expected` range written as `min` and `max`), `metrics` (the engine's numbers), and the set's `baseline`. The format is detected unless `--format manabox|text|collector` is given; `--brief <file>` replaces `briefs/<code>.json` for kit warnings. `baseline` is `null` with `--no-baseline` or when the set has no baseline file, and stderr says which. Unresolved lines and kit warnings still exit 0. Keys print in a fixed order, so the same input prints the same bytes.

```bash
cli/build/install/spa/bin/spa analyze fixtures/pools/trk-manabox-01.csv --set trk
```

**validate-brief** checks a brief against its schema and rules. A valid brief prints `OK <file>` on stdout, then one `warning: <pointer>: '<name>' is not a card in set '<code>'` line for each signpost uncommon or key common the set's cards do not include, and exits 0. An invalid brief prints `<file>: <n> error(s)` and one `error: <pointer>: <message>` line per problem on stderr, and exits 1.

```bash
cli/build/install/spa/bin/spa validate-brief fixtures/briefs/trk.json --set trk
```

**fetch-set** refreshes one set's Scryfall cache under `<data>/scryfall/<code>/` and prints `<code>: <n> cards in <dir>`. `--companions a,b` writes those codes to the set's `companions.json` and refreshes each companion's cache too; without it, an existing `companions.json` is kept and a missing one is written as `[]`. It needs the network and exits 1 on a network or Scryfall error.

```bash
cli/build/install/spa/bin/spa fetch-set trk --companions sds
```

**winrates** derives a set's win-rate grade table from a 17Lands public game data file (`game_data_public.<SET>.PremierDraft.csv.gz`, gzipped or plain) and writes it to `<data>/winrates/<code>.json`, a grade file with source `WIN_RATE`. A card's GIH WR is its win rate over the games where it was in the opening hand or drawn. Each card with at least 500 such games gets a score of `2.5 + (gihwr - m) / s`, clamped to 0 to 5 and rounded to the nearest 0.5, where `m` and `s` are the mean and population standard deviation of GIH WR over the set's cards with 500 or more games, and a confidence of `games / (games + 2000)`; its `gihwr` and `games` are kept in the file. It prints the game and card counts, the scale, one `unmatched: <name>` line for each 17Lands name that is no card of the set (skipped), and the path written, and exits 0. The provenance names the file and its `Last-Modified` date, from `--dataset-date <yyyy-mm-dd>` or else the file's modified date. A missing file prints one line and exits 1.

```bash
cli/build/install/spa/bin/spa winrates ~/Downloads/game_data_public.MSH.PremierDraft.csv.gz --set msh --dataset-date 2026-07-27
```

**grade-backtest** scores a model grade file against a win-rate grade file: `--model <file>` (default `<data>/grades/<code>.json`) and `--winrates <file>` (default `<data>/winrates/<code>.json`). Over the cards the win-rate file grades that the model file grades too, matched by set code and collector number, it computes Spearman's rho between the model's scores and the win-rate side's GIH WR (`gihwr`, or `score` for an entry without one): each list is ranked, ties taking the average of the ranks they span, and rho is the Pearson correlation of the ranks. It prints one line for `all` cards, then `common`, `uncommon`, `rare` and `mythic` (rarity from the set's Scryfall cache), each `<group>: rho <rho to 3 places>, n <cards>`; a group with fewer than 5 cards, or whose scores are all equal, prints `rho n/a`. Then the model file's provenance, the win-rate file's provenance, the count of win-rate cards missing from the model file, and one `missing: <name> (<set> <number>)` line for each, and exits 0. A missing or invalid file prints one line naming it and exits 1. It makes no network or model call.

```bash
cli/build/install/spa/bin/spa grade-backtest --set msh
```

**grade-packet** writes the blind grading packet for a set's sealed pool (the set and its companions, basic lands excluded, one entry per distinct card) into `--out <dir>`: `instructions.md` (the grading prompt, `agent/src/main/resources/spa/agent/grader/prompt-v1.md`, then the set's format brief and a description of the files), `response.schema.json`, and `cards-01.json`, `cards-02.json`, ... with 50 cards each in collector order. Each card is `{ id, name, manaCost, typeLine, rarity, oracleText, power, toughness }`, with `id` as `<set>/<collector number>` and the faces of a multi-face card joined with ` // `. The packet holds no win rates. The same inputs always give the same bytes; earlier packet files in `<dir>` are replaced and response files are kept. It prints the card and file counts and exits 0. `--prompt <file>` puts that file's text (line feeds only, trailing blank lines dropped) at the top of `instructions.md` in place of prompt v1; the rest is unchanged. `--themes` adds a `## Themes in this set` section after the format brief, one line per kept theme (as `spa themes` keeps them) with its kind, its has and refers counts and the either count by colour, and gives each card `"themes": [{ "name", "role" }]`, role `has` or `refers` (a card that has a theme and refers to it lists it once, as `has`). Without either option the packet is byte for byte the one SPA-033 wrote. The response format and `grade-import` are the same either way.

**grade-import** reads the packet in `--responses <dir>` and the grader's `cards-NN.response.json` beside each card file, each a JSON array of `{ "id", "score", "confidence" }`. It checks that every card id is graded exactly once, that no id is unknown, that every score is 0.0 to 5.0 in steps of 0.5 and every confidence is 0 to 1, and writes `<data>/grades/<code>.json`, a grade file with source `MODEL` and the given `--provenance`. On any problem it prints one line per problem, naming the file and the card, writes nothing, and exits 1. `fixtures/grades/README.md` describes the grading run. Neither command calls a model.

```bash
cli/build/install/spa/bin/spa grade-packet msh --out ../grading/msh
cli/build/install/spa/bin/spa grade-import msh --responses ../grading/msh --provenance "claude-fable-5-1 via Claude Code, prompt v1, 2026-11-05"
```

**grades** parses a pool file (any format `analyze` reads) and prints one line per pool entry: grade, letter label, confidence, source, count, name, and set and collector number. Grades come through the grade lookup from `<data>/winrates/<code>.json` and `<data>/grades/<code>.json`, whichever exist, preferring the win-rate grade; a card is matched by printing, else by name. Graded entries come first, highest grade first and ties by name; entries with no grade (basic lands, which grade files never hold) follow, by name, with `-` for grade, label and confidence and source `none`. Lines that do not resolve to a card are listed on stderr; the command still exits 0. No grade file for the set prints one line and exits 1. It makes no network or model call.

```bash
cli/build/install/spa/bin/spa grades fixtures/pools/trk-manabox-01.csv --set trk
```

```text
4.0  A−  0.90  MODEL     1x He's Dead, Jim (trk 114)
...
-    -   -     none      3x Swamp (trk 309)
```

Letter labels, relative to the card's set (G-003 Decided 2):

| Score | 5.0 | 4.5 | 4.0 | 3.5 | 3.0 | 2.5 | 2.0 | 1.5 | 1.0 | 0.5 | 0.0 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Label | A+ | A | A− | B+ | B | C+ | C | C− | D | D− | F |

**themes** tags each card of the set's sealed pool (the set and its companions, basic lands out, one card per oracle id) with themes and counts them. A keyword theme is a keyword in the card's Scryfall `keywords` (read from `<data>/scryfall/<code>/cards.json` and its companions'), unless it is evergreen (Flying, Trample, Ward, Scry and the like); a mechanic the set's brief names is always a theme. A type theme is any subtype on a face's type line. A card has a theme when the keyword is in its keywords or the subtype on its type line; it refers to a theme when its rules text, without reminder text, its own names and every `this <theme>`, names the theme as a whole word (any case, optional plural `s`) and it does not have the theme as a keyword. A theme with 3 or more cards that have it or refer to it is printed, one line each, most cards first: name, kind, the has, refers and either counts, then the either count by colour (W, U, B, R, G, M multicoloured, C colourless) and rarity (C, U, R, M). It exits 0; a set with no cache prints one line and exits 1. It makes no network or model call.

```bash
cli/build/install/spa/bin/spa themes trk
```

```text
Human type has=68 refers=1 either=69 colour W=15 U=13 B=5 R=9 G=11 M=15 C=1 rarity C=5 U=26 R=16 M=22
Officer type has=34 refers=15 either=45 colour W=9 U=8 B=7 R=8 G=6 M=5 C=2 rarity C=10 U=16 R=11 M=8
...
```

**pool-grades** moves each card's set grade up or down for one pool and one colour pair, by fixed rules, and prints the reason for every move. Set grades come from `--grades <file>`, else `<data>/grades/<code>.v2.json` if it exists, else `<data>/grades/<code>.json`, matched by printing, else by name. The weights come from `--weights <file>`, else `<data>/weights/default.json` (see `fixtures/weights/README.md`). A pair's candidates are the pool's nonland copies whose colours are within the pair's, colourless included. Each candidate gets four parts, in this order, each clamped to plus or minus its weight; a part whose weight is 0 or missing from the weights file is off. Each part is its own `CardPart` in `spa.agent.poolgrade`, listed in `CardParts.ALL`, weighted by its key and writing its own reason line:

- **synergy**: `n`, the candidate copies other than one copy of the card that are its partners (a partner has a theme the card refers to, or has or refers to a theme the card has, as `spa themes` tags them), against `e`, the share of partners among the set's sealed-pool nonland cards that fit the pair times the candidates less one: weight × (n − e) / max(e, 1). A card with no theme has none.
- **curve**: the pair's cards at the card's mana value (7 or more share a bucket) against the baseline's typical count `m`: weight × (m − count) / max(m, 1). A short bucket raises the card; a crowded one lowers it.
- **colour**: for each of the card's colours, the pool's playables of that colour against the baseline's typical count, (playables − typical) / max(typical, 1); the weight times the smallest. A colourless card has none.
- **tagSynergy** (weight 0 in `default.json` and `tuned.json`): the card's tag synergy part as `spa synergy` computes it, weight × clamp((syn − e) / max(e, 1), −1, 1), with the reason line `tag synergy +0.13: 26 with the pool, 20.69 typical (<up to three partners>)` or `(no partners in the pool)`. The tags come from `--tags <file>`, else `<data>/tags/<code>.json`, and are read only when the weight is above 0; with weight 0 a missing tag file is no error.

The adjusted grade is the set grade plus the parts' sum, the sum capped at plus or minus `cap`, kept within 0 to 5. Each pair (all ten, highest mean first, or the one `--pair` names, as `WU` or `UW`) prints a heading with the mean adjusted grade of its best 23 candidate copies, then each distinct graded candidate, highest adjusted first: adjusted grade, its letter (the nearest half step, as in the table above), set grade, change, quantity, name, and set and collector number; then one indented line per nonzero part. An unknown set or pair, an unreadable pool, a missing baseline or grade file, a bad weights file, or (with `tagSynergy` above 0) a missing or bad tag file prints one line and exits 1. It makes no network or model call.

```bash
cli/build/install/spa/bin/spa pool-grades fixtures/pools/trk-manabox-01.csv --set trk --pair WU
```

```text
WU  mean 2.88  (24 cards, 33 copies)
3.63  B+  3.5  +0.13  1x Enterprise NX-01 (trk 58)
      synergy +0.16: 8 partners in the pool, 6.07 expected (Spacecraft, Station, Stun)
      curve −0.14: 7 cards at mana value 4, 4.48 typical
      colour +0.12: Blue playables 18, typical 12.27
...
```

**deck-pick** measures how well a ranking picks the cards strong players registered from their own pools, on a 17Lands public Sealed game data file (`game_data_public.<SET>.Sealed.csv.gz`, gzipped or plain; never committed). Each build, one `(draft_id, build_index)`, is read once. A build is used when, checked in this order, its `user_game_win_rate_bucket` is a number of 0.60 or more, its `main_colors` are exactly two letters of WUBRG, every `deck_` and `sideboard_` name with a copy is a card of the set's Scryfall cache (by full or front-face name), and it leaves a choice; each skipped build is counted under the first check it fails. A build's candidates are its deck and sideboard copies that are not lands and whose colours are within `main_colors` (colourless included), each copy counted; N is the candidates' deck copies. A ranking's overlap is the deck copies among its top N candidates over N; a group of equal scores that straddles position N counts its deck copies times places left over the group's size. It prints the builds used and skipped by reason, the weights, and the mean overlap of a random ranking (N over the candidates), of set grades alone, and of the pool-adjusted grades for the build's pair (`pool-grades`' rules on the build's pool, all its non-basic copies), and adjusted minus set grade, to 4 decimals. Grades and weights default as for `pool-grades`. With `--tune`, it takes one file per set of `--set a,b` (and of `--grades a,b`, if given), measures every combination of synergy {0, 0.25, 0.5, 0.75, 1.0}, curve {0, 0.25, 0.5} and colour {0, 0.25, 0.5} with cap 1.0 on the sets' builds pooled, prints each, and writes the best (ties to the smaller total weight) to `--out`, else `<data>/weights/tuned.json`, in the format of `default.json`. It makes no network or model call.

```bash
cli/build/install/spa/bin/spa deck-pick ~/Downloads/game_data_public.HOB.Sealed.csv.gz --set hob
cli/build/install/spa/bin/spa deck-pick MSH.Sealed.csv.gz HOB.Sealed.csv.gz --set msh,hob --tune
cli/build/install/spa/bin/spa deck-pick ~/Downloads/game_data_public.FRA.Sealed.csv.gz --set fra --weights fixtures/weights/tuned.json
```

**fixing** classes each card of the set's sealed pool (the set and its companions, basic lands out, one card per oracle id) as colour fixing or not, by its Scryfall oracle text: every face's, with reminder text (everything in parentheses) removed. Each kind is one rule: MANA, an `Add` clause, up to the next period or line end, that makes any colour (`any color`, `any one color`, `any combination of colors`, `chosen color`: all five) or names two or more coloured symbols (those colours), quoted token abilities included; CYCLING, `Basic landcycling` (all five) or a land-type cycling such as `Mountaincycling` (its colour); SEARCH, a `search your library for` clause, to the next period or line end, that names a `land card` and does not say `into your graveyard` (all five); TREASURE, `create ... Treasure` in one sentence (all five). A card's colours are the union over its kinds. With no pool file it prints every fixing card, sorted by name, as name, kinds joined by `+`, colours in WUBRG order, and `land` when its front face is a land, separated by two spaces, then a line with the count of fixing cards and of each kind. With a pool file it prints only the pool's fixing cards, each with its quantity first, then a line with the fixing copies and how many of them can supply each colour; pool lines that do not resolve are listed on stderr. An unknown set or an unreadable pool file prints one line and exits 1. It makes no network or model call.

```bash
cli/build/install/spa/bin/spa fixing --set fra
cli/build/install/spa/bin/spa fixing fixtures/pools/trk-manabox-01.csv --set trk
```

**facts** prints the card facts of the set's sealed pool: a tag layer made by code from card data, with no model. Each card's facts come from its front face's type line and text, reminder text removed: each card type it has (`land`, `creature`, `artifact`, `enchantment`, `planeswalker`, `instant`, `sorcery`, `battle`), `legendary`, `type:<subtype>` for each creature type of a creature, `kw:<keyword>` for each of its Scryfall `keywords` and for flying, reach, deathtouch, lifelink, first strike, double strike, trample, vigilance, haste, menace, hexproof or indestructible that it grants itself ("This creature gains reach until end of turn", by "this creature", its full name or its name before the first comma; only the words before any "until", "as long as" or "if" count), `cheap` (not a land, mana value 2 or less), `enters-trigger` and `dies-trigger` (its own: "When" or "Whenever", then "this creature" (for enters also this permanent, artifact, enchantment, land, Spacecraft or Vehicle) or its name, then "enters" or "dies"), and `makes-token` (any face says "create" or "creates" and later in the same sentence "token" or "tokens"). Names and keywords are lowercased with every other character a hyphen. It prints `<fact> <count>`, most common first, then by name, and last `<pool size> cards, <n> distinct facts`; with `--card <name or id>`, one line, `<name> (<id>): <facts, sorted>`, and a card the pool does not hold prints one line and exits 1. Each fact family is its own `FactRule` in `agent/src/main/kotlin/spa/agent/facts/`, listed in `FactRules.ALL`, so a new fact is one file and one entry. When the set has an otags file (below), each card also gets `otag:<slug>` for each of its Scryfall Tagger tags, and `--card` ends its line with the card's cares tags, `cares:<fact>=<strength>`, sorted by fact; `--otags <file>` reads another otags file and `--otags none` reads none.

**fetch-tags** writes `<data>/otags/<code>.json` (see `fixtures/otags/README.md`): each card of the set's sealed pool, in collector order, with its Scryfall Tagger oracle tags and every ancestor tag through `parent_ids`, sorted, from Scryfall's Oracle Tags bulk file, `{ "set", "source", "updatedAt", "attribution": "Card tags from Scryfall Tagger (tagger.scryfall.com)", "cards": [{ "id", "otags" }] }`. `--bulk <file>` reads a downloaded bulk file (`.jsonl`, or gzipped); without it, the command downloads the current file named by `https://api.scryfall.com/bulk-data` into `.cache/otags/` (not committed), which needs the network, and sets the file's last-modified time to Scryfall's `updated_at`. `source` names the bulk file and `updatedAt` is its last-modified time, so the same bulk file always gives the same bytes. It first checks every fact of `<data>/otags/cares.json`, the cares table: each must be a code fact name (a card type, `legendary`, `type:<x>`, `kw:<x>`, `cheap`, `enters-trigger`, `dies-trigger`, `makes-token`) or `otag:<slug>` of a tag in the bulk file; it prints one line per unknown fact, writes nothing, and exits 1. The table maps a "cares about" tag to the facts it cares about, each with a strength from 1 to 3; a card with that tag gets `cares:<fact>` at that strength, the largest when two of its tags give the same fact. A new tag is a new entry in the table, with no code change.

`fetch-tags` also writes `<data>/otags/<code>.tags.json` (SPA-055), from the same bulk file: `{ "set", "source", "updatedAt", "attribution", "tags": [{ "slug", "description", "parents" }] }`, one entry for each tag in the set's otags file (ancestors are already there), sorted by slug, with Scryfall's `description` (null when it has none) and the sorted slugs of its parents. The cares packet reads it for the vocabulary's descriptions and to find the name tags.

**cares-packet** `<code> --out <dir> [--otags <file>] [--data <dir>]` (SPA-055) writes the blind packet in which Fable fills the gaps Scryfall's tags leave in the cares tags: `instructions.md` (the prompt, `agent/src/main/resources/spa/agent/otags/cares-gaps-v1.md`; then the cares table as a list; the format brief and theme counts; the files), the grade packet's `cards-NN.json` files byte for byte, `facts.json` (each card's code facts and `otag:` facts), `cares-NN.json` beside each card file (the cares tags the cares table gives those cards), `vocabulary.json` (every fact name a cares tag may use with its description: each code fact present in the set, and each Scryfall tag present with its ancestors as `otag:<slug>`), `cares-gaps.schema.json` and `cares-table-suggestions.schema.json`. Name tags (`name-matters` and every tag whose parents lead to it) are left out of `vocabulary.json` and of the `otag:` facts in `facts.json`, so the owner's Andromedan Automatons check stays independent; the cares table and `cares-NN.json` keep what they give. The packet is byte-identical across runs and holds no win rate and no check id. A set with no otags file or no tags file is one line and exit 1.

**cares-import** `<code> --packet <dir> --provenance <text> [--data <dir>]` (SPA-055) reads Fable's `cards-NN.cares.json` (a list of `{ "id", "add": [{ "fact", "strength", "reason" }] }`, empty when no card needs one) and optional `cares-table-suggestions.json` (a list of `{ "tag", "facts": [{ "fact", "strength" }], "reason" }`) from the packet. It checks that every card file has a response, every id is a card of its file, every fact is in `vocabulary.json`, every strength is 1, 2 or 3, no card repeats a fact or adds one it already cares about from Scryfall, every reason has text, and every suggestion names a Scryfall tag of the set that is not already in `cares.json` with facts in the vocabulary. It names every problem on stderr, one line each, and writes nothing on any problem. Otherwise it writes `<data>/otags/<code>.fable.json` (`{ "set", "provenance", "cards": [{ "id", "cares": [{ "fact", "strength", "reason" }] }] }`, only the cards with additions, in collector order) and prints the count of additions and each suggestion; suggestions are never applied. Each check is its own class in `agent/src/main/kotlin/spa/agent/otags/cares/`, listed in `CaresChecks.ALL`. The synergy commands and `facts` join the Fable file's cares tags with the cares table's (the larger strength per fact); a set with no Fable file scores exactly as before, and `--otags none` reads neither.

```bash
cli/build/install/spa/bin/spa fetch-tags --set trk --bulk ../oracle-tags.jsonl.gz
cli/build/install/spa/bin/spa fetch-tags --set trk
```

```bash
cli/build/install/spa/bin/spa facts --set trk
cli/build/install/spa/bin/spa facts --set trk --card Moopsy
```

**tags-packet** writes a blind synergy tagging packet for the set's sealed pool into `--out <dir>`, in two passes. Pass 1 (no `--axes`) writes `instructions.md` (the axes prompt `agent/src/main/resources/spa/agent/tags/tags-axes-v1.md`, then the set's format brief as `grade-packet` renders it, the `## Themes in this set` section as `grade-packet --themes` writes it, and a description of the files), `axes.schema.json`, and the card files `cards-01.json`, ... byte for byte as `grade-packet` writes them without `--themes`. The tagger writes `axes.response.json`: a JSON array of 3 to 15 `{ "id", "name", "definition" }`, each id lowercase letters, digits and hyphens, the ids distinct. Pass 2, `--axes <file>`, checks that file first; on any problem it prints one line per problem, writes nothing, and exits 1. It then writes `instructions.md` (the roles prompt `tags-roles-v1.md`, then the axes as a list in the given order, then the brief, theme counts and files), `axes.json` (the axes, which `tags-import` reads), `response.schema.json`, and the same card files. The tagger writes `cards-NN.response.json` beside each card file: a JSON array of `{ "id", "roles": [{ "axis", "role": "enabler" | "payoff", "strength": 1 | 2 | 3 }], "partners": [{ "id", "strength": 1 | 2 | 3 }] }`. Neither packet holds a win rate or review text. `--prompt <version>` (default `v2`) picks the prompt files `tags-axes-<version>.md` and `tags-roles-<version>.md`, so a new prompt is a new resource file; a version with no file prints one line and exits 1. The same inputs always give the same bytes; earlier packet files in `<dir>` are replaced and response files are kept. The paragraph above is prompt v1 (`--prompt v1`), which still works exactly so. From v2 both packets also hold `facts.json`, each card id with its facts as `spa facts` gives them; pass 1 adds a `## Card facts` section after the theme counts, each fact with its count, most common first, then by name; and each axis of `axes.response.json` carries `"suppliedBy": [{ "fact", "strength": 1 | 2 | 3 }]` (possibly empty), the facts that supply it just by a card having them, with 3 to 20 axes. Pass 2 then checks that every `suppliedBy` fact is one some card of the set has, every strength is 1, 2 or 3 and no fact repeats on an axis, one line per problem, and shows each axis's supply after its definition: "Supplied by:" and each fact with its strength, or "Supplied by: no fact."

**tags-import** reads the pass-2 packet in `--packet <dir>`, its `axes.json` and every response, and writes `<data>/tags/<code>.json`, a tag file (`agent/src/main/resources/spa/agent/tags/tags.schema.json`): `{ "set", "provenance", "axes", "cards": [{ "id", "name", "roles", "partners" }] }` in collector order, with the given `--provenance`. It checks that every card of the pool is tagged exactly once and no id is unknown, every axis is one of the packet's, every role is `enabler` or `payoff` and every strength 1, 2 or 3, no card gives the same axis and role twice, and every partner is a card of the pool (the card itself allowed), at most 5 and none twice. For a v2 packet, whose `axes.json` carries `suppliedBy`, it also runs the three supply checks of pass 2 against the set's facts, worked out from the Scryfall cache, and the tag file keeps each axis's `suppliedBy`. Each check is its own class in `agent/src/main/kotlin/spa/agent/tags/`, listed in `TagChecks.ALL`. On any problem it prints one line per problem, naming the file and the card, writes nothing, and exits 1. `fixtures/tags/README.md` describes the tagging run. Neither command calls a model.

```bash
cli/build/install/spa/bin/spa tags-packet trk --out ../tags-trk
cli/build/install/spa/bin/spa tags-packet trk --axes ../tags-trk/axes.response.json --out ../tags-trk
cli/build/install/spa/bin/spa tags-import trk --packet ../tags-trk --provenance "claude-fable-5-1 via Claude Code, tags prompt v2, <yyyy-mm-dd>"
```

**tags-review-packet** writes a blind review packet (pass 3) for the set's tag file, `--tags <file>` or by default `<data>/tags/<code>.json`, into `--out <dir>`: `instructions.md` (the review prompt `agent/src/main/resources/spa/agent/tags/tags-review-v1.md`, then the axes as pass 2 lists them, each with its supply, then the brief and theme counts and a description of the files), the card files byte for byte as `grade-packet` writes them, `tags-NN.json` beside each `cards-NN.json` (the tag file's entries for that file's cards, in order), `facts.json` as the v2 tags packets write it, `tag-file.json` (the tag file's absolute path and the SHA-256 of its bytes), `review.schema.json` and `fact-suggestions.schema.json`. `--prompt <version>` (default `v1`) picks `tags-review-<version>.md`. The reviewer writes `cards-NN.review.json` beside every card file, a JSON array (possibly empty) of corrections `{ "id", "action", "axis"?, "role"?, "partner"?, "strength"?, "reason" }`, the action one of `add-role` (axis, role, strength), `remove-role` (axis, role), `set-strength` (axis, role, strength), `add-partner` (partner, strength) and `remove-partner` (partner), and optionally `fact-suggestions.json`, a list of `{ "fact", "rule", "examples": [card ids], "reason" }`. The packet holds no win rate, review text or synergy check, and the same inputs give the same bytes; earlier packet files are replaced and review files kept.

**tags-review-import** reads the tag file the packet in `--packet <dir>` was built from and stops with one line if its bytes have changed since. It applies the corrections in file order, then card order (a card's own corrections in the order given), each through its action, one class per action in `agent/src/main/kotlin/spa/agent/tags/review/` listed in `ReviewActions.ALL`. A correction must name a card of its card file, give exactly its action's fields and a non-empty reason, and not remove or re-strength something the card lacks or add something it has; the corrected tags must then pass every `tags-import` check. On any problem it prints one line per problem and writes nothing. Otherwise it writes `<data>/tags/<code>.json` with the same set, axes and provenance, the corrected cards, and a `review` block: `{ "provenance" (the given --provenance), "counts" (applied corrections by action), "corrections" (each applied one with its reason) }`, replacing any earlier review block. It prints the count by action, then each fact suggestion on its own line; suggestions are never applied, since facts are code. Neither command calls a model.

```bash
cli/build/install/spa/bin/spa tags-review-packet trk --out ../review-trk
cli/build/install/spa/bin/spa tags-review-import trk --packet ../review-trk --provenance "claude-fable-5-1 via Claude Code, tags review prompt v1, <yyyy-mm-dd>"
```

**synergy-table** scores every pair of cards in the set's tag file, `--tags <file>` or by default `<data>/tags/<code>.json`, each card with itself included, from the tags alone. A pair's score is the sum of its terms, each its own class in `agent/src/main/kotlin/spa/agent/synergy/`, listed in `PairTerms.ALL`. The axis term is, over the set's axes, a's enabler strength times b's payoff strength plus b's enabler strength times a's payoff strength (0 for a role a card does not have), so a card with itself scores twice the sum of its enabler times payoff strengths. The partner term is the strength with which a names b plus the strength with which b names a, or, for a card with itself, the strength with which it names itself. The fact term counts an axis's `suppliedBy` facts (tags prompt v2) as enabler strength: for card c and axis x, `F(c,x)` is the largest `suppliedBy` strength on x among c's facts, or 0, and `Ê(c,x) = max(E(c,x), F(c,x))`; the term adds `(Ê(a,x) − E(a,x))·P(b,x) + (Ê(b,x) − E(b,x))·P(a,x)` over the axes, so axis plus fact is `Σ Ê(a,x)·P(b,x) + Ê(b,x)·P(a,x)`, the diagonal included. A tag file with no `suppliedBy` fact has no fact term: it scores and prints exactly as before, and needs no Scryfall cache. The cares term (SPA-054) reads the set's otags, `--otags <file>` or by default `<data>/otags/<code>.json` when it exists (`--otags none` reads none), with the cares tags of `<data>/otags/cares.json` (see **fetch-tags**): for cards a and b, the sum over a's facts f of b's `cares:<f>` strength, plus the sum over b's facts f of a's `cares:<f>` strength, each fact counting once, otag facts included; the diagonal uses the same formula, so it counts twice. A set with no otags file, or no card with a cares tag, has no cares term and scores and prints exactly as before. It prints every pair scoring at least `--min` (default 1), highest first, ties in collector order of the first card and then the second, as the score, `axis <part>`, `partner <part>`, `fact <part>` when the file has a fact term, `cares <part>` when the part is not 0, and `<name> (<id>) with <name> (<id>)` (or `with itself`), separated by two spaces. The last line gives the number of cards, of pairs with the diagonal, of nonzero pairs and nonzero diagonal pairs, and the maximum score, whatever `--min` is. A missing or invalid tag file, or one for another set, prints one line and exits 1.

**synergy** scores each pool card's synergy with the rest of the pool in one colour pair, `--pair <XY>` (required, either letter order), against a typical pool. The candidates are the pool's nonland copies whose colours are within the pair, as for `pool-grades`, N copies in all. A candidate a with n(a) copies has `syn` = the sum over the other distinct candidates b of n(b) times the pair score of a and b, plus (n(a) − 1) times a's score with itself; `e` = the mean pair score of a with each of the set's sealed-pool cards other than a that is nonland and fits the pair, times N − 1; and part = weight × clamp((syn − e) / max(e, 1), −1, 1). The weight comes from `--weights <file>`, else `<data>/weights/tag-synergy.json` (see `fixtures/weights/README.md`). It prints one line per distinct candidate, highest part first, ties by name: the part with its sign and 3 decimals, `syn <n>`, `e <x.xxx>`, the quantity and the name; under each, indented two spaces, `partners:` and up to three partners in the pool with the largest copies times score, ties by name, each as `<name> (<copies> x <score> = <product>)` (the card's own other copies under its own name), or `no partners in the pool`. The tags come from `--tags <file>`, else `<data>/tags/<code>.json`. A missing tag file, an unknown set, an unreadable pool file, a bad pair or a bad weights file prints one line and exits 1; pool lines that do not resolve are listed on stderr. Neither command calls a model.

**synergy-check** runs the owner's held-out synergy checks, `<data>/synergy-checks/<code>.json` (see `fixtures/synergy-checks/README.md`), on the set's pair table, the one `synergy-table` prints, from the same tag file (`--tags <file>`, else `<data>/tags/<code>.json`) and otags (`--otags`, as for `synergy-table`; `synergy` takes `--otags` too). A `pair` check passes when the score of its two cards is at least `min`, and prints `score <n>` and each term's part. A `groups` check takes every pair of a card in `groupA` and a different card in `groupB`, and passes when the share of them scoring at least `min` is greater than `share` (equal fails); it prints `<n> pairs`, `<k> meeting min <min>`, and the share and the mean score to 4 decimals. Each kind is its own class in `agent/src/main/kotlin/spa/agent/synergy/check/`, listed in `CheckKinds.ALL`. One line per check, in file order: `PASS` or `FAIL`, the id and the status (`independent` or `seen`), then the numbers, separated by two spaces. The last line is `<passed> of <total> checks pass (<k> independent)`, where k counts the passing checks that are independent. A failing check is a result and exits 0. A missing or invalid check file or tag file, an unknown set, an unknown kind or a card id the tag file does not hold prints one line and exits 1, and no check runs. No packet, prompt or scoring rule reads the check files; only this command does.

**build** builds the best 40 card deck a pool makes in a set of colours, `--colours <main>[/<splash>]` (`WU`, `WU/R`, `WUB`: main colours, then up to two splash colours), by search with no model or network call (SPA-044). Without `--colours` it tries every colour configuration the pool supports, ranks the decks by score, and prints the top `--top <n>` (3 by default), each headed `Rank <n>: <colours>` (SPA-045; see below). With `--colours`, `--top` is ignored. The candidates are the pool's nonland cards whose colours fit inside the deck's; a splash card (any splash colour) has at most `splash.maxPips` splash pips and at most `splash.maxCards` are played. The deck score is the sum of the deck parts in `agent/src/main/kotlin/spa/agent/build/`, each its own class behind `DeckPart`, listed in `DeckParts.ALL`, each weighted by key from `--weights` (default `<data>/weights/deck-default.json`, see `fixtures/weights/README.md`; a part with weight 0 is off): `grades` (the spells' set grades, from the same grade file `pool-grades` reads), `curve` (minus the spells short of a target at each mana value 1 to 6 or more), `creatures` (minus the creatures short of a minimum), `removal` (spells `RulesTagger` tags `REMOVAL`, up to a cap), `castability` (minus each spell's grade times its chance of not being castable on curve, from `Hypergeometric` over the deck's basics, fixing lands and half a source per fixing spell), `fixing` (a bonus for fixing cards that rises with the deck's colours past two: `(k - 2) x` the share of the deck's `k` colours a fixing card supplies, 0 in a two colour deck) and `synergy` (the pair scores of every pair of spell copies, over the spells; `--tags` and `--otags` as for `synergy-table`, read only when its weight is above 0). Lands are 16, 17 or 18 by the spells' mean mana value and the deck's colours (`lands` in the weights file); the pool's nonbasic lands that supply two or more of the deck's colours are played first, and basics fill the rest by `LandMath.recommendLands` over the spells' pips. The search is a greedy fill and then single-swap local search at that land count, repeated until the land count the rule gives for the result is the one searched; the same inputs give the same deck. It prints the score and each part's value and reason, the spells by mana value with a grade and a reason each (top two synergy partners in the deck, and the chance of casting it on curve when below 90%), the lands, the sideboard, and the off-colour cards counted by colour. A pool with too few candidates for 22 spells, a bad `--colours`, a bad `--top` (0, negative, not a number) and a bad weights file are one line and exit 1; so is a ranked build where no configuration can be built. Unresolved pool lines go to stderr.

**The configurations** (SPA-045) come from the rules in `agent/src/main/kotlin/spa/agent/build/`, each its own class behind `ConfigRule`, listed in `ConfigRules.ALL`, with its settings in the `configs` section of the weights file (a rule missing from the section is off, `"enabled": false` switches one off): `pairs` (the 10 two-colour pairs), `pairSplash` (each pair with one splash colour and, with `maxSplashColours` 2, with two), `triples` (the 10 three-colour sets, each also with one splash colour when `tripleSplash` is true) and `fourColour` (a four-colour set, only when the pool holds `minFixing` copies of cards that supply two or more of its colours). A pool with no fixing gets 100 configurations. Each is built with the same search, and the decks are ranked by score, highest first, ties by the colours text; a configuration with too few candidates is left out, and configurations that give the same 40 cards are one deck, headed `Rank <n>: <colours> (same cards as <other colours>)`. No rule knows a set: the pool decides.

```bash
cli/build/install/spa/bin/spa build fixtures/pools/trk-manabox-01.csv --set trk --colours WU/R
cli/build/install/spa/bin/spa build fixtures/pools/trk-manabox-01.csv --set trk --top 5
```

**deck-backtest** measures the deck builder against strong players, on a 17Lands public Sealed game data file (`game_data_public.<SET>.Sealed.csv.gz`, gzipped or plain; never committed), with no model or network call (SPA-046). Each build, one `(draft_id, build_index)`, is read once, from its first row. A build is strong when its `user_game_win_rate_bucket` is 0.60 or more. Its deck shape is from `main_colors` and `splash_colors` (letters of WUBRG): two (2 main colours, no splash), splash (2 main colours and a splash) or multi (3 or 4 main colours, with or without a splash). A strong build is skipped, under the first reason that applies, for one main colour, five, a `deck_` or `sideboard_` name with a copy that is no card of the set's Scryfall cache (full or front-face name), or no nonland card, deck or sideboard, inside its main and splash colours (no-choice). The builder runs on the player's pool (every deck and sideboard copy that is not a basic land): `DeckRanking` over all configurations gives the ranked decks, and the deck for the player's own configuration, its main and splash colours, gives overlap and land count (a pool that cannot fill it is unbuildable: left out of those two, still counted in the colour measures). The five measures, each its own object in `agent/src/main/kotlin/spa/agent/deckbacktest/` and an entry of `Measures.ALL`: `colour` (the top deck's main colours, as a set, are the player's), `top3` (the player's main colours are among the first three decks'), `splash` (the top deck has a splash exactly when the player did), `overlap` (the sum over nonland cards of the smaller of the builder's and the player's deck copies, as a mean, then the mean number of nonland cards the players registered, over the buildable builds) and `lands` (the builder's land count is within 1 of the player's, every deck copy of a land card). The first three count a build with no ranked deck as a miss. The output is the `read` line (strong builds, their shapes, the skipped), the `used` line, four lines each (overall, two, splash, multi) for the builder and for a baseline builder, and a `time` line. The baseline is `--baseline` (default `<data>/weights/deck-baseline.json`): set grades alone, two-colour pairs only; its overlap and land count are measured on its top ranked deck. `--limit n` keeps n builds spread evenly through the kept builds in file order (build i is the one at position `i * kept / n`); `--threads` (default 2) never changes a number; `--weights` (default `<data>/weights/deck-default.json`), `--tags` and `--otags` are as for `build`. A missing file, an unknown set, a bad number or a bad weights file is one line and exit 1.

```bash
cli/build/install/spa/bin/spa deck-backtest ~/Downloads/game_data_public.HOB.Sealed.csv.gz --set hob --limit 200
cli/build/install/spa/bin/spa deck-backtest .cache/17lands/game_data_public.MSH.Sealed.csv.gz --set msh --weights fixtures/weights/deck-default.json
```

**deck-tune** tunes the deck builder's weights on `msh` and `hob` by a deterministic search, with no model or network call (SPA-060): `spa deck-tune <sealed-file>... --set <code,...> --out <file>`, one Sealed file per set in the order of `--set`. Only the tuning sets may be named: `--set fra` and `--set trk` are refused in one line (G-005: `fra` is held out for the measurement, `trk` for everything; `syn`, the tests' hand-built set, is the only other one allowed). The search is coordinate ascent: it starts from `--start` (default `<data>/weights/deck-default.json`) and visits the knobs of `--grid` (default `<data>/weights/deck-grid.json`, see `fixtures/weights/README.md`) in file order; for each knob it tries every other value with the rest as they stand, and moves to the value with the best objective if that beats the current by more than 1e-9 (a tie keeps the current value, and among equal better values the first in grid order wins). It stops when a round changes nothing or after `--rounds` rounds (default 3). The objective of a weights file is, for each set, the overlap share (the builder's mean main-deck overlap divided by the mean number of nonland cards the players registered) plus the colour agreement (the rate of the `colour` measure), averaged over the sets; it is its own small interface in `agent/src/main/kotlin/spa/agent/deckbacktest/tune/` (`TuneObjective`, with `OverlapAndColour` the only entry of `TuneObjectives.ALL`). `--limit` is per set, as in `deck-backtest`, and the search runs on that sample; `--threads` (default 2) changes the time, never a number. Nothing is rebuilt that a change cannot affect: each build's candidates are kept, the decks of a build and a configuration are kept per weights without the `configs` section (a change to `configs` builds only the new configurations' decks), and weights already evaluated are answered from memory; the numbers are the same as without any of that. The output is the `read` and `used` lines of each set, one line per evaluation (`round <r> <path> <value> objective <x>`, and `round 0 start objective <x>` for the start), then the start and final objectives, each set's builder lines for the start and the final file, and the number of evaluations and the seconds. `--out` holds the best weights so far: it is written for the start and again after every move, in the format of `deck-default.json`. A missing file, a number of files that differs from the number of sets, a bad number, a bad grid or start file, and a failure inside a worker thread are each one line and exit 1.

```bash
cli/build/install/spa/bin/spa deck-tune .cache/17lands/game_data_public.MSH.Sealed.csv.gz .cache/17lands/game_data_public.HOB.Sealed.csv.gz --set msh,hob --limit 60 --out fixtures/weights/deck-tuned.json
```

```bash
cli/build/install/spa/bin/spa synergy-table --set trk --min 10
cli/build/install/spa/bin/spa synergy fixtures/pools/trk-manabox-01.csv --set trk --pair WU
cli/build/install/spa/bin/spa synergy-check --set trk
cli/build/install/spa/bin/spa synergy-check --set trk --tags fixtures/tags/v1/trk.json
```

`fixtures/tags/v1/` holds the four v1 tag files frozen as test data (see its README): a test that pins a number or line computed from tags reads them through `--tags`, so the live tag files can change. Such a test also passes `--otags none`, and `fixtures/synergy-checks/v1/trk.json` is the check file frozen at SPA-054 (a byte copy of the live one then), which `SynergyV1UnchangedTest` reads, so the owner's live check file can change too.

## Android app

The `app` module builds the Android app, a debug APK signed with the build machine's debug key. It needs the JDK and the Android SDK in [Toolchain](#toolchain).

**Build.** From the repository root:

```bash
./gradlew :app:assembleDebug
```

This writes `app/build/outputs/apk/debug/app-debug.apk`.

**Test.** The app's JVM tests (Compose UI tests and snapshot tests under Robolectric, with no emulator or phone) run with:

```bash
./gradlew :app:verifyRoborazziDebug
```

The gate runs them too, as its `ui-tests` step.

**Install on a phone.** Android blocks apps that do not come from a store until you allow them. Pick one route.

- Copy the file over. Copy `app-debug.apk` to the phone (over USB, Google Drive, or email to yourself) and tap it in the Files app (or Chrome's downloads). When Android asks, allow the app that opened it: Settings > Apps > Special app access > Install unknown apps > Files (or Chrome) > Allow. Menu names differ slightly by manufacturer. If Google Play Protect warns that the app is from an unknown developer, choose "Install anyway". You can turn the permission off again afterwards.
- Install over USB with `adb`. On the phone, turn on USB debugging: Settings > About phone > tap Build number seven times, then Settings > System > Developer options > USB debugging. Connect the cable and accept the "Allow USB debugging" prompt on the phone; `adb devices` should list the phone as `device`. Then:

  ```bash
  adb install -r app/build/outputs/apk/debug/app-debug.apk
  ```

**No account, no key, no network.** The app needs no account and no API key, and it has no network permission. To check the last, list the APK's permissions with `aapt2` from the SDK build tools the first build installed (`ls "$ANDROID_HOME/build-tools"` shows the version; use yours in place of `36.0.0`):

```bash
"$ANDROID_HOME/build-tools/36.0.0/aapt2" dump permissions app/build/outputs/apk/debug/app-debug.apk
```

`android.permission.INTERNET` is not in the list.

## Toolchain

**JDK.** JDK 21 is required, on `PATH` or in `JAVA_HOME`. On Windows, `winget install EclipseAdoptium.Temurin.21.JDK` installs it (from an administrator prompt); a shell opened before the install may still see an old `java` stub, so set `JAVA_HOME` to `C:\Program Files\Eclipse Adoptium\jdk-21...` if `java -version` does not report 21. Nothing else needs installing: the committed Gradle wrapper (`./gradlew`) downloads Gradle itself. On Windows, run every command below in Git Bash.

**Android SDK.** The app needs the Android SDK command-line tools, installed on their own from https://developer.android.com/studio ("Command line tools only", unpacked so that `%LOCALAPPDATA%\Android\Sdk\cmdline-tools\latest\bin` holds `sdkmanager`) or through Android Studio (Settings > Languages & Frameworks > Android SDK > SDK Tools > "Android SDK Command-line Tools (latest)"). Set the user environment variable `ANDROID_HOME` to the SDK folder, `%LOCALAPPDATA%\Android\Sdk`, and add its `cmdline-tools\latest\bin`, `platform-tools`, and `emulator` folders to your user `PATH`. A terminal opened before you set them does not see the new variables, so close every terminal and open a new one. Then install the packages, and accept the SDK licenses yourself with `sdkmanager --licenses`, reading each one and answering `y`:

```bash
sdkmanager.bat --install "platform-tools" "emulator" "system-images;android-36;google_apis;x86_64"
sdkmanager.bat --licenses
```

Git Bash needs the `.bat` on `sdkmanager`; PowerShell does not. Gradle downloads the SDK platform and build tools the app asks for on its first build, once the licenses are accepted. `ANDROID_HOME` must be visible in Git Bash, where the gate runs; in a new Git Bash window, this prints a path and two versions:

```bash
echo "$ANDROID_HOME" && adb version && sdkmanager.bat --version
```

**Running the gate.** From the repository root:

```bash
pipeline/gate/run.sh
```

It runs `./gradlew check`, which runs the seven steps of `pipeline/gate/README.md` in order (`format`, `lint`, `compile`, `unit-tests`, `ui-tests`, `coverage`, `dependencies`) and stops at the first that fails. It exits 0 on pass and 1 on fail. The ticket is `--ticket SPA-###` if given, else the `GATE_TICKET` environment variable, else the first `SPA-###` in the branch name (`GATE_BRANCH` overrides the branch name, which CI uses), else `none`. With `none`, the coverage threshold is 0 and `gradle/libs.versions.toml` must be unchanged from `git merge-base origin/main HEAD`. One step can be run alone with `./gradlew gateFormat`, `gateLint`, `gateCompile`, `gateUnitTests`, `gateUiTests`, `gateCoverage`, or `gateDependencies`. `./gradlew ktfmtFormat` fixes formatting. The gate's own code is the `spa.gate` Gradle plugin in `build-logic/`.

**The pre-push hook.** Once per clone:

```bash
pipeline/gate/install-hook.sh
```

This sets `core.hooksPath` to `.githooks`. From then on `git push` runs the gate on the working tree and refuses the push when it is red. A push that only deletes remote branches skips it.

**The verdict.** Every run writes `gate-verdict.json` at the repository root (gitignored): `ticket`, `commit` (short SHA), `result` (`pass` or `fail`), `failures` (each with `step`, `file`, `line` or null, `message`, and a `reproduce` command), and `steps` (each step's `pass`, `fail`, or `not_run`, with a note such as `skipped: no app module`). Read `failures` first; the full Gradle log is in `build/gate/gradle.log`. CI runs the same script on every pull request to `main` and every push to `main`, and uploads the verdict as the `gate-verdict` workflow artifact.

**Dependencies.** Every dependency coordinate and version lives in `gradle/libs.versions.toml`; build scripts use catalog aliases, never string coordinates. A ticket that adds an entry to `[libraries]` or `[plugins]` must list it in its Scope section in exactly this form, one backticked coordinate per sub-bullet, `group:artifact` for a library and `plugin:<plugin id>` for a Gradle plugin:

```markdown
- Dependencies this ticket may add (none unless listed):
  - `plugin:org.jetbrains.kotlin.jvm`
  - `org.jetbrains.kotlinx:kotlinx-serialization-json`
```

The `dependencies` step fails, naming the coordinate, for any added entry the ticket does not list.
