# Deck builder: pools, decks, card reasons

- Status: **DRAFT for the owner's one review** (written 2026-11-06). Nothing here is a ticket yet. Once the owner answers "Open questions", the choices are written into "Settled" and the file is committed to `main`.
- Replaces the Arena-inspired direction (owner, 2026-11-06). Takes its look and layout from ManaBox (screenshots in `design/references/manabox/`). The owner's rule: ManaBox has a lot of functionality; **we draw inspiration from its UI only**.
- Builds on `design/first-app.md` (G-002): same dark "Low light" theme and tokens, same Atkinson Hyperlegible type. Where this spec changes a G-002 screen, it says so.
- Who and where: one player at a table, on a phone, in a hurry, often one-handed. Fewer taps beat more options.

Text in quotes is copy and tests may assert it. Text in angle brackets is filled from data.

## What we take from ManaBox, and what we leave

Taken (look and layout only):
- A grid of tiles for the list of things, each with art, a name and colour pips.
- A deck screen with three tabs: cards, stats, info.
- Cards grouped under headed sections with a count, each row an art thumbnail, name, mana cost and a −/quantity/+ stepper.
- An "Add cards" search row with filter and view buttons.
- Stacked mana-value bars and colour pies on the stats tab.
- A floating action button that opens a short speed-dial.
- A card detail: full card over the list, neighbours peeking left and right, a bottom sheet beneath.

Left out, on purpose: prices and "Buy", collection, scan, global search, Explore, bracket, commander and 98-card rules, tags, folders, import and export of decks, "Test deck", the bottom navigation bar, deck art picking, sharing, and accounts.

## Decisions this spec makes

1. **A pool owns its decks.** The hierarchy is Pools, then one Pool, then one Deck, then one Card. A deck never exists outside a pool. (Owner, 2026-11-06.)
2. **Decks can only use the pool.** Adding a card to a deck offers only that pool's cards, up to the quantity the pool holds. Basic lands are the one exception: unlimited, as in a real sealed event. (Owner: "restricted to cards in the pool"; basics are the Designer's call, Open question 3.)
3. **Opening a pool shows ranked decks at once.** The builder's top three decks (`spa build` default) are made when a pool is imported and listed under the pool's Decks tab. The player can build more, or start an empty deck.
4. **The "i" tab describes the deck.** It says in words how the deck is meant to be played, then shows the score by part and the sideboard. (Owner, 2026-11-06.)
5. **Tapping a card shows why it was chosen.** The card detail lists the builder's reason lines for that card, each with its number. (Owner, 2026-11-06.)
6. **Edits are allowed and re-score at once.** A player's swap changes the deck's score and description immediately. An edited deck is marked "Edited" and can be reset to the builder's deck. (Open question 4.)
7. **No bottom bar.** There are only two levels of list, so a back arrow and one top-bar About icon are enough. G-002's About screen is unchanged.
8. **The theme stays the G-002 "Low light" dark scheme** with its tokens, plus the additions in "Visual direction".

## Visual direction

Uses `SpaColors`, `SpaTheme` and the type scale from `design/first-app.md`. Additions:

- **Colour identity.** Five colour tokens for pips, bars and pies: `manaW`, `manaU`, `manaB`, `manaR`, `manaG`, plus `manaC` for colourless and `manaGold` for multicolour. Values are fixed in the first ticket that needs them, by script, so each meets 3:1 against `ground` and `surface` (non-text) and no two are told apart by colour alone: every pie and bar also carries a number and a letter.
- **Tiles.** Pool and deck tiles are 14dp-radius cards in a two-column grid, art cropped behind a dark gradient, name top-left, pips bottom-right (ManaBox's decks list).
- **Rows.** A card row is a `surface` card: 56dp art thumbnail on the left, name, mana-cost pips, one reason line; a grade chip and the stepper on the right.
- **Floating button.** `accent` fill, `onAccent` icon, 64dp, bottom right. It opens a speed-dial of up to three labelled buttons.
- **Card art.** Scryfall art crops and card images (attribution already required, `CLAUDE.md`). **Needs a decision** (Open question 1): G-002's app holds no network permission, and art means one. Until decided, every row, tile and carousel page also has its no-art fallback, described below, and the spec works with it alone.
- **No-art fallback.** A flat tile in the card's colour identity with its pips and name, and a rounded rectangle of the same colour in place of a thumbnail. The fallback is also what shows while art loads or when it cannot load.
- **Mana pips** are drawn by the app (circles with a letter or number), never copied from Wizards' symbol art.

## Screen inventory

| Screen | Route | Purpose |
|---|---|---|
| Pools | `pools` | Home. Every saved pool as a tile. Floating button adds a pool. |
| Import pool | `pool/import` | G-002's Pool screen, renamed. Get a pool in: open a file, or paste text. Unchanged in behaviour; on success it lands on the new pool's Decks tab. |
| Pool | `pool/{id}` | One pool, three tabs: Cards, Decks, Analysis. |
| Deck | `pool/{id}/deck/{deckId}` | One deck, three tabs: Cards, Stats, Info. |
| Add cards | `pool/{id}/deck/{deckId}/add` | The pool's cards left to add, plus basics. |
| Card detail | `pool/{id}/card/{cardId}` and `pool/{id}/deck/{deckId}/card/{cardId}` | One card in a carousel with a bottom sheet. In a deck it carries the reasons. |
| New deck sheet | (sheet over Pool) | Choose "Build for colours" or "Empty deck". |
| About | `about` | G-002's About screen, unchanged. |
| Delete dialogs | (dialogs) | Confirm deleting a pool or a deck. |

G-002's Results screen becomes the Pool's **Analysis** tab, content unchanged (problems, totals, ten pairs, cards per colour, fixing, each beside its typical value). Pair detail is reached from it as before.

## Navigation flow

```
launch ──> Pools
Pools ── pool tile ──> Pool (Decks tab) ── back ──> Pools
  ├── floating "+" ──> Import pool ── success ──> Pool (Decks tab, new pool)
  │                        └── fail ──> Import pool (error card)       [G-002 rules]
  ├── long-press tile ──> menu: Rename, Delete (dialog)
  └── About icon ──> About

Pool
  ├── Cards tab ── card row ──> Card detail (pool view) ── back ──> Pool
  ├── Decks tab ── deck tile ──> Deck ── back ──> Pool (Decks tab)
  │      └── floating "+" ──> New deck sheet ── "Build for colours" ──> colour picker ──> Deck (Cards tab)
  │                                      └── "Empty deck" ──> Deck (Cards tab, empty)
  └── Analysis tab ── pair row ──> Pair detail ── back ──> Pool (Analysis tab)

Deck
  ├── Cards tab ── card row ──> Card detail (deck view, reasons) ── back ──> Deck
  │      ├── "Add cards" row or search button ──> Add cards ── back ──> Deck
  │      └── − at quantity 1 ──> removes the card (snackbar "Removed <card>", action "Undo")
  ├── Stats tab (read-only)
  ├── Info tab ── "Reset to built deck" (only when Edited) ──> dialog ──> Deck
  └── menu (⋮): Rename, Duplicate, Delete (dialog)
```

Rules:
- Each tab is a place in the back stack's one entry; switching tabs does not add back-stack entries. Rotation keeps the tab, scroll position, search text and carousel position.
- After a successful import, the back stack is Pools, then Pool. Back from Pool returns to Pools.
- Share-to-app and "Open with" import the file and land on the new pool's Decks tab, back stack Pools, then Pool (replaces G-002's "lands on Results").
- Deleting the open pool or deck returns to its parent list with a snackbar and an Undo for the next 8 seconds.
- Launch always opens Pools. With no saved pool, Pools shows its empty state, which is one tap from Import pool.

## States per screen

**Offline, for every screen:** works. All deck, pool and score work is on the phone. Only card art can be missing; it falls back (Visual direction).

### Pools
Top bar: title "Pools", About icon. Body: a two-column grid of pool tiles. A tile shows the set code and name, the card count, the colour pips of the pool's two strongest colours by card count, and "<n> decks".
- **Empty.** "No pools yet." and one line: "Add a pool from a ManaBox CSV or pasted text." The floating "+" is the only action.
- **Loading.** Tiles appear as pools are read; no spinner is needed unless a read takes over a second.
- **Error (a saved pool unreadable).** That tile shows "Can't read this pool" and a Delete action; the other tiles work.

### Import pool
G-002's Pool screen with its states and copy (empty, wrong set, not a pool, saved-pool-unreadable), renamed. "Analyze pool" becomes "Add pool". On success the pool is saved as a new pool, never replacing another (changes G-002's "remembered pool" rule: many pools now).

### Pool, Cards tab
A search row ("Search pool"), a filter button (colour, type, in a deck or not), and a sort button (grade, mana value, colour). Cards are grouped under headed sections with a count, in this order: Creatures, Spells, Lands. Row: thumbnail, name, mana cost, the pool-adjusted grade chip, quantity owned ("×2"), and "in <n> decks".
- **Empty (filter).** "No cards match." with a "Clear filter" button.
- **Card tapped.** Opens the card detail's pool view.

### Pool, Decks tab
A grid of deck tiles in rank order. Tile: the art of the deck's highest-scored card (or the fallback), the deck's name ("<Colours> deck", for example "WUB · splash G"), "Edited" when so, the score, and colour pips with main colours first, a thin gap, then splash colours.
- **Building.** On a fresh import, three placeholder tiles with "Building decks…" until the builder returns; it runs on the phone, off the main thread.
- **No deck possible.** If the builder finds none (too few playables): one card, "This pool has too few playable cards for a 40-card deck.", the two shortfalls it counted, and the floating "+" still works (Empty deck).
- **Error.** "Couldn't build decks." with a "Try again" button. The pool is untouched.
- The floating "+" opens the New deck sheet.

### New deck sheet
Two buttons. **Build for colours** opens a picker: five colour toggles, a splash toggle row, and "Build"; the builder's best deck for that choice opens in a new Deck. **Empty deck** opens an empty Deck.
- **Colour choice with no result.** "No 40-card deck found in these colours." and the picker stays open.

### Deck, Cards tab
Header: the deck name, "<n> cards" (40 is the goal; a count other than 40 shows in the `accent` colour), colour pips. Below the tab bar: the "Add cards" row, a filter button, and a sort-or-group button (by type, by mana value, by colour). Cards sit in headed sections with counts: Creatures, Spells, Lands, then Sideboard (collapsed by default). Basics are one row per type with a quantity. Row: thumbnail, name, mana cost, grade chip, **one reason line** (the card's strongest reason, for example "fills the 2-drop gap"), and the stepper. A floating search button repeats "Add cards".
- **Stepper +** is disabled, with the line "All <n> in the pool are used", when the deck has every copy; basics never disable.
- **Stepper −** at 1 removes the card (Undo snackbar).
- **Over or under 40.** A banner under the header: "<n> cards. A sealed deck needs 40." It does not block anything.
- **Empty deck.** "No cards yet." and an "Add cards" button.

### Add cards
Search row, filter button, same filters as the pool's Cards tab. Lists only the pool's cards that have copies left ("<n> left"), best pool-adjusted grade first, with basics pinned at the top as five rows. Tapping a row adds one copy and keeps the screen open; a bar at the bottom shows "<n> added" and a Done button.
- **None left.** "Every card in this pool is in the deck."
- **No search match.** "No cards in this pool match."

### Deck, Stats tab (read-only)
Three panels, as in ManaBox's stats tab, from the deck's current cards:
1. **Mana value.** Stacked bars for values 0, 1 to 6 and 7+, coloured by colour identity (white, blue, black, red, green, colourless, multicolour), with each bar's count above it, "Average <x>" and "Total <n>". A dropdown switches the stack between "Colour" and "Type".
2. **Mana costs.** A pie of coloured mana symbols in the deck, with counts in the legend. A "Generic mana" switch adds the generic share.
3. **Mana production.** A pie of the colours the deck's lands and fixing can make, with counts. Each slice is a count and a letter, not colour alone.
Below: counts of creatures, removal, fixing cards and lands, and the land count against the builder's choice ("17 lands").
- **Empty deck.** "Add cards to see the numbers."

### Deck, Info tab
Four cards, top to bottom:
1. **How it plays.** A short description (about 4 to 6 sentences) of the deck: its colours and splash, its plan, which cards and themes carry it, and what to watch for. See "How the description is made". Heading "How it plays"; a small line "Written from the builder's numbers." beneath it.
2. **Score.** Total, then each scoring part with its signed value and its own reason line (the builder's parts: grades, curve, creatures, removal, castability, fixing, synergy).
3. **Mana.** The land split, the fixing lands and how often each colour is on time (the castability numbers).
4. **Sideboard.** The sideboard in order of grade, each with its grade. Tapping one opens its detail with "Why not in the deck".
- **Edited.** A line "You changed this deck. The text and score below match your changes." and a "Reset to built deck" button.
- **Empty deck.** Only the Score and Mana cards, with "No cards yet."

### Card detail
Over the list: the full card image in the middle, its neighbours peeking left and right; swiping moves through the list the player came from, in the same order. Bottom sheet below, always showing:
- **Name** and a quantity stepper (deck view only), the set and number.
- **Grades.** "Set grade <x>" and, when different, "In this pool <y>", each with a word for why they differ ("your pool backs it up", "colour is thin").
- **In a deck: "Why it's in this deck".** One line per scoring part that gave the card a number, each with its signed value and wording (for example "+0.8 grade", "+0.4 fills the 2-drop gap", "+0.6 fixing for your splash", "−0.2 hard to cast on time (84%)"). Parts that gave nothing are not listed. A card the player added by hand reads "Added by you" and shows only its grades.
- **Synergy.** Its strongest partners that are in this deck (deck view) or in this pool (pool view), each with the pair score.
- **Chips.** "Mainboard" and "Sideboard", tappable to move the card (deck view).
- **Pool view.** No stepper or chips; it shows "In <n> decks" and a list of those decks.
- **Sideboard card.** "Why not in the deck": the score change if it replaced the weakest card of the same kind, from the same parts. If the builder cannot say, "Close call. Its grade is below the deck's weakest <type>."
- **No art.** The fallback card in the carousel shows the name, mana cost, type line and rules text from the data the app already holds.
- **Loading art.** The fallback until the image arrives; the sheet is usable at once.

### Delete dialogs
"Delete <name>?" with "Delete" and "Keep". Deleting a pool also says "This also deletes its <n> decks."

## How the description is made

The description is **text made by code from the deck's own numbers**: no model call (the product constraints keep models out of the metric engine, and nothing here may hold an API key). It is assembled from fixed sentence templates, filled from data the builder already has, and it stays alterable like every scoring part: templates and thresholds live in a versioned weights-style file under `fixtures/`, never in code, each part writes its own sentence, and a test shows a test-only part adds a sentence with no other change.

Sentences, each present only when its data supports it:
1. **Shape.** Colours and splash, and the land count ("A <colours> deck with a <splash> splash on 17 lands.").
2. **Plan.** From the mean mana value and the creature and removal counts: aggressive, midrange or controlling, named by thresholds from the file (the app does not assume one set's shape; thresholds come from the set's own data, per "every set is different").
3. **What carries it.** The top synergy axes in the deck by score, and up to three cards that carry the most score.
4. **Mana.** The fixing it relies on and the card or colour least reliable to cast.
5. **Watch for.** The curve gap or thin creature count the score shows, if any.

A model-written version using the player's own key is a later goal (agent layer). This spec leaves the card's slot and heading unchanged so it can drop in.

## Component map

New, in the `app` module's Compose UI. Names are for Developers to find or create; none exists yet.

| Component | Used on |
|---|---|
| `PoolsScreen`, `PoolTile` | Pools |
| `PoolScreen` (tab host), `PoolCardsTab`, `PoolDecksTab`, `PoolAnalysisTab` (the G-002 Results content) | Pool |
| `DeckTile` | Pool Decks tab |
| `NewDeckSheet`, `ColourPicker` | New deck sheet |
| `DeckScreen` (tab host), `DeckCardsTab`, `DeckStatsTab`, `DeckInfoTab` | Deck |
| `CardRow`, `CardSection`, `CardStepper`, `GradeChip`, `ReasonLine` | Pool Cards, Deck Cards, Add cards |
| `AddCardsScreen` | Add cards |
| `ManaValueChart`, `ColourPie`, `ChartLegend` | Stats tab |
| `DescriptionCard`, `ScoreCard`, `ManaCard`, `SideboardCard` | Info tab |
| `CardPager`, `CardSheet`, `ReasonList`, `PartnerList` | Card detail |
| `CardArt` (image with the no-art fallback), `ManaPip`, `PipRow` | everywhere |
| `PoolFab` (speed-dial) | Pools, Pool Decks tab |
| `SpaTopBar`, `SpaTabs`, `SpaDialog`, `SpaSnackbar` | G-002 components, reused |

The deck's description, the score parts and the reason lines come from `agent` and `core`, not from the screens. A screen never computes a score; it renders a list of parts and lines it is given. That keeps scoring alterable (G-005's constraint) with no screen change.

## Accessibility checklist

Per screen unless stated. All of G-002's rules still apply.
- **Targets.** Every control is at least 48 × 48dp, including the stepper buttons, the filter and sort buttons and the chips. The stepper's two buttons are 48dp apart.
- **Labels.** Every icon has a content description ("Add one <card>", "Remove one <card>", "Filter", "Sort", "More options"). A card row reads as one item: "<name>, <quantity>, mana cost <cost>, grade <x>, <reason>".
- **Not by colour alone.** Mana pips carry their letter or number as text; the pies and bars carry counts; "Edited", "over 40" and "below typical" are words, not just `accent`.
- **Contrast.** Text meets 4.5:1 on its background; chart colours and chips 3:1; both checked by script and pinned by a test, as in G-002.
- **Text scaling.** Layouts hold at 200% font size: rows grow in height, reason lines wrap (never truncate to one line at that size), the stepper stays reachable.
- **Charts.** Each chart has a text equivalent a screen reader reads in order ("Mana value 3: 7 cards, 3 blue, 2 red…"), and the pies a legend list.
- **Carousel.** Swiping has two alternative controls: "Previous card" and "Next card" buttons, and the card is a heading for the sheet beneath.
- **Art.** Card art has no description of its own (the name carries it); the fallback never hides the name.
- **Focus order** (TalkBack): top bar, tabs, search row, sections in order, floating button last.

## Snapshot list

Screens and states that get snapshot tests (G-002's gate step), each at default and 200% font size:
- Pools: empty, three pools, one unreadable.
- Pool Cards tab: default, filter with no match.
- Pool Decks tab: building, three decks, an edited deck, no deck possible.
- New deck sheet: default, colour picker, no result.
- Deck Cards tab: default, empty, over 40, a card with all copies used, sideboard expanded.
- Add cards: default, none left.
- Deck Stats tab: default, empty.
- Deck Info tab: default, edited, empty.
- Card detail: deck view with reasons, hand-added card, sideboard card, pool view, no-art fallback.
- Delete dialog (pool, deck).

## Not decided here (belongs to tickets, not to the owner)

- **The builder on the phone.** The builder and the pool scoring run in the `agent` module on the JVM today. Running them on the device, and bundling each supported set's grades, tags and weights, is the first work a goal must plan. The spec assumes it.
- **Storage** for many pools and edited decks, and what "Undo" keeps.
- **Which sets** the first release supports (today: `trk`, with `msh`, `hob`, `fra` data used for tuning).

## Open questions for the owner

Each has a default. An unanswered question takes the default.

1. **Card art and the network.** G-002's app has no network permission. Art needs one. Default: allow it only for fetching Scryfall card images on demand, cached on the phone, with the no-art fallback for everything else; the rest of the app stays offline. The alternative is no art at all, using the fallback tiles and the text card.
2. **Many pools.** Default: the app saves many pools (G-002 kept one), because the Pools list is the new home.
3. **Basic lands.** Default: unlimited in any deck, even if the pool's CSV lists none.
4. **Editing.** Default: decks are editable, re-score live, and show "Edited" with a reset to the built deck. The alternative is read-only decks.
5. **The description.** Default: made by code from templates now (above); a model-written one later, with the player's own key.
6. **Pool tabs.** Default: Cards, Decks, Analysis (the old Results). The alternative is dropping Analysis from the app and keeping it only in the command line.
7. **Deck names.** Default: "<Colours> deck" ("WUB · splash G"), renamable.

## Settled

(Nothing yet. This section receives the owner's answers.)
