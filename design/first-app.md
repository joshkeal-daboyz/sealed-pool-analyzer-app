# First app: pool entry, results, About

- Goal: G-002 (`tickets/goals/G-002-first-app.md`), deliverables 0, 3, 4, 5, 7.
- Written by the Designer, 2026-11-03. Every G-002 ticket that changes `app/src/main` cites this file in `design_ref`.
- Who and where: one player at a prerelease table, on a phone (the owner's is a Pixel 6), in a hurry, often one-handed, often in poor light. Fewer taps beat more options. The app has no network and no account.

This spec gives states and words, not pixels. Where this file gives text in quotes, that text is the copy: tests may assert it. Text in angle brackets (`<file name>`) is filled in from data.

## Decisions this spec makes

These are the Designer's calls inside G-002's scope. Each one is used below.

1. **Launch goes where the pool is.** With a remembered pool, launch opens the Results screen. With none, it opens the Pool screen. Nobody at the table should have to tap to get back to their numbers.
2. **Problems come first, and stay open.** Unresolved lines and kit warnings are the first section under the pool header on Results, always expanded, never truncated. The numbers follow them.
3. **Every number carries its typical value on the same row**, written as "typical <median> (<p10>–<p90>)". When `p10` equals `p90`, only "typical <median>" is written. A value below `p10` adds the words "below typical"; above `p90` adds "above typical". Colour may echo this, but the words are always there.
4. **Ten pairs are a list; one pair is a screen.** Results lists all ten pairs with their two headline numbers. Tapping a pair opens Pair detail with every pair metric. The list is sorted by the pair's card count, highest first, ties in the order WU, WB, WR, WG, UB, UR, UG, BR, BG, RG. The sort is stated on screen ("Sorted by cards in the pair") so it does not read as a recommendation.
5. **A failed import never touches the remembered pool.** Only a successful import replaces it.
6. **A pool with no resolved entry is an error, not a results page.** Empty, wrong set, and not a pool are told apart by rule (see Pool screen states).
7. **Clear pool asks once.** A confirmation dialog, because the pool may be the only copy the player has at the table.
8. **The theme is always dark.** One fixed dark scheme, no dynamic Material You colour, and the system's light setting is ignored in G-002, so contrast and snapshot tests give the same result on every device. The scheme is in Visual direction.
9. **Not shown in G-002:** `castProbability` from the engine (the goal does not ask for it and it needs wording the goal does not give). See Settled, item 4.

## Visual direction

Direction "B · Low light", chosen by the owner on 2026-11-03 (artboards B-Pool and B-Results on the design canvas). The values in this section are the source of truth.

**Theme.** Always dark (decision 8). There is no light scheme in G-002.

**Colour tokens.** Names for `SpaColors` in `SpaTheme`:

| Token | Value | Used for |
|---|---|---|
| `ground` | `#121417` | Background. |
| `surface` | `#1C2026` | Cards, rows, the text field. |
| `onSurface` | `#ECEFF3` | Primary text. |
| `onSurfaceVariant` | `#B8C0CA` | Secondary text, typical values. |
| `muted` | `#8C96A3` | The "or" divider text, chevrons. |
| `outline` | `#6B7480` | Button, chip, and text-field borders. The canvas drew `#4A535E`, which is 2.1 to 2.4:1 and fails the 3:1 non-text rule; `#6B7480` replaces it. |
| `divider` | `#2E343C` | Decorative only. |
| `accent` | `#F2B544` | Primary button fill, the Problems border and icon, the selected chip's border. |
| `onAccent` | `#1A1406` | Text on the accent. |
| `accentText` | `#FFE2A3` | The Problems summary line, the "below typical" / "above typical" words, selected-chip text, the Analyze button text. |
| `selectedChip` | `#3A2E12` | Selected chip fill. |

**Measured contrast** (WCAG formula, computed by script 2026-11-03; A3's test pins these):

| Foreground on background | Ratio |
|---|---|
| `onSurface` on `ground` | 16.00 |
| `onSurface` on `surface` | 14.18 |
| `onSurfaceVariant` on `surface` | 8.90 |
| `onSurfaceVariant` on `ground` | 10.04 |
| `accentText` on `ground` | 14.64 |
| `accentText` on `surface` | 12.97 |
| `onAccent` on `accent` | 10.00 |
| `accentText` on `selectedChip` | 10.55 |
| `muted` on `ground` | 6.16 |
| `accent` on `ground` | 10.08 |
| `outline` on `ground` | 3.90 |
| `outline` on `surface` | 3.45 |

**Type.** Atkinson Hyperlegible (regular 400, bold 700) for everything, and Atkinson Hyperlegible Mono (400) for pasted text and unread-line text. Both are under the SIL Open Font License and are bundled in the APK as font resources (the app has no network, so no downloadable fonts). Sizes in sp:

| Text | Size |
|---|---|
| Screen title | 20 bold |
| Pool heading ("Add your pool") | 30 bold |
| Section headings | 22 bold |
| Row labels | 18 bold |
| Secondary and typical lines | 15 to 16 |
| Big row value | 34 bold, tabular figures |
| Primary button | 19 bold |
| Outlined button | 16 bold |

**Shape and layout.**

- Value first. A metric row and a pair row put the value large on the left, with the label, the typical line, and the outside-typical words to its right.
- Each row is its own `surface` card (corner radius 14dp, 10dp gap between cards), not a divided list.
- The pool header card and the Problems section have radius 18dp.
- Primary buttons are 64dp tall, radius 16dp.
- Format chips are pills 48dp tall.
- The outlined buttons (New pool, Clear pool) are pills 52dp tall.
- Problems is an outlined section (2dp `accent` border) with a warning-triangle icon beside its heading; each unread line and kit warning is a `surface` card inside it.

## Screen inventory

| Screen | Route | Purpose |
|---|---|---|
| Pool | `pool` | Get a pool in: open a file, or paste text with an auto-detected or chosen format. Shows import errors. |
| Results | `results` | One pool's problems, totals, the ten pairs, cards per colour, and fixing, each number beside its typical `trk` value. Holds New pool and Clear pool. |
| Pair detail | `pair/{pair}` (`pair` is `WU`, `WB`, ... `RG`) | Every metric for one colour pair: cards, creatures, curve, removal, interaction, fixing, splash candidates, and land recommendation, each beside its typical value. |
| About | `about` | Scryfall attribution and Fan Content notice word for word, app version, and the date of the bundled card data. |
| Clear pool dialog | (dialog over Results) | Confirms clearing the remembered pool. |

Share-to-app and "Open with" add no screen: they land on Results.

## Navigation flow

```
launch ── remembered pool? ── yes ──> Results (Loading) ──ok──> Results
   │                                         └──re-read fails──> Pool (Saved pool unreadable)
   └── no ──> Pool (Empty)

share / Open with (CSV) ──> Results (Loading) ──ok──> Results          [back stack: Results only]
                                    └──fails──> Pool (error card)       [back stack: Pool only]

Pool ── "Open file" ──> system picker ── pick ──> Results (Loading) ──ok──> Results
  │                                   └─ cancel ──> Pool, unchanged
  ├── paste + "Analyze pool" ──> Results (Loading) ──ok──> Results
  │                                     └──fails──> back to Pool (error card, pasted text kept)
  └── About icon ──> About ── back ──> Pool

Results ── pair row ──> Pair detail ── back ──> Results (same scroll position)
   ├── "New pool" ──> Pool ── back ──> Results (pool unchanged)
   ├── "Clear pool" ──> dialog ── "Clear pool" ──> Pool (Empty), snackbar "Pool cleared"
   │                          └── "Keep pool" / back / outside tap ──> Results
   └── About icon ──> About ── back ──> Results
```

Rules:

- After a successful import the back stack is just Results; system back from Results leaves the app. "New pool" pushes Pool on top of Results, so back returns to the current pool.
- After Clear pool, Pool is the only screen in the stack.
- A share or "Open with" that arrives while the app is open (on any screen, including during another import) starts a new import and replaces the stack as above. The latest import wins; an earlier one still running is discarded.
- About is one tap from both possible first screens (Pool and Results) through the same top-bar icon. Pair detail also has it.
- Rotation keeps the current screen, the scroll position, the pasted text, and the chosen format. Process death keeps only the remembered pool (deliverable 7); unsent pasted text is lost, which is acceptable.

## States per screen

**Offline, for every screen:** no different. The app holds no network permission and reads no network, so there is no offline indicator, banner, or retry anywhere. The one outward link (Scryfall on About) hands off to the phone's browser, which deals with its own connection.

### Pool screen

Top bar: title "Sealed Pool Analyzer"; a back arrow only when Pool was opened with "New pool"; an About icon (info icon). Body, top to bottom: error card (only in an error state), the "Open file" button, a divider with the word "or", the paste box, the format chooser, the detected-format line, and the "Analyze pool" button.

| State | What the user sees | What the user can do |
|---|---|---|
| **Empty** (first launch, or after Clear pool) | Heading "Add your pool". Line "Open a ManaBox CSV, or paste a list." "Open file" button. Empty paste box labelled "Paste your pool" with the hint "ManaBox CSV, a list with counts, or collector numbers". Format chooser on "Auto". No detected-format line. "Analyze pool" disabled. | Open file; paste; open About. |
| **Pasted, format detected** | The pasted text (box grows to about 8 lines, then scrolls inside itself). Format chooser on "Auto". Line "Detected: <format name>", updated when typing pauses. "Analyze pool" enabled. | Analyze; change the format; edit the text. |
| **Pasted, format chosen by hand** | The chosen chip selected; line "Reading as <format name> (chosen)". The detected format is not shown. | Analyze; tap "Auto" to go back to detection. |
| **Text that is only spaces or blank lines** | As Empty, but with the text in the box. "Analyze pool" disabled. | Edit the text. |
| **Picker cancelled** | Pool screen exactly as before opening the picker. No message. | Anything above. |
| **Opened from Results ("New pool")** | As Empty, plus the back arrow. The paste box is empty, not filled with the current pool. | Back returns to the current Results; anything above. |
| **Error: empty file** | Error card titled "This file is empty", body "<file name> has no cards in it.", above the inputs. | Open another file; paste. The remembered pool, if any, is unchanged and back returns to it. |
| **Error: not a pool** | Error card titled "This doesn't look like a pool", body "None of the <n> lines in <source> is a trk card." Then the first 3 unread lines, each with line number, text, and reason (the same item as on Results), and a "Show all <n> lines" button when there are more. | Expand the list; open another file; edit the pasted text (it is kept) and analyze again; change the format. |
| **Error: wrong set** | Error card titled "This pool is from another set", body "Every card in <source> is from a set other than trk. This app reads Star Trek (trk) and its bonus sheet (sds) only." Then the first 3 lines as in Not a pool, with "Show all <n> lines". | Same as Not a pool. |
| **Error: can't read the file** | Error card titled "Couldn't read this file", body "<file name> couldn't be opened as text. Try exporting it again, or paste its contents." | Open another file; paste. |
| **Error: saved pool unreadable** (launch, the remembered pool fails to re-read) | Error card titled "Your last pool couldn't be read", body "<source> was saved, but the app can't read it now." A "Clear pool" button in the card (no extra confirmation here: the pool is already unusable). | Clear it; open a file; paste. |
| **Error: card data missing** (the bundled set data fails to load; should never happen in a correct build) | Error card titled "Card data is missing", body "The app's trk card data didn't load. Reinstall the app." All inputs disabled. | Open About; leave. |
| **Loading** | Not on this screen: every import moves to Results (Loading). | — |

Classifying a finished import that resolved zero entries (`pool.entries == 0`):

- zero unresolved lines → **Empty** (covers a 0-byte file, only blank lines, and a CSV with only a header row);
- every unresolved line has reason `wrong-set` → **Wrong set**;
- anything else → **Not a pool**.

**Can't read** is any failure to get text: the file can't be opened (missing, permission revoked), reading throws, or the bytes are not valid UTF-8 text. `<source>` is the file name, or "the pasted text". Error cards are announced politely to TalkBack when they appear.

Format names, used everywhere: "ManaBox CSV", "List with counts", "Collector numbers". Chooser chips, in order: "Auto", "ManaBox CSV", "List with counts", "Collector numbers". The chooser applies to pasted text; a file is always auto-detected.

### Results screen

Top bar: title "Your pool"; About icon. Body is one vertical scroll, in this order:

1. **Pool header card.** Source: "<file name>" or "Pasted". Line "Read as <format name>". Line "<totalCards> cards in <entries> entries". When `skippedLines > 0`, line "<n> blank or header lines skipped". Two buttons side by side (stacked at large font): "New pool" and "Clear pool".
2. **Problems section** (heading "Problems"). See states.
3. **Legend line**: "Typical: the median of <kits> simulated trk pools, with the middle 80% (10th to 90th percentile)." (`kits` from the baseline file.)
4. **Pool at a glance** (heading). Metric rows: Total cards, Nonland cards, Basic lands, Nonbasic lands, Removal, Interaction, Fixing sources.
5. **Colour pairs** (heading), with "Sorted by cards in the pair" under it. Ten pair rows. Each row: the pair's name ("White–Blue (WU)"), "<cards> cards, typical ...", "<creatures> creatures, typical ...", and a chevron. The whole row is one tap target opening Pair detail.
6. **Cards per colour** (heading). One row per colour in the order White, Blue, Black, Red, Green, Multicolour, Colourless. Collapsed: "Cards <total>, typical ..." and "Playables <total>, typical ...". Tapping the row expands it in place to add Creatures, Removal, Interaction, then cards and playables by rarity (common, uncommon, rare, mythic), each with its typical value.
7. **Fixing sources** (heading). One line per source: name, the colours it makes as names, and the count when above 1. When the list is empty: "No fixing sources".

| State | What the user sees | What the user can do |
|---|---|---|
| **Loading** (after any import, and at launch with a remembered pool) | Top bar; pool header with the source and "Reading your pool…" in place of the counts; a progress indicator in the body; no buttons. Nothing else. The indicator appears only if loading passes 300 ms, to avoid a flash. | Open About; system back (leaves the app, or returns to Pool if Pool is under it). |
| **Clean pool** (no unresolved lines, no kit warnings) | Problems section is one line: "Every line read. Kit check passed." Then the numbers. | Open a pair; expand a colour; New pool; Clear pool; About. |
| **Unresolved lines** | Problems section first line: "<n> lines not read. The numbers below leave them out." Then each unresolved line as an item: "Line <line>", the line's text in full (monospace, wrapping, never ellipsized), and the reason in words with the code after it, e.g. "No trk or sds card has this name (unknown-name)". | As Clean, plus "Skip to numbers" (see next row). Fix the source and import again. |
| **Long problem list** (more than 5 items in the section) | A "Skip to numbers" button at the top of the Problems section. | Tap it to scroll to Pool at a glance. |
| **Kit warnings** | After any unresolved lines, each warning as an item: a title from its code, "<actual>, expected <min> to <max>", and the engine's `message` verbatim. | As Clean. |
| **Some lines from another set** (at least one unresolved line with reason `wrong-set`, and some lines resolved) | The unresolved items as above, and one extra line at the top of the section: "Some cards are from another set. This app reads trk and its sds bonus sheet only." | As Clean. |
| **Clear pool dialog open** | Dialog titled "Clear this pool?", body "The app will forget <source>. To see it again you'll need to import it again." Buttons "Keep pool" and "Clear pool". | "Clear pool" deletes the pool file and goes to Pool (Empty) with snackbar "Pool cleared". "Keep pool", back, or tapping outside closes the dialog. |
| **Error** | Results has no error state of its own: a failed import goes to the Pool screen's error card (navigation rules). | — |
| **Empty** | Not reachable: with no pool, the app shows Pool. | — |

Reason words (unknown codes show the code alone):

| Code | Words |
|---|---|
| `unknown-name` | "No trk or sds card has this name" |
| `unknown-collector-number` | "No trk or sds card has this collector number" |
| `wrong-set` | "This card is from another set" |

Kit warning titles (unknown codes show the code): `card-count` → "Card count"; `high-count` → "Too many copies".

### Pair detail screen

Top bar: back arrow; title "<pair name>", e.g. "White–Blue (WU)"; About icon. Body is one vertical scroll:

1. When the pool has unresolved lines: a line "This pool has <n> lines not read; these numbers leave them out."
2. **Lands** (heading): "<n> <basic land name>" per colour, e.g. "6 Plains" and "11 Island", each with its typical value. Basic land names: W Plains, U Island, B Swamp, R Mountain, G Forest.
3. **Cards** (heading): metric rows Cards, Creatures, Noncreatures, Removal, Interaction, Fixing sources.
4. **Curve** (heading): one row per mana value bucket 0 to 7, each "Mana value <mv>" (the last bucket reads "Mana value 7+", since it holds 7 or more), then "<n> creatures, typical ..." and "<n> other, typical ...". Rows, not a grid, so it reflows at large font.
5. **Splash candidates** (heading): "<count> splash candidates, typical ..." and then one line each: card name, colour name, and the reason in words ("removal" for `REMOVAL`, "rare or mythic" for `RARE_OR_MYTHIC`, the code for anything else). When there are none: "No splash candidates".

| State | What the user sees | What the user can do |
|---|---|---|
| **Normal** | As above. | Scroll; back to Results; About. |
| **Pool has unresolved lines** | The reminder line at the top. | As Normal. |
| **No splash candidates** | "0 splash candidates, typical ..." then "No splash candidates". | As Normal. |
| **Empty pair** (0 cards in the pair) | Every row shows 0 with its typical value and "below typical" where it applies; curve rows all 0. | As Normal. |
| **Loading, error, empty** | Not reachable: Pair detail opens only from loaded Results. A share arriving here starts a new import (navigation rules). | — |

### About screen

Top bar: back arrow; title "About". Body, one vertical scroll:

1. App name "Sealed Pool Analyzer" and "Version <versionName>" (from the build).
2. "Card data snapshot: <date>" where `<date>` is the date part (YYYY-MM-DD, UTC) of `fixtures/scryfall/trk/fetched-at.txt` as bundled in the APK; today that file reads `2026-11-03T18:19:53Z`, so the line reads "Card data snapshot: 2026-11-03".
3. Heading "Card data", then the README's Card data section's first two sentences, word for word:

   > Card data comes from Scryfall and is used under Scryfall's terms. Sealed Pool Analyzer is unofficial Fan Content: it is not produced by, endorsed by, or affiliated with Scryfall or Wizards of the Coast, and Magic: The Gathering is a trademark of Wizards of the Coast.

   The word "Scryfall" in the first sentence is a link to `https://scryfall.com`, opened in the phone's browser. The README's third sentence is about the repository's fixtures folder and is not shown.

| State | What the user sees | What the user can do |
|---|---|---|
| **Normal** | As above. | Tap the Scryfall link; back. |
| **Loading, empty, error** | None: everything on this screen is bundled at build time. A missing `fetched-at.txt` must fail the build, not show a blank. | — |

## Component map

All under `app/src/main/kotlin`, package `spa.app` unless a ticket says otherwise. Names are what a Developer searches for; one composable per name.

**App shell**

- `SpaApp`: the root composable; applies `SpaTheme`, hosts `SpaNavHost`.
- `SpaTheme`: Material 3 `MaterialTheme` with `SpaColors` (the one dark scheme in Visual direction, no dynamic colour) and the Atkinson Hyperlegible type scale in `sp`.
- `SpaNavHost`: Navigation Compose `NavHost` with the routes in the screen inventory.
- `SpaTopBar(title, onBack?, onAbout)`: `TopAppBar` with an optional back `IconButton` and the About `IconButton` (`Icons.Outlined.Info`).
- `PoolViewModel` with `PoolUiState`: `NoPool`, `Loading(source)`, `Loaded(result, source, format)`, `ImportFailed(kind, source, lines)`, `DataMissing`; `ImportFailureKind`: `EMPTY`, `NOT_A_POOL`, `WRONG_SET`, `UNREADABLE`, `SAVED_POOL_UNREADABLE`. Tests drive screens through these states.

**Pool screen**: `PoolEntryScreen`

- `OpenFileButton`: `Button` launching `ActivityResultContracts.OpenDocument` for CSV and plain-text types.
- `PasteBox`: `OutlinedTextField`, multi-line, with a visible `label` (not a placeholder only).
- `FormatChooser`: `FlowRow` of `FilterChip`s, single choice, wraps at large font.
- `DetectedFormatLine`: `Text`.
- `AnalyzeButton`: `Button`.
- `ImportErrorCard(kind, source, lines)`: `Card` with title, body, optional `UnresolvedLineItem` list and "Show all" `TextButton`, optional "Clear pool" `Button`.

**Results screen**: `ResultsScreen`

- `PoolHeaderCard`: `Card` with source, format, counts, and `NewPoolButton` / `ClearPoolButton` (`OutlinedButton`s in a `FlowRow`).
- `ProblemsSection`: heading, `AllClearLine` or `ProblemSummaryLine`, `WrongSetNote`, `SkipToNumbersButton`, then `UnresolvedLineItem` and `KitWarningItem` items.
- `BaselineLegend`: `Text`.
- `MetricRow(label, value, stat)`: the shared row: label, value, `TypicalRange(stat)`, and `OutsideTypicalTag` ("below typical" / "above typical") when it applies. One merged semantics node.
- `TypicalRange(stat)`: formats "typical <median> (<p10>–<p90>)".
- `PairList` of `PairRow(pair)`: whole-row `clickable` with role Button.
- `ColorBreakdown` of `ColorRow(color)`: expandable, with `RarityBreakdown` inside.
- `FixingSourceList`.
- `ClearPoolDialog`: `AlertDialog`.
- `LoadingState(source)`: `CircularProgressIndicator` and the "Reading your pool…" line.

**Pair detail screen**: `PairDetailScreen`

- `UnreadLinesReminder`, `LandRecommendationRows`, `MetricRow`, `CurveTable` of `CurveRow(bucket)`, `SplashCandidateList` of `SplashCandidateItem`.

**About screen**: `AboutScreen`

- `AppInfoRows` (name, version, snapshot date), `AttributionText` (the two sentences as one `Text` with a link annotation on "Scryfall").

**Shared names**: `PairNames` (pair code → "White–Blue (WU)"), `ColorNames` (W, U, B, R, G, MULTI, COLORLESS → "White", "Blue", "Black", "Red", "Green", "Multicolour", "Colourless"), `ReasonText` (reason codes → words), `BasicLandNames`. Pure Kotlin, unit tested.

## Accessibility checklist

Each item names how it is checked. "UI test" means a Compose UI test on the JVM in the gate; "Emulator" means an Acceptance Tester scenario on `spa-test`. The ticket that builds the screen names the test.

**Every screen** (each item applies to Pool, Results, Pair detail, About, and the Clear pool dialog)

- A1. Every node with a click action is at least 48 × 48 dp. UI test: for each screen state on the snapshot list, every node matching `hasClickAction()` asserts `assertWidthIsAtLeast(48.dp)` and `assertHeightIsAtLeast(48.dp)` (on its touch bounds).
- A2. Every icon-only button has a content description: About icon "About", back arrow "Back". UI test: `onNodeWithContentDescription("About")` exists and is clickable on Pool, Results, and Pair detail; `"Back"` on Pair detail, About, and Pool when opened from New pool.
- A3. Text contrast is at least 4.5:1 for body text and 3:1 for large text, icons, and borders. JVM unit test over `SpaColors`: each of these pairs computes to the ratio in Visual direction's contrast table (to two decimals) and meets its threshold: `onSurface`/`ground`, `onSurface`/`surface`, `onSurfaceVariant`/`surface`, `onSurfaceVariant`/`ground`, `accentText`/`ground`, `accentText`/`surface`, `onAccent`/`accent`, `accentText`/`selectedChip`, `muted`/`ground`, `accent`/`ground` (4.5:1); `outline`/`ground`, `outline`/`surface` (3:1).
- A4. Accessibility Test Framework checks pass. UI test: with `enableAccessibilityChecks()` (Compose UI test's ATF integration) on, each snapshot-list state renders with no ATF error (touch target, contrast, missing label, duplicate clickable).
- A5. Text scales. UI test: at `fontScale = 2.0f` every screen state on the snapshot list has no `Text` with visual overflow (`TextLayoutResult.hasVisualOverflow` false via the `GetTextLayoutResult` semantics action), and every action named in that state's table is reachable with `performScrollTo()` and clickable. No text size is set in `dp`.
- A6. Section headings are headings. UI test: "Add your pool", "Problems", "Pool at a glance", "Colour pairs", "Cards per colour", "Fixing sources", "Lands", "Cards", "Curve", "Splash candidates", "Card data" each have `SemanticsProperties.Heading`.
- A7. Nothing is told by colour alone. UI test: a value outside its typical range has the text "below typical" or "above typical" in its merged node.
- A8. TalkBack reads the main flow. Emulator: with TalkBack on, import `trk-text-misspelled-01.txt` by paste, and swipe through Results: the problem summary is read before any metric, and each metric row is read as one item.

**Pool screen**

- P1. The paste box has the accessible label "Paste your pool". UI test: `onNode(hasSetTextAction() and hasText("Paste your pool"))` exists (label, not placeholder).
- P2. The format chooser exposes selection. UI test: each chip has role and selected state; after tapping "Collector numbers", that chip `assertIsSelected()` and "Auto" `assertIsNotSelected()`.
- P3. "Analyze pool" disabled state is exposed. UI test: `assertIsNotEnabled()` when the box is empty or whitespace, `assertIsEnabled()` with text.
- P4. Error cards are announced. UI test: the `ImportErrorCard` has `LiveRegionMode.Polite`.

**Results screen**

- R1. Problems come first in reading order. UI test: in the semantics tree of `trk-text-misspelled-01.txt`'s Results, every unresolved and kit-warning node comes before the first `MetricRow` node.
- R2. Each metric row is one node read as a sentence. UI test: the "Removal" row of `trk-manabox-01.csv` is a single merged node whose text includes the label, the value, and "typical <median> (<p10>–<p90>)", with the numbers read from the golden and baseline files, not typed in.
- R3. Pair rows are one button each, named by the pair. UI test: the row for WU has `Role.Button` and its text includes "White–Blue (WU)"; clicking it opens Pair detail.
- R4. Colour rows expose expanded/collapsed. UI test: a `ColorRow` has `stateDescription` "Collapsed", then "Expanded" after a click.
- R5. The loading indicator has a description. UI test: in Loading, a node with content description "Reading your pool" exists.
- R6. Unread line text is never cut. UI test: at `fontScale = 2.0f` with `trk-manabox-wrong-set-01.csv`, each unresolved line's full text is present in the tree and `hasVisualOverflow` is false.

**Pair detail screen**

- D1. Land counts read with names, not letters. UI test: for `trk-manabox-01.csv` WU, the lands section contains "6 Plains" and "11 Island" (values read from the golden file).
- D2. Curve rows are read one bucket per item. UI test: each `CurveRow` is a single merged node containing "Mana value <mv>" ("Mana value 7+" for the last bucket).

**About screen**

- B1. The attribution is word for word. UI test: the About screen's text equals the two README sentences, read from `README.md` at test time, not typed in.
- B2. The Scryfall link is reachable as a link. UI test: the `AttributionText` node exposes a link to `https://scryfall.com` (link annotation), and its touch target meets A1 or the whole sentence is the target.
- B3. About is one tap from the first screen. UI test: from Pool (no pool) and from Results (remembered pool), clicking the "About" node shows the About screen.

**Clear pool dialog**

- C1. Buttons say what they do. UI test: the dialog has buttons "Clear pool" and "Keep pool", and no "OK" or "Cancel".
- C2. Focus is trapped in the dialog. Emulator: with TalkBack on, swiping from the dialog never reaches Results content.

## Snapshot list

Snapshot tests render on the JVM (no emulator), on a Pixel 6 sized surface (411 × 914 dp), font scale 1.0 unless the name says otherwise. The theme is always dark, so every snapshot is dark. Each name is the test's name; the ticket that builds the screen adds them. Pool data comes from the named fixture through the app's import path.

| # | Snapshot | Fixture / setup | Why it is pinned |
|---|---|---|---|
| 1 | `pool_empty` | No remembered pool | The first screen. |
| 2 | `pool_empty_fontScale200` | As 1, font scale 2.0 | The first screen at large text. |
| 3 | `pool_pasteDetected` | `trk-collector-01.txt` pasted, Auto | Detection line and enabled Analyze. |
| 4 | `pool_errorNotAPool` | A text file of 10 lines that are not cards | The error card with its lines list (shared by Wrong set). |
| 5 | `pool_errorSavedPoolUnreadable` | Remembered pool file that fails to read | The only error card with an action button. |
| 6 | `results_loading` | Launch with remembered `trk-manabox-01.csv`, held in Loading | What launch shows first. |
| 7 | `results_clean` | `trk-manabox-01.csv` | The main screen, all-clear problems line. |
| 8 | `results_clean_fontScale200` | As 7, font scale 2.0 | Goal criterion 12. |
| 9 | `results_problems` | `trk-text-misspelled-01.txt` | Unresolved lines and a kit warning before the numbers. |
| 10 | `results_problems_fontScale200` | As 9, font scale 2.0 | Long lines at large text, where wrapping breaks first. |
| 11 | `results_wrongSetLines` | `trk-manabox-wrong-set-01.csv` | The wrong-set note and long CSV lines. |
| 12 | `results_clearPoolDialog` | As 7, dialog open | The one destructive action. |
| 13 | `pairDetail_WU` | `trk-manabox-01.csv`, WU | Every pair metric, curve, splash list. |
| 14 | `pairDetail_WU_fontScale200` | As 13, font scale 2.0 | Curve rows reflowing at large text. |
| 15 | `about` | — | Goal criterion 11. |
| 16 | `about_fontScale200` | As 15, font scale 2.0 | The legal text at large text. |

Snapshots capture the whole scrollable content (not just the first viewport) where the snapshot library allows; the ticket says which.

States without a snapshot are covered by UI tests asserting their copy: Pool's other error cards (empty, wrong set, can't read, card data missing), format chosen by hand, Results' long-list "Skip to numbers", expanded colour row, Pair detail's empty pair and no-splash states.

## Settled (2026-11-03)

1. **Per-pair playables:** owner decision, dropped from G-002. Playables are shown per colour only (Results, Cards per colour); no `core` change.
2. **Fixing count:** settled from code. `MetricFlattener` keeps a dropped list's length as `<pointer>/count`, so `/fixingSources/count` is the number of entries in `metrics.fixingSources`; Pool at a glance shows that number.
3. **Curve bucket 7:** settled from code. `Curve.kt` has eight buckets, 0 to 6 and then 7 or more (`TOP = 7`), so the last row reads "Mana value 7+".
4. **`castProbability`:** owner decision, not shown in G-002.
5. **File size limit:** none. An import ticket that finds a problem with very large files adds a limit to this spec, shown as "Couldn't read this file".
